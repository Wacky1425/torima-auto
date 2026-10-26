package com.neru.powlautotest
import org.junit.Assert.*
import org.junit.Test
class NavigationVisualPolicyTest {
 @Test fun screenNamesDoNotProvideVisualAuthorization(){
  assertEquals("MAP",NavigationVisualPolicy.label("  MAP ","UNKNOWN"))
  assertEquals("MAP",NavigationVisualPolicy.label("  ","MAP"))
  assertFalse(NavigationVisualPolicy.sameBand(IntArray(279){128},IntArray(279){128}))
 }
 @Test fun movingMapTilesDoesNotChangeHeaderOrBottomProof(){
  val w=540;val h=1200
  val p=IntArray(w*h){i->if((i%w/7+i/w/7)%2==0)-1 else 0xff444444.toInt()}
  val top=NavigationVisualPolicy.band(p,w,h,false)!!;val bottom=NavigationVisualPolicy.band(p,w,h,true)!!
  for(y in 250..1000)for(x in 0 until w)p[y*w+x]=0xff00cc77.toInt()
  assertTrue(NavigationVisualPolicy.sameBand(top,NavigationVisualPolicy.band(p,w,h,false)!!))
  assertTrue(NavigationVisualPolicy.sameBand(bottom,NavigationVisualPolicy.band(p,w,h,true)!!))
  assertFalse(NavigationVisualPolicy.sameBand(top,IntArray(279){0}))
 }
 @Test fun localControlSurvivesChangingMapButNotMissingButton(){
  val w=540;val h=1200;val x=470;val y=842
  val p=IntArray(w*h){0xff222222.toInt()}
  for(dy in -28..28)for(dx in -28..28)p[(y+dy)*w+x+dx]=if((dx/5+dy/5)%2==0)-1 else 0xff222222.toInt()
  val rule=VisualTemplateModel.capture(p,w,h,x,y)!!
  for(yy in y-110..y+110)for(xx in x-110..minOf(w-1,x+110))if(kotlin.math.abs(xx-x)>35 || kotlin.math.abs(yy-y)>35)p[yy*w+xx]=0xff00cc77.toInt()
  assertNotNull(NavigationVisualPolicy.localControl(rule,p,w,h))
  assertNull(NavigationVisualPolicy.localControl(rule,IntArray(w*h){-1},w,h))
 }
}
