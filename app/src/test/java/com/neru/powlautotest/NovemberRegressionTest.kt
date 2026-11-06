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
 @Test fun missingScreenshotResponseReleasesOnlyItsOwnRequest(){
  val expired=CaptureSlotPolicy.Completion()
  assertTrue(expired.claim()) // timeout releases the slot
  val replacement=CaptureSlotPolicy.Completion()
  assertFalse(expired.claim()) // old Android callback cannot release it again
  assertFalse(replacement.finished)
  assertTrue(replacement.claim())
 }
 @Test fun movingBackgroundDoesNotRequireTheTaughtSceneButStillNeedsAnX(){
  val w=540;val h=1206;val x=483;val y=76
  fun frame(moving:Boolean,drawX:Boolean):IntArray{
   val pixels=IntArray(w*h){i->
    val xx=i%w;val yy=i/w
    val local=kotlin.math.abs(xx-x)<=33 && kotlin.math.abs(yy-y)<=33
    val bright=!local && ((xx<x)==moving)
    if(bright)-1 else 0xff101010.toInt()
   }
   if(drawX)for(d in -13..13)for(t in -2..2){
    pixels[(y+d+t)*w+x+d]=-1
    pixels[(y-d+t)*w+x+d]=-1
   }
   return pixels
  }
  val rule=checkNotNull(VisualTemplateModel.capture(frame(false,true),w,h,x,y))
  val moving=frame(true,true)
  assertNull(VisualTemplateModel.search(rule,moving,w,h))
  val local=checkNotNull(VisualTemplateModel.searchLocalX(rule,moving,w,h))
  assertEquals(x,local.x)
  assertEquals(y,local.y)
  assertNull(VisualTemplateModel.searchLocalX(rule,frame(true,false),w,h))
 }
 @Test fun bottomRoleCannotBeRegisteredAsMapNavigation(){
  assertTrue(AppNavigationRules.allowed(AppNavigationRules.Role.BOTTOM,400,750,540,1200))
  assertFalse(AppNavigationRules.allowed(AppNavigationRules.Role.MAP,400,750,540,1200))
 }
}
