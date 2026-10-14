package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class VisualCloseDetectorTest {
 private val w=360
 private val h=800
 private fun canvas(color:Int=0xff222222.toInt())=IntArray(w*h){color}
 private fun xShape(p:IntArray,cx:Int,cy:Int,bright:Boolean=true){
  val ink=if(bright)0xffeeeeee.toInt() else 0xff111111.toInt()
  for(d in -13..13){
   for(t in -1..1){
    val a=cx+d
    val b=cy+d+t
    val c=cy-d+t
    if(a in 0 until w && b in 0 until h)p[b*w+a]=ink
    if(a in 0 until w && c in 0 until h)p[c*w+a]=ink
   }
  }
 }
 @Test fun pureBlankImageDoesNotFindX(){
  assertTrue(VisualCloseDetector.detect(canvas(),w,h).isEmpty())
 }
 @Test fun topRightXIsReportedWithoutAnyTap(){
  val p=canvas()
  xShape(p,320,58)
  val results=VisualCloseDetector.detect(p,w,h)
  assertTrue(results.any{it.region=="top-right" &&
   kotlin.math.abs(it.x-320)<=8 && kotlin.math.abs(it.y-58)<=8})
 }
 @Test fun bottomCloseXIsReported(){
  val p=canvas(0xffeeeeee.toInt())
  xShape(p,180,720,bright=false)
  val results=VisualCloseDetector.detect(p,w,h)
  assertTrue(results.any{it.region=="bottom" &&
   kotlin.math.abs(it.x-180)<=8 && kotlin.math.abs(it.y-720)<=8})
 }
 @Test fun unsupportedDimensionsNeverCrash(){
  assertTrue(VisualCloseDetector.detect(IntArray(3),w,h).isEmpty())
 }
 @Test fun plusShapeIsNotMisclassifiedAsX(){
  val p=canvas()
  for(d in -13..13){
   for(t in -1..1){
    p[(58+d)*w+(320+t)]=0xffeeeeee.toInt()
    p[(58+t)*w+(320+d)]=0xffeeeeee.toInt()
   }
  }
  assertTrue(VisualCloseDetector.detect(p,w,h).none{
   it.region=="top-right" && kotlin.math.abs(it.x-320)<=8 &&
    kotlin.math.abs(it.y-58)<=8 && it.score>=26
  })
 }
}
