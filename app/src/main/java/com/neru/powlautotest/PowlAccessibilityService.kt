package com.neru.powlautotest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PowlAccessibilityService:AccessibilityService(){
 companion object{@Volatile var instance:PowlAccessibilityService?=null}
 private val h=Handler(Looper.getMainLooper())
 private val screenCollector by lazy { ScreenCollector(this) }
 @Volatile var report="未実行"
 override fun onServiceConnected(){super.onServiceConnected();instance=this;report="サービス接続完了"}
 override fun onInterrupt(){}
 override fun onAccessibilityEvent(e:AccessibilityEvent?){}
 override fun onDestroy(){screenCollector.hide();stopSequence();if(instance===this)instance=null;super.onDestroy()}



 fun showScreenCollector():String {
  stopSequence()
  val message=screenCollector.show()
  report=message
  return message
 }
 fun hideScreenCollector():String {
  screenCollector.hide()
  val message="画面記録バーを終了。アプリで『収集ログをまとめてコピー』を押してください。"
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