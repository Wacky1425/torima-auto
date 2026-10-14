package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class PendingLearningPolicyTest {
 private val w=1080;private val h=2412
 private fun key(sig:String?,clazz:String="ImageView",id:String="",label:String="",
  x:Int=1027,y:Int=167)=PendingLearningPolicy.groupKey(sig,clazz,id,label,x,y,w,h)
 @Test fun failedDifferentAdvertiserCornerTapsAreOneProblemFamily(){
  assertEquals(key("中央バス自動車学校|詳しくはこちら"),
   key("Google Play|ダウンロード|パズル",x=1002,y=202))
 }
 @Test fun opaqueTouchSamplesCloseTogetherAreDeduplicated(){
  assertEquals(key(null,clazz="",x=724,y=1833),
   key(null,clazz="",x=708,y=1825))
 }
 @Test fun namedUniqueSdkTargetsStaySeparated(){
  assertNotEquals(key("sample",clazz="View",id="dismiss-button",x=350,y=2290),
   key("sample",clazz="View",id="install-button",x=350,y=2290))
 }
 @Test fun differentNamedControlsStayDistinct(){
  assertNotEquals(key("Next",clazz="Button",label="Next",x=990,y=170),
   key("Skip",clazz="Button",label="Skip",x=990,y=170))
 }
}
