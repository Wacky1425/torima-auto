package com.neru.powlautotest

/**
 * HOME-only hypotheses. Each attempt is independent and must be followed by
 * UI verification. Neither a gesture completion nor ACTION_CLICK=true is proof
 * that the reward video opened.
 */
internal object HomeTouchTrials {
 enum class Mode { GESTURE, NODE_CLICK, PARENT_CLICK }
 enum class Position { UPPER_BUTTON_RIGHT_CENTER, UPPER_BUTTON_CENTER, LEARNED }
 data class Trial(
  val id:String,
  val label:String,
  val durationMs:Long,
  val position:Position,
  val tinyDriftPx:Int=0,
  val mode:Mode=Mode.GESTURE
 )
 val variants=listOf(
  Trial("learned_110","学習位置110ms・操作バーを透過",110L,Position.LEARNED),
  Trial("node_click","ボタン自身のACTION_CLICK",0L,Position.LEARNED,
   mode=Mode.NODE_CLICK),
  Trial("parent_click","ボタン親のACTION_CLICK（狭いクリック領域のみ）",
   0L,Position.LEARNED,mode=Mode.PARENT_CLICK),
  Trial("center_110","紫ボタンの幾何学的中心110ms",110L,Position.UPPER_BUTTON_CENTER),
  Trial("learned_240","学習位置240ms・操作バーを透過",240L,Position.LEARNED),
  Trial("learned_520","学習位置520ms・操作バーを透過",520L,Position.LEARNED),
  Trial("right_300","右寄り59.3%で300ms",300L,Position.UPPER_BUTTON_RIGHT_CENTER)
 )
 const val OVERLAY_SETTLE_MS=220L
 const val DELAY_BETWEEN_TRIALS_MS=7_000L
 const val MAX_EXPERIMENT_MS=85_000L
 const val NON_HOME_CONFIRM_MS=1_200L
 const val MIN_PARENT_WIDTH_RATIO=75
 const val MAX_PARENT_WIDTH_RATIO=160

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
 fun plausibleTransition(kind:String):Boolean=kind in setOf(
  "SKIP_NO","RETRY","W_CHANCE","SHARE_CLOSE","CARD_RESULT",
  "AD_CLOSE","AD_SKIP","AD_GOOGLE_PLAY","AD_VISUAL_X","AD_VISUAL_NEXT",
  "LEARNED","LEARNED_TEXT","LEARNED_ICON"
 )
 /** A full-screen ancestor is not a safe alternative HOME click target. */
 fun parentIsNarrow(buttonW:Int,buttonH:Int,parentW:Int,parentH:Int,
  containsButton:Boolean,clickable:Boolean):Boolean{
  if(buttonW<=0||buttonH<=0||!containsButton||!clickable)return false
  return parentW*100>=buttonW*MIN_PARENT_WIDTH_RATIO &&
   parentW*100<=buttonW*MAX_PARENT_WIDTH_RATIO &&
   parentH>=buttonH && parentH<=buttonH*2
 }
 /** Classify a new root, not just the gesture callback, to decide a retry. */
 fun shouldRetry(kind:String,stableForMs:Long):Boolean =
  isHomeKind(kind) && stableForMs>=DELAY_BETWEEN_TRIALS_MS
}
