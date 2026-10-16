package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class PixelAdCloseDetectorTest {
 private val w=540
 private val h=1206
 private val black=0xff101010.toInt()
 private val white=0xffffffff.toInt()
 private fun drawX(x:Int,y:Int):IntArray{
  val pixels=IntArray(w*h){black}
  for(d in -18..18)for(thick in -2..2){
   val x1=x+d;val y1=y+d+thick
   val x2=x+d;val y2=y-d+thick
   if(x1 in 0 until w && y1 in 0 until h)pixels[y1*w+x1]=white
   if(x2 in 0 until w && y2 in 0 until h)pixels[y2*w+x2]=white
  }
  return pixels
 }
 @Test fun detectsRealAdCornerX(){
  val detected=PixelAdCloseDetector.detectWhiteX(drawX(490,136),w,h)
  assertNotNull(detected)
  assertTrue(kotlin.math.abs(detected!!.x-490)<=10)
  assertTrue(kotlin.math.abs(detected.y-136)<=10)
  assertTrue(detected.score>=445)
 }
 @Test fun detectsBlackFullscreenX(){
  assertNotNull(PixelAdCloseDetector.detectWhiteX(drawX(514,80),w,h))
 }
 @Test fun ignoresHomeBadgeAndBottomGachaX(){
  assertNull(PixelAdCloseDetector.detectWhiteX(drawX(435,90),w,h))
  assertNull(PixelAdCloseDetector.detectWhiteX(drawX(490,1070),w,h))
 }
 @Test fun ignoresWhiteTextOrSolidSquare(){
  val full=IntArray(w*h){black}
  for(y in 120..149)for(x in 480..510)full[y*w+x]=white
  assertNull(PixelAdCloseDetector.detectWhiteX(full,w,h))
 }
 @Test fun rejectsUnusableFrames(){
  assertNull(PixelAdCloseDetector.detectWhiteX(IntArray(1),w,h))
  assertNull(PixelAdCloseDetector.detectWhiteX(IntArray(0),0,0))
 }
}
