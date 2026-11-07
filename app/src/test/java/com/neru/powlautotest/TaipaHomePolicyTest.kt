package com.neru.powlautotest
import org.junit.Assert.*
import org.junit.Test
class TaipaHomePolicyTest {
 @Test fun recognizesSingleButtonLayoutWithoutTheOldDivider(){
  assertTrue(TaipaHomePolicy.modeLabel("タイパモード発動中 ›"))
  assertFalse(TaipaHomePolicy.modeLabel("動画スキップ券"))
  assertTrue(TaipaHomePolicy.verified(true,940,1220,1080,2412,false))
  assertTrue(TaipaHomePolicy.button(TaipaHomePolicy.Box(90,960,990,1120),940,1220,1080,2412))
 }
 @Test fun followsCurrentAnchorsAfterTheHomeContentMoves(){
  assertTrue(TaipaHomePolicy.verified(true,1040,1320,1080,2412,false))
  assertTrue(TaipaHomePolicy.button(TaipaHomePolicy.Box(90,1060,990,1220),1040,1320,1080,2412))
  assertFalse(TaipaHomePolicy.button(TaipaHomePolicy.Box(90,850,990,1010),1040,1320,1080,2412))
 }
 @Test fun rejectsAdsMapMissingAnchorsAndOtherButtons(){
  assertFalse(TaipaHomePolicy.verified(false,940,1220,1080,2412,false))
  assertFalse(TaipaHomePolicy.verified(true,940,1220,1080,2412,true))
  assertFalse(TaipaHomePolicy.verified(true,0,1220,1080,2412,false))
  assertFalse(TaipaHomePolicy.verified(true,940,900,1080,2412,false))
  assertFalse(TaipaHomePolicy.button(TaipaHomePolicy.Box(850,960,1040,1150),940,1220,1080,2412))
  assertFalse(TaipaHomePolicy.button(TaipaHomePolicy.Box(0,0,1080,2412),940,1220,1080,2412))
  assertFalse(TaipaHomePolicy.button(TaipaHomePolicy.Box(90,1500,990,1670),940,1220,1080,2412))
 }
}
