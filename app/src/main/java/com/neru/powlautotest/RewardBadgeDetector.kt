package com.neru.powlautotest

/**
 * Reads only the tiny red notification dots above the MOVE and STEPS
 * header tabs, using the verified 688x1536 HOME screenshot proportions.
 *
 * These dots are hints about possible rewards, not proof of completion:
 * page-specific remaining-count and disabled controls remain authoritative.
 * No network request or raw screenshot retention.
 */
internal object RewardBadgeDetector {
 enum class Mark { PRESENT, ABSENT, UNKNOWN }
 data class Result(
  val move:Mark,val steps:Mark,
  val moveRedPixels:Int,val stepRedPixels:Int,
  val width:Int,val height:Int,
  val selected:RewardTabPolicy.Tab=RewardTabPolicy.Tab.UNKNOWN
 ){
  fun describe():String="赤丸（移動=${move.name}:${moveRedPixels}px / 歩数=${steps.name}:${stepRedPixels}px）"
 }

 private fun red(c:Int):Boolean{
  val r=(c ushr 16) and 255
  val g=(c ushr 8) and 255
  val b=c and 255
  return r>=205 && g<=72 && b<=72 && r>=g+110 && r>=b+110
 }
 private fun yellowHeader(c:Int):Boolean{
  val r=(c ushr 16) and 255
  val g=(c ushr 8) and 255
  val b=c and 255
  return r>=205 && g in 75..238 && b<=85
 }
 private fun orangeHeader(c:Int):Boolean{
  val r=(c ushr 16) and 255
  val g=(c ushr 8) and 255
  val b=c and 255
  return r>=235 && g in 78..150 && b<=55
 }
 private fun goldHeader(c:Int):Boolean{
  val r=(c ushr 16) and 255
  val g=(c ushr 8) and 255
  val b=c and 255
  return r>=235 && g in 155..225 && b<=55
 }
 private fun selectedHeader(pixels:IntArray,w:Int,h:Int):RewardTabPolicy.Tab{
  // Small blank strips left of the MOVE and STEPS icons. In the two user
  // screenshots, the active tab is orange and the other tab is gold.
  val y=(h*0.145).toInt()
  val mx=(w*0.035).toInt()
  val sx=(w*0.160).toInt()
  if(y !in 0 until h || mx !in 0 until w || sx !in 0 until w)
   return RewardTabPolicy.Tab.UNKNOWN
  val move=pixels[y*w+mx]
  val steps=pixels[y*w+sx]
  return when{
   orangeHeader(move) && goldHeader(steps)->RewardTabPolicy.Tab.MOVE
   orangeHeader(steps) && goldHeader(move)->RewardTabPolicy.Tab.STEPS
   else->RewardTabPolicy.Tab.UNKNOWN
  }
 }
 // A 15px diameter red dot in a 688px full-height screenshot becomes
 // approximately 12px in the 540px detector image.
 private fun marker(pixels:IntArray,w:Int,h:Int,xFraction:Double):Pair<Mark,Int>{
  val x=(w*xFraction).toInt()
  val y=(h*0.1172).toInt()
  val rx=maxOf(10,(w*0.021).toInt())
  val ry=maxOf(10,(h*0.010).toInt())
  if(x-rx<0 || x+rx>=w || y-ry<0 || y+ry>=h)
   return Pair(Mark.UNKNOWN,0)
  var reds=0
  var minX=w;var maxX=0;var minY=h;var maxY=0
  for(yy in y-ry..y+ry)for(xx in x-rx..x+rx){
   if(red(pixels[yy*w+xx])){
    reds++
    minX=minOf(minX,xx);maxX=maxOf(maxX,xx)
    minY=minOf(minY,yy);maxY=maxOf(maxY,yy)
   }
  }
  val enough=reds>=maxOf(30,(w*w*0.000100).toInt())
  val compact= maxX-minX in 7..(2*rx) &&
   maxY-minY in 7..(2*ry)
  if(enough && compact)return Pair(Mark.PRESENT,reds)
  // Absence is reliable only with the characteristic yellow/orange HOME
  // header behind this exact position. A modal or overlay is UNKNOWN.
  val center=pixels[y*w+x]
  val side=pixels[y*w+(x+rx+4).coerceAtMost(w-1)]
  return if(reds<=8 && (yellowHeader(center)||yellowHeader(side)))
   Pair(Mark.ABSENT,reds)
  else Pair(Mark.UNKNOWN,reds)
 }
 fun detect(pixels:IntArray,width:Int,height:Int):Result{
  if(width<330 || height<650 || pixels.size!=width*height ||
   height.toDouble()/width !in 1.9..2.65)
   return Result(Mark.UNKNOWN,Mark.UNKNOWN,0,0,width,height)
  val move=marker(pixels,width,height,0.116)
  val steps=marker(pixels,width,height,0.260)
  return Result(move.first,steps.first,move.second,steps.second,
   width,height,selectedHeader(pixels,width,height))
 }
 fun same(a:Result,b:Result):Boolean =
  a.move==b.move && a.steps==b.steps &&
   a.selected==b.selected &&
   a.move!=Mark.UNKNOWN && a.steps!=Mark.UNKNOWN
 fun canSkipMoveOnStepStart(result:Result,stableReads:Int):Boolean =
  stableReads>=2 && result.move==Mark.ABSENT &&
   result.steps==Mark.PRESENT
}
