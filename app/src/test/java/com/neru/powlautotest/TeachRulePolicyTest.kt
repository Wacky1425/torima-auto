package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class TeachRulePolicyTest {
 private val googleOneSdk=listOf("app-interstitial-slot","mys-creative","mys-wrapper",
  "towerVideo","dismiss-button","More")
 @Test fun googleOneEndCardIsRecognizedAsAnAdWithoutAClickableDismissButton(){
  assertTrue(TeachRulePolicy.provenWebAdFrame(googleOneSdk))
  assertTrue(TeachRulePolicy.canLearnDismissByCoordinate(
   true,"dismiss-button",true,true))
 }
 @Test fun aGachaCloseCaptionAloneNeverApprovesLearning(){
  assertFalse(TeachRulePolicy.provenWebAdFrame(listOf("閉じる","カードを1枚選んでください")))
  assertFalse(TeachRulePolicy.canLearnDismissByCoordinate(
   false,"dismiss-button",true,true))
  assertFalse(TeachRulePolicy.canLearnDismissByCoordinate(
   true,"dismiss-button",false,true))
 }
 @Test fun aDifferentAdCTAOrUnrelatedSpotCannotBeTaughtAsDismiss(){
  assertFalse(TeachRulePolicy.canLearnDismissByCoordinate(true,"install-button",true,true))
  assertFalse(TeachRulePolicy.canLearnDismissByCoordinate(true,"dismiss-button",true,false))
  assertTrue(TeachRulePolicy.isForbidden("インストール"))
  assertTrue(TeachRulePolicy.isForbidden("友達にシェアする"))
  assertTrue(TeachRulePolicy.isForbiddenTargetId("install-button"))
  assertTrue(TeachRulePolicy.isForbiddenTargetId("lcec-cta"))
  assertTrue(TeachRulePolicy.isForbiddenTargetId("share-button"))
  assertFalse(TeachRulePolicy.isForbiddenTargetId("dismiss-button"))
 }
 @Test fun incompleteWebVideoIsNotEvidenceOfAnEndcard(){
  assertFalse(TeachRulePolicy.provenWebAdFrame(listOf("towerVideo","dismiss-button")))
  assertFalse(TeachRulePolicy.provenWebAdFrame(listOf("app-interstitial-slot","dismiss-button")))
  assertTrue(TeachRulePolicy.isDismissLabel("閉じる"))
 }
}
