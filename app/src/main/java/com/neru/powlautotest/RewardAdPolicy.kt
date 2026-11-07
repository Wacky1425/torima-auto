package com.neru.powlautotest

/** Source-screen evidence; it never authorizes a saved coordinate by itself. */
internal object RewardAdPolicy {
 val retryLabels=setOf("動画を見て再チャレンジ","ガチャに再チャレンジ")
 fun retryLabel(labels:Collection<String>):String?{
  val exact=labels.map{it.trim()}.toSet()
  return retryLabels.singleOrNull{it in exact}
 }
 fun playableFrame(ids:Collection<String>):Boolean{
  val parts=ids.map{it.substringAfterLast('/').lowercase()}.toSet()
  return "app-interstitial-slot" in parts && "mys-creative" in parts &&
   "mys-wrapper" in parts && "application-canvas" in parts &&
   ("playable-container" in parts || "playable-with-exit-no-banner" in parts)
 }
 fun isSkipLabel(value:String)=value.trim().lowercase() in setOf("skip","スキップ",">>","≫")
 fun skipBounds(left:Int,top:Int,right:Int,bottom:Int,w:Int,h:Int):Boolean =
  w>0 && h>0 && left>=0 && top>=0 && right<=w && bottom<=h*20/100 &&
   right-left in 20..(w*30/100) && bottom-top in 20..(h*12/100) &&
   (right<=w*25/100 || left>=w*70/100)

 class AdAfterResult {
  private var since=0L
  private var samples=0
  fun observe(phase:GachaSafety.Phase,provenFrame:Boolean,appControls:Boolean,
   elapsedMs:Long,now:Long):Boolean{
   if(phase!=GachaSafety.Phase.AWAIT_REWARD || !provenFrame || appControls){
    reset();return false
   }
   if(samples==0)since=now
   samples++
   return elapsedMs>=3000L && samples>=2 && now-since>=850L
  }
  fun reset(){since=0L;samples=0}
 }
}
