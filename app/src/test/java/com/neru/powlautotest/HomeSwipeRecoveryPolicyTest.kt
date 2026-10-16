package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class HomeSwipeRecoveryPolicyTest {
 @Test fun swipeOnlyAfterTwoFailedHomeInputsAndBeforeRestart(){
  assertFalse(HomeSwipeRecoveryPolicy.shouldTry(true,0,false,"HOME",true,1,false,30_000L))
  assertFalse(HomeSwipeRecoveryPolicy.shouldTry(true,0,false,"HOME",true,2,false,6_999L))
  assertTrue(HomeSwipeRecoveryPolicy.shouldTry(true,0,false,"HOME",true,2,false,7_000L))
  assertFalse(HomeSwipeRecoveryPolicy.shouldTry(true,0,true,"HOME",true,2,false,8_000L))
  assertFalse(HomeSwipeRecoveryPolicy.shouldTry(true,1,false,"HOME",true,2,false,8_000L))
  assertFalse(HomeSwipeRecoveryPolicy.shouldTry(true,0,false,"HOME_WAIT",true,2,false,8_000L))
  assertFalse(HomeSwipeRecoveryPolicy.shouldTry(true,0,false,"HOME",false,2,false,8_000L))
  assertFalse(HomeSwipeRecoveryPolicy.shouldTry(true,0,false,"HOME",true,2,true,8_000L))
  assertFalse(HomeSwipeRecoveryPolicy.shouldTry(false,0,false,"HOME",true,2,false,8_000L))
 }
 @Test fun gestureIsShortAndStaysOutsidePurpleButton(){
  val stroke=HomeSwipeRecoveryPolicy.strokeFor(90,1175,990,1080,2412)
  assertNotNull(stroke)
  assertEquals(540,stroke!!.x)
  assertTrue(stroke.toY>stroke.fromY)
  assertTrue(stroke.toY-stroke.fromY in 85..140)
  assertTrue(stroke.toY<1175-45)
  assertTrue(stroke.fromY>2412*17/100)
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
