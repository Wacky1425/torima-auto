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
 @Volatile var report="未実行"
 override fun onServiceConnected(){super.onServiceConnected();instance=this;report="サービス接続完了"}
 override fun onInterrupt(){}
 override fun onAccessibilityEvent(e:AccessibilityEvent?){}
 override fun onDestroy(){stopSequence();if(instance===this)instance=null;super.onDestroy()}

 private var sequenceActive=false
 private var sequenceStarted=0L
 private var closeAttempted=false
 private val adPoll=object:Runnable{override fun run(){checkAdClose()}}
 fun stopSequence(){
  sequenceActive=false
  h.removeCallbacks(adPoll)
  report="停止しました。\n"+report
 }
 fun startSequence(){
  sequenceActive=false
  h.removeCallbacks(adPoll)
  closeAttempted=false
  report="5秒後にHOMEを確認して紫ボタンを1回押します。\n広告は最大90秒待機します。"
  h.postDelayed({startHomeSequence()},5000)
 }
 private fun startHomeSequence(){
  val root=rootInActiveWindow ?: run{report="HOME取得失敗。操作なし";return}
  val nodes=ArrayList<AccessibilityNodeInfo>()
  fun walk(n:AccessibilityNodeInfo){if(nodes.size>=350)return;nodes.add(n);for(i in 0 until n.childCount)n.getChild(i)?.let{walk(it)}}
  walk(root)
  val texts=nodes.map{it.text?.toString().orEmpty()+" "+it.contentDescription?.toString().orEmpty()}
  val remaining=texts.any{Regex("あと\\s*[0-9０-９]+\\s*回獲得できます").containsMatchIn(it)}
  val tank=texts.any{it.contains("タンク") && (it.contains("マイル")||it.contains("満タン"))}
  val move=nodes.any{it.contentDescription?.toString()=="move"}
  val candidates=nodes.filter{val b=Rect();it.getBoundsInScreen(b);it.isClickable&&it.isEnabled&&it.isVisibleToUser&&b.left>=0&&b.right<=1080&&b.top in 1100..1300&&b.width()>=600&&b.height() in 90..230}
  if(root.packageName?.toString()!="jp.co.incrementp.milemobile"||!remaining||!tank||!move||candidates.size!=1||texts.any{it.contains("友達にシェアする")||it.contains("このまま受け取る")}){
   report="HOME判定失敗。操作なし。 remaining=$remaining tank=$tank move=$move candidates=${candidates.size}";return
  }
  val target=candidates.single()
  val bounds=Rect();target.getBoundsInScreen(bounds)
  val ok=target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
  if(ok){beginAdWait("HOME ACTION_CLICK=true")}
  else{
   val path=Path().apply{moveTo(bounds.exactCenterX(),bounds.exactCenterY())}
   val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,120)).build()
   val accepted=dispatchGesture(gesture,object:GestureResultCallback(){
    override fun onCompleted(g:GestureDescription?){beginAdWait("HOME gesture completed")}
    override fun onCancelled(g:GestureDescription?){report="HOME gesture cancelled。停止"}
   },null)
   if(!accepted)report="HOME gesture rejected。停止"
  }
 }
 private fun beginAdWait(prefix:String){
  sequenceActive=true
  sequenceStarted=android.os.SystemClock.elapsedRealtime()
  report="$prefix\n広告Close Adを待機中..."
  h.postDelayed(adPoll,1500)
 }
 private fun checkAdClose(){
  if(!sequenceActive||closeAttempted)return
  val elapsed=android.os.SystemClock.elapsedRealtime()-sequenceStarted
  if(elapsed>90000){sequenceActive=false;report="90秒経過。Close Ad未検出のため停止。";return}
  val root=rootInActiveWindow
  if(root!=null && root.packageName?.toString()=="jp.co.incrementp.milemobile"){
   val found=ArrayList<AccessibilityNodeInfo>()
   fun walk(n:AccessibilityNodeInfo){
    if(n.contentDescription?.toString()?.trim()?.equals("Close Ad",true)==true && n.isClickable && n.isEnabled && n.isVisibleToUser){
     val b=Rect();n.getBoundsInScreen(b)
     if(b.width()>0&&b.height()>0)found.add(n)
    }
    for(i in 0 until n.childCount)n.getChild(i)?.let{walk(it)}
   }
   walk(root)
   if(found.size==1){
    closeAttempted=true
    sequenceActive=false
    val b=Rect();found.single().getBoundsInScreen(b)
    val clicked=found.single().performAction(AccessibilityNodeInfo.ACTION_CLICK)
    report="Close Ad検出 ${b.toShortString()}\nACTION_CLICK=$clicked\n操作はここで終了。次の画面を確認してください。"
    return
   }
   if(found.size>1){sequenceActive=false;report="Close Adが複数。安全停止。";return}
  }
  report="広告Close Ad待機中 ${elapsed/1000}秒 / 90秒"
  h.postDelayed(adPoll,2000)
 }
 fun scheduleAdScan(){
  report="5秒後に広告UIを取得します（操作なし）"
  h.postDelayed({scanAd()},5000)
 }
 private fun scanAd(){
  val root=rootInActiveWindow
  if(root==null){report="広告診断: root=null。操作なし";return}
  val nodes=ArrayList<AccessibilityNodeInfo>()
  fun walk(n:AccessibilityNodeInfo){
   if(nodes.size>=400)return
   nodes.add(n)
   for(i in 0 until n.childCount)n.getChild(i)?.let{walk(it)}
  }
  walk(root)
  val pkg=root.packageName?.toString().orEmpty()
  val sb=StringBuilder()
  sb.append("広告診断（自動タップなし）\npackage=$pkg nodes=${nodes.size}\n")
  val words=listOf("閉じる","とじる","close","skip","スキップ","終了","×","✕","✖","dismiss")
  var found=0
  for(n in nodes){
   val text=n.text?.toString().orEmpty()
   val desc=n.contentDescription?.toString().orEmpty()
   val label=(text+" "+desc).trim()
   val rect=Rect();n.getBoundsInScreen(rect)
   val match=words.any{label.contains(it,ignoreCase=true)}
   // List potential unlabeled clickable controls near the top corners as diagnostics only.
   val topCorner=n.isClickable && rect.width() in 15..200 && rect.height() in 15..200 &&
    rect.top in 0..380 && (rect.left in 0..200 || rect.right in 880..1080)
   if(match || topCorner){
    found++
    sb.append("#$found ").append(n.className?.toString()?.substringAfterLast('.'))
      .append(" bounds=").append(rect.toShortString())
      .append(" clickable=").append(n.isClickable)
      .append(" enabled=").append(n.isEnabled)
      .append(" visible=").append(n.isVisibleToUser)
      .append(" selected=").append(n.isSelected)
      .append(" text=").append(text.take(120))
      .append(" desc=").append(desc.take(120))
      .append("\n")
   }
  }
  sb.append("候補数=$found\n")
  if(found==0)sb.append("閉じる候補がUIツリーに見つかりませんでした。\n")
  sb.append("\n--- 参考: 表示中の文字・クリック可能ノード（最大120件） ---\n")
  var shown=0
  for(n in nodes){
   if(shown>=120)break
   val t=n.text?.toString().orEmpty()
   val d=n.contentDescription?.toString().orEmpty()
   if(t.isBlank() && d.isBlank() && !n.isClickable)continue
   val rect=Rect();n.getBoundsInScreen(rect)
   sb.append("${shown+1}. ${rect.toShortString()} click=${n.isClickable} enabled=${n.isEnabled} text=${t.take(70)} desc=${d.take(70)}\n")
   shown++
  }
  report=sb.toString()
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