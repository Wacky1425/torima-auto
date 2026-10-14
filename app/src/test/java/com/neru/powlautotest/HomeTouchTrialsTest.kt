package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class HomeTouchTrialsTest {
 @Test fun shortAndMediumDurationTrialsAreBounded(){
  assertEquals(3,HomeTouchTrials.variants.size)
  assertEquals(listOf(300L,520L,750L),HomeTouchTrials.variants.map{it.durationMs})
  assertTrue(HomeTouchTrials.variants.all{it.durationMs in 200L..900L})
  assertTrue(HomeTouchTrials.variants.all{it.tinyDriftPx in 0..5})
 }
 @Test fun learnedWinningMethodComesFirstNextTime(){
  val first=HomeTouchTrials.ordered(null)
  assertEquals("right_300",first.first().id)
  val win=HomeTouchTrials.ordered("learned_520")
  assertEquals("learned_520",win.first().id)
  assertEquals(3,win.distinctBy{it.id}.size)
  assertEquals(first,HomeTouchTrials.ordered("missing"))
 }
 @Test fun betweenTrialsWaitAndStopAfterThree(){
  assertTrue(HomeTouchTrials.canAttempt(0,0L,1_000L))
  assertFalse(HomeTouchTrials.canAttempt(1,10_000L,16_499L))
  assertTrue(HomeTouchTrials.canAttempt(1,10_000L,16_500L))
  assertFalse(HomeTouchTrials.canAttempt(3,10_000L,200_000L))
  assertFalse(HomeTouchTrials.hasTimedOut(10_000L,43_999L))
  assertTrue(HomeTouchTrials.hasTimedOut(10_000L,44_000L))
 }
 @Test fun fadingButtonOrHomeWaitIsNotTransition(){
  assertTrue(HomeTouchTrials.isHomeKind("HOME"))
  assertTrue(HomeTouchTrials.isHomeKind("HOME_WAIT"))
  assertFalse(HomeTouchTrials.plausibleTransition("HOME"))
  assertFalse(HomeTouchTrials.plausibleTransition("HOME_WAIT"))
  assertFalse(HomeTouchTrials.plausibleTransition("UNKNOWN"))
  assertTrue(HomeTouchTrials.plausibleTransition("SKIP_NO"))
  assertFalse(HomeTouchTrials.plausibleTransition("OPAQUE"))
  assertTrue(HomeTouchTrials.plausibleTransition("RETRY"))
 }
}
