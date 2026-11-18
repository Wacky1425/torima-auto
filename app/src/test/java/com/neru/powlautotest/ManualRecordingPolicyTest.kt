package com.neru.powlautotest
import org.junit.Assert.*
import org.junit.Test
class ManualRecordingPolicyTest {
 private fun f(id:Int,start:Long,end:Long,epoch:Int=1)=ManualRecordingPolicy.Frame(id,start,end,epoch)
 @Test fun inFlightScreenshotCannotPretendToBeBeforeOrAfterAnAction(){
  val frames=listOf(f(1,1000,1200),f(2,1800,2200),f(3,2400,2600))
  assertEquals(ManualRecordingPolicy.Pairing(1,3),ManualRecordingPolicy.pair(frames,2000,1))
 }
 @Test fun neverPairsAcrossLeavingAndReturningToTorima(){
  val frames=listOf(f(1,1000,1200,1),f(2,2400,2600,3))
  assertEquals(ManualRecordingPolicy.Pairing(null,null),ManualRecordingPolicy.pair(frames,2000,2))
  assertEquals(ManualRecordingPolicy.Pairing(null,2),ManualRecordingPolicy.pair(frames,2000,3))
 }
 @Test fun rejectsStaleAmbiguousAndImpossibleFrameTimes(){
  val frames=listOf(f(1,1000,1200),f(2,6000,6000),f(3,6100,6000),f(4,11000,12000))
  assertEquals(ManualRecordingPolicy.Pairing(null,null),ManualRecordingPolicy.pair(frames,6000,1))
 }
 @Test fun eachQuickActionRetainsItsOwnTimestampAndAvailableEvidence(){
  val frames=listOf(f(1,1000,1200),f(2,1700,1800),f(3,2100,2200))
  assertEquals(ManualRecordingPolicy.Pairing(1,2),ManualRecordingPolicy.pair(frames,1500,1))
  assertEquals(ManualRecordingPolicy.Pairing(2,3),ManualRecordingPolicy.pair(frames,1900,1))
 }
 @Test fun ignoresBackgroundTorimaNotificationsAndOtherApps(){
  val target=ManualRecordingPolicy.TARGET
  assertTrue(ManualRecordingPolicy.accepts(target,target))
  assertFalse(ManualRecordingPolicy.accepts(target,"com.android.vending"))
  assertFalse(ManualRecordingPolicy.accepts("com.android.vending",target))
 }
 @Test fun recordingIsBoundedAndScrollCoalescingCannotMergeOtherControls(){
  assertFalse(ManualRecordingPolicy.limit(179999,499))
  assertTrue(ManualRecordingPolicy.limit(180000,0))
  assertTrue(ManualRecordingPolicy.limit(1,500))
  assertTrue(ManualRecordingPolicy.coalesceScroll(true,1000,1249))
  assertFalse(ManualRecordingPolicy.coalesceScroll(true,1000,1250))
  assertFalse(ManualRecordingPolicy.coalesceScroll(false,1000,1100))
 }
}
