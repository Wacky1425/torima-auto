package com.neru.rewardauto.update

import org.json.JSONObject
import java.net.URI

/** One explicitly registered feed per installed QA application. No stable/DEV fallback. */
data class QaFeedConfig(val appKey: String, val applicationId: String, val repository: String) {
    init {
        require(appKey.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")))
        require(applicationId.endsWith(".qa"))
        require(repository.matches(Regex("[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+")))
    }
    val feedUrl get() = "https://github.com/$repository/releases/download/qa-channel-$appKey/manifest.json"
    fun acceptsApkUrl(url: String, versionCode: Long): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath == "/$repository/releases/download/qa-$appKey-$versionCode/$appKey-qa-$versionCode.apk"
    }
}

data class QaUpdate(
    val appKey: String, val applicationId: String, val channel: String,
    val versionCode: Long, val versionName: String, val notes: String,
    val apkUrl: String, val sha256: String, val sizeBytes: Long, val minSdk: Int
) {
    fun validate(config: QaFeedConfig, installedId: String, sdk: Int) {
        require(installedId == config.applicationId && applicationId == installedId) { "更新対象のアプリIDが一致しません" }
        require(channel == "qa" && appKey == config.appKey) { "更新チャンネルが一致しません" }
        require(versionCode in 1..2100000000L && versionName.isNotBlank()) { "バージョン情報が不正です" }
        require(notes.isNotBlank() && notes.length <= 16000) { "変更内容がありません" }
        require(sha256.matches(Regex("[a-f0-9]{64}"))) { "ハッシュが不正です" }
        require(sizeBytes in 1024..MAX_APK_BYTES) { "APKサイズが不正です" }
        require(minSdk in 24..sdk) { "このAndroidバージョンには対応していません" }
        require(config.acceptsApkUrl(apkUrl, versionCode)) { "許可されていないAPK配布先です" }
    }
    companion object {
        const val MAX_APK_BYTES = 150L * 1024 * 1024
        fun parse(text: String): QaUpdate {
            val j = JSONObject(text)
            require(j.getInt("schemaVersion") == 1) { "未対応の更新情報です" }
            return QaUpdate(j.getString("appKey"), j.getString("applicationId"), j.getString("channel"),
                j.getLong("versionCode"), j.getString("versionName"), j.getString("notes"),
                j.getString("apkUrl"), j.getString("sha256"), j.getLong("sizeBytes"), j.getInt("minSdk"))
        }
    }
}

/** Also applied to the downloaded archive; manifest claims alone never authorize installation. */
object QaArchivePolicy {
    fun validate(update: QaUpdate, installedCode: Long, archiveId: String, archiveCode: Long,
                 archiveName: String?, installedSigners: Set<String>, archiveSigners: Set<String>) {
        require(archiveId == update.applicationId) { "APKのアプリIDが一致しません" }
        require(archiveCode == update.versionCode && archiveCode > installedCode) { "旧版または異なるAPKです" }
        require(archiveName == update.versionName) { "APKのバージョン名が一致しません" }
        require(installedSigners.isNotEmpty() && installedSigners == archiveSigners) { "署名が一致しません。アンインストールせず開発担当へ連絡してください" }
    }
}
