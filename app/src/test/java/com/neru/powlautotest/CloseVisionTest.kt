package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CloseVisionTest {
 private val w=540
 private val h=1206
 private fun drawing(kind:String,cx:Int=490,cy:Int=120):IntArray {
  val pixels=IntArray(w*h){0x222222}
  fun plot(x:Int,y:Int){
   if(x in 0 until w && y in 0 until h)pixels[y*w+x]=0xeeeeee
  }
  for(d in -28..28){
   for(thickness in -1..1){
    if(kind=="X" || kind=="PLUS" && d==0){
     plot(cx+d,cy+d+thickness)
     plot(cx+d,cy-d+thickness)
    }
    if(kind=="PLUS"){
     plot(cx+d,cy+thickness)
     plot(cx+thickness,cy+d)
    }
   }
  }
  return pixels
 }
 private fun scan(pixels:IntArray)=CloseVision.detect(w,h){x,y->pixels[y*w+x]}
 @Test fun blankImageDoesNotProduceCloseIcons(){
  assertTrue(scan(IntArray(w*h){0x252525}).isEmpty())
 }
 @Test fun diagnosticCanFindASharpXInTopRightRegion(){
  val matches=scan(drawing("X"))
  assertTrue("X should yield a candidate",matches.any{
   it.region=="upper_right" && abs(it.x-490)<=22 && abs(it.y-120)<=22
  })
 }
 @Test fun plusSignIsNotAConfirmedX(){
  val matches=scan(drawing("PLUS"))
  assertFalse(matches.any{
   it.region=="upper_right" && abs(it.x-490)<=12 && abs(it.y-120)<=12 &&
    it.confidence>.7
  })
 }
 @Test fun outputIsBoundedAndNeverDeclaresAnAction(){
  val result=scan(drawing("X"))
  assertTrue(result.size<=12)
  assertTrue(result.all{it.confidence in 0.0..1.0 && it.radius>0})
 }
}
