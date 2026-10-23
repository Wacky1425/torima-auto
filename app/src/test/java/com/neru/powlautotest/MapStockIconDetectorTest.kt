package com.neru.powlautotest
import org.junit.Assert.*
import org.junit.Test
class MapStockIconDetectorTest {
 private val w=540;private val h=1200
 private fun frame(cx:Int,cy:Int):IntArray {
  val pixels=IntArray(w*h){0xff668899.toInt()};val size=78
  for(y in 0 until size)for(x in 0 until size){
   pixels[(cy-size/2+y)*w+cx-size/2+x]=0xff000000.toInt() or
    MapStockIconDetector.reference[(y*16/size).coerceAtMost(15)*16+(x*16/size).coerceAtMost(15)]
  }
  return pixels
 }
 @Test fun findsOnlyTheStockControlInRightLowerRegion(){
  val hit=MapStockIconDetector.detect(frame(470,842),w,h)
  assertNotNull(hit);assertTrue(kotlin.math.abs(hit!!.x-470)<10)
  assertTrue(kotlin.math.abs(hit.y-842)<10)
  assertNull(MapStockIconDetector.detect(frame(200,600),w,h))
 }
 @Test fun coveredControlAndBlankScreenAreRejected(){
  val covered=frame(470,842)
  for(y in 790..890)for(x in 420..520)covered[y*w+x]=0xff112233.toInt()
  assertNull(MapStockIconDetector.detect(covered,w,h))
  assertNull(MapStockIconDetector.detect(IntArray(w*h){-1},w,h))
  assertFalse(MapStockIconDetector.onMap(frame(470,842),w,h))
  assertFalse(MapStockIconDetector.onMap(IntArray(1),w,h))
 }
}
