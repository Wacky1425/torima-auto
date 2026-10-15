package com.neru.powlautotest

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Best-effort one-time HOME recovery. Android does not expose a public
 * force-stop-other-app API. This controller only operates verified Settings UI
 * controls for the exact Torima app, never arbitrary recents/tasks or blind
 * coordinates. Unknown vendor UI -> safe soft relaunch; never claim kill.
 */
internal class HomeSettingsRecovery(
 private val service:PowlAccessibilityService,
 private val status:(String)->Unit
) {
 companion object{
  private const val TARGET="jp.co.incrementp.milemobile"
  private const val POLL_MS=600L
 }
 private enum class Phase { IDLE, APP_INFO, CONFIRM, WAIT_STOP, OPEN_APP, WAIT_APP }
 private val handler=Handler(Looper.getMainLooper())
 private var phase=Phase.IDLE
 private var generation=0
 private var phaseAt=0L
 private var callback:((Boolean,Boolean,String)->Unit)?=null
 private var label="トリマ"
 private var settingsPackage=""
 private var appInfoVerified=false
 private var stopButtonPressed=false
 private var confirmPressed=false
 private var forceButtonDisabled=false
 private var why=""
 fun isRunning():Boolean=phase!=Phase.IDLE
 fun cancel(){
  generation++
  handler.removeCallbacksAndMessages(null)
  phase=Phase.IDLE
  callback=null
  service.restoreHomeTouch()
 }
 private fun log(state:String,message:String){
  HomeRecoveryJournal.note(service,state,message)
  status("HOME復旧: $message")
 }
 private fun advance(next:Phase){
  phase=next
  phaseAt=SystemClock.elapsedRealtime()
 }
 private fun timedOut(limit:Long)=SystemClock.elapsedRealtime()-phaseAt>=limit
 private fun later(ms:Long=POLL_MS){
  val token=generation
  handler.postDelayed({if(token==generation && isRunning())tick()},ms)
 }
 fun start(done:(Boolean,Boolean,String)->Unit):Boolean{
  if(isRunning())return false
  generation++
  callback=done
  advance(Phase.APP_INFO)
  appInfoVerified=false
  stopButtonPressed=false
  confirmPressed=false
  forceButtonDisabled=false
  why=""
  try{
   val app=service.packageManager.getApplicationInfo(TARGET,0)
   label=service.packageManager.getApplicationLabel(app).toString()
  }catch(_:Exception){label="トリマ"}
  val intent=Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
   Uri.parse("package:$TARGET")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  settingsPackage=intent.resolveActivity(service.packageManager)?.packageName.orEmpty()
  log("START","HOME失敗が続いたため、トリマのアプリ情報を開いて復旧を試す")
  service.prepareRecoveryOverlay {ready,note->
   if(!isRunning())return@prepareRecoveryOverlay
   if(!ready){
    finish(false,false,"操作バーの透過設定に失敗: $note")
    return@prepareRecoveryOverlay
   }
   try{
    service.startActivity(intent)
    advance(Phase.APP_INFO)
    log("INFO_OPENED","アプリ情報を開く。設定パッケージ=$settingsPackage")
    later()
   }catch(e:Exception){
    log("INFO_UNAVAILABLE","設定画面を開けず: ${e.javaClass.simpleName}")
    softReopen("設定画面の起動失敗")
   }
  }
  return true
 }
 private fun trustedSettings(pkg:String):Boolean=
  (pkg.isNotBlank() && pkg==settingsPackage) ||
   pkg in setOf("com.android.settings","com.coloros.safecenter",
    "com.oplus.safecenter","com.oplus.securitypermission",
    "com.oplus.securitycenter","com.heytap.safecenter")
 private fun visible(root:AccessibilityNodeInfo):List<AccessibilityNodeInfo>{
  val all=ArrayList<AccessibilityNodeInfo>()
  fun scan(node:AccessibilityNodeInfo){
   if(all.size>=250)return
   all.add(node)
   for(i in 0 until node.childCount){
    if(all.size>=250)break
    node.getChild(i)?.let{scan(it)}
   }
  }
  scan(root)
  return all.filter{it.isVisibleToUser}
 }
 private fun text(node:AccessibilityNodeInfo):String{
  val t=node.text?.toString()?.trim().orEmpty()
  val d=node.contentDescription?.toString()?.trim().orEmpty()
  return if(t.isNotBlank())t else d
 }
 private fun safeClickable(node:AccessibilityNodeInfo):AccessibilityNodeInfo?{
  var current:AccessibilityNodeInfo?=node
  val display=service.resources.displayMetrics
  for(i in 0..3){
   val n=current?:return null
   val rect=Rect();n.getBoundsInScreen(rect)
   if(n.isClickable && n.isEnabled && n.isVisibleToUser &&
    HomeRecoveryPolicy.safeClickableBounds(rect.width(),rect.height(),
     display.widthPixels,display.heightPixels))return n
   current=n.parent
  }
  return null
 }
 private fun findCandidates(nodes:List<AccessibilityNodeInfo>,
  match:(String)->Boolean):List<AccessibilityNodeInfo>{
  val distinct=LinkedHashMap<String,AccessibilityNodeInfo>()
  for(n in nodes){
   if(!match(text(n)))continue
   val clickable=safeClickable(n)?:continue
   val r=Rect();clickable.getBoundsInScreen(r)
   distinct[r.toShortString()]=clickable
  }
  return distinct.values.toList()
 }
 private fun appInfo(){
  val root=service.rootInActiveWindow
  val pkg=root?.packageName?.toString().orEmpty()
  if(root!=null && trustedSettings(pkg)){
   val nodes=visible(root)
   val titles=nodes.map{HomeRecoveryPolicy.isTargetTitle(text(it),label)}
   if(titles.any{it}){
    appInfoVerified=true
    val candidates=findCandidates(nodes,HomeRecoveryPolicy::exactStopLabel)
    if(candidates.size==1){
     stopButtonPressed=candidates[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
     log("FORCE_STOP_REQUESTED",
      "トリマ本人と設定画面を確認、強制停止を押した=$stopButtonPressed")
     if(stopButtonPressed){
      advance(Phase.CONFIRM);later();return
     }
    }else if(candidates.size>1){
     log("AMBIGUOUS_STOP","強制停止が複数候補。誤操作を避ける")
     softReopen("停止ボタンの候補が複数")
     return
    }
   }
  }
  if(timedOut(HomeRecoveryPolicy.SETTINGS_TIMEOUT_MS)){
   log("UNKNOWN_SETTINGS",
    "トリマのアプリ情報を特定できず。pkg=$pkg label=$label verified=$appInfoVerified")
   softReopen("OPPO設定画面の対象ボタンを特定できない")
  }else later()
 }
 private fun confirm(){
  val root=service.rootInActiveWindow
  val pkg=root?.packageName?.toString().orEmpty()
  if(root!=null && (trustedSettings(pkg)||pkg=="com.android.systemui")){
   val nodes=visible(root)
   val labels=nodes.map{ text(it) }
   // A generic OK dialog is unsafe. Explicit "force stop" question required.
   val question=labels.any{
    HomeRecoveryPolicy.isForceStopQuestion(it) &&
     !HomeRecoveryPolicy.exactStopLabel(it)
   }
   if(question){
    val c=findCandidates(nodes,HomeRecoveryPolicy::exactConfirmLabel)
    if(c.size==1){
     confirmPressed=c[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
     log("STOP_CONFIRM_PRESSED","強制停止確認画面の肯定操作=$confirmPressed")
     if(confirmPressed){
      advance(Phase.WAIT_STOP)
      later(HomeRecoveryPolicy.STRONG_STOP_SETTLE_MS)
      return
     }
    }
   }
   // Some OEM builds immediately stop without showing a confirmation.
   if(appInfoVerified && !question && nodes.any{
     HomeRecoveryPolicy.exactStopLabel(text(it)) && !it.isEnabled
   }){
    forceButtonDisabled=true
    log("STOP_DISABLED","強制停止ボタンが無効へ変化（内部終了は未確認）")
    advance(Phase.WAIT_STOP)
    later(HomeRecoveryPolicy.STRONG_STOP_SETTLE_MS)
    return
   }
  }
  if(timedOut(HomeRecoveryPolicy.CONFIRM_TIMEOUT_MS)){
   log("UNKNOWN_CONFIRM","確認画面を特定できず。汎用OKを押さずに開き直す")
   softReopen("強制停止の確認ができない")
  }else later()
 }
 private fun softReopen(reason:String){
  why=reason
  advance(Phase.OPEN_APP)
  later(250L)
 }
 private fun openApp(){
  val launch=service.packageManager.getLaunchIntentForPackage(TARGET)
  if(launch==null){
   finish(false,false,"トリマの起動Intentを取得できない: $why")
   return
  }
  try{
   launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
   service.startActivity(launch)
   advance(Phase.WAIT_APP)
   log("REOPEN_REQUESTED",
    "トリマを起動、設定からの停止確認操作=$confirmPressed、停止ボタン無効=$forceButtonDisabled")
   later(850L)
  }catch(e:Exception){
   finish(false,false,"トリマの起動失敗: ${e.javaClass.simpleName}")
  }
 }
 private fun waitApp(){
  val pkg=service.rootInActiveWindow?.packageName?.toString().orEmpty()
  if(pkg==TARGET){
   val stopByUi=confirmPressed||forceButtonDisabled
   finish(true,stopByUi,
    "トリマ前面への復帰を確認。強制停止UI操作=$stopByUi（内部プロセス状態は未確認）")
  }else if(timedOut(HomeRecoveryPolicy.REOPEN_TIMEOUT_MS)){
   finish(false,confirmPressed||forceButtonDisabled,
    "トリマを17秒以内に確認できず（前面=$pkg）")
  }else later()
 }
 private fun tick(){
  when(phase){
   Phase.APP_INFO->appInfo()
   Phase.CONFIRM->confirm()
   Phase.WAIT_STOP->softReopen("強制停止操作後の再起動")
   Phase.OPEN_APP->openApp()
   Phase.WAIT_APP->waitApp()
   Phase.IDLE->Unit
  }
 }
 private fun finish(reopened:Boolean,stoppedViaUi:Boolean,reason:String){
  val cb=callback
  val label=when{
   !reopened->"FAILED"
   stoppedViaUi->"TARGET_REOPENED"
   else->"SOFT_REOPENED"
  }
  log(label,reason)
  generation++
  handler.removeCallbacksAndMessages(null)
  phase=Phase.IDLE
  callback=null
  service.restoreHomeTouch()
  cb?.invoke(reopened,stoppedViaUi,reason)
 }
}
