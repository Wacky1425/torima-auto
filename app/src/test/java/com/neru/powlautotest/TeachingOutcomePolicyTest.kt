package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class TeachingOutcomePolicyTest {
 private fun possibleClose(label:String="閉じる",id:String="",width:Int=82,height:Int=65)=
  TeachingOutcomePolicy.isNamedDismissTarget(label,id,width,height,1080,2412)

 @Test fun safeUserTaughtCloseIsRecordedEvenWhenPlaySheetOpens(){
  assertTrue(possibleClose("×"))
  assertTrue(possibleClose("閉じる"))
  assertTrue(possibleClose("close"))
  assertTrue(possibleClose("","com.example:id/dismiss-button"))
  assertTrue(TeachingOutcomePolicy.eligibleForRule(
   true,true,"com.android.vending",false,true))
  assertTrue(TeachingOutcomePolicy.eligibleForRule(
   true,true,"com.google.android.gms",true,true))
  assertEquals("PLAY_AFTER_TAUGHT_DISMISS",
   TeachingOutcomePolicy.outcomeName("com.android.vending",false,true))
 }
 @Test fun registrationDoesNotDependOnForegroundWhenUserPressesRegister(){
  assertTrue(TeachingOutcomePolicy.eligibleForRule(
   true,true,"jp.co.incrementp.milemobile",false,false))
  // No subsequent check of a different current package is needed.
  assertTrue(TeachingOutcomePolicy.eligibleForRule(
   true,true,"com.android.vending",false,true))
 }
 @Test fun installOrOtherExternalLinkCannotBeTaughtAsAdDismiss(){
  for(label in listOf("インストール","Install","Google Play","詳細を見る",
   "アプリ内課金","購入する","友達にシェア","動画スキップ券")){
   assertFalse(possibleClose(label))
  }
  assertFalse(possibleClose("閉じる","com.example:id/install-button"))
  assertFalse(possibleClose("閉じる","com.example:id/skip-ticket"))
  assertFalse(possibleClose("閉じる","com.example:id/lcec-cta"))
  assertFalse(possibleClose("閉じる","",730,190))
  assertFalse(TeachingOutcomePolicy.eligibleForRule(
   true,true,"com.android.vending",false,false))
  assertFalse(TeachingOutcomePolicy.eligibleForRule(
   false,true,"com.android.vending",false,true))
  assertFalse(TeachingOutcomePolicy.eligibleForRule(
   true,false,"com.android.vending",false,true))
  assertFalse(TeachingOutcomePolicy.eligibleForRule(
   true,true,"com.google.android.gms",false,true))
  assertFalse(TeachingOutcomePolicy.eligibleForRule(
   true,true,"com.example.otherapp",true,true))
 }
 @Test fun clearlyExplainsUncertainDestination(){
  assertEquals("PENDING",TeachingOutcomePolicy.outcomeName(
   "com.android.vending",false,false))
  assertEquals("TORIMA_SCREEN_CHANGED",TeachingOutcomePolicy.outcomeName(
   "jp.co.incrementp.milemobile",false,true))
  assertFalse(TeachingOutcomePolicy.isPlayDestination(
   "jp.co.incrementp.milemobile",true))
 }
}
