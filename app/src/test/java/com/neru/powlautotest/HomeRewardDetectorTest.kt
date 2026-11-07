package com.neru.powlautotest
import org.junit.Assert.*
import org.junit.Test
class HomeRewardDetectorTest {
 private val w=540;private val h=1200
 private fun frame(mode:Boolean=true,strip:Boolean=true,purple:Boolean=true,move:Boolean=true):IntArray {
  val p=IntArray(w*h){-1}
  fun fill(l:Int,t:Int,r:Int,b:Int,c:Int){for(y in t until b)for(x in l until r)p[y*w+x]=c}
  fill(0,0,w,220,0xffffc107.toInt())
  if(move)fill(0,130,75,220,0xffff6f00.toInt())
  else fill(76,130,150,220,0xffff6f00.toInt())
  if(mode)fill(45,465,225,499,0xffffc107.toInt())
  fill(45,510,495,580,if(purple)0xff7d56c1.toInt() else 0xffaaaaaa.toInt())
  // White text/icons split central rows, as in the actual reward caption.
  fill(175,532,275,553,-1);fill(295,532,335,553,-1)
  if(strip)fill(45,615,495,642,0xffffc107.toInt())
  return p
 }
 @Test fun detectsCurrentButtonEvenWhenCaptionInterruptsThePurpleRows(){
  val hit=checkNotNull(HomeRewardDetector.detect(frame(),w,h))
  assertEquals(HomeRewardDetector.Box(45,510,495,580),hit)
 }
 @Test fun rejectsDisabledControlsMissingAnchorsAndWrongTab(){
  assertNull(HomeRewardDetector.detect(frame(mode=false),w,h))
  assertNull(HomeRewardDetector.detect(frame(strip=false),w,h))
  assertNull(HomeRewardDetector.detect(frame(purple=false),w,h))
  assertNull(HomeRewardDetector.detect(frame(move=false),w,h))
  assertNull(HomeRewardDetector.detect(IntArray(w*h){0xff7d56c1.toInt()},w,h))
 }
 @Test fun movedControlMustBeReobservedAtItsNewBounds(){
  val a=HomeRewardDetector.Box(45,510,495,580)
  assertTrue(HomeRewardDetector.same(a,a.copy(top=515,bottom=585)))
  assertFalse(HomeRewardDetector.same(a,a.copy(top=550,bottom=620)))
 }
}
