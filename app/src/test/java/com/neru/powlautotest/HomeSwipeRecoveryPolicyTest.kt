package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class HomeSwipeRecoveryPolicyTest {
 @Test fun oneFailedHomeTapTriggersSwipeBeforeAnySecondMethod(){
  val threshold=HomeSwipeRecoveryPolicy.FIRST_FAILURE_WAIT_MS
  assertFalse(HomeSwipeRecoveryPolicy.shouldTry(true,0,false,"HOME",true,0,false,threshold+10))
  assertFalse(HomeSwipeRecoveryPolicy.shouldTry(true,0,false,"HOME",true,1,false,threshold-1))
  assertTrue(HomeSwipeRecoveryPolicy.shouldTry(true,0,false,"HOME",true,1,false,threshold))
  assertTrue(HomeSwipeRecoveryPolicy.shouldTry(true,0,false,"HOME",true,1,false,7_000L))
  assertFalse(HomeSwipeRecoveryPolicy.shouldTry(true,0,true,"HOME",true,2,false,8_000L))
  assertFalse(HomeSwipeRecoveryPolicy.shouldTry(true,1,false,"HOME",true,1,false,8_000L))
  assertFalse(HomeSwipeRecoveryPolicy.shouldTry(true,0,false,"HOME_WAIT",true,1,false,8_000L))
  assertFalse(HomeSwipeRecoveryPolicy.shouldTry(true,0,false,"HOME",false,1,false,8_000L))
  assertFalse(HomeSwipeRecoveryPolicy.shouldTry(true,0,false,"HOME",true,1,true,8_000L))
  assertFalse(HomeSwipeRecoveryPolicy.shouldTry(false,0,false,"HOME",true,1,false,8_000L))
 }
 @Test fun realButtonMovementIsMeasuredFromFreshRect(){
  assertEquals(18,HomeSwipeRecoveryPolicy.measuredButtonShift(1175,1193))
  assertTrue(HomeSwipeRecoveryPolicy.buttonActuallyMoved(1175,1193))
  assertFalse(HomeSwipeRecoveryPolicy.buttonActuallyMoved(1175,1177))
 }

 @Test fun gestureIsShortAndStaysOutsidePurpleButton(){
  val stroke=HomeSwipeRecoveryPolicy.strokeFor(90,1175,990,1080,2412)
  assertNotNull(stroke)
  assertEquals(540,stroke!!.x)
  assertTrue("Finger must move upward, not downward",stroke.toY<stroke.fromY)
  assertTrue(stroke.fromY-stroke.toY in 85..140)
  assertTrue("The gesture starts above purple button",stroke.fromY<1175-45)
  assertTrue("The gesture ends above purple button",stroke.toY<1175-45)
  assertTrue(stroke.toY>2412*17/100)
  assertEquals(340L,HomeSwipeRecoveryPolicy.SWIPE_DURATION_MS)
 }
 @Test fun noGestureIfButtonNearTopOrScreenInvalid(){
  assertNull(HomeSwipeRecoveryPolicy.strokeFor(90,150,990,1080,2412))
  assertNull(HomeSwipeRecoveryPolicy.strokeFor(90,1175,990,0,2412))
  assertNull(HomeSwipeRecoveryPolicy.strokeFor(90,1175,2000,1080,2412))
  assertNull(HomeSwipeRecoveryPolicy.strokeFor(90,1175,990,1080,300))
 }
 @Test fun retapRequiresReacquiredHomeAndUniqueWideButton(){
  assertTrue(HomeSwipeRecoveryPolicy.freshlyIdentifiedHome(
   "HOME",90,1185,990,1340,1080,2412))
  assertFalse(HomeSwipeRecoveryPolicy.freshlyIdentifiedHome(
   "HOME_WAIT",90,1185,990,1340,1080,2412))
  assertFalse(HomeSwipeRecoveryPolicy.freshlyIdentifiedHome(
   "SKIP_NO",90,1185,990,1340,1080,2412))
  assertFalse(HomeSwipeRecoveryPolicy.freshlyIdentifiedHome(
   "HOME",900,1185,990,1340,1080,2412))
  assertFalse(HomeSwipeRecoveryPolicy.freshlyIdentifiedHome(
   "HOME",90,1185,990,3410,1080,2412))
 }
}
