package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class MapStockPolicyTest {
 @Test fun todayIsOptionalAndOnlyPressedWhenVisible(){
  assertEquals(MapStockPolicy.EntryAction.TODAY,MapStockPolicy.entryAction(true))
  assertEquals(MapStockPolicy.EntryAction.STOCK,MapStockPolicy.entryAction(false))
 }
 @Test fun returningToTodayThenOpensStockWithoutRequiringTodayAgain(){
  val actions=listOf(true,false,false).map{MapStockPolicy.entryAction(it)}
  assertEquals(listOf(MapStockPolicy.EntryAction.TODAY,MapStockPolicy.EntryAction.STOCK,
   MapStockPolicy.EntryAction.STOCK),actions)
 }

 @Test fun readsStockCountWithoutUsingMaximumAsRemaining(){
  assertEquals(1,MapStockPolicy.count(listOf("ストック中1個（最大15個）")))
  assertEquals(0,MapStockPolicy.count(listOf("ストック中０個（最大15個）")))
  assertNull(MapStockPolicy.count(listOf("最大15個","ストックしている宝箱")))
  assertNull(MapStockPolicy.count(listOf("ストック中1個","ストック中2個")))
 }
 @Test fun missingRowsOrMissingCountNeverProveEmpty(){
  assertFalse(MapStockPolicy.emptyConfirmed(null,3,5000))
  assertFalse(MapStockPolicy.emptyConfirmed(1,3,5000))
  assertFalse(MapStockPolicy.emptyConfirmed(0,1,5000))
  assertFalse(MapStockPolicy.emptyConfirmed(0,2,2499))
  assertTrue(MapStockPolicy.emptyConfirmed(0,2,2500))
 }
 @Test fun returnToListNeedsAnIndependentCountDecrease(){
  assertTrue(MapStockPolicy.decreased(2,1))
  assertTrue(MapStockPolicy.decreased(1,0))
  assertFalse(MapStockPolicy.decreased(2,2))
  assertFalse(MapStockPolicy.decreased(1,null))
 }
 @Test fun detectsCompactMapDotButRejectsBannerAndOtherTab(){
  val w=540;val h=1200
  fun pixels(cx:Int,cy:Int,radius:Int):IntArray = IntArray(w*h){i->
   val x=i%w;val y=i/w
   if((x-cx)*(x-cx)+(y-cy)*(y-cy)<=radius*radius)0xffff0000.toInt() else 0xffffffff.toInt()
  }
  assertTrue(MapStockPolicy.hasMapDot(pixels(407,1135,6),w,h))
  assertFalse(MapStockPolicy.hasMapDot(pixels(304,1135,6),w,h))
  assertFalse(MapStockPolicy.hasMapDot(pixels(407,1135,24),w,h))
  assertFalse(MapStockPolicy.hasMapDot(IntArray(4),w,h))
 }
 @Test fun postAdGuardRecognizesOnlyVerifiedMapRewardControls(){
  assertTrue(PostAdTransitionPolicy.canLeaveTransition("MAP_BOTTOM",true,false,3000))
  assertFalse(PostAdTransitionPolicy.canLeaveTransition("MAP_BOTTOM",true,true,3000))
  assertFalse(PostAdTransitionPolicy.canLeaveTransition("MAP_RECEIVE",false,false,3000))
  assertFalse(PostAdTransitionPolicy.canLeaveTransition("MAP_RECEIVE",true,false,2999))
 }
}
