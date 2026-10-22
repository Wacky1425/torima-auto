package com.neru.rewardauto.api

import org.junit.Assert.*
import org.junit.Test

class SafeActionTest {
    private val app = AppIdentity("sample", "example.target")
    private val observation = ScreenObservation(app, "example.target", 100, "reward")
    private val action = SafeAction(app, "reward", ActionKind.TAP, "Observed reward button", 50)

    @Test fun requiresMatchingTargetScreenAndFreshObservation() {
        assertTrue(action.permits(observation, 150))
        assertFalse(action.permits(observation, 151))
        assertFalse(action.permits(observation, 99))
        assertFalse(action.permits(observation.copy(foregroundPackageName = null), 100))
        assertFalse(action.permits(observation.copy(foregroundPackageName = "example.other"), 100))
        assertFalse(action.permits(observation.copy(screenKey = null), 100))
        assertFalse(action.permits(observation.copy(app = AppIdentity("other", "example.target")), 100))
    }
    @Test fun separatesLearningEvenForSameTargetPackage() {
        assertNotEquals(app.learningNamespace, AppIdentity("other", "example.target").learningNamespace)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsCompletionOnUnrelatedForeground() {
        CompletionEvidence(observation.copy(foregroundPackageName = "example.other"), "counter changed")
    }
}
