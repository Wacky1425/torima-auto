package com.neru.powlautotest

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Small offline X-shape detector for captured ad screenshots.
 * This produces DIAGNOSTIC candidates only. It cannot prove an ad is finished,
 * and MUST NOT dispatch a tap or authorize a learned close by itself.
 *
 * Callers provide a downscaled bitmap's ARGB pixels (e.g. width=360).
 */
internal object VisualCloseDetector {
 data class Candidate(val x:Int,val y:Int,val score:Int,val region:String,val radius:Int)

 private fun gray(v:Int):Int{
  val r=v ushr 16 and 255
  val g=v ushr 8 and 255
  val b=v and 255
  return (r*30+g*59+b*11)/100
 }

 private fun contrast(pixels:IntArray,w:Int,h:Int,x:Int,y:Int,radius:Int):Int{
  if(x-radius<0 || x+radius>=w || y-radius<0 || y+radius>=h)return 0
  val center=gray(pixels[y*w+x])
  var diagonals=0
  var arms=0
  var off=0
  var diagMismatch=0
  for(i in 1..4){
   val d=max(2,radius*i/5)
   val diag=listOf(
    gray(pixels[(y+d)*w+(x+d)]),
    gray(pixels[(y-d)*w+(x-d)]),
    gray(pixels[(y+d)*w+(x-d)]),
    gray(pixels[(y-d)*w+(x+d)])
   )
   val background=listOf(
    gray(pixels[y*w+(x+d)]),
    gray(pixels[y*w+(x-d)]),
    gray(pixels[(y+d)*w+x]),
    gray(pixels[(y-d)*w+x])
   )
   diagonals+=diag.sum()
   off+=background.sum()
   arms+=4
   diagMismatch+=diag.sumOf{abs(it-center)}
  }
  val meanDiag=diagonals/arms
  val meanOff=off/arms
  val difference=abs(meanDiag-meanOff)
  val centerAgreement=abs(meanDiag-center)
  val consistency=diagMismatch/arms
  if(difference<62 || centerAgreement>58 || consistency>76)return 0
  // A plus-shaped icon has bright axis strokes and dim diagonals;
  // it fails because its diagonal intensity disagrees with the center.
  return (difference-centerAgreement*2-consistency/3).coerceIn(0,255)*100/255
 }

 fun detect(pixels:IntArray,width:Int,height:Int):List<Candidate>{
  if(width<100 || height<180 || pixels.size!=width*height)return emptyList()
  val candidates=ArrayList<Candidate>()
  val step=max(3,width/90)
  val rightStart=(width*75/100).coerceIn(0,width)
  val leftEnd=width*25/100
  val topEnd=min(height*23/100,width*72/100)
  val bottomStart=height*72/100
  fun scan(region:String,left:Int,top:Int,right:Int,bottom:Int){
   for(y in max(18,top) until min(height-18,bottom) step step){
    for(x in max(18,left) until min(width-18,right) step step){
     var winner=0
     var radius=0
     for(r in listOf(7,11,15)){
      val score=contrast(pixels,width,height,x,y,r)
      if(score>winner){winner=score;radius=r}
     }
     if(winner>=23)candidates.add(Candidate(x,y,winner,region,radius))
    }
   }
  }
  scan("top-left",0,0,leftEnd,topEnd)
  scan("top-right",rightStart,0,width,topEnd)
  scan("bottom",0,bottomStart,width,height)
  val distinct=ArrayList<Candidate>()
  for(candidate in candidates.sortedByDescending{it.score}){
   if(distinct.any{same->
     abs(candidate.x-same.x)<max(14,width/22) &&
      abs(candidate.y-same.y)<max(14,width/22)
   })continue
   distinct.add(candidate)
   if(distinct.size>=8)break
  }
  return distinct
 }
}
