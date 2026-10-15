package com.neru.powlautotest

/**
 * A dismissed video can briefly expose the *gacha-loading* X.
 * Protect this narrow window, not every subsequent advertisement.
 *
 * Never tap based only on a timer: after the quiet period, verify that the
 * currently visible button belongs to a stable new ad, or a real next screen.
 */
internal object PostAdTransitionPolicy {
 const val QUIET_AFTER_CLOSE_MS=4_000L
 const val NEXT_AD_STABLE_MS=1_700L
 const val NEXT_SCREEN_STABLE_MS=3_000L
 const val MAX_WAIT_MS=90_000L

 val nextScreens=setOf("HOME","SKIP_NO","RETRY","W_CHANCE",
  "CARDS","CARD_RESULT","SHARE_CLOSE")

 val adActions=setOf("AD_CLOSE","AD_SKIP","AD_GOOGLE_PLAY","AD_VISUAL_X",
  "AD_VISUAL_NEXT","LEARNED","LEARNED_TEXT","LEARNED_ICON")

 /** Skip can advance to another video; a verified close starts the quiet window. */
 fun isAdTransitionAction(kind:String)=kind in adActions

 fun quietPeriodActive(elapsedMs:Long)=elapsedMs<QUIET_AFTER_CLOSE_MS

 fun canContinueOnVerifiedAd(kind:String,hasTarget:Boolean,
  hasAdEvidence:Boolean,elapsedSinceCloseMs:Long,stableForMs:Long):Boolean =
  kind in adActions && hasTarget && hasAdEvidence &&
   !quietPeriodActive(elapsedSinceCloseMs) &&
   stableForMs>=NEXT_AD_STABLE_MS

 fun canLeaveTransition(kind:String,targetExists:Boolean,
  adStructureVisible:Boolean,stableForMs:Long):Boolean =
  kind in nextScreens && targetExists && !adStructureVisible &&
   stableForMs>=NEXT_SCREEN_STABLE_MS

 fun timedOut(waitedMs:Long):Boolean=waitedMs>=MAX_WAIT_MS
}
