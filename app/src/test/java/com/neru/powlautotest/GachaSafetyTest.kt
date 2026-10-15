package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class GachaSafetyTest {
 @Test fun afterWChanceOnlyCardSelectionOrPendingSameActionIsAllowed() {
  assertTrue(GachaSafety.allows(GachaSafety.Phase.AWAIT_CARDS,"CARDS",false))
  assertTrue(GachaSafety.allows(GachaSafety.Phase.AWAIT_CARDS,"W_CHANCE",true))
  for(kind in listOf("AD_CLOSE","LEARNED","LEARNED_TEXT","LEARNED_ICON","CARD_RESULT",
   "SHARE_CLOSE","HOME","UNKNOWN","OPAQUE","RETRY")) {
   assertFalse("Should block $kind during W chance animation",
    GachaSafety.allows(GachaSafety.Phase.AWAIT_CARDS,kind,false))
  }
 }
 @Test fun afterCardSelectionOnlyFinalResultIsAllowed() {
  assertTrue(GachaSafety.allows(GachaSafety.Phase.AWAIT_RESULT,"CARD_RESULT",false))
  assertTrue(GachaSafety.allows(GachaSafety.Phase.AWAIT_RESULT,"CARDS",true))
  for(kind in listOf("AD_CLOSE","LEARNED","LEARNED_TEXT","LEARNED_ICON","SHARE_CLOSE",
   "HOME","UNKNOWN","OPAQUE","W_CHANCE","SKIP_NO")) {
   assertFalse("Should block $kind during card animation",
    GachaSafety.allows(GachaSafety.Phase.AWAIT_RESULT,kind,false))
  }
 }
 @Test fun afterResultWaitForRewardOrHome() {
  assertTrue(GachaSafety.allows(GachaSafety.Phase.AWAIT_REWARD,"SHARE_CLOSE",false))
  assertTrue(GachaSafety.allows(GachaSafety.Phase.AWAIT_REWARD,"HOME",false))
  assertFalse(GachaSafety.allows(GachaSafety.Phase.AWAIT_REWARD,"AD_CLOSE",false))
  assertFalse(GachaSafety.allows(GachaSafety.Phase.AWAIT_REWARD,"LEARNED",false))
 }
 @Test fun adCloseMustAlwaysHaveSeparateEvidence() {
  for(kind in listOf("AD_CLOSE","LEARNED","LEARNED_TEXT","LEARNED_ICON")){
   assertTrue(GachaSafety.requiresAdEvidence(kind))
  }
  assertFalse(GachaSafety.requiresAdEvidence("CARD_RESULT"))
  assertFalse(GachaSafety.requiresAdEvidence("CARDS"))
 }
 @Test fun postResultAllowsOnlyAProvenSettledAd(){
  val p=GachaSafety.Phase.AWAIT_REWARD
  assertTrue(GachaSafety.canDismissProvenAdAfterResult(p,"LEARNED_TEXT",true,3100L))
  assertTrue(GachaSafety.canDismissProvenAdAfterResult(p,"LEARNED_ICON",true,3100L))
  assertFalse(GachaSafety.canDismissProvenAdAfterResult(p,"LEARNED_ICON",false,5000L))
  assertTrue(GachaSafety.canDismissProvenAdAfterResult(p,"AD_CLOSE",true,3100L))
  assertFalse(GachaSafety.canDismissProvenAdAfterResult(p,"LEARNED_TEXT",false,6000L))
  assertFalse(GachaSafety.canDismissProvenAdAfterResult(p,"LEARNED_TEXT",true,2000L))
  assertFalse(GachaSafety.canDismissProvenAdAfterResult(GachaSafety.Phase.AWAIT_CARDS,"AD_CLOSE",true,9000L))
  assertFalse(GachaSafety.canDismissProvenAdAfterResult(GachaSafety.Phase.AWAIT_RESULT,"AD_CLOSE",true,9000L))
  assertFalse(GachaSafety.canDismissProvenAdAfterResult(p,"CARD_RESULT",true,5000L))
 }
 @Test fun onlyActualGachaActionsStartNewPhases() {
  assertEquals(GachaSafety.Phase.AWAIT_CARDS,GachaSafety.newPhaseAfter("W_CHANCE"))
  assertEquals(GachaSafety.Phase.AWAIT_RESULT,GachaSafety.newPhaseAfter("CARDS"))
  assertEquals(GachaSafety.Phase.AWAIT_REWARD,GachaSafety.newPhaseAfter("CARD_RESULT"))
  assertNull(GachaSafety.newPhaseAfter("AD_CLOSE"))
  assertNull(GachaSafety.newPhaseAfter("LEARNED"))
 }
 @Test fun postGachaMetaSkipAndVisualNextRequireAdProof(){
  val p=GachaSafety.Phase.AWAIT_REWARD
  for(kind in listOf("AD_SKIP","AD_GOOGLE_PLAY","AD_VISUAL_X","AD_VISUAL_NEXT")){
   assertTrue(GachaSafety.requiresAdEvidence(kind))
   assertTrue(GachaSafety.canDismissProvenAdAfterResult(p,kind,true,3100L))
   assertFalse(GachaSafety.canDismissProvenAdAfterResult(p,kind,false,3100L))
   assertFalse(GachaSafety.canDismissProvenAdAfterResult(p,kind,true,2000L))
   assertFalse(GachaSafety.canDismissProvenAdAfterResult(
    GachaSafety.Phase.AWAIT_RESULT,kind,true,9000L))
  }
 }

 @Test fun exactLearnedMatchBypassesRedundantAdProofOnlyWithoutExplicitGacha(){
  for(kind in listOf("LEARNED","LEARNED_TEXT","LEARNED_ICON")){
   assertTrue(GachaSafety.exactLearnedCloseAllowed(kind,true,false))
   assertFalse(GachaSafety.exactLearnedCloseAllowed(kind,false,false))
   assertFalse(GachaSafety.exactLearnedCloseAllowed(kind,true,true))
  }
  assertFalse(GachaSafety.exactLearnedCloseAllowed("AD_CLOSE",true,false))
  assertFalse(GachaSafety.exactLearnedCloseAllowed("UNKNOWN",true,false))
 }
 @Test fun exactMatchStillHonorsGachaTransitionTiming(){
  assertFalse(GachaSafety.canDismissProvenAdAfterResult(
   GachaSafety.Phase.AWAIT_CARDS,"LEARNED",true,10_000L))
  assertFalse(GachaSafety.canDismissProvenAdAfterResult(
   GachaSafety.Phase.AWAIT_RESULT,"LEARNED",true,10_000L))
  assertFalse(GachaSafety.canDismissProvenAdAfterResult(
   GachaSafety.Phase.AWAIT_REWARD,"LEARNED",true,2_000L))
  assertTrue(GachaSafety.canDismissProvenAdAfterResult(
   GachaSafety.Phase.AWAIT_REWARD,"LEARNED",true,3_100L))
 }

}
