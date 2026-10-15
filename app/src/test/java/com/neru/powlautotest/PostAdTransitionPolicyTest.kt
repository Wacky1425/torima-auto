package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class PostAdTransitionPolicyTest {
 @Test fun transientCloseDuringGachaLoadingIsNeverAnotherClickableAd(){
  for(kind in listOf("AD_CLOSE","LEARNED","LEARNED_TEXT","LEARNED_ICON",
   "UNKNOWN","OPAQUE","HOME_WAIT")){
   assertFalse(PostAdTransitionPolicy.canLeaveTransition(kind,true,false,120_000L))
  }
 }
 @Test fun nextScreenMustBeRealStableAndAdOverlayGone(){
  assertFalse(PostAdTransitionPolicy.canLeaveTransition("W_CHANCE",true,true,20_000L))
  assertFalse(PostAdTransitionPolicy.canLeaveTransition("W_CHANCE",true,false,2_999L))
  assertFalse(PostAdTransitionPolicy.canLeaveTransition("W_CHANCE",false,false,20_000L))
  assertTrue(PostAdTransitionPolicy.canLeaveTransition("W_CHANCE",true,false,3_000L))
  assertTrue(PostAdTransitionPolicy.canLeaveTransition("RETRY",true,false,4_000L))
  assertTrue(PostAdTransitionPolicy.canLeaveTransition("SHARE_CLOSE",true,false,4_000L))
 }
 @Test fun slowNetworkFailsClosedInsteadOfBlindDismiss(){
  assertFalse(PostAdTransitionPolicy.timedOut(89_999L))
  assertTrue(PostAdTransitionPolicy.timedOut(90_000L))
 }
 @Test fun quietPeriodIsOnlyFourSecondsAfterAdClose(){
  assertTrue(PostAdTransitionPolicy.quietPeriodActive(0L))
  assertTrue(PostAdTransitionPolicy.quietPeriodActive(3_999L))
  assertFalse(PostAdTransitionPolicy.quietPeriodActive(4_000L))
  assertFalse(PostAdTransitionPolicy.quietPeriodActive(15_000L))
 }
 @Test fun nextVerifiedAdIsAllowedAfterQuietAndStableRecognition(){
  for(kind in listOf("AD_CLOSE","AD_SKIP","AD_VISUAL_X",
   "AD_GOOGLE_PLAY","AD_VISUAL_NEXT","LEARNED_TEXT")){
   assertFalse(PostAdTransitionPolicy.canContinueOnVerifiedAd(
    kind,true,true,3_999L,20_000L))
   assertFalse(PostAdTransitionPolicy.canContinueOnVerifiedAd(
    kind,true,true,5_000L,1_699L))
   assertFalse(PostAdTransitionPolicy.canContinueOnVerifiedAd(
    kind,true,false,20_000L,20_000L))
   assertFalse(PostAdTransitionPolicy.canContinueOnVerifiedAd(
    kind,false,true,20_000L,20_000L))
   assertTrue(PostAdTransitionPolicy.canContinueOnVerifiedAd(
    kind,true,true,5_700L,1_700L))
  }
 }
 @Test fun randomOrGachaXCanNeverBeAuthorizedByWaitTimeAlone(){
  for(kind in listOf("UNKNOWN","OPAQUE","CARD_RESULT","W_CHANCE","CARDS")){
   assertFalse(PostAdTransitionPolicy.canContinueOnVerifiedAd(
    kind,true,true,90_000L,90_000L))
  }
 }
 @Test fun allAdNavigationActionsStartBriefTransitionProtection(){
  assertTrue(PostAdTransitionPolicy.isAdTransitionAction("AD_SKIP"))
  assertTrue(PostAdTransitionPolicy.isAdTransitionAction("AD_VISUAL_X"))
  assertTrue(PostAdTransitionPolicy.isAdTransitionAction("AD_CLOSE"))
  assertFalse(PostAdTransitionPolicy.isAdTransitionAction("W_CHANCE"))
  assertFalse(PostAdTransitionPolicy.isAdTransitionAction("CARD_RESULT"))
 }

}
