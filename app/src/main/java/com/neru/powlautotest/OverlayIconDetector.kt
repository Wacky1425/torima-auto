package com.neru.powlautotest

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Small live, background-independent overlay-glyph detector.
 * Only bright strokes in fixed top-corner UI zones are considered. A marker
 * alone never proves that an ad is dismissible or authorizes a tap.
 */
internal object OverlayIconDetector {
 data class Marker(val kind:String,val x:Int,val y:Int,val score:Int,val region:String)
 private fun lum(v:Int):Int {
  val r=v ushr 16 and 255
  val g=v ushr 8 and 255
  val b=v and 255
  return (r*30+g*59+b*11)/100
 }
 fun detect(pixels:IntArray,width:Int,height:Int):List<Marker>{
  if(width<300 || height<450 || pixels.size!=width*height)return emptyList()
  fun l(x:Int,y:Int)=lum(pixels[y*width+x])
  val found=ArrayList<Marker>()
  val xMin=width*75/100
  val xMax=width*99/100
  val yMin=max(14,height*2/100)
  val yMax=min(height*15/100,width*53/100)
  // The moving video is NOT compared: only local diagonal strokes vs
  // immediate neighboring background. X and chevrons have different geometry.
  for(y in yMin until yMax step 2){
   for(x in xMin until xMax step 2){
    for(r in listOf(6,9,12)){
     if(x-r<0 || x+r>=width || y-r<0 || y+r>=height)continue
     val d1=max(3,r*55/100);val d2=max(4,r*78/100)
     val diag=ArrayList<Int>()
     val off=ArrayList<Int>()
     for(d in listOf(d1,d2)){
      for(sx in listOf(-1,1))for(sy in listOf(-1,1)){
       diag.add(l(x+sx*d,y+sy*d))
      }
      off.add(l(x-d,y));off.add(l(x+d,y))
      off.add(l(x,y-d));off.add(l(x,y+d))
     }
     val main=diag.average();val bg=off.average()
     val score=(main-bg).toInt()
     if(score>=90 && diag.count{it>=bg+65}>=6 &&
      l(x,y)>=bg+70){
      found.add(Marker("X",x,y,score,"top-right"))
     }
    }
    // Two > strokes: upper/lower diagonals of TWO adjacent chevrons.
    for(gap in listOf(14,18,22)){
     val other=x-gap
     if(other<0 || x+3>=width || y-12<0 || y+12>=height)continue
     var worst=255
     for(tip in listOf(other,x)){
      val diag=ArrayList<Int>();val off=ArrayList<Int>()
      for(d in listOf(4,7,10)){
       diag.add(l(tip-d,y-d));diag.add(l(tip-d,y+d))
       off.add(l(tip-d,y))
      }
      val background=off.average()
      val score=(diag.average()-background).toInt()
      val valid=diag.count{it>=background+55}>=4 &&
       l(tip,y)>=background+55
      worst=min(worst,if(valid)score else 0)
     }
     if(worst>=85)found.add(Marker("NEXT",x,y,worst,"top-right"))
    }
   }
  }
  // At most one hypothesis of each type. Avoid multiple neighboring glyph
  // candidates moving between frames and forcing repeated actions.
  return found.groupBy{it.kind}.values.mapNotNull{group->
   group.maxByOrNull{it.score}
  }.sortedByDescending{it.score}
 }
}
