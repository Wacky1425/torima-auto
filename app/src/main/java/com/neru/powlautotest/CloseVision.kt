package com.neru.powlautotest

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * On-device, dependency-free visual discovery of likely X shapes. This only
 * produces hypotheses for diagnosis. It NEVER authorizes taps: a loading/gacha
 * screen can show an identical-looking X.
 */
internal object CloseVision {
 data class Candidate(
  val x:Int,val y:Int,val radius:Int,val confidence:Double,val region:String
 )
 private data class Area(val name:String,val x0:Double,val y0:Double,
  val x1:Double,val y1:Double)
 private val regions=listOf(
  Area("upper_right",.72,.025,.985,.235),
  Area("upper_left",.015,.025,.28,.235),
  Area("lower_left",.02,.72,.40,.965),
  Area("lower_middle",.28,.72,.72,.965),
  Area("lower_right",.60,.72,.985,.965)
 )

 /** pixel supplies packed RGB (0xRRGGBB). Scores are rough, not probabilities. */
 fun detect(width:Int,height:Int,pixel:(Int,Int)->Int):List<Candidate>{
  if(width<320 || height<550)return emptyList()
  val scale=width/1080.0
  val radii=listOf(9,13,18,24,32).map{max(5,(it*scale).toInt())}.distinct()
  val result=ArrayList<Candidate>()
  fun luminance(x:Int,y:Int):Int{
   val rgb=pixel(x.coerceIn(0,width-1),y.coerceIn(0,height-1))
   return (((rgb shr 16) and 255)*299+
    ((rgb shr 8) and 255)*587+(rgb and 255)*114)/1000
  }
  for(area in regions){
   val xStart=(width*area.x0).toInt().coerceIn(0,width-1)
   val xEnd=(width*area.x1).toInt().coerceIn(0,width-1)
   val yStart=(height*area.y0).toInt().coerceIn(0,height-1)
   val yEnd=(height*area.y1).toInt().coerceIn(0,height-1)
   for(r in radii){
    val step=max(5,r/2)
    var cy=yStart+r
    while(cy<=yEnd-r){
     var cx=xStart+r
     while(cx<=xEnd-r){
      val s=scoreX(cx,cy,r,::luminance)
      if(s>=.28)result.add(Candidate(cx,cy,r,s,area.name))
      cx+=step
     }
     cy+=step
    }
   }
  }
  val kept=ArrayList<Candidate>()
  for(candidate in result.sortedByDescending{it.confidence}){
   if(kept.size>=12)break
   if(kept.none{abs(it.x-candidate.x)<max(it.radius,candidate.radius)*1.7 &&
      abs(it.y-candidate.y)<max(it.radius,candidate.radius)*1.7}){
    kept.add(candidate)
   }
  }
  return kept
 }

 private fun scoreX(cx:Int,cy:Int,r:Int,lum:(Int,Int)->Int):Double {
  val diag=ArrayList<Int>()
  val off=ArrayList<Int>()
  // Compare both diagonals to adjacent pixels *off* the diagonal stroke.
  for(t in listOf(.40,.60,.80)){
   val d=max(3,(r*t).toInt())
   val near=max(2,d/2)
   diag.add(lum(cx-d,cy-d))
   diag.add(lum(cx+d,cy+d))
   diag.add(lum(cx-d,cy+d))
   diag.add(lum(cx+d,cy-d))
   off.add(lum(cx-d,cy-near))
   off.add(lum(cx+d,cy+near))
   off.add(lum(cx-d,cy+near))
   off.add(lum(cx+d,cy-near))
  }
  val a=diag.average()
  val b=off.average()
  val contrast=abs(a-b)/255.0
  if(contrast<.28)return 0.0
  val polarity=if(a>b)1 else -1
  val fraction=diag.count{polarity*(it-b)>=40}.toDouble()/diag.size
  // A real X should appear along both crossing strokes.
  if(fraction<.75)return 0.0
  // Reject a plus, a straight line, and flat title graphics.
  val axes=listOf(lum(cx-r/2,cy),lum(cx+r/2,cy),
   lum(cx,cy-r/2),lum(cx,cy+r/2)).average()
  if(polarity*(a-axes)<25)return 0.0
  return min(1.0,contrast*.75+fraction*.25)
 }
}
