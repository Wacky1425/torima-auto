package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class StableAdClosePolicyTest {
 @Test fun rakutenStyleSmallUpperRightXQualifies(){
  assertTrue(StableAdClosePolicy.upperRightClose(
   963,70,1035,139,1080,2412))
  assertTrue(StableAdClosePolicy.upperRightClose(
   890,100,1010,215,1080,2412))
 }
 @Test fun bottomGachaXAndLargeOrUnknownSurfacesNeverQualify(){
  assertFalse(StableAdClosePolicy.upperRightClose(
   470,2130,620,2260,1080,2412))
  assertFalse(StableAdClosePolicy.upperRightClose(
   300,70,410,139,1080,2412))
  assertFalse(StableAdClosePolicy.upperRightClose(
   40,40,1020,2150,1080,2412))
  assertFalse(StableAdClosePolicy.upperRightClose(
   963,70,1035,139,0,2412))
 }
 @Test fun transientLoadingIsASeparateState(){
  assertTrue(StableAdClosePolicy.plausibleTransientLoading(
   1,false,false,false))
  assertTrue(StableAdClosePolicy.plausibleTransientLoading(
   90,true,false,false))
  assertTrue(StableAdClosePolicy.plausibleTransientLoading(
   90,false,false,true))
  assertTrue(StableAdClosePolicy.plausibleTransientLoading(
   90,false,true,false))
  assertFalse(StableAdClosePolicy.plausibleTransientLoading(
   90,false,false,false))
  assertTrue(StableAdClosePolicy.hasGachaOrLoadingText(
   listOf("カードを1枚選んでください")))
  assertTrue(StableAdClosePolicy.hasGachaOrLoadingText(
   listOf("通信データ読み込み中")))
  assertFalse(StableAdClosePolicy.hasGachaOrLoadingText(
   listOf("Rakuten","防水シートカバー","今すぐチェック")))
 }
 @Test fun stableTopRightXCanBeDismissedWithoutSdkIdentifiers(){
  val good="AD_CLOSE"
  assertFalse(StableAdClosePolicy.isStableCloseAllowed(
   good,true,false,false,false,false,2_999,3))
  assertFalse(StableAdClosePolicy.isStableCloseAllowed(
   good,true,false,false,false,false,4_000,1))
  assertTrue(StableAdClosePolicy.isStableCloseAllowed(
   good,true,false,false,false,false,3_000,2))
  assertFalse(StableAdClosePolicy.isStableCloseAllowed(
   good,false,false,false,false,false,9_000,5))
  assertFalse(StableAdClosePolicy.isStableCloseAllowed(
   good,true,true,false,false,false,9_000,5))
  assertFalse(StableAdClosePolicy.isStableCloseAllowed(
   good,true,false,true,false,false,9_000,5))
  assertFalse(StableAdClosePolicy.isStableCloseAllowed(
   good,true,false,false,true,false,9_000,5))
  assertFalse(StableAdClosePolicy.isStableCloseAllowed(
   good,true,false,false,false,true,9_000,5))
  assertFalse(StableAdClosePolicy.isStableCloseAllowed(
   "CARD_RESULT",true,false,false,false,false,9_000,5))
  assertFalse(StableAdClosePolicy.isStableCloseAllowed(
   good,true,false,false,false,false,5_999,5,
   StableAdClosePolicy.AFTER_GACHA_STABLE_MS))
  assertTrue(StableAdClosePolicy.isStableCloseAllowed(
   good,true,false,false,false,false,6_000,5,
   StableAdClosePolicy.AFTER_GACHA_STABLE_MS))
 }
 @Test fun moderateOverlapAllowedButNotSingleGenericCloseText(){
  assertFalse(StableAdClosePolicy.sufficientAnchorOverlap(2,1))
  assertTrue(StableAdClosePolicy.sufficientAnchorOverlap(2,2))
  assertTrue(StableAdClosePolicy.sufficientAnchorOverlap(5,3))
  assertTrue(StableAdClosePolicy.sufficientAnchorOverlap(6,3))
  assertFalse(StableAdClosePolicy.sufficientAnchorOverlap(8,3))
  assertTrue(StableAdClosePolicy.sufficientAnchorOverlap(8,4))
  assertFalse(StableAdClosePolicy.sufficientAnchorOverlap(1,1))
 }
}
