package com.neru.powlautotest

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Public release APK only. Never embed a GitHub token in the application. */
class TorimaUpdater(private val activity: Activity, private val status: TextView) {
    private val endpoint = "https://api.github.com/repos/Wacky1425/torima-auto-distribution/releases/latest"
    private fun show(s: String) = activity.runOnUiThread { status.text = s }
    private fun connection(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 30000
        c.setRequestProperty("User-Agent", "TorimaAuto-Android-Updater")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.instanceFollowRedirects = true
        return c
    }
    fun check() {
        show("更新情報を確認しています…")
        Thread {
            try {
                val c = connection(endpoint)
                val code = c.responseCode
                if (code != 200) {
                    c.disconnect()
                    show("更新情報を取得できません（HTTP $code）。公開配布リポジトリのReleaseを確認してください。")
                    return@Thread
                }
                val release = c.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                c.disconnect()
                val tag = release.optString("tag_name")
                val remote = tag.removePrefix("v").substringBefore('-').split('.').mapNotNull { it.toIntOrNull() }
                val local = packageVersion()
                val assets = release.optJSONArray("assets")
                var apkUrl = ""
                if (assets != null) for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name") == "TorimaAuto-release.apk") apkUrl = a.optString("browser_download_url")
                }
                if (remote.isEmpty() || apkUrl.isEmpty() || !apkUrl.startsWith("https://github.com/Wacky1425/torima-auto-distribution/releases/download/")) {
                    show("配布情報が不完全です。タグとTorimaAuto-release.apkを確認してください。")
                    return@Thread
                }
                if (compareVersions(remote, local) <= 0) {
                    show("最新版です（現在 v${local.joinToString(".")} / 配布 v$tag）")
                } else {
                    show("更新があります：v$tag（現在 v${local.joinToString(".")}）")
                    activity.runOnUiThread {
                        AlertDialog.Builder(activity).setTitle("Torima Auto v$tag")
                            .setMessage("APKをダウンロードしてAndroidのインストール確認画面を開きます。")
                            .setNegativeButton("キャンセル", null)
                            .setPositiveButton("ダウンロード") { _, _ -> download(apkUrl) }.show()
                    }
                }
            } catch (e: Exception) { show("更新確認エラー: ${e.javaClass.simpleName}: ${e.message}") }
        }.start()
    }
    private fun packageVersion(): List<Int> {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        return (info.versionName ?: "0").split('.').mapNotNull { it.toIntOrNull() }
    }
    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size,b.size)) {
            val d = (a.getOrElse(i){0}).compareTo(b.getOrElse(i){0})
            if (d != 0) return d
        }
        return 0
    }
    private fun download(url: String) {
        show("APKをダウンロードしています…")
        Thread {
            try {
                val c = connection(url)
                if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
                val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
                val file = File(dir, "TorimaAuto-release.apk")
                c.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                c.disconnect()
                if (file.length() < 1024) throw IllegalStateException("APKサイズが不正")
                show("ダウンロード完了。インストール確認を開きます。")
                activity.runOnUiThread { install(file) }
            } catch (e: Exception) { show("ダウンロード失敗: ${e.message}") }
        }.start()
    }
    private fun install(file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            status.text = "このアプリからのインストールを許可してから、もう一度更新確認を押してください。"
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
    }
}
