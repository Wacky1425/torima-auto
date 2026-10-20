package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class VisualTemplateModelTest {
 private val w=540
 private val h=1206
 private fun image(x:Int=491,y:Int=130,red:Int=30,withX:Boolean=true):IntArray{
  val pixels=IntArray(w*h){yy->
   val row=yy/w
   val shade=(red+(row/22)%13).coerceIn(0,255)
   (0xff shl 24) or (shade shl 16) or (shade shl 8) or shade
  }
  // Nonuniform banner background near the user's chosen close/skip.
  for(dy in -135..135)for(dx in -145..40){
   val xx=x+dx;val yy=y+dy
   if(xx !in 0 until w || yy !in 0 until h)continue
   val shade=if((dx/17+dy/15)%2==0)red+30 else red+4
   val c=shade.coerceIn(0,255)
   pixels[yy*w+xx]=(0xff shl 24) or (c shl 16) or (c shl 8) or c
  }
  if(withX)for(d in -17..17)for(t in -2..2){
   val xx=x+d
   for(yy in listOf(y+d+t,y-d+t)){
    if(xx in 0 until w && yy in 0 until h)
     pixels[yy*w+xx]=0xffffffff.toInt()
   }
  }
  return pixels
 }
 @Test fun learnsCloseImageWithoutAnyAccessibilityNode(){
  val source=image()
  val saved=VisualTemplateModel.capture(source,w,h,491,130)
  assertNotNull(saved)
  val hit=VisualTemplateModel.search(saved!!,source,w,h)
  assertNotNull(hit)
  assertTrue(hit!!.score>=VisualTemplateModel.MIN_TOTAL_SCORE)
  assertTrue(kotlin.math.abs(hit.x-491)<=8)
  assertTrue(kotlin.math.abs(hit.y-130)<=8)
 }
 @Test fun matchingAllowsMovementAndSmallVisualChanges(){
  val saved=VisualTemplateModel.capture(image(),w,h,491,130)!!
  val shifted=image(497,134,35,true)
  assertNotNull(VisualTemplateModel.search(saved,shifted,w,h))
 }
 @Test fun refusesUnrelatedOrFeaturelessScreens(){
  val learned=VisualTemplateModel.capture(image(),w,h,491,130)!!
  assertNull(VisualTemplateModel.search(learned,
   IntArray(w*h){0xff353535.toInt()},w,h))
  assertNull(VisualTemplateModel.capture(
   IntArray(w*h){0xff121212.toInt()},w,h,491,130))
 }
 @Test fun doesNotAcceptInvalidImageDimensions(){
  assertNull(VisualTemplateModel.capture(IntArray(1),w,h,491,130))
  assertNull(VisualTemplateModel.search(
   VisualTemplateModel.Rule(490,130,w,h,IntArray(3),IntArray(3)),
   image(),w,h))
 }
}
