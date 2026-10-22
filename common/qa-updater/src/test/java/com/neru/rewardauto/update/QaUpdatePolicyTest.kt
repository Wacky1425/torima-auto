package com.neru.rewardauto.update

import org.junit.Assert.*
import org.junit.Test

class QaUpdatePolicyTest {
    private val config = QaFeedConfig("torima", "com.neru.powlautotest.qa", "Wacky1425/torima-auto")
    private val update = QaUpdate("torima", config.applicationId, "qa", 100001, "3.14-qa.100001", "Update QA",
        "https://github.com/Wacky1425/torima-auto/releases/download/qa-torima-100001/torima-qa-100001.apk", "a".repeat(64), 2048, 24)
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Must reject mismatched update") } catch (_: IllegalArgumentException) { }
    }
    @Test fun permitsOnlyRegisteredQaIdentity() {
        update.validate(config, config.applicationId, 35)
        rejects { update.copy(applicationId = "com.neru.powlautotest").validate(config, config.applicationId, 35) }
        rejects { update.copy(channel = "stable").validate(config, config.applicationId, 35) }
        rejects { update.copy(appKey = "other").validate(config, config.applicationId, 35) }
        rejects { update.validate(config, "com.neru.powlautotest.dev", 35) }
    }
    @Test fun rejectsForeignOrAmbiguousUrls() {
        listOf("http://github.com/", update.apkUrl + "?x=1", update.apkUrl + "#x",
            update.apkUrl.replace("github.com/", "github.com.evil/"),
            update.apkUrl.replace("qa-torima-100001", "v3.14"),
            update.apkUrl.replace("torima-qa-100001.apk", "TorimaAuto-release.apk"),
            update.apkUrl.replace("github.com/", "github.com:443/"),
            update.apkUrl.replace("github.com/", "evil@github.com/")).forEach { url ->
            assertFalse(url, config.acceptsApkUrl(url, 100001))
        }
    }
    @Test fun rejectsInvalidMetadataAndIncompatibleDevice() {
        rejects { update.copy(sha256 = "bad").validate(config, config.applicationId, 35) }
        rejects { update.copy(sizeBytes = QaUpdate.MAX_APK_BYTES + 1).validate(config, config.applicationId, 35) }
        rejects { update.copy(minSdk = 36).validate(config, config.applicationId, 35) }
        rejects { update.copy(versionCode = 0).validate(config, config.applicationId, 35) }
        rejects { update.copy(notes = "").validate(config, config.applicationId, 35) }
    }
    @Test fun checksDownloadedArchiveIndependentlyOfManifest() {
        val signer = setOf("fixed certificate")
        fun verify(id: String = config.applicationId, code: Long = 100001, name: String = update.versionName,
                   signers: Set<String> = signer, local: Long = 47) =
            QaArchivePolicy.validate(update, local, id, code, name, signer, signers)
        verify()
        rejects { verify(id = "com.neru.powlautotest") }
        rejects { verify(code = 100002) }
        rejects { verify(name = "other") }
        rejects { verify(signers = emptySet()) }
        rejects { verify(signers = setOf("other certificate")) }
        rejects { verify(local = 100001) }
        rejects { verify(local = 100002) }
    }
    @Test fun parsesSchemaAndRejectsUnknownFormat() {
        val json = """{"schemaVersion":1,"appKey":"torima","applicationId":"${config.applicationId}","channel":"qa","versionCode":100001,"versionName":"3.14-qa.100001","notes":"Update QA","apkUrl":"${update.apkUrl}","sha256":"${update.sha256}","sizeBytes":2048,"minSdk":24}"""
        assertEquals(update, QaUpdate.parse(json))
        rejects { QaUpdate.parse(json.replace("schemaVersion\":1", "schemaVersion\":2")) }
    }
}
