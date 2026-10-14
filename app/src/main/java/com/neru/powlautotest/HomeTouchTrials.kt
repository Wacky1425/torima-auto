package com.neru.powlautotest

/**
 * HOME reward-button experiments. These are only eligible after AutoFlow has
 * positively identified the selected MOVE HOME screen and the top reward button.
 * A gesture callback is NOT a successful reward; only leaving HOME is evidence
 * that the interaction did something.
 */
internal object HomeTouchTrials {
 data class Trial(
  val id:String,
  val label:String,
  val durationMs:Long,
  val position:Position,
  val tinyDriftPx:Int=0
 )
 enum class Position { UPPER_BUTTON_RIGHT_CENTER, LEARNED }
 val variants=listOf(
  Trial("right_300","やや長めのタップ",300L,Position.UPPER_BUTTON_RIGHT_CENTER),
  Trial("learned_520","学習位置で短い長押し",520L,Position.LEARNED),
  Trial("right_750_drift","指を少し動かす短い長押し",750L,
   Position.UPPER_BUTTON_RIGHT_CENTER,4)
 )
 const val DELAY_BETWEEN_TRIALS_MS=6_500L
 const val MAX_EXPERIMENT_MS=34_000L
 const val NON_HOME_CONFIRM_MS=1_200L

 fun ordered(winnerId:String?):List<Trial>{
  val win=variants.firstOrNull{it.id==winnerId}
  return if(win==null)variants else listOf(win)+variants.filter{it.id!=win.id}
 }
 fun canAttempt(attempts:Int,lastAttemptElapsedMs:Long,nowElapsedMs:Long):Boolean =
  attempts<variants.size &&
   (attempts==0 || nowElapsedMs-lastAttemptElapsedMs>=DELAY_BETWEEN_TRIALS_MS)

 fun hasTimedOut(startedElapsedMs:Long,nowElapsedMs:Long):Boolean =
  startedElapsedMs>0 && nowElapsedMs-startedElapsedMs>=MAX_EXPERIMENT_MS

 fun isHomeKind(kind:String):Boolean=kind=="HOME" || kind=="HOME_WAIT"

 // Only a real change of screen, not a dispatched tap or a faded button,
 // is evidence that a HOME tap worked. A transient empty node tree is not enough.
 fun plausibleTransition(kind:String):Boolean = kind in setOf(
  "SKIP_NO","RETRY","W_CHANCE","SHARE_CLOSE","CARD_RESULT",
  "AD_CLOSE","LEARNED","LEARNED_TEXT","OPAQUE"
 )
}
