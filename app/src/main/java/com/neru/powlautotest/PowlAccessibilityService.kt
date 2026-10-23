package com.neru.powlautotest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PowlAccessibilityService:AccessibilityService(){
 companion object{@Volatile var instance:PowlAccessibilityService?=null}
 private val h=Handler(Looper.getMainLooper())
 private val screenCollector by lazy { ScreenCollector(this) }
 private val learnedActions by lazy { LearnedActions(this) }
 internal val visualLearnedActions by lazy { VisualLearnedActions(this) }
 private val teachOverlay by lazy {
  TeachOverlay(this,learnedActions){msg->screenCollector.updateAutoStatus(msg)}
 }
 private val autoFlow by lazy { AutoFlow(this){ message -> screenCollector.updateAutoStatus(message) } }
 private val imageDiagnostics by lazy{ScreenshotDiagnostics(this)}
 private val manualHomeComparison by lazy{
  HomeManualComparison(this){msg->screenCollector.updateAutoStatus(msg)}
 }
 private val homeRecovery by lazy{
  HomeSettingsRecovery(this){msg->screenCollector.updateAutoStatus(msg)}
 }
 internal fun runHomeRecovery(done:(Boolean,Boolean,String)->Unit):Boolean=
  homeRecovery.start(done)
 internal fun cancelHomeRecovery(){homeRecovery.cancel()}
 internal fun probeOverlayIcons(
  onDone:(List<OverlayIconDetector.Marker>?,VisualLearnedActions.Match?)->Unit){
  imageDiagnostics.probeOverlay(onDone)
 }
 internal fun probeRewardBadges(onDone:(RewardBadgeDetector.Result?)->Unit){
  imageDiagnostics.probeRewardBadges(onDone)
 }
 internal fun probeMapStock(role:AppNavigationRules.Role,done:(MapStockIconDetector.Result?)->Unit){
  imageDiagnostics.probeMapStock(role,done)
 }
 internal fun probeLearnedX(x:Int,y:Int,onDone:(Boolean?)->Unit){
  imageDiagnostics.probeLearnedX(x,y,onDone)
 }
 internal fun matchVisualLearning(pixels:IntArray,width:Int,height:Int):
  VisualLearnedActions.Match? =
  visualLearnedActions.matchCanonical(pixels,width,height)
 internal fun isCapturingCleanScreenshot()=screenCollector.isCapturingScreenshot()
 internal fun hideControlBarForScreenshot():()->Unit=screenCollector.hideForScreenshot()
 internal fun suspendTeachingBar():Boolean=screenCollector.suspendForTeaching()
 internal fun resumeTeachingBar(){screenCollector.resumeAfterTeaching()}
 internal fun captureVisualTeaching(onDone:(android.graphics.Bitmap?,String)->Unit){
  // Keep the collector invisible until the PERSON completes the next tap.
  // The old implementation exposed the bar immediately after capture,
  // allowing it to intercept a real finger tap.
  if(!screenCollector.suspendForTeaching()){
   onDone(null,"学習中の操作バーをタッチ透過にできず、画像を取得しませんでした")
   return
  }
  h.postDelayed({
   imageDiagnostics.captureForVisualTeaching {bitmap,note->
    onDone(bitmap,note)
   }
  },200L)
 }
 internal fun prepareHomeTouch(x:Int,y:Int,onReady:(Boolean,String)->Unit){
  screenCollector.prepareHomeTouch(x,y,onReady)
 }
 internal fun restoreHomeTouch(){screenCollector.restoreHomeTouch()}
 internal fun prepareManualHomeObservation(onReady:(Boolean,String)->Unit){
  screenCollector.prepareManualHomeObservation(onReady)
 }
 internal fun prepareRecoveryOverlay(onReady:(Boolean,String)->Unit){
  screenCollector.prepareRecoveryOverlay(onReady)
 }
 fun isManualHomeComparisonRunning():Boolean=manualHomeComparison.isActive()
 fun finishManualHomeComparison(){manualHomeComparison.cancel()}
 fun startManualHomeComparison():String{
  if(autoFlow.isRunning())stopAutomaticOperation()
  return manualHomeComparison.start()
 }
 fun saveVisualScreenshot(stage:String,nodeCount:Int=0,manual:Boolean=true,
  onDone:(String)->Unit={}){
  imageDiagnostics.capture(stage,nodeCount,manual){message->
   if(manual)report=message
   else TeachOverlay.recordAutomatic(this,message)
   onDone(message)
  }
 }
 @Volatile var report="未実行"
 private var lastObservedTopPackage=""
 private var confirmedPlayTransitionAt=0L
 // Can be used when the user starts automation while an ad-opened Play window is already visible.
 fun markPlayTransitionFromTorima(){
  confirmedPlayTransitionAt=SystemClock.elapsedRealtime()
 }
 fun hasRecentPlayTransition():Boolean =
  confirmedPlayTransitionAt>0L &&
   SystemClock.elapsedRealtime()-confirmedPlayTransitionAt<300_000L
 fun consumePlayTransitionProof(){
  confirmedPlayTransitionAt=0L
 }
 fun isPlayBrandedInstallPopup(root:AccessibilityNodeInfo?):Boolean{
  if(root?.packageName?.toString()!="com.google.android.gms")return false
  var checked=0
  val labels=ArrayList<String>()
  fun inspect(node:AccessibilityNodeInfo){
   if(checked>=95)return
   checked++
   labels.add(node.text?.toString().orEmpty())
   labels.add(node.contentDescription?.toString().orEmpty())
   for(i in 0 until node.childCount){
    if(checked>=95)break
    node.getChild(i)?.let{inspect(it)}
   }
  }
  inspect(root)
  val branded=labels.any{
   it.contains("Google Play",ignoreCase=true) ||
    it.contains("Play ストア",ignoreCase=true)
  }
  // On some OEM Play bottom-sheets the install CTA is not exposed in the
  // accessibility tree. Play Pass / app-purchase details still identify the
  // Play product sheet when the foreground *package* is GMS.
  val productSheet=labels.any{
   it.contains("インストール") || it.contains("Install",ignoreCase=true) ||
    it.contains("Play Pass",ignoreCase=true) ||
    it.contains("アプリ内課金") || it.contains("レビュー") ||
    it.contains("件のレビュー")
  }
  return branded && productSheet
 }
 override fun onServiceConnected(){super.onServiceConnected();instance=this;report="サービス接続完了"}
 override fun onInterrupt(){}
 override fun onAccessibilityEvent(e:AccessibilityEvent?){
  if(e==null)return
  manualHomeComparison.onEvent(e)
  if(e.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
   val p=e.packageName?.toString().orEmpty()
   val playPopup=p=="com.google.android.gms" && isPlayBrandedInstallPopup(rootInActiveWindow)
   if((p=="com.android.vending" || playPopup) &&
    lastObservedTopPackage=="jp.co.incrementp.milemobile"){
    markPlayTransitionFromTorima()
   }
   if(p=="jp.co.incrementp.milemobile" || p=="com.android.vending" || playPopup){
    lastObservedTopPackage=p
   }else if(p.isNotBlank() && p!=packageName && p!="com.android.systemui"){
    lastObservedTopPackage=p
   }
  }
  if(e.eventType==AccessibilityEvent.TYPE_VIEW_CLICKED &&
   e.packageName?.toString()=="jp.co.incrementp.milemobile" &&
   autoFlow.isRunning()){
   autoFlow.onHomeClickObserved(e)
  }
  if(e.eventType==AccessibilityEvent.TYPE_VIEW_CLICKED &&
   e.packageName?.toString()=="jp.co.incrementp.milemobile" &&
   screenCollector.isVisible() && !autoFlow.isRunning()){
   teachOverlay.observedClick(e)
  }
 }
 override fun onDestroy(){
  homeRecovery.cancel()
  manualHomeComparison.cancel()
  navigationTeacher.cancel()
  teachOverlay.cancelCapture("サービス終了")
  stopSequence()
  screenCollector.hide()
  if(instance===this)instance=null
  super.onDestroy()
 }



 fun showScreenCollector():String {
  stopSequence()
  val message=screenCollector.show()
  report=message
  return message
 }
 fun hideScreenCollector():String {
  stopSequence()
  screenCollector.hide()
  val message="画面記録バーを終了。アプリで『収集ログをまとめてコピー』を押してください。"
  report=message
  return message
 }


 private val navigationTeacher by lazy{AppNavigationTeachOverlay(this)}
 internal fun reportNavigationTeaching(message:String){
  report=message;screenCollector.updateAutoStatus(message)
  android.widget.Toast.makeText(this,message,android.widget.Toast.LENGTH_LONG).show()
 }
 internal fun captureNavigationFrame(done:(android.graphics.Bitmap?,String)->Unit){imageDiagnostics.captureWithoutControlBar(done)}
 fun showLearningMenu():String {
  stopAutomaticOperation()
  val choices=arrayOf("広告の×／スキップを学習（従来の2段階）","アプリ内：マップを開くを登録","アプリ内：今日に戻るを登録","アプリ内：ストックを開くを登録","登録したアプリ内操作だけ削除")
  val dialog=android.app.AlertDialog.Builder(this).setTitle("学習する操作の種類")
   .setItems(choices){_,which->
    h.postDelayed({
     if(which==0){navigationTeacher.cancel();reportNavigationTeaching(beginTeachTap())}
     else if(which==4){AppNavigationRules.clear(this);reportNavigationTeaching("アプリ内操作だけ削除しました。広告の学習は保持")}
     else {
      teachOverlay.cancelCapture("アプリ内操作の登録へ切り替え")
      reportNavigationTeaching(navigationTeacher.begin(AppNavigationRules.Role.values()[which-1]))
     }
    },250L)
   }.setNegativeButton("キャンセル",null).create()
  dialog.window?.setType(android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
  dialog.show()
  return "アプリ内操作の登録／広告の×学習を選択"
 }
 fun beginTeachTap():String {
  navigationTeacher.cancel()
  stopAutomaticOperation()
  val msg=teachOverlay.begin()
  if(msg.startsWith("学習 1/2")){
   // Hide and make the window NOT_TOUCHABLE for real manual interaction.
   if(!screenCollector.suspendForTeaching()){
    return teachOverlay.cancelCapture("学習用操作バーのタッチ透過設定に失敗。学習を中止しました")
   }
  }
  report=msg
  return msg
 }
 fun saveTaughtTap():String {
  val msg=teachOverlay.confirm()
  report=msg
  return msg
 }
 fun isAutomaticOperationRunning():Boolean=autoFlow.isRunning()
 fun startAutomaticOperation():String {
  if(manualHomeComparison.isActive())
   return "指タップ比較の18秒間は自動操作を開始できません。比較終了後に開始してください"
  stopSequence()
  val shown=screenCollector.show()
  if(shown.startsWith("記録バーを表示できません")){
   report=shown
   return shown
  }
  val message=autoFlow.start()
  report=message
  return message
 }
 fun stopAutomaticOperation():String {
  autoFlow.stop()
  runId++
  sequenceActive=false
  h.removeCallbacks(adPoll)
  h.removeCallbacks(adScanPoll)
  val message="自動操作を停止しました。記録バーは表示したままです。"
  report=message
  return message
 }

 private var sequenceActive=false
 private var sequenceStarted=0L
 private var closeAttempted=false
 private var runId=0
 private var homeFallbackUsed=false
 private var homeTransitionStart=0L
 private var homeTransitionCount=0
 private var homeTapBounds=Rect()
 private val homeTrace=ArrayList<String>()
 private val adTrace=ArrayList<String>()
 private var lastAdState=""
 private var lastAdTraceAt=0L
 private val adPoll=object:Runnable{override fun run(){checkAdClose()}}

 private fun appendHomeTrace(message:String){
  homeTrace.add(message)
  while(homeTrace.size>22)homeTrace.removeAt(0)
 }
 private fun homeLog():String=homeTrace.joinToString("\n")

 fun stopSequence(){
  autoFlow.stop()
  runId++
  sequenceActive=false
  h.removeCallbacks(adPoll)
  h.removeCallbacks(adScanPoll)
  report="停止しました。\n"+report
 }
 fun startSequence(){
  runId++
  sequenceActive=false
  h.removeCallbacks(adPoll)
  h.removeCallbacks(adScanPoll)
  closeAttempted=false
  homeFallbackUsed=false
  homeTransitionCount=0
  homeTrace.clear()
  adTrace.clear();lastAdState="";lastAdTraceAt=0L
  val id=runId
  report="5秒後にHOME判定。画面遷移を確認できた場合だけ広告待機へ進みます。"
  h.postDelayed({if(runId==id)startHomeSequence(id)},5000)
 }
 private fun startHomeSequence(id:Int){
  if(runId!=id)return
  val root=rootInActiveWindow ?: run{report="HOME取得失敗。操作なし";return}
  val nodes=ArrayList<AccessibilityNodeInfo>()
  fun walk(n:AccessibilityNodeInfo){
   if(nodes.size>=350)return
   nodes.add(n)
   for(i in 0 until n.childCount)n.getChild(i)?.let{walk(it)}
  }
  walk(root)
  val texts=nodes.map{it.text?.toString().orEmpty()+" "+it.contentDescription?.toString().orEmpty()}
  val remaining=texts.any{Regex("あと\\s*[0-9０-９]+\\s*回獲得できます").containsMatchIn(it)}
  val tank=texts.any{it.contains("タンク")&&(it.contains("マイル")||it.contains("満タン"))}
  val move=nodes.any{it.contentDescription?.toString()=="move"}
  val candidates=nodes.filter{
   val b=Rect();it.getBoundsInScreen(b)
   it.isClickable && it.isEnabled && it.isVisibleToUser &&
    b.left>=0 && b.right<=1080 && b.top in 1100..1300 &&
    b.width()>=600 && b.height() in 90..230
  }
  val pkg=root.packageName?.toString().orEmpty()
  appendHomeTrace("HOME判定 pkg=$pkg nodes=${nodes.size} remaining=$remaining tank=$tank move=$move candidates=${candidates.size}")
  if(pkg!="jp.co.incrementp.milemobile"||!remaining||!tank||!move||candidates.size!=1||
   texts.any{it.contains("友達にシェアする")||it.contains("このまま受け取る")}){
   report="HOME判定失敗。操作なし\n"+homeLog()
   return
  }
  val button=candidates.single()
  button.getBoundsInScreen(homeTapBounds)
  appendHomeTrace("HOMEボタン ${homeTapBounds.toShortString()} / ACTION_CLICK開始")
  homeTransitionStart=android.os.SystemClock.elapsedRealtime()
  val ok=button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
  appendHomeTrace("HOME ACTION_CLICK=$ok（実際の遷移は未確認）")
  if(ok){
   report="HOMEタップ命令受付。遷移確認中...\n"+homeLog()
   h.postDelayed({verifyHomeTransition(id)},2400)
  }else{
   homeFallbackUsed=true
   sendHomeGesture(id,"ACTION_CLICK=false")
  }
 }
 private fun sendHomeGesture(id:Int,reason:String){
  if(runId!=id)return
  appendHomeTrace("HOME gesture試行: $reason / ${homeTapBounds.toShortString()}")
  val path=Path().apply{moveTo(homeTapBounds.exactCenterX(),homeTapBounds.exactCenterY())}
  val gesture=GestureDescription.Builder()
   .addStroke(GestureDescription.StrokeDescription(path,0,120)).build()
  val accepted=dispatchGesture(gesture,object:GestureResultCallback(){
   override fun onCompleted(g:GestureDescription?){
    if(runId!=id)return
    appendHomeTrace("HOME gesture=COMPLETED（画面遷移は未確認）")
    h.postDelayed({verifyHomeTransition(id)},2000)
   }
   override fun onCancelled(g:GestureDescription?){
    if(runId!=id)return
    report="HOME gesture=CANCELLED。停止\n"+homeLog()
   }
  },null)
  if(!accepted)report="HOME gesture=REJECTED。停止\n"+homeLog()
  else report="HOMEのタップを確認中...\n"+homeLog()
 }
 private fun verifyHomeTransition(id:Int){
  if(runId!=id || sequenceActive)return
  val elapsed=android.os.SystemClock.elapsedRealtime()-homeTransitionStart
  val root=rootInActiveWindow
  val pkg=root?.packageName?.toString().orEmpty()
  var count=0;var remaining=false;var tank=false;var move=false
  if(root!=null){
   walkNodes(root){node->
    count++
    val value=node.text?.toString().orEmpty()+" "+node.contentDescription?.toString().orEmpty()
    if(Regex("あと\\s*[0-9０-９]+\\s*回獲得できます").containsMatchIn(value))remaining=true
    if(value.contains("タンク") && (value.contains("マイル")||value.contains("満タン")))tank=true
    if(node.contentDescription?.toString()=="move")move=true
   }
  }
  val stillHome=pkg=="jp.co.incrementp.milemobile" && remaining && tank && move
  // A rendered ad sometimes exposes just one opaque accessibility node.
  // Require persistence before treating that screen as a likely transition.
  val opaqueTarget=pkg=="jp.co.incrementp.milemobile" && count==1 && elapsed>=4500L
  val differentTarget=pkg=="jp.co.incrementp.milemobile" && !stillHome && (count>=3 || opaqueTarget)
  appendHomeTrace("遷移確認 ${elapsed/1000}s pkg=$pkg nodes=$count HOME=$stillHome")
  if(differentTarget)homeTransitionCount++ else homeTransitionCount=0
  if(homeTransitionCount>=2){
   appendHomeTrace("HOME以外の画面を連続確認（nodes=$count）。広告待機開始（広告かどうかは未確定）")
   beginAdWait("HOMEからの画面遷移を確認")
   return
  }
  if(stillHome && !homeFallbackUsed && elapsed>=2200){
   homeFallbackUsed=true
   sendHomeGesture(id,"ACTION_CLICK後もHOMEのまま")
   return
  }
  if(elapsed>=16500){
   report="HOMEからの遷移を確認できないため停止（広告待機には進まない）。\n"+homeLog()
   return
  }
  report="HOMEの画面遷移を確認中...\n"+homeLog()
  h.postDelayed({verifyHomeTransition(id)},1500)
 }
 private fun beginAdWait(prefix:String){
  sequenceActive=true
  sequenceStarted=android.os.SystemClock.elapsedRealtime()
  report="$prefix\n広告Close Adを待機中...\n--- HOME履歴 ---\n"+homeLog()
  h.postDelayed(adPoll,1200)
 }
 private fun recordAdState(state:String, elapsed:Long){
  if(state!=lastAdState || elapsed-lastAdTraceAt>=10000){
   lastAdState=state;lastAdTraceAt=elapsed
   adTrace.add("${elapsed/1000}s: $state")
   while(adTrace.size>18)adTrace.removeAt(0)
  }
 }
 private fun adLog():String=adTrace.joinToString("\n")
 private fun closeNodes(root:AccessibilityNodeInfo):List<AccessibilityNodeInfo>{
  val result=ArrayList<AccessibilityNodeInfo>()
  var scanned=0
  fun walk(n:AccessibilityNodeInfo){
   if(++scanned>450)return
   if(n.contentDescription?.toString()?.trim()?.equals("Close Ad",ignoreCase=true)==true)result.add(n)
   for(i in 0 until n.childCount)n.getChild(i)?.let{walk(it)}
  }
  walk(root)
  return result
 }
 private fun tapCloseCenter(bounds:Rect, reason:String){
  val x=bounds.exactCenterX();val y=bounds.exactCenterY()
  val path=Path().apply{moveTo(x,y)}
  val gesture=GestureDescription.Builder()
   .addStroke(GestureDescription.StrokeDescription(path,0,120)).build()
  val accepted=dispatchGesture(gesture,object:GestureResultCallback(){
   override fun onCompleted(g:GestureDescription?){
    report="Close Ad座標タップ完了 ($x,$y)\nreason=$reason\n" + adLog()
   }
   override fun onCancelled(g:GestureDescription?){
    report="Close Ad座標タップ中止。\nreason=$reason\n" + adLog()
   }
  },null)
  if(!accepted)report="Close Ad座標タップ拒否。\nreason=$reason\n"+adLog()
 }

 private data class UiRoot(val label:String,val node:AccessibilityNodeInfo)
 private data class CloseCandidate(val node:AccessibilityNodeInfo,val bounds:Rect,val source:String)

 // Active window may only contain a placeholder FrameLayout (nodes=1).
 // Check all accessible windows as well. The same node may be exposed twice.
 private fun currentRoots():List<UiRoot>{
  val roots=ArrayList<UiRoot>()
  rootInActiveWindow?.let{roots.add(UiRoot("active",it))}
  try{
   windows?.forEachIndexed{index,window->
    window.root?.let{root->
     roots.add(UiRoot("window$index(id=${window.id},type=${window.type},layer=${window.layer},active=${window.isActive})",root))
    }
   }
  }catch(e:Exception){
   // Some Android builds may temporarily reject window enumeration.
  }
  return roots
 }
 private fun walkNodes(root:AccessibilityNodeInfo,limit:Int=450,visit:(AccessibilityNodeInfo)->Unit):Int{
  var visited=0
  fun walk(node:AccessibilityNodeInfo){
   if(visited>=limit)return
   visited++
   visit(node)
   for(i in 0 until node.childCount){
    if(visited>=limit)break
    node.getChild(i)?.let{walk(it)}
   }
  }
  walk(root)
  return visited
 }
 private fun findCloseCandidates(roots:List<UiRoot>):List<CloseCandidate>{
  val unique=LinkedHashMap<String,CloseCandidate>()
  for(ui in roots){
   if(ui.node.packageName?.toString()!="jp.co.incrementp.milemobile")continue
   walkNodes(ui.node){node->
    val label=node.contentDescription?.toString()?.trim().orEmpty()
    val isCloseAd=label.equals("Close Ad",ignoreCase=true)
    val isCloseButton=label.equals("Close button",ignoreCase=true)
    if(isCloseAd||isCloseButton){
     val rect=Rect();node.getBoundsInScreen(rect)
     val screenWidth=resources.displayMetrics.widthPixels
     // "Close button" is generic: only accept a small, visible Button at the top right.
     val safeCloseButton=!isCloseButton || (
      node.className?.toString()?.substringAfterLast('.')=="Button" &&
      rect.left>=screenWidth*7/10 && rect.right<=screenWidth &&
      rect.top in 0..400 && rect.width() in 24..180 && rect.height() in 24..180
     )
     if(safeCloseButton && rect.width()>0 && rect.height()>0){
      val key="${node.windowId}/${rect.left},${rect.top},${rect.right},${rect.bottom}"
      if(!unique.containsKey(key))unique[key]=CloseCandidate(node,rect,ui.label)
     }
    }
   }
  }
  return unique.values.toList()
 }
 private fun isActionableClose(candidate:CloseCandidate):Boolean{
  val node=candidate.node
  return node.isClickable && node.isEnabled && node.isVisibleToUser
 }
 private fun describeRoots(roots:List<UiRoot>):String=
  if(roots.isEmpty())"root=none" else roots.joinToString(" | "){ui->
   val pkg=ui.node.packageName?.toString().orEmpty()
   val count=walkNodes(ui.node){}
   "${ui.label}:$pkg nodes=$count"
  }
 private fun checkAdClose(){
  if(!sequenceActive||closeAttempted)return
  val elapsed=android.os.SystemClock.elapsedRealtime()-sequenceStarted
  val roots=currentRoots()
  val closes=findCloseCandidates(roots)
  val valid=closes.filter{isActionableClose(it)}
  recordAdState("${describeRoots(roots)} / CloseCandidates=${closes.size} 有効=${valid.size}",elapsed)
  if(valid.size==1){
   closeAttempted=true;sequenceActive=false
   val candidate=valid.single()
   val b=candidate.bounds
   val clicked=candidate.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
   report="閉じるボタン検出 [${candidate.node.contentDescription}] ${b.toShortString()} (${candidate.source})\nACTION_CLICK=$clicked\nクリック後を確認中...\n"+adLog()
   if(!clicked){
    tapCloseCenter(b,"ACTION_CLICK=false（Close Ad特定済み）")
   }else{
    h.postDelayed({
     val still=findCloseCandidates(currentRoots()).filter{
      isActionableClose(it) && it.bounds==b
     }
     if(still.size==1){
      tapCloseCenter(b,"ACTION_CLICK後も同じClose Adが表示")
     }else{
      report="ACTION_CLICK=true。Close Adが消えました（次の画面を確認してください）。\n"+adLog()
     }
    },1800)
   }
   return
  }
  if(valid.size>1){
   sequenceActive=false
   report="広告の閉じるボタン候補が複数のため停止。\n"+adLog()
   return
  }
  if(elapsed>90000){
   sequenceActive=false
   report="90秒経過。広告の閉じるボタン未検出のため停止。\n--- 途中ログ ---\n"+adLog()
   return
  }
  report="広告Close Ad待機中 ${elapsed/1000}秒 / 90秒\n--- 途中ログ ---\n"+adLog()
  h.postDelayed(adPoll,2000)
 }


 private var adScanTapMode=false
 private var adScanTapped=false
 private var adScanResult=""
 private var adScanId=0

 private fun displayAdScan(){
  report=adScanPoll.snapshots.joinToString("\n\n")+
   (if(adScanTapMode)"\n\n--- タップ診断 ---\n"+adScanResult else "")
 }
 private fun tryScanCloseTap(){
  if(!adScanTapMode||adScanTapped)return
  val valid=findCloseCandidates(currentRoots()).filter{isActionableClose(it)}
  if(valid.size!=1){
   adScanResult=if(valid.size==0)"安全な閉じるボタンを特定できず。タップなし。"
    else "閉じる候補が複数。タップなし。"
   return
  }
  adScanTapped=true
  val candidate=valid.single()
  val bounds=candidate.bounds
  val id=adScanId
  val x=bounds.exactCenterX()
  val y=bounds.exactCenterY()
  val label=candidate.node.contentDescription?.toString().orEmpty()
  adScanResult="検出: $label ${bounds.toShortString()} / 中央を1回タップ試行中"
  val path=Path().apply{moveTo(x,y)}
  val gesture=GestureDescription.Builder()
   .addStroke(GestureDescription.StrokeDescription(path,0,120)).build()
  val accepted=dispatchGesture(gesture,object:GestureResultCallback(){
   override fun onCompleted(g:GestureDescription?){
    if(id!=runId)return
    adScanResult="検出: $label ${bounds.toShortString()}\nジェスチャー完了 ($x,$y)。画面が閉じたかは実機で確認。"
    displayAdScan()
   }
   override fun onCancelled(g:GestureDescription?){
    if(id!=runId)return
    adScanResult="検出: $label ${bounds.toShortString()}\nジェスチャーがキャンセルされました。"
    displayAdScan()
   }
  },null)
  if(!accepted)adScanResult="検出: $label ${bounds.toShortString()}\nジェスチャーが拒否されました。"
 }
 private val adScanPoll=object:Runnable{
  var count=0
  val snapshots=ArrayList<String>()
  override fun run(){
   if(adScanId!=runId)return
   count++
   snapshots.add(scanAdSnapshot(count))
   if(adScanTapMode&&!adScanTapped)tryScanCloseTap()
   if(count==3&&adScanTapMode&&!adScanTapped){
    adScanResult+="\n3回診断終了。閉じるボタンを特定できないため押していません。"
   }
   displayAdScan()
   if(count<3)h.postDelayed(this,2000)
  }
 }
 fun scheduleAdScan(tap:Boolean=false){
  runId++
  sequenceActive=false
  h.removeCallbacks(adPoll)
  h.removeCallbacks(adScanPoll)
  adScanId=runId
  adScanTapMode=tap
  adScanTapped=false
  adScanResult=if(tap)"診断待機中。識別できた閉じるボタンだけ1回押します。" else ""
  adScanPoll.count=0
  adScanPoll.snapshots.clear()
  report="15秒後に広告UIを3回診断（${if(tap)"識別した×のみ1回タップ" else "タップなし"}）"
  h.postDelayed(adScanPoll,15000)
 }
 private fun scanAdSnapshot(number:Int):String{
  val roots=currentRoots()
  val sb=StringBuilder("広告診断 #$number（${if(adScanTapMode)"識別した×のみタップ" else "自動タップなし"}）\n")
  sb.append("root数=${roots.size}\n")
  for(ui in roots){
   val nodes=ArrayList<AccessibilityNodeInfo>()
   val count=walkNodes(ui.node,400){nodes.add(it)}
   sb.append("[${ui.label}] package=${ui.node.packageName} nodes=$count")
   if(count==400)sb.append(" (上限到達)")
   sb.append("\n")
   var found=0
   for(node in nodes){
    val txt=node.text?.toString().orEmpty()
    val desc=node.contentDescription?.toString().orEmpty()
    val label="$txt $desc"
    val rect=Rect();node.getBoundsInScreen(rect)
    val match=listOf("Close Ad","閉じる","とじる","close","skip","スキップ","終了","×","✕","✖","dismiss")
     .any{label.contains(it,ignoreCase=true)}
    val corner=node.isClickable && rect.width() in 15..200 && rect.height() in 15..200 &&
     rect.top in 0..380 && (rect.left in 0..200 || rect.right in 880..1080)
    if(match||corner){
     found++
     if(found<=20){
      sb.append(" #$found ${node.className?.toString()?.substringAfterLast('.')} ")
       .append(rect.toShortString())
       .append(" clickable=${node.isClickable} enabled=${node.isEnabled}")
       .append(" visible=${node.isVisibleToUser}")
       .append(" text=${txt.take(90)} desc=${desc.take(90)}\n")
     }
    }
   }

   if(ui.node.packageName?.toString()=="jp.co.incrementp.milemobile"){
    sb.append("参考ノード（最大45件、文字/descまたはクリック可能）:\n")
    var detailCount=0
    for(node in nodes){
     val t=node.text?.toString().orEmpty()
     val d=node.contentDescription?.toString().orEmpty()
     if(t.isBlank() && d.isBlank() && !node.isClickable)continue
     detailCount++
     if(detailCount>45)break
     val rect=Rect();node.getBoundsInScreen(rect)
     sb.append(" - ${rect.toShortString()} click=${node.isClickable} enabled=${node.isEnabled} visible=${node.isVisibleToUser} text=${t.take(70)} desc=${d.take(70)}\n")
    }
    if(detailCount==0)sb.append(" 表示できる詳細ノードなし\n")
   }
   sb.append("候補数=$found\n")
   if(count<=2){
    val r=Rect();ui.node.getBoundsInScreen(r)
    sb.append("root bounds=${r.toShortString()} childCount=${ui.node.childCount}\n")
   }
  }
  if(roots.isEmpty())sb.append("取得可能なウィンドウなし\n")
  val closes=findCloseCandidates(roots)
  sb.append("重複除去後 閉じるボタン=${closes.size} 有効=${closes.count{isActionableClose(it)}}\n")
  if(closes.isEmpty())sb.append("対応する閉じるボタンはUIツリーに見つかりませんでした。\n")
  return sb.toString().take(15000)
 }
 fun scheduleHomeTest(tap:Boolean){
  report="5秒後にHOME判定。タップ=${if(tap)"あり" else "なし"}"
  h.postDelayed({inspect(tap)},5000)
 }
 private fun inspect(tap:Boolean){
  val root=rootInActiveWindow
  if(root==null){report="判定失敗: root=null。操作なし";return}
  val nodes=ArrayList<AccessibilityNodeInfo>()
  fun walk(n:AccessibilityNodeInfo){
   if(nodes.size>=350)return
   nodes.add(n)
   for(i in 0 until n.childCount)n.getChild(i)?.let{walk(it)}
  }
  walk(root)
  val pkg=root.packageName?.toString().orEmpty()
  val texts=nodes.map{it.text?.toString().orEmpty()+" "+it.contentDescription?.toString().orEmpty()}
  val remaining=texts.any{Regex("あと\\s*[0-9０-９]+\\s*回獲得できます").containsMatchIn(it)}
  val tank=texts.any{it.contains("タンク") && (it.contains("マイル")||it.contains("満タン"))}
  val move=nodes.any{it.contentDescription?.toString()=="move"}
  val candidate=nodes.filter{
   val r=Rect();it.getBoundsInScreen(r)
   it.isClickable && it.isEnabled && it.isVisibleToUser &&
    r.width()>0 && r.height()>0 &&
    r.left>=0 && r.right<=1080 && r.top in 1100..1300 &&
    r.width()>=600 && r.height() in 90..230
  }
  val button=candidate.minByOrNull{val r=Rect();it.getBoundsInScreen(r);kotlin.math.abs(r.centerY()-1253)}
  val bounds=Rect();button?.getBoundsInScreen(bounds)
  val home=pkg=="jp.co.incrementp.milemobile" && remaining && tank && move && button!=null
  val sb=StringBuilder()
  sb.append("package=$pkg nodes=${nodes.size}\n")
  sb.append("HOME=${if(home)"YES" else "NO"}\n")
  sb.append("残り回数=$remaining タンク=$tank move=$move\\n")
  sb.append("対象ボタン=${if(button!=null)bounds.toShortString() else "なし"}\\n")
  sb.append("enabled=${button?.isEnabled} visible=${button?.isVisibleToUser} clickable=${button?.isClickable} selected=${button?.isSelected}\\n")
  sb.append("候補数=${candidate.size}\\n")
  if(!tap){sb.append("判定のみ。操作なし");report=sb.toString();return}
  if(!home){sb.append("安全停止: HOME未確定。操作なし");report=sb.toString();return}
  if(candidate.size!=1){sb.append("安全停止: ボタン候補が一意でない。操作なし");report=sb.toString();return}
  // Avoid clicking on share/reward views if a conflicting label appears.
  if(texts.any{it.contains("友達にシェアする") || it.contains("このまま受け取る")}){
   sb.append("安全停止: 別画面の文言を検出");report=sb.toString();return
  }
  sb.append("ACTION_CLICK実行\\n")
  report=sb.toString()
  val ok=button!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
  if(ok){report=sb.append("ACTION_CLICK=true (実際の遷移は未確認)").toString();return}
  val x=bounds.exactCenterX();val y=bounds.exactCenterY()
  val path=Path().apply{moveTo(x,y)}
  val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,120)).build()
  val accepted=dispatchGesture(gesture,object:GestureResultCallback(){
   override fun onCompleted(g:GestureDescription?){report=sb.toString()+"dispatchGesture=COMPLETED ($x,$y)"}
   override fun onCancelled(g:GestureDescription?){report=sb.toString()+"dispatchGesture=CANCELLED ($x,$y)"}
  },null)
  if(!accepted)report=sb.toString()+"dispatchGesture=REJECTED"
 }
}