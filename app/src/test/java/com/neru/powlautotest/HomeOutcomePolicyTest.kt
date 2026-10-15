package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class HomeOutcomePolicyTest {
 @Test fun provenAdvertisingScreenIsConfirmed(){
  for(kind in listOf("AD_CLOSE","AD_SKIP","AD_GOOGLE_PLAY",
   "AD_VISUAL_X","AD_VISUAL_NEXT","LEARNED","LEARNED_TEXT","LEARNED_ICON")){
   assertEquals(kind,HomeOutcomePolicy.AD_OPEN_CONFIRMED,
    HomeOutcomePolicy.classifyNewScreen(kind,true,false))
   assertTrue(HomeOutcomePolicy.isConfirmed(
    HomeOutcomePolicy.classifyNewScreen(kind,true,false)))
  }
 }
 @Test fun adLookingXWithoutIndependentProofIsNotHomeSuccess(){
  for(kind in listOf("AD_CLOSE","AD_SKIP","AD_VISUAL_X","LEARNED")){
   assertEquals(HomeOutcomePolicy.UNVERIFIED_CHANGE,
    HomeOutcomePolicy.classifyNewScreen(kind,false,false))
  }
  assertEquals(HomeOutcomePolicy.AD_OPEN_CONFIRMED,
   HomeOutcomePolicy.classifyNewScreen("LEARNED",false,true))
 }
 @Test fun knownNextScreenIsNavigationButNotProofOfReward(){
  for(kind in listOf("RETRY","SKIP_NO","W_CHANCE","SHARE_CLOSE","CARD_RESULT")){
   assertEquals(HomeOutcomePolicy.NEXT_SCREEN_CONFIRMED,
    HomeOutcomePolicy.classifyNewScreen(kind,false,false))
  }
  assertNotEquals(HomeOutcomePolicy.REWARD_DECREASED,
   HomeOutcomePolicy.NEXT_SCREEN_CONFIRMED)
 }
 @Test fun unknownHomeAndTapCallbacksAreNotSuccess(){
  for(kind in listOf("UNKNOWN","OPAQUE","HOME","HOME_WAIT","NOT_DISPATCHED")){
   assertFalse(HomeOutcomePolicy.isConfirmed(
    HomeOutcomePolicy.classifyNewScreen(kind,true,true)))
  }
  for(status in listOf(HomeOutcomePolicy.PREPARING,
   HomeOutcomePolicy.SENT_UNCONFIRMED,HomeOutcomePolicy.NO_NAVIGATION,
   HomeOutcomePolicy.UNVERIFIED_CHANGE,HomeOutcomePolicy.ABORTED,
   HomeOutcomePolicy.INTERRUPTED)){
   assertFalse(HomeOutcomePolicy.isConfirmed(status))
  }
  assertTrue(HomeOutcomePolicy.isConfirmed(HomeOutcomePolicy.REWARD_DECREASED))
 }
}
