package com.neru.powlautotest
import org.junit.Assert.*
import org.junit.Test
class NovemberRegressionTest {
 @Test fun acceptsStrongLeftXButNeverLeftNextOrWeakMarks(){
  assertTrue(AdCornerPolicy.accepted("X","top-left",445))
  assertFalse(AdCornerPolicy.accepted("X","top-left",444))
  assertFalse(AdCornerPolicy.accepted("NEXT","top-left",500))
  assertFalse(AdCornerPolicy.accepted("X","bottom",500))
 }
 @Test fun findsLeftCornerWithTheSamePixelProofAsRight(){
  val w=540;val h=1206;val p=IntArray(w*h){0xff101010.toInt()}
  for(d in -16..16)for(t in -2..2){p[(80+d+t)*w+28+d]=-1;p[(80-d+t)*w+28+d]=-1}
  val hit=PixelAdCloseDetector.detectCornerXs(p,w,h).singleOrNull()
  assertNotNull(hit);assertEquals("top-left",hit!!.second)
  assertTrue(kotlin.math.abs(hit.first.x-28)<8)
  assertTrue(PixelAdCloseDetector.detectCornerXs(IntArray(w*h){-1},w,h).isEmpty())
 }
 @Test fun bottomOfferNeedsIndependentRewardContext(){
  assertTrue(MapStockPolicy.bottomOffer(listOf("宝箱の底のおもちゃをチェック","このまま受け取る")))
  assertFalse(MapStockPolicy.bottomOffer(listOf("宝箱の底を見る","インストール")))
  assertFalse(MapStockPolicy.bottomOffer(listOf("このまま受け取る")))
  assertTrue(MapStockPolicy.rolePhaseAllowed(AppNavigationRules.Role.BOTTOM,MapStockPolicy.Phase.FIRST_AD))
  assertFalse(MapStockPolicy.rolePhaseAllowed(AppNavigationRules.Role.BOTTOM,MapStockPolicy.Phase.SECOND_AD))
  assertFalse(MapStockPolicy.rolePhaseAllowed(AppNavigationRules.Role.BOTTOM,MapStockPolicy.Phase.OPEN_MAP))
 }
 @Test fun queuedCaptureWaitsForBusyAndPlatformInterval(){
  assertFalse(CaptureSlotPolicy.ready(true,2000,1000))
  assertFalse(CaptureSlotPolicy.ready(false,1500,1000))
  assertTrue(CaptureSlotPolicy.ready(false,1600,1000))
  assertTrue(CaptureSlotPolicy.ready(false,2000,1000))
  assertFalse(CaptureSlotPolicy.expired(1000,8999))
  assertTrue(CaptureSlotPolicy.expired(1000,9000))
 }
 @Test fun bottomRoleCannotBeRegisteredAsMapNavigation(){
  assertTrue(AppNavigationRules.allowed(AppNavigationRules.Role.BOTTOM,400,750,540,1200))
  assertFalse(AppNavigationRules.allowed(AppNavigationRules.Role.MAP,400,750,540,1200))
 }
}
