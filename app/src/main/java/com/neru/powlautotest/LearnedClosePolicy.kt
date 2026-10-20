package com.neru.powlautotest

/**
 * A user-approved rule has already passed LearnedActions.match():
 * same Torima window, comparable screen signature (or proven SDK frame),
 * a unique named/id-matched target and tightly matching screen bounds.
 *
 * Don't block that approved target again for lack of a second ad-SDK marker.
 * Still reject known gacha/share/skip-ticket screens and never tap indefinitely.
 */
internal object LearnedClosePolicy {
 const val STABLE_FOR_MS=1_400L
 const val AFTER_TAP_RETRY_MS=3_500L
 const val AFTER_TAP_GIVE_UP_MS=12_000L
 const val REPEAT_COOLDOWN_MS=6_500L
 fun isLearnedKind(kind:String):Boolean =
  kind=="LEARNED" || kind=="LEARNED_TEXT" ||
  kind=="LEARNED_ICON" || kind=="LEARNED_VISUAL"
 fun approvedOnCurrentScreen(kind:String,registered:Boolean,
  forbiddenUiVisible:Boolean):Boolean =
  isLearnedKind(kind) && registered && !forbiddenUiVisible
 fun canRetry(kind:String,sameScreen:Boolean,alreadyRetried:Boolean,
  elapsedMs:Long):Boolean =
  isLearnedKind(kind) && sameScreen && !alreadyRetried &&
   elapsedMs>=AFTER_TAP_RETRY_MS
 fun shouldStopWaiting(kind:String,elapsedMs:Long):Boolean =
  isLearnedKind(kind) && elapsedMs>=AFTER_TAP_GIVE_UP_MS
 fun repeatCooldown(kind:String):Long=
  if(isLearnedKind(kind))REPEAT_COOLDOWN_MS else 18_000L
}
