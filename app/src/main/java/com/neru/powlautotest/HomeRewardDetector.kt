package com.neru.powlautotest

/** Current single-button tank layout; never searches ads or uses a saved tap point. */
internal object HomeRewardDetector {
 data class Box(val left:Int,val top:Int,val right:Int,val bottom:Int)
 private fun purple(c:Int):Boolean {
  val r=c ushr 16 and 255;val g=c ushr 8 and 255;val b=c and 255
  return r in 85..180 && g in 35..140 && b in 135..235 && b>=r+20 && r>=g+25
 }
 private fun yellow(c:Int):Boolean = (c ushr 16 and 255)>=225 &&
  (c ushr 8 and 255) in 145..225 && (c and 255)<75
 fun detect(p:IntArray,w:Int,h:Int):Box? {
  if(w<330 || h<650 || p.size!=w*h ||
   RewardBadgeDetector.detect(p,w,h).selected!=RewardTabPolicy.Tab.MOVE)return null
  fun purpleRow(y:Int):Boolean {
   var n=0
   for(x in w/10 until w*9/10)if(purple(p[y*w+x]))n++
   return n>=w*40/100
  }
  val candidates=ArrayList<Box>();var y=h*32/100
  while(y<h*72/100){
   if(!purpleRow(y)){y++;continue}
   val top=y
   while(y<h*72/100 && purpleRow(y))y++
   val bottom=y
   if(bottom-top !in h*3/100..h*10/100)continue
   val middle=(top+bottom)/2
   val xs=(0 until w).filter{purple(p[middle*w+it])}
   if(xs.isEmpty())continue
   val left=xs.first();val right=xs.last()+1
   if(right-left !in w*75/100..w*92/100 ||
    kotlin.math.abs((left+right)/2-w/2)>w*4/100)continue
   // The mode bubble is above; the wide yellow remaining-count strip is below.
   var modePixels=0;var modeArea=0
   for(yy in maxOf(0,top-h*6/100) until top-h/100)
    for(x in w*8/100 until w*43/100){modeArea++;if(yellow(p[yy*w+x]))modePixels++}
   if(modeArea==0 || modePixels<modeArea/5)continue
   var stripRows=0
   for(yy in bottom+h/100 until minOf(h,bottom+h*8/100)){
    var n=0
    for(x in w*10/100 until w*80/100)if(yellow(p[yy*w+x]))n++
    if(n>=w*55/100)stripRows++
   }
   if(stripRows<h/100)continue
   candidates.add(Box(left,top,right,bottom))
  }
  return candidates.singleOrNull()
 }
 fun same(a:Box,b:Box)=kotlin.math.abs(a.left-b.left)<=12 &&
  kotlin.math.abs(a.top-b.top)<=12 && kotlin.math.abs(a.right-b.right)<=12 &&
  kotlin.math.abs(a.bottom-b.bottom)<=12
}
