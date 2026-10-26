package com.neru.powlautotest
import org.junit.Assert.*
import org.junit.Test
class LearnedXPresenceTest {
 @Test fun waitsForTwoFreshFramesAndRejectsDisappearance(){
  val g=LearnedXPresence.Gate()
  g.expect("rule",1000)
  assertFalse(g.ready("rule",90000))
  g.observe("rule",true,2000)
  assertFalse(g.ready("rule",2001))
  g.observe("rule",true,3500)
  assertTrue(g.ready("rule",3600))
  assertFalse(g.ready("rule",4501))
  g.observe("rule",false,3700)
  assertFalse(g.ready("rule",3701))
 }
 @Test fun rejectsOldOrDifferentRuleEvidence(){
  val g=LearnedXPresence.Gate()
  g.expect("a",1000)
  g.observe("a",true,2000);g.observe("a",true,7000)
  assertFalse(g.ready("a",7001))
  g.observe("a",true,8500);assertTrue(g.ready("a",8501))
  g.expect("b",8600);g.observe("a",true,8700)
  assertFalse(g.ready("b",8701))
 }
 @Test fun requiresLocalDrawnXForBothPolarities(){
  val w=540;val h=960;val x=490;val y=70
  for(inverse in listOf(false,true)){
   val bg=if(inverse)0xffffffff.toInt() else 0xff101010.toInt()
   val ink=if(inverse)0xff101010.toInt() else 0xffffffff.toInt()
   val pixels=IntArray(w*h){bg}
   assertFalse(LearnedXPresence.visible(pixels,w,h,x,y))
   for(d in -9..9)for(t in -1..1){pixels[(y+d+t)*w+x+d]=ink;pixels[(y-d+t)*w+x+d]=ink}
   assertTrue(LearnedXPresence.visible(pixels,w,h,x,y))
   assertFalse(LearnedXPresence.visible(pixels,w,h,x-70,y))
  }
 }
 @Test fun detectsGrayXAgainstItsLocalBackground(){
  val w=540;val h=960;val pixels=IntArray(w*h){0xffffffff.toInt()}
  for(d in -7..7)for(t in -1..1){pixels[(70+d+t)*w+490+d]=0xff888888.toInt();pixels[(70-d+t)*w+490+d]=0xff888888.toInt()}
  assertTrue(LearnedXPresence.visible(pixels,w,h,508,54))
  assertFalse(LearnedXPresence.visible(IntArray(w*h){0xff888888.toInt()},w,h,508,54))
 }
 @Test fun rejectsFilledBoxAndPlus(){
  val w=540;val h=960;val x=490;val y=70
  for(box in listOf(false,true)){
   val pixels=IntArray(w*h){0xff101010.toInt()}
   for(dy in -10..10)for(dx in -10..10)if(box || dx==0 || dy==0)pixels[(y+dy)*w+x+dx]=0xffffffff.toInt()
   assertFalse(LearnedXPresence.visible(pixels,w,h,x,y))
  }
 }
}
