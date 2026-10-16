package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class LearnedClosePolicyTest {
 @Test fun approvedRegisteredMatchNeedsNoAdditionalSdkProof(){
  for(kind in listOf("LEARNED","LEARNED_TEXT","LEARNED_ICON")){
   assertTrue(LearnedClosePolicy.approvedOnCurrentScreen(kind,true,false))
   assertFalse(LearnedClosePolicy.approvedOnCurrentScreen(kind,false,false))
   assertFalse(LearnedClosePolicy.approvedOnCurrentScreen(kind,true,true))
  }
  for(kind in listOf("HOME","CARD_RESULT","SKIP_NO","SHARE_CLOSE",
   "AD_CLOSE","UNKNOWN","OPAQUE")){
   assertFalse(LearnedClosePolicy.approvedOnCurrentScreen(kind,true,false))
  }
 }
 @Test fun learnedCloseDoesNotWaitAnotherFourSecondsAfterApproval(){
  assertTrue(LearnedClosePolicy.STABLE_FOR_MS<=1_500)
  assertTrue(LearnedClosePolicy.STABLE_FOR_MS>0)
  assertTrue(LearnedClosePolicy.REPEAT_COOLDOWN_MS<18_000)
 }
 @Test fun repeatedLearnedCloseWaitsSeveralSecondsAndOnlyOnce(){
  for(kind in listOf("LEARNED","LEARNED_TEXT","LEARNED_ICON")){
   assertFalse(LearnedClosePolicy.canRetry(kind,true,false,3_499))
   assertTrue(LearnedClosePolicy.canRetry(kind,true,false,3_500))
   assertFalse(LearnedClosePolicy.canRetry(kind,false,false,9_000))
   assertFalse(LearnedClosePolicy.canRetry(kind,true,true,9_000))
   assertFalse(LearnedClosePolicy.shouldStopWaiting(kind,11_999))
   assertTrue(LearnedClosePolicy.shouldStopWaiting(kind,12_000))
  }
  assertFalse(LearnedClosePolicy.canRetry("CARD_RESULT",true,false,7_000))
 }
 @Test fun nonLearnedActionsKeepTheirOldCooldown(){
  assertEquals(18_000L,LearnedClosePolicy.repeatCooldown("CARD_RESULT"))
  assertEquals(18_000L,LearnedClosePolicy.repeatCooldown("AD_CLOSE"))
  assertEquals(6_500L,LearnedClosePolicy.repeatCooldown("LEARNED"))
 }
}
