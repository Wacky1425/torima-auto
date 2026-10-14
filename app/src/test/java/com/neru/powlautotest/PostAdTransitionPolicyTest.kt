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
}
