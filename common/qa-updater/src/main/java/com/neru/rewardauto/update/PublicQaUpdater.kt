package com.neru.rewardauto.update

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** Public HTTPS read only; the APK contains no GitHub credentials or build-trigger endpoint. */
class PublicQaUpdater(private val activity: Activity, private val status: TextView, private val config: QaFeedConfig) {
    companion object { private val busy = AtomicBoolean(false) }
    private fun ui(block: () -> Unit) = activity.runOnUiThread {
        if (!activity.isFinishing && !activity.isDestroyed) block()
    }
    private fun show(message: String) = ui { status.text = message }
    private fun fail(e: Exception) = show("QA更新エラー：${e.message ?: e.javaClass.simpleName}（現在のアプリとデータはそのままです）")
    fun check() {
        if (activity.packageName != config.applicationId) {
            show("このQA更新先は現在のアプリに使用できません")
            return
        }
        if (!busy.compareAndSet(false, true)) { show("更新処理中です…"); return }
        show("QAの更新情報を確認しています…")
        Thread {
            try {
                val local = installed()
                show("現在のQA：${local.versionName}（${code(local)}）。更新情報を確認しています…")
                val bytes = ByteArrayOutputStream()
                readPublic(config.feedUrl) { boundedCopy(it, 64 * 1024L) { b, n -> bytes.write(b, 0, n) } }
                val update = QaUpdate.parse(bytes.toString("UTF-8"))
                update.validate(config, activity.packageName, Build.VERSION.SDK_INT)
                if (update.versionCode < code(local)) {
                    show("現在のQA：${local.versionName}（${code(local)}）。配布中のQA（${update.versionCode}）より新しいため更新しません。")
                } else if (update.versionCode == code(local)) {
                    show("QA最新版です：${local.versionName}（${code(local)}）")
                } else {
                    ui {
                        AlertDialog.Builder(activity).setTitle("${config.appKey} QA ${update.versionName}")
                            .setMessage("現在：${local.versionName}（${code(local)}）\n更新：${update.versionName}（${update.versionCode}）\n\n${update.notes}\n\n学習データ・設定を保持して上書き更新します。Androidの確認画面でインストールしてください。")
                            .setNegativeButton("キャンセル", null)
                            .setPositiveButton("ダウンロードして更新") { _, _ -> download(update) }.show()
                    }
                }
            } catch (e: Exception) { fail(e) }
            finally { busy.set(false) }
        }.start()
    }
    private fun download(update: QaUpdate) {
        if (!busy.compareAndSet(false, true)) return
        show("テスト済みQA APKをダウンロードしています…")
        Thread {
            val dir = File(activity.cacheDir, "updates/${config.appKey}").apply { mkdirs() }
            val file = File(dir, "${update.sha256}.apk")
            val partial = File(dir, "download.part")
            try {
                update.validate(config, activity.packageName, Build.VERSION.SDK_INT)
                // Reuse after returning from unknown-source settings, but revalidate every byte.
                if (!file.exists() || file.length() != update.sizeBytes || digest(file) != update.sha256) {
                    dir.listFiles()?.forEach { it.delete() }
                    readPublic(update.apkUrl) { input ->
                        partial.outputStream().use { output ->
                            val count = boundedCopy(input, update.sizeBytes) { b, n -> output.write(b, 0, n) }
                            require(count == update.sizeBytes) { "APKのダウンロードが途中で終了しました" }
                        }
                    }
                    require(digest(partial) == update.sha256) { "APKのハッシュが一致しません" }
                    require(partial.renameTo(file)) { "APKを保存できません" }
                }
                verifyArchive(file, update)
                ui {
                    try {
                        // Final checks also cover updates installed during this download.
                        verifyArchive(file, update)
                        install(file)
                    } catch (e: Exception) { file.delete(); fail(e) }
                }
            } catch (e: Exception) { file.delete(); fail(e) }
            finally { partial.delete(); busy.set(false) }
        }.start()
    }
    @Suppress("DEPRECATION")
    private fun installed(): PackageInfo = activity.packageManager.getPackageInfo(activity.packageName, signatureFlags())
    private fun signatureFlags() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    @Suppress("DEPRECATION")
    private fun code(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val certificates = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return certificates?.map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }?.toSet().orEmpty()
    }
    @Suppress("DEPRECATION")
    private fun verifyArchive(file: File, update: QaUpdate) {
        require(file.length() == update.sizeBytes && digest(file) == update.sha256) { "保存APKが破損しています" }
        val local = installed()
        val archive = activity.packageManager.getPackageArchiveInfo(file.absolutePath, signatureFlags())
            ?: error("APKを読み取れません")
        QaArchivePolicy.validate(update, code(local), archive.packageName, code(archive), archive.versionName, signers(local), signers(archive))
    }
    private fun install(file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            status.text = "このQAアプリからのインストールを許可し、戻って「更新を確認」→「ダウンロードして更新」を押してください。取得済みAPKを再利用します。"
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").apply {
            clipData = ClipData.newRawUri("QA APK", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        status.text = "Androidの確認画面で更新してください。完了後にQAアプリを開き直してください。"
    }
    private fun readPublic(address: String, consume: (InputStream) -> Unit) {
        var url = URL(address)
        repeat(6) {
            require(url.protocol == "https" && url.userInfo == null && url.port == -1 &&
                url.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")) { "許可されていない配布先です" }
            val c = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000; readTimeout = 30000; instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("User-Agent", "RewardAuto-QA-Updater")
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                when (val code = c.responseCode) {
                    200 -> { c.inputStream.use(consume); return }
                    301, 302, 303, 307, 308 -> url = URL(url, c.getHeaderField("Location") ?: error("配布先情報がありません"))
                    404 -> error("QA配布はまだ公開されていません。開発担当へ確認してください")
                    else -> error("配布情報を取得できません（HTTP $code）")
                }
            } finally { c.disconnect() }
        }
        error("配布先の転送回数が多すぎます")
    }
    private fun boundedCopy(input: InputStream, limit: Long, write: (ByteArray, Int) -> Unit): Long {
        val start = SystemClock.elapsedRealtime()
        val buffer = ByteArray(32768)
        var count = 0L
        while (true) {
            require(SystemClock.elapsedRealtime() - start < 180000) { "ダウンロードがタイムアウトしました" }
            val n = input.read(buffer)
            if (n < 0) return count
            count += n
            require(count <= limit) { "配布ファイルがサイズ上限を超えています" }
            write(buffer, n)
        }
    }
    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32768)
            while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
        }
        return hex(hash.digest())
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
