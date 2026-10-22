"""Publish verified QA bytes, then switch only this app's public channel manifest.

Runs after Gradle tests. GITHUB_TOKEN is confined to CI. Never publishes a stable release.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
import urllib.request


def gh(*args):
    return subprocess.check_output(["gh", *args], text=True)


def existing(tag):
    # Distinguish 404 from auth, rate-limit and network failures: never assume absent.
    result = subprocess.run(["gh", "api", f'repos/{os.environ["GH_REPO"]}/releases/tags/{tag}'], capture_output=True, text=True)
    if result.returncode:
        if "HTTP 404" in result.stderr:
            return None
        raise RuntimeError(result.stderr)
    release = json.loads(result.stdout)
    if not release["prerelease"] or release["draft"]:
        raise ValueError("Refuse to overwrite a non-QA release")
    return release


def manifest_for(apk, app_key, app, repo, sha, code, badging, notes):
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not match or match[1] != app["applicationId"] or int(match[2]) != code:
        raise ValueError("Built APK identity/version mismatch")
    sdk = re.search(r"sdkVersion:'(\d+)'", badging)
    if not sdk or int(sdk[1]) != app["minSdk"]:
        raise ValueError("Built APK minSdk mismatch")
    if not notes.strip() or len(notes) > 16000:
        raise ValueError("Missing or excessive change notes")
    size = apk.stat().st_size
    if not 1024 <= size <= 150 * 1024 * 1024:
        raise ValueError("Invalid APK size")
    tag = f"qa-{app_key}-{code}"
    name = f"{app_key}-qa-{code}.apk"
    return dict(schemaVersion=1, appKey=app_key, applicationId=app["applicationId"], channel="qa",
                versionCode=code, versionName=match[3], notes=notes.strip(), minSdk=app["minSdk"],
                sourceSha=sha, apkUrl=f"https://github.com/{repo}/releases/download/{tag}/{name}",
                sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), sizeBytes=size)


def main():
    key = os.environ["APP_KEY"]
    app = json.loads(Path("qa/apps.json").read_text())[key]
    repo, sha = os.environ["GH_REPO"], os.environ["GITHUB_SHA"]
    code = int(os.environ["QA_VERSION_CODE"])
    apk = Path(app["module"].replace(":", "/")) / "build/outputs/apk/qa" / f'{app["module"].split(":")[-1]}-qa.apk'
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
    # Android verifies APK signatures before any public upload.
    signature = subprocess.check_output([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk)], text=True)
    fingerprint = re.search(r"Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+)", signature)
    if not fingerprint:
        raise ValueError("No verified APK signer")
    # Compare with the configured existing distribution key's certificate.
    keyinfo = subprocess.check_output(["keytool", "-exportcert", "-keystore", os.environ["TORIMA_KEYSTORE"],
        "-alias", os.environ["TORIMA_KEY_ALIAS"], "-storepass:env", "TORIMA_STORE_PASSWORD"], stderr=subprocess.DEVNULL)
    if hashlib.sha256(keyinfo).hexdigest() != fingerprint[1].lower():
        raise ValueError("APK signer differs from configured fixed key")
    badging = subprocess.check_output([str(tools / "aapt"), "dump", "badging", str(apk)], text=True)
    notes = os.environ.get("QA_NOTES", "").strip()
    if not notes:
        notes = subprocess.check_output(["git", "log", "-1", "--format=%s"], text=True).strip()
    notes += f"\nソース: {sha[:12]} / QA（実機確認待ち）"
    manifest = manifest_for(apk, key, app, repo, sha, code, badging, notes)
    tag, channel = f"qa-{key}-{code}", f"qa-channel-{key}"
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        immutable = existing(tag)
        if immutable:
            # Reruns must reuse already published bytes, never replace a versioned APK.
            gh("release", "download", tag, "--pattern", f"{key}-qa-{code}.apk", "--dir", temp)
            if hashlib.sha256((root / f"{key}-qa-{code}.apk").read_bytes()).hexdigest() != manifest["sha256"]:
                raise ValueError("Version already published with different bytes; start a new run")
        else:
            asset = root / f"{key}-qa-{code}.apk"
            asset.write_bytes(apk.read_bytes())
            notes_file = root / "notes.txt"
            notes_file.write_text(notes)
            gh("release", "create", tag, str(asset), "--target", sha, "--prerelease", "--latest=false",
               "--title", f'{app["displayName"]} {manifest["versionName"]}', "--notes-file", str(notes_file))
        # Confirm the public uploaded asset exists before advertising it.
        published = existing(tag)
        assets = published["assets"]
        if not any(a["browser_download_url"] == manifest["apkUrl"] and a["size"] == manifest["sizeBytes"] for a in assets):
            raise ValueError("Published APK asset not found")
        # Anonymous GET proves the same URL works without CI credentials.
        digest = hashlib.sha256()
        count = 0
        with urllib.request.urlopen(manifest["apkUrl"], timeout=30) as response:
            while chunk := response.read(32768):
                count += len(chunk)
                if count > manifest["sizeBytes"]:
                    raise ValueError("Public APK too large")
                digest.update(chunk)
        if count != manifest["sizeBytes"] or digest.hexdigest() != manifest["sha256"]:
            raise ValueError("Public APK checksum mismatch")
        feed = existing(channel)
        if feed:
            if any(a["name"] == "manifest.json" for a in feed["assets"]):
                gh("release", "download", channel, "--pattern", "manifest.json", "--dir", temp)
                previous = json.loads((root / "manifest.json").read_text())
                if previous["applicationId"] != app["applicationId"] or previous["appKey"] != key or previous["channel"] != "qa":
                    raise ValueError("Existing channel identity mismatch")
                if previous["versionCode"] > code:
                    raise ValueError("Refuse channel downgrade")
        else:
            gh("release", "create", channel, "--target", sha, "--prerelease", "--latest=false",
               "--title", f'{app["displayName"]} update channel',
               "--notes", "QA専用の更新情報。正式版の配布先ではありません。")
        (root / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2))
        # Last write only, serialized per app by workflow concurrency.
        gh("release", "upload", channel, str(root / "manifest.json"), "--clobber")
        feed_url = f"https://github.com/{repo}/releases/download/{channel}/manifest.json"
        for attempt in range(5):
            try:
                request = urllib.request.Request(feed_url, headers={"Cache-Control": "no-cache"})
                with urllib.request.urlopen(request, timeout=30) as response:
                    public_manifest = json.loads(response.read(65537))
                if public_manifest != manifest:
                    raise ValueError("Public manifest differs from the verified release")
                break
            except Exception:
                if attempt == 4:
                    raise
                time.sleep(2)
        print(f"Anonymous QA feed verified: {feed_url}")
    print(f'QA published: {manifest["apkUrl"]}')


if __name__ == "__main__":
    main()
