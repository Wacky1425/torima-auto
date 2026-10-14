package com.neru.powlautotest

/**
 * Once a rewarded video ad has been dismissed, a loading view may still show
 * an X / dismiss-button while a gacha animation is being prepared.
 * An ad-close click never authorizes another close until the next real screen
 * has remained visible without ad SDK residue for multiple polls.
 */
internal object PostAdTransitionPolicy {
 const val NEXT_SCREEN_STABLE_MS=3_000L
 const val MAX_WAIT_MS=90_000L
 val nextScreens=setOf("HOME","SKIP_NO","RETRY","W_CHANCE",
  "CARDS","CARD_RESULT","SHARE_CLOSE")
 fun canLeaveTransition(kind:String,targetExists:Boolean,
  adStructureVisible:Boolean,stableForMs:Long):Boolean =
  kind in nextScreens && targetExists && !adStructureVisible &&
   stableForMs>=NEXT_SCREEN_STABLE_MS
 fun timedOut(waitedMs:Long):Boolean=waitedMs>=MAX_WAIT_MS
}
