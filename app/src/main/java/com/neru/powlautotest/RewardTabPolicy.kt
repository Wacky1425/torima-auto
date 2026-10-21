package com.neru.powlautotest

/**
 * Two reward panels share the same video -> ad -> gacha flow, but each
 * has its own completion proof. A visible tab title alone is not evidence
 * that that tab is currently ACTIVE.
 */
internal object RewardTabPolicy {
 enum class Tab { MOVE, STEPS, UNKNOWN }
 enum class Phase { MOVE, SWITCHING_TO_STEPS, STEPS }

 const val EMPTY_CONFIRM_MS=2_500L
 const val STEP_SWITCH_CONFIRM_MS=950L
 const val STEP_SWITCH_RETRY_MS=5_500L
 const val STEP_SWITCH_TIMEOUT_MS=28_000L
 const val MAX_STEP_SWITCH_TAPS=2
 const val TAB_RELATIVE_X=0.214f // from supplied 688px HOME screenshot
 const val TAB_RELATIVE_Y=0.145f // finger center of the "歩数" header tab

 fun selectedTab(selectedMove:Boolean,selectedSteps:Boolean,
  stepHero:Boolean,moveEmptyHint:Boolean):Tab=when{
  stepHero && !moveEmptyHint->Tab.STEPS
  moveEmptyHint && !stepHero->Tab.MOVE
  selectedSteps && !selectedMove->Tab.STEPS
  selectedMove && !selectedSteps->Tab.MOVE
  else->Tab.UNKNOWN
 }

 fun moveOutOfRewards(remaining:Int?,onMove:Boolean,
  divider:Boolean,emptyPrompt:Boolean,videoButtonEnabled:Boolean,
  tankFull:Boolean,blocked:Boolean):Boolean=
  onMove && divider && !blocked && remaining==null &&
   emptyPrompt && !videoButtonEnabled && !tankFull

 fun stepOutOfRewards(remaining:Int?,onSteps:Boolean,
  divider:Boolean,hasStepHero:Boolean,videoButtonEnabled:Boolean,
  blocked:Boolean):Boolean=
  onSteps && divider && hasStepHero && !blocked &&
   remaining==null && !videoButtonEnabled

 fun shouldFinishEmpty(firstRead:Long,readCount:Int,now:Long):Boolean =
  firstRead>0L && readCount>=2 && now-firstRead>=EMPTY_CONFIRM_MS

 fun confirmedStepSwitch(firstRead:Long,readCount:Int,now:Long):Boolean =
  firstRead>0L && readCount>=2 && now-firstRead>=STEP_SWITCH_CONFIRM_MS

 fun tabPointAllowed(x:Int,y:Int,width:Int,height:Int,tab:Tab):Boolean{
  if(width<=0 || height<=0 || x !in 0 until width || y !in 0 until height)
   return false
  val fx=x.toFloat()/width
  val fy=y.toFloat()/height
  return when(tab){
   Tab.MOVE->fx in 0.015f..0.15f && fy in 0.09f..0.22f
   Tab.STEPS->fx in 0.12f..0.32f && fy in 0.09f..0.22f
   Tab.UNKNOWN->false
  }
 }
}
