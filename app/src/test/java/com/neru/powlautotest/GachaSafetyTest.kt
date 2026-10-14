package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class GachaSafetyTest {
 @Test fun afterWChanceOnlyCardSelectionOrPendingSameActionIsAllowed() {
  assertTrue(GachaSafety.allows(GachaSafety.Phase.AWAIT_CARDS,"CARDS",false))
  assertTrue(GachaSafety.allows(GachaSafety.Phase.AWAIT_CARDS,"W_CHANCE",true))
  for(kind in listOf("AD_CLOSE","LEARNED","LEARNED_TEXT","CARD_RESULT",
   "SHARE_CLOSE","HOME","UNKNOWN","OPAQUE","RETRY")) {
   assertFalse("Should block $kind during W chance animation",
    GachaSafety.allows(GachaSafety.Phase.AWAIT_CARDS,kind,false))
  }
 }
 @Test fun afterCardSelectionOnlyFinalResultIsAllowed() {
  assertTrue(GachaSafety.allows(GachaSafety.Phase.AWAIT_RESULT,"CARD_RESULT",false))
  assertTrue(GachaSafety.allows(GachaSafety.Phase.AWAIT_RESULT,"CARDS",true))
  for(kind in listOf("AD_CLOSE","LEARNED","LEARNED_TEXT","SHARE_CLOSE",
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
  for(kind in listOf("AD_CLOSE","LEARNED","LEARNED_TEXT")){
   assertTrue(GachaSafety.requiresAdEvidence(kind))
  }
  assertFalse(GachaSafety.requiresAdEvidence("CARD_RESULT"))
  assertFalse(GachaSafety.requiresAdEvidence("CARDS"))
 }
 @Test fun onlyActualGachaActionsStartNewPhases() {
  assertEquals(GachaSafety.Phase.AWAIT_CARDS,GachaSafety.newPhaseAfter("W_CHANCE"))
  assertEquals(GachaSafety.Phase.AWAIT_RESULT,GachaSafety.newPhaseAfter("CARDS"))
  assertEquals(GachaSafety.Phase.AWAIT_REWARD,GachaSafety.newPhaseAfter("CARD_RESULT"))
  assertNull(GachaSafety.newPhaseAfter("AD_CLOSE"))
  assertNull(GachaSafety.newPhaseAfter("LEARNED"))
 }
}
