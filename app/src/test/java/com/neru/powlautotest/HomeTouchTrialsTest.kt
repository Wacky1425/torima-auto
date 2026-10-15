package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class HomeTouchTrialsTest {
 @Test fun coversDistinctInputMethodsAndPositions(){
  assertEquals(7,HomeTouchTrials.variants.size)
  assertEquals(listOf("learned_110","node_click","parent_click","center_110",
   "learned_240","learned_520","right_300"),
   HomeTouchTrials.variants.map{it.id})
  val gestures=HomeTouchTrials.variants.filter{it.mode==HomeTouchTrials.Mode.GESTURE}
  assertEquals(5,gestures.size)
  assertTrue(gestures.all{it.durationMs in 100L..900L})
  assertTrue(HomeTouchTrials.variants.filter{it.mode!=HomeTouchTrials.Mode.GESTURE}
   .all{it.durationMs==0L})
  assertTrue(gestures.all{it.tinyDriftPx in 0..5})
 }
 @Test fun previouslySuccessfulMethodCanBeTriedFirst(){
  val normal=HomeTouchTrials.ordered(null)
  assertEquals("learned_110",normal.first().id)
  for(id in listOf("learned_520","node_click","center_110")){
   val reordered=HomeTouchTrials.ordered(id)
   assertEquals(id,reordered.first().id)
   assertEquals(7,reordered.distinctBy{it.id}.size)
  }
  assertEquals(normal,HomeTouchTrials.ordered("invalid"))
 }
 @Test fun attemptsAreBoundedAndSpacedSevenSeconds(){
  assertTrue(HomeTouchTrials.canAttempt(0,0L,1000L))
  assertFalse(HomeTouchTrials.canAttempt(1,10_000L,16_999L))
  assertTrue(HomeTouchTrials.canAttempt(1,10_000L,17_000L))
  assertFalse(HomeTouchTrials.canAttempt(7,10_000L,200_000L))
  assertFalse(HomeTouchTrials.hasTimedOut(1000L,85_999L))
  assertTrue(HomeTouchTrials.hasTimedOut(1000L,86_000L))
 }
 @Test fun parentClickRejectsFullScreenAndNonClickableAncestors(){
  assertTrue(HomeTouchTrials.parentIsNarrow(880,165,900,180,true,true))
  assertFalse(HomeTouchTrials.parentIsNarrow(880,165,1080,2412,true,true))
  assertFalse(HomeTouchTrials.parentIsNarrow(880,165,900,180,false,true))
  assertFalse(HomeTouchTrials.parentIsNarrow(880,165,900,180,true,false))
  assertFalse(HomeTouchTrials.parentIsNarrow(880,165,1600,180,true,true))
  assertFalse(HomeTouchTrials.parentIsNarrow(0,165,900,180,true,true))
 }
 @Test fun fadingHomeIsNotProofOfNewAdvertisement(){
  assertTrue(HomeTouchTrials.isHomeKind("HOME"))
  assertTrue(HomeTouchTrials.isHomeKind("HOME_WAIT"))
  assertFalse(HomeTouchTrials.plausibleTransition("HOME"))
  assertFalse(HomeTouchTrials.plausibleTransition("HOME_WAIT"))
  assertFalse(HomeTouchTrials.plausibleTransition("UNKNOWN"))
  assertFalse(HomeTouchTrials.plausibleTransition("OPAQUE"))
  assertTrue(HomeTouchTrials.plausibleTransition("AD_SKIP"))
  assertTrue(HomeTouchTrials.plausibleTransition("AD_VISUAL_X"))
  assertTrue(HomeTouchTrials.plausibleTransition("RETRY"))
  assertFalse(HomeTouchTrials.shouldRetry("UNKNOWN",70_000L))
  assertFalse(HomeTouchTrials.shouldRetry("HOME",6_999L))
  assertTrue(HomeTouchTrials.shouldRetry("HOME",7_000L))
 }
}
