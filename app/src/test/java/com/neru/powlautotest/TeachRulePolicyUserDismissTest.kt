package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class TeachRulePolicyUserDismissTest{
 private fun can(label:String="×",unique:Boolean=true,
  x:Int=990,y:Int=105,width:Int=72,height:Int=72,
  labels:List<String> = listOf("Whiteout Survival","広告を終了")):Boolean =
  TeachRulePolicy.userTaughtCornerDismissWithoutSdk(
   label,unique,x,y,x+width,y+height,1080,2412,labels)

 @Test fun explicitSmallCornerXIsLearnableWithoutAdSdk(){
  assertTrue(can())
  assertTrue(can("閉じる"))
  assertTrue(can("close"))
 }
 @Test fun genericOrUnidentifiedTargetStaysPending(){
  assertFalse(can("今すぐチェック"))
  assertFalse(can("×",false))
  assertFalse(can(x=180))
  assertFalse(can(y=1910))
  assertFalse(can(width=700))
  assertFalse(can(labels=listOf("カードを1枚選んでください","×")))
  assertFalse(can(labels=listOf("動画スキップ券を使いますか","×")))
  assertFalse(can(labels=listOf("友達にシェアする","×")))
 }
}
