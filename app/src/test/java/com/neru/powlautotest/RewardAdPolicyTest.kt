package com.neru.powlautotest
import org.junit.Assert.*
import org.junit.Test

class RewardAdPolicyTest {
 @Test fun acceptsNewRetryCaptionButNeverTicketOrAdText(){
  assertEquals("ガチャに再チャレンジ",RewardAdPolicy.retryLabel(listOf("ガチャに再チャレンジ","このまま受け取る")))
  assertEquals("動画を見て再チャレンジ",RewardAdPolicy.retryLabel(listOf("動画を見て再チャレンジ")))
  assertNull(RewardAdPolicy.retryLabel(listOf("スキップ券を使う")))
  assertNull(RewardAdPolicy.retryLabel(listOf("広告：ガチャに再チャレンジすると特典")))
  assertNull(RewardAdPolicy.retryLabel(RewardAdPolicy.retryLabels))
 }
 @Test fun playableNeedsIndependentSdkAndCanvasNotMerelyAClose(){
  val frame=listOf("app-interstitial-slot","mys-creative","mys-wrapper","application-canvas","playable-container")
  assertTrue(RewardAdPolicy.playableFrame(frame))
  for(missing in frame)assertFalse(RewardAdPolicy.playableFrame(frame-missing))
  assertFalse(RewardAdPolicy.playableFrame(listOf("close","Skip")))
 }
 @Test fun onlyPostResultPhaseCanLeaveForProvenAdAndNeedsRepeatedEvidence(){
  val gate=RewardAdPolicy.AdAfterResult()
  assertFalse(gate.observe(GachaSafety.Phase.AWAIT_RESULT,true,false,4000,10000))
  assertFalse(gate.observe(GachaSafety.Phase.AWAIT_REWARD,true,false,4000,10000))
  assertTrue(gate.observe(GachaSafety.Phase.AWAIT_REWARD,true,false,5000,11000))
  assertFalse(gate.observe(GachaSafety.Phase.AWAIT_REWARD,true,true,6000,12000))
  assertFalse(gate.observe(GachaSafety.Phase.AWAIT_REWARD,true,false,7000,13000))
  assertFalse(gate.observe(GachaSafety.Phase.AWAIT_REWARD,false,false,8000,14000))
 }
 @Test fun skipNeverMeansTicketOrInstallAndMustBeSmallUpperCorner(){
  assertTrue(RewardAdPolicy.isSkipLabel("Skip"))
  assertFalse(RewardAdPolicy.isSkipLabel("スキップ券を使う"))
  assertFalse(RewardAdPolicy.isSkipLabel("11"))
  assertFalse(RewardAdPolicy.isSkipLabel("インストール"))
  assertTrue(RewardAdPolicy.skipBounds(36,144,108,216,1080,2412))
  assertFalse(RewardAdPolicy.skipBounds(36,1500,108,1572,1080,2412))
  assertFalse(RewardAdPolicy.skipBounds(0,0,1080,2412,1080,2412))
 }
 @Test fun skipUsesTwoChevronsNotAnXOrASavedCoordinate(){
  val w=540;val h=1206
  fun base()=IntArray(w*h){0xff111111.toInt()}
  fun dot(p:IntArray,x:Int,y:Int){
   for(dy in -1..1)for(dx in -1..1)p[(y+dy)*w+x+dx]=-1
  }
  val skip=base()
  for(tip in listOf(31,52))for(d in 0..11){dot(skip,tip-d,88-d);dot(skip,tip-d,88+d)}
  assertTrue(OverlayIconDetector.skipVisibleAt(skip,w,h,40,88))
  assertFalse(OverlayIconDetector.skipVisibleAt(skip,w,h,500,88))
  assertFalse(OverlayIconDetector.skipVisibleAt(base(),w,h,40,88))
  val x=base()
  for(d in -11..11){dot(x,40+d,88+d);dot(x,40+d,88-d)}
  assertFalse(OverlayIconDetector.skipVisibleAt(x,w,h,40,88))
 }
 @Test fun retryModelIgnoresMovingMiddleAdButChecksAllThreeFixedAreas(){
  val w=540;val h=1206;val x=280;val y=845
  val source=IntArray(w*h){i->
   if(i/w in y-25..y+25 && i%w in x-90..x+90) {
    if((i%w+i/w)%9==0)-1 else 0xff8055bb.toInt()
   }else if((i%w+i/w)%7<3)-1 else 0xff303030.toInt()
  }
  val model=checkNotNull(ReportedRetryModel.capture(source,w,h,x,y))
  val moving=source.copyOf()
  for(yy in 480..730)for(xx in 55..485)moving[yy*w+xx]=0xffabcdef.toInt()
  assertTrue(ReportedRetryModel.matches(model,moving,w,h))
  val missing=source.copyOf()
  for(yy in y-35..y+35)for(xx in x-40..x+40)missing[yy*w+xx]=-1
  assertFalse(ReportedRetryModel.matches(model,missing,w,h))
  assertNull(ReportedRetryModel.capture(IntArray(w*h){-1},w,h,x,y))
 }
}
