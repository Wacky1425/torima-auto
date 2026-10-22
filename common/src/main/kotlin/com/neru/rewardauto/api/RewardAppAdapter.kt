package com.neru.rewardauto.api

/** Target identity also scopes learning and diagnostics. No Android execution lives here. */
data class AppIdentity(val slug: String, val targetPackageName: String) {
    init {
        require(slug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")))
        require(targetPackageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")))
    }
    val learningNamespace: String get() = "$slug/$targetPackageName"
}

data class ScreenObservation(
    val app: AppIdentity,
    val foregroundPackageName: String?,
    val capturedAtMonotonicMs: Long,
    val screenKey: String?
)

enum class ActionKind { TAP, SWIPE, BACK }

/** A proposal only. The future Android host must recheck foreground, lock and bounds. */
data class SafeAction(
    val app: AppIdentity,
    val expectedScreenKey: String,
    val kind: ActionKind,
    val reason: String,
    val maxObservationAgeMs: Long
) {
    init {
        require(expectedScreenKey.isNotBlank())
        require(reason.isNotBlank())
        require(maxObservationAgeMs > 0)
    }
    fun permits(observation: ScreenObservation, nowMonotonicMs: Long): Boolean =
        observation.app == app &&
            observation.foregroundPackageName == app.targetPackageName &&
            observation.screenKey == expectedScreenKey &&
            nowMonotonicMs >= observation.capturedAtMonotonicMs &&
            nowMonotonicMs - observation.capturedAtMonotonicMs <= maxObservationAgeMs
}

enum class OperationOutcome { PROGRESS, RETRY, STOP, UNKNOWN }

/** Reward evidence must be observed independently of gesture completion. */
data class CompletionEvidence(val observation: ScreenObservation, val description: String) {
    init {
        require(description.isNotBlank())
        require(observation.foregroundPackageName == observation.app.targetPackageName)
        require(!observation.screenKey.isNullOrBlank())
    }
}

interface RewardAppAdapter {
    val identity: AppIdentity
    fun nextAction(observation: ScreenObservation): SafeAction?
    fun verifyOutcome(before: ScreenObservation, after: ScreenObservation): OperationOutcome
    fun completionEvidence(observation: ScreenObservation): CompletionEvidence?
}
