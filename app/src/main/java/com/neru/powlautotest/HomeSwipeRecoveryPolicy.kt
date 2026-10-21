package com.neru.powlautotest

/**
 * In-place HOME refresh: immediately after ONE failed purple video-button input,
 * gently drag the content DOWN away from the button (not on the button),
 * reread the fresh HOME position, then tap once.
 *
 * One failed HOME tap is the entry point. A failed retry then falls
 * through to the existing once-per-run Settings force-stop / relaunch.
 */
internal object HomeSwipeRecoveryPolicy {
 const val SWIPE_DURATION_MS=340L
 // Faster than another 7-second HOME tap hypothesis, while leaving time
 // to observe the genuine ad/skip-ticket transition if the tap succeeded.
 const val FIRST_FAILURE_WAIT_MS=3_800L
 const val MIN_BUTTON_MOVEMENT_PX=7
 const val AFTER_SWIPE_SETTLE_MS=750L
 const val RETRY_HOLD_MS=110L
 const val RETRY_OBSERVE_MS=HomeTouchTrials.DELAY_BETWEEN_TRIALS_MS
 const val ID="swipe_then_tap_110"
 data class Stroke(val x:Int,val fromY:Int,val toY:Int)

 fun shouldTry(enabled:Boolean,priorRecoveries:Int,priorSwipe:Boolean,
  homeKind:String,buttonPresent:Boolean,attempts:Int,inFlight:Boolean,
  elapsedAfterLastMs:Long):Boolean =
  enabled && priorRecoveries==0 && !priorSwipe && !inFlight &&
   homeKind=="HOME" && buttonPresent &&
   attempts>=HomeRecoveryPolicy.PRE_RESTART_TRIALS &&
   elapsedAfterLastMs>=HomeTouchTrials.DELAY_BETWEEN_TRIALS_MS

 fun strokeFor(buttonLeft:Int,buttonTop:Int,buttonRight:Int,
  screenWidth:Int,screenHeight:Int):Stroke? {
  if(screenWidth<300 || screenHeight<650 ||
   buttonLeft<0 || buttonRight<=buttonLeft ||
   buttonRight>screenWidth || buttonTop<screenHeight*32/100 ||
   buttonTop>screenHeight*83/100)return null
  val fromY=buttonTop-maxOf(190,screenHeight*9/100)
  val travel=maxOf(85,minOf(140,screenHeight*5/100))
  val toY=fromY+travel
  if(fromY<screenHeight*17/100 || toY>=buttonTop-45)return null
  return Stroke((buttonLeft+buttonRight)/2,fromY,toY)
 }

 fun measuredButtonShift(beforeTop:Int,afterTop:Int):Int=afterTop-beforeTop
 fun buttonActuallyMoved(beforeTop:Int,afterTop:Int):Boolean =
  kotlin.math.abs(measuredButtonShift(beforeTop,afterTop))>=MIN_BUTTON_MOVEMENT_PX

 fun freshlyIdentifiedHome(kind:String,buttonLeft:Int,buttonTop:Int,
  buttonRight:Int,buttonBottom:Int,screenWidth:Int,screenHeight:Int):Boolean =
  kind=="HOME" && buttonLeft>=0 && buttonTop>=0 &&
   buttonRight<=screenWidth && buttonBottom<=screenHeight &&
   buttonRight>buttonLeft && buttonBottom>buttonTop &&
   buttonRight-buttonLeft>=screenWidth*65/100 &&
   buttonBottom-buttonTop>=65
}
