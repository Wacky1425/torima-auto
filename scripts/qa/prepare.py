"""Resolve an approved app before accessing signing secrets. No hard-coded app count."""
import json
import os
import re
from pathlib import Path


def resolve(key, branch, registry):
    if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", key):
        raise ValueError("Invalid app key")
    app = registry[key]
    if branch not in app["sourceBranches"]:
        raise ValueError("Branch is not approved for this QA channel")
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_-]*(?::[A-Za-z][A-Za-z0-9_-]*)*", app["module"]):
        raise ValueError("Invalid module")
    if not re.fullmatch(r"[A-Z][A-Z0-9_]*", app["signingPrefix"]):
        raise ValueError("Invalid signing prefix")
    ids = [item["applicationId"] for item in registry.values()]
    if len(ids) != len(set(ids)) or not app["applicationId"].endswith(".qa"):
        raise ValueError("QA applicationIds must be unique and end with .qa")
    return app


if __name__ == "__main__":
    app = resolve(os.environ["APP_KEY"], os.environ["GITHUB_REF_NAME"], json.loads(Path("qa/apps.json").read_text()))
    code = 100000 + int(os.environ["GITHUB_RUN_NUMBER"])
    if not 100001 <= code <= 2100000000:
        raise ValueError("QA version counter exhausted")
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        output.write(f'module={app["module"]}\nsigning_prefix={app["signingPrefix"]}\nversion_code={code}\n')
