package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class OverlayIconDetectorTest {
 private val w=540
 private val h=1206
 private fun black()=IntArray(w*h){0xff111111.toInt()}
 private fun dot(p:IntArray,x:Int,y:Int){
  for(dy in -1..1)for(dx in -1..1)
   if(x+dx in 0 until w && y+dy in 0 until h)
    p[(y+dy)*w+x+dx]=0xffffffff.toInt()
 }
 @Test fun detectsWhiteXOnBlackOverlay(){
  val p=black();val x=505;val y=63
  for(d in -10..10){dot(p,x+d,y+d);dot(p,x+d,y-d)}
  val found=OverlayIconDetector.detect(p,w,h)
  assertTrue(found.any{it.kind=="X" && kotlin.math.abs(it.x-x)<=7 &&
   kotlin.math.abs(it.y-y)<=7})
 }
 @Test fun detectsDoubleChevronInsteadOfWholeAd(){
  val p=black();val y=64
  for(tip in listOf(490,511)){
   for(d in 0..11){dot(p,tip-d,y-d);dot(p,tip-d,y+d)}
  }
  val found=OverlayIconDetector.detect(p,w,h)
  assertTrue(found.any{it.kind=="NEXT" && kotlin.math.abs(it.y-y)<=7})
 }
 @Test fun flatAndPlusSignDoNotCountAsDismissIcons(){
  val p=black()
  for(d in -12..12){dot(p,504+d,63);dot(p,504,63+d)}
  val found=OverlayIconDetector.detect(p,w,h)
  assertFalse(found.any{it.kind=="X"})
  assertFalse(found.any{it.kind=="NEXT"})
 }
}
