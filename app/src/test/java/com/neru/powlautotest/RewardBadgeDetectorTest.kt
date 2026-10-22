package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class RewardBadgeDetectorTest {
 private val width=540
 private val height=1206
 private val yellow=0xffffc107.toInt()
 private val orange=0xffff6f00.toInt()
 private val red=0xffff0000.toInt()
 private fun screen(
  move:Boolean=false,steps:Boolean=true,
  selected:RewardTabPolicy.Tab=RewardTabPolicy.Tab.UNKNOWN
 ):IntArray{
  val pixels=IntArray(width*height){yellow}
  val selectedX=when(selected){
   RewardTabPolicy.Tab.MOVE->(width*0.035).toInt()
   RewardTabPolicy.Tab.STEPS->(width*0.160).toInt()
   else->-1
  }
  if(selectedX>=0)for(yy in (height*0.14).toInt()..(height*0.15).toInt())
   for(xx in selectedX-3..selectedX+3)pixels[yy*width+xx]=orange
  val y=(height*0.1172).toInt()
  for((on,fx) in listOf(move to 0.116,steps to 0.260)){
   if(!on)continue
   val x=(width*fx).toInt()
   for(dy in -6..6)for(dx in -6..6){
    if(dx*dx+dy*dy<=36)pixels[(y+dy)*width+x+dx]=red
   }
  }
  return pixels
 }
 @Test fun detectsRightStepDotButNoMoveDot(){
  val pixels=screen(false,true,RewardTabPolicy.Tab.STEPS)
  val y=(height*0.1172).toInt()
  val x=(width*0.116).toInt()
  pixels[y*width+x]=orange
  val detected=RewardBadgeDetector.detect(pixels,width,height)
  assertEquals(RewardBadgeDetector.Mark.ABSENT,detected.move)
  assertEquals(RewardBadgeDetector.Mark.PRESENT,detected.steps)
  assertEquals(RewardTabPolicy.Tab.STEPS,detected.selected)
  assertTrue(RewardBadgeDetector.canSkipMoveOnStepStart(detected,2))
  assertFalse(RewardBadgeDetector.canSkipMoveOnStepStart(detected,1))
  val uncertainSelection=RewardBadgeDetector.detect(
   screen(false,true),width,height)
  assertFalse(RewardBadgeDetector.canSkipMoveOnStepStart(
   uncertainSelection,4))
 }
 @Test fun moveDotMeansCheckMoveFirst(){
  val result=RewardBadgeDetector.detect(
   screen(true,true,RewardTabPolicy.Tab.MOVE),width,height)
  assertEquals(RewardBadgeDetector.Mark.PRESENT,result.move)
  assertEquals(RewardBadgeDetector.Mark.PRESENT,result.steps)
  assertEquals(RewardTabPolicy.Tab.MOVE,result.selected)
  assertFalse(RewardBadgeDetector.canSkipMoveOnStepStart(result,3))
 }
 @Test fun hiddenOrUnknownBackgroundNeverBecomesNoReward(){
  val pixels=IntArray(width*height){0xff303030.toInt()}
  val result=RewardBadgeDetector.detect(pixels,width,height)
  assertEquals(RewardBadgeDetector.Mark.UNKNOWN,result.move)
  assertEquals(RewardBadgeDetector.Mark.UNKNOWN,result.steps)
  assertFalse(RewardBadgeDetector.canSkipMoveOnStepStart(result,4))
 }
 @Test fun requiresTwoEquivalentClearScreens(){
  val first=RewardBadgeDetector.detect(screen(false,true),width,height)
  val second=RewardBadgeDetector.detect(screen(false,true),width,height)
  val other=RewardBadgeDetector.detect(screen(true,true),width,height)
  assertTrue(RewardBadgeDetector.same(first,second))
  assertFalse(RewardBadgeDetector.same(first,other))
 }
}
