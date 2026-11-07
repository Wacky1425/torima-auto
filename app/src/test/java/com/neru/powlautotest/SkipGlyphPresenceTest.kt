package com.neru.powlautotest
import org.junit.Assert.*
import org.junit.Test

class SkipGlyphPresenceTest {
 private val w=540;private val h=1206
 private fun frame(dark:Boolean)=IntArray(w*h){if(dark)0xff111111.toInt() else -1}
 private fun draw(p:IntArray,dark:Boolean,square:Boolean=false,bar:Boolean=true){
  val ink=if(dark)-1 else 0xff333333.toInt()
  fun dot(x:Int,y:Int){for(d in 0..1)p[y*w+x+d]=ink}
  for(y in 83..97){dot(496,y);if(bar)dot(513,y)}
  if(square){
   for(x in 496..509){dot(x,83);dot(x,97)}
   for(y in 83..97)dot(509,y)
  }else{
   for(x in 496..509){dot(x,83+(x-496)/2);dot(x,97-(x-496)/2)}
  }
 }
 @Test fun recognizesOutlinedTriangleBarInEitherPolarity(){
  for(dark in listOf(false,true)){
   val p=frame(dark);draw(p,dark)
   assertTrue(SkipGlyphPresence.visible(p,w,h,504,90))
   assertFalse(SkipGlyphPresence.dismissVisible(p,w,h,504,90,false))
   assertTrue(SkipGlyphPresence.dismissVisible(p,w,h,504,90,true))
  }
 }
 @Test fun verifiesGlyphAtCurrentButtonCenterWhilePreservingEdgeTap(){
  val p=frame(false);draw(p,false)
  val control=DismissProbePolicy.Box(486,72,522,108)
  val tap=DismissProbePolicy.Point(504,74)
  assertFalse(SkipGlyphPresence.visible(p,w,h,tap.x,tap.y))
  val probe=DismissProbePolicy.point(tap,control)
  assertEquals(DismissProbePolicy.Point(504,90),probe)
  assertTrue(SkipGlyphPresence.visible(p,w,h,probe.x,probe.y))
  assertEquals(DismissProbePolicy.Point(504,74),tap)
  assertEquals(tap,DismissProbePolicy.point(tap,null))
 }
 @Test fun rejectsEmptyRectangleAndPlayWithoutStopBar(){
  assertFalse(SkipGlyphPresence.visible(frame(false),w,h,504,90))
  val square=frame(false);draw(square,false,square=true)
  assertFalse(SkipGlyphPresence.visible(square,w,h,504,90))
  val play=frame(false);draw(play,false,bar=false)
  assertFalse(SkipGlyphPresence.visible(play,w,h,504,90))
 }
 @Test fun movingAdCanRevalidateTaughtSkipWithoutMatchingTheWholeCreative(){
  val x=504;val y=90
  fun creative(moved:Boolean,button:Boolean):IntArray{
   val p=frame(false)
   for(yy in 0 until h)for(xx in 0 until w){
    if(kotlin.math.abs(xx-x)<=33 && kotlin.math.abs(yy-y)<=33)continue
    if((xx<x)==moved)p[yy*w+xx]=0xff111111.toInt()
   }
   // A stable circular control rim supplies the taught local texture.
   for(dy in -23..23)for(dx in -23..23)
    if(dx*dx+dy*dy in 400..529)p[(y+dy)*w+x+dx]=0xff333333.toInt()
   if(button)draw(p,false)
   return p
  }
  val rule=checkNotNull(VisualTemplateModel.capture(creative(false,true),w,h,x,y))
  val current=creative(true,true)
  assertNull(VisualTemplateModel.search(rule,current,w,h))
  assertNull(VisualTemplateModel.searchLocalX(rule,current,w,h))
  assertNotNull(VisualTemplateModel.searchLocalX(rule,current,w,h,allowSkip=true))
  assertNull(VisualTemplateModel.searchLocalX(rule,creative(true,false),w,h,allowSkip=true))
 }
 @Test fun learnedSkipNeedsIndependentAdContextAndCannotEscapeGacha(){
  fun allowed(kind:String="LEARNED_VISUAL",registered:Boolean=true,ad:Boolean=true,blocked:Boolean=false,x:Int=1008,y:Int=180)=
   RewardAdPolicy.learnedSkipAllowed(kind,registered,ad,blocked,x,y,1080,2412)
  assertTrue(allowed())
  assertFalse(allowed(registered=false))
  assertFalse(allowed(ad=false))
  assertFalse(allowed(blocked=true))
  assertFalse(allowed(kind="HOME"))
  assertFalse(allowed(x=540,y=1200))
 }
}
