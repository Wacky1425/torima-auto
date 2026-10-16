package com.neru.powlautotest

import kotlin.math.max
import kotlin.math.min

/**
 * Pixel-based white X recognizer, tuned and tested against v3.9's 24 saved
 * screenshots. These images included a Rakuten red endcard at (978,272)
 * and a black fullscreen ad with a white X around (1030,160).
 *
 * Unlike the old white connected-component detector, this uses the brightness
 * of both diagonals relative to the four cardinal directions. The rightmost
 * 10% region excludes Torima's own top-leftward HOME icons that otherwise
 * trigger false positives. No OCR, cloud inference or saved images required.
 */
internal object PixelAdCloseDetector {
 private val RADII=intArrayOf(6,8,10,12,14)
 data class Hit(val x:Int,val y:Int,val score:Int)
 private fun whiteness(argb:Int):Int =
  min(argb ushr 16 and 255,min(argb ushr 8 and 255,argb and 255))
 fun detectWhiteX(pixels:IntArray,width:Int,height:Int):Hit?{
  if(width<300 || height<450 || pixels.size!=width*height)return null
  val x0=width*90/100
  val x1=width*99/100
  val y0=max(12,height*3/100)
  val y1=min(height*20/100,width*45/100)
  var best:Hit?=null
  for(y in y0 until y1 step 2)for(x in x0 until x1 step 2){
   if(whiteness(pixels[y*width+x])<180)continue
   for(r in RADII){
    if(x-r<0 || x+r>=width || y-r<0 || y+r>=height)continue
    val d0=whiteness(pixels[(y-r)*width+(x-r)])
    val d1=whiteness(pixels[(y-r)*width+(x+r)])
    val d2=whiteness(pixels[(y+r)*width+(x-r)])
    val d3=whiteness(pixels[(y+r)*width+(x+r)])
    val diagonals=intArrayOf(d0,d1,d2,d3)
    if(diagonals.count{it>=180}<3)continue
    val axisSum=whiteness(pixels[y*width+x-r])+
     whiteness(pixels[y*width+x+r])+
     whiteness(pixels[(y-r)*width+x])+
     whiteness(pixels[(y+r)*width+x])
    val axisAvg=axisSum/4
    if(axisAvg>=130)continue
    val diagAvg=(d0+d1+d2+d3)/4
    val score=diagonals.minOrNull()!!+diagAvg-axisAvg
    if(score>=445 && (best==null || score>best.score))
     best=Hit(x,y,score)
   }
  }
  return best
 }
}
