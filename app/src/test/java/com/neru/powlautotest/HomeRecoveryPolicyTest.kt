package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class HomeRecoveryPolicyTest {
 @Test fun recoveryRequiresTwoRealFailuresWhileStillOnHome(){
  assertFalse(HomeRecoveryPolicy.shouldRecover(true,1,0,false,true,90_000L))
  assertFalse(HomeRecoveryPolicy.shouldRecover(true,2,0,true,true,90_000L))
  assertFalse(HomeRecoveryPolicy.shouldRecover(true,2,0,false,false,90_000L))
  assertFalse(HomeRecoveryPolicy.shouldRecover(false,7,0,false,true,90_000L))
  assertFalse(HomeRecoveryPolicy.shouldRecover(true,2,1,false,true,90_000L))
  assertFalse(HomeRecoveryPolicy.shouldRecover(true,2,0,false,true,6_999L))
  assertTrue(HomeRecoveryPolicy.shouldRecover(true,2,0,false,true,7_000L))
 }
 @Test fun onlyThreeFreshAttemptsAfterOneRecovery(){
  assertFalse(HomeRecoveryPolicy.exhaustedAfterRestart(7,0,999_999L))
  assertFalse(HomeRecoveryPolicy.exhaustedAfterRestart(2,1,100_000L))
  assertFalse(HomeRecoveryPolicy.exhaustedAfterRestart(3,1,6_999L))
  assertTrue(HomeRecoveryPolicy.exhaustedAfterRestart(3,1,7_000L))
 }
 @Test fun forceStopAndConfirmationMustBeExact(){
  for(t in listOf("強制停止","強制終了","Force stop","FORCE CLOSE"))
   assertTrue(HomeRecoveryPolicy.exactStopLabel(t))
  for(t in listOf("アンインストール","無効にする","設定を開く","トリマを強制停止できます"))
   assertFalse(HomeRecoveryPolicy.exactStopLabel(t))
  assertTrue(HomeRecoveryPolicy.isForceStopQuestion("アプリを強制停止しますか？"))
  assertTrue(HomeRecoveryPolicy.isForceStopQuestion("Force stopping this app?"))
  assertFalse(HomeRecoveryPolicy.isForceStopQuestion("OK"))
  assertTrue(HomeRecoveryPolicy.exactConfirmLabel("OK"))
  assertFalse(HomeRecoveryPolicy.exactConfirmLabel("キャンセル"))
 }
 @Test fun unsafeLargeAncestorCannotBeClicked(){
  assertTrue(HomeRecoveryPolicy.safeClickableBounds(320,90,1080,2412))
  assertFalse(HomeRecoveryPolicy.safeClickableBounds(1080,2200,1080,2412))
  assertFalse(HomeRecoveryPolicy.safeClickableBounds(100,500,1080,2412))
  assertFalse(HomeRecoveryPolicy.safeClickableBounds(0,100,1080,2412))
 }
 @Test fun onlyExactTargetTitlePasses(){
  assertTrue(HomeRecoveryPolicy.isTargetTitle("トリマ","トリマ - 移動でポイント"))
  assertTrue(HomeRecoveryPolicy.isTargetTitle("トリマ - 移動でポイント","トリマ - 移動でポイント"))
  assertFalse(HomeRecoveryPolicy.isTargetTitle("トリマ以外のアプリ","トリマ"))
  assertFalse(HomeRecoveryPolicy.isTargetTitle("トリマの設定情報","トリマ"))
 }
}
