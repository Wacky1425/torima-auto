package com.neru.powlautotest

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Explicit teach tap: captures the screen and selected position before one dispatched tap. */
class TeachOverlay(
 private val service:PowlAccessibilityService,
 private val memory:LearnedActions,
 private val status:(String)->Unit
) {
 companion object{
  private const val APP="jp.co.incrementp.milemobile"
  private const val HISTORY="torima_action_history.json"
  private const val MAX_LOG=80
  private fun file(ctx:Context)=File(ctx.filesDir,HISTORY)
  private fun read(ctx:Context):JSONArray=try {
   JSONArray(file(ctx).takeIf{it.exists()}?.readText(Charsets.UTF_8)?:"[]")
  }catch(_:Exception){JSONArray()}
  fun export(ctx:Context):String{
   val data=read(ctx)
   return if(data.length()==0)"操作記録はまだありません。" else
    JSONObject().put("count",data.length()).put("events",data).toString(2)
  }
  fun recordAutomatic(ctx:Context,title:String){
   try{
    record(ctx,JSONObject().apply{
     put("type","auto_state")
     put("at",System.currentTimeMillis())
     put("message",title.take(180))
    })
   }catch(_:Exception){}
  }
  /** Structured HOME-only evidence alongside the existing action history. */
  fun recordHomeExperiment(ctx:Context,stage:String,trial:String,detail:String){
   try{
    record(ctx,JSONObject().apply{
     put("type","home_experiment")
     put("at",System.currentTimeMillis())
     put("stage",stage.take(40))
     put("trial",trial.take(45))
     put("detail",detail.take(550))
    })
   }catch(_:Exception){}
  }
  /**
   * Preserve a small, structured snapshot when gacha is blocked on an UNKNOWN
   * screen. Unlike repeating wait logs, this retains evidence for unattended
   * debugging without replaying taps or requiring the user to watch the screen.
   */
  fun recordGachaDiagnostic(ctx:Context,root:AccessibilityNodeInfo?,phase:String){
   if(root?.packageName?.toString()!=APP)return
   try{
    val samples=JSONArray()
    var visited=0
    fun scan(node:AccessibilityNodeInfo){
     if(visited>=160 || samples.length()>=50)return
     visited++
     val r=android.graphics.Rect();node.getBoundsInScreen(r)
     if(node.isVisibleToUser && r.width()>0 && r.height()>0){
      val txt=node.text?.toString().orEmpty().take(100)
      val desc=node.contentDescription?.toString().orEmpty().take(100)
      val id=node.viewIdResourceName.orEmpty().take(100)
      if(node.isClickable || txt.isNotBlank() || desc.isNotBlank() || id.isNotBlank()){
       samples.put(JSONObject().apply{
        put("class",node.className?.toString()?.substringAfterLast('.').orEmpty())
        put("bounds",r.toShortString())
        put("clickable",node.isClickable && node.isEnabled)
        if(txt.isNotBlank())put("text",txt)
        if(desc.isNotBlank())put("description",desc)
        if(id.isNotBlank())put("viewId",id)
       })
      }
     }
     for(i in 0 until node.childCount){
      if(visited>=160 || samples.length()>=50)break
      node.getChild(i)?.let{scan(it)}
     }
    }
    scan(root)
    record(ctx,JSONObject().apply{
     put("type","gacha_unknown_snapshot")
     put("at",System.currentTimeMillis())
     put("phase",phase)
     put("scanned",visited)
     put("nodes",samples)
    })
   }catch(_:Exception){}
  }
  private fun record(ctx:Context,item:JSONObject){
   val current=read(ctx)
   if(current.length()>=MAX_LOG)current.remove(0)
   current.put(item)
   file(ctx).writeText(current.toString(),Charsets.UTF_8)
  }
 }
 private val h=Handler(Looper.getMainLooper())
 private var wm:WindowManager?=null
 private var touchLayer:View?=null
 private var before:AccessibilityNodeInfo?=null
 private var beforeCount=0
 private var beforeSignature:String?=null
 private var outcomeReady=false
 private var homeTeachSucceeded=false
 private var homeSeparatorTop=0
 private var proposed:LearnedActions.Proposal?=null
 private var changed=false
 private var outcomeEligible=false
 private var outcomeKind="PENDING"
 private var outcomePackage=""
 private var visualCapture:VisualLearnedActions.Captured?=null
 private var sourceNodeDescription=""
 private var teachId=0
 private var active=false
 private var awaitingPhysicalTap=false
 private var physicalTapStartedAt=0L
 private var physicalClickAt=0L
 private var changedSince=0L
 private fun dp(n:Int)=(n*service.resources.displayMetrics.density+0.5f).toInt()

 fun begin():String{
  if(active || awaitingPhysicalTap)return "学習中です。現在の選択または指タップが終わるまで待ってください。"
  val root=service.rootInActiveWindow
  if(root?.packageName?.toString()!=APP)return "トリマの画面を前面に出してから『学習』を押してください。"
  before=root
  val initial=memory.scan(root)
  beforeCount=initial?.rootCount?:0
  beforeSignature=initial?.signature
  homeSeparatorTop=initial?.items?.filter{
   it.node.text?.toString()?.trim()=="または" && it.bounds.width()>0
  }?.map{it.bounds.top}?.distinct()?.singleOrNull()?:0
  proposed=null
  changed=false
  outcomeEligible=false
  outcomeKind="PENDING"
  outcomePackage=""
  visualCapture=null
  sourceNodeDescription=""
  outcomeReady=false
  homeTeachSucceeded=false
  teachId++
  val id=teachId
  return try{
   val manager=service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
   val layer=FrameLayout(service).apply{
    // Stage 1 captures the POSITION. Fully transparent layer only lives
    // until that choice; no dimming and no top-left help banner.
    setBackgroundColor(Color.TRANSPARENT)
    isClickable=true
    setOnTouchListener{_,event->
     if(event.actionMasked==MotionEvent.ACTION_UP){
      select(event.rawX.toInt(),event.rawY.toInt(),id)
     }
     true
    }
   }
   // The top-left help box hid top-left ad dismiss controls. Don't cover
   // the underlying screen: show instructions through Toast instead.
   val cancel=TextView(service).apply{
    text="中止"
    textSize=17f
    setTextColor(Color.WHITE)
    setBackgroundColor(Color.rgb(127,40,44))
    setPadding(dp(16),dp(13),dp(16),dp(13))
    setOnClickListener{cancelCapture("学習をキャンセルしました。")}
   }
   layer.addView(cancel,FrameLayout.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.WRAP_CONTENT,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply{
     bottomMargin=dp(24)
    })
   val params=WindowManager.LayoutParams(
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT
   ).apply{gravity=Gravity.TOP or Gravity.LEFT}
   manager.addView(layer,params)
   wm=manager
   touchLayer=layer
   active=true
   h.postDelayed({
    if(teachId==id&&active)cancelCapture("20秒経過したので学習を中止しました。")
   },20000)
   status("学習 1/2：まず押したい×／スキップの位置を選んでください")
   Toast.makeText(service,
    "学習 1/2：場所を選ぶ → 膜が消えたら同じ場所を指で押す",
    Toast.LENGTH_LONG).show()
   "学習 1/2：位置を指定。膜が消えたらもう一度指で直接押してください。"
  }catch(e:Exception){
   active=false
   before=null
   touchLayer=null
   wm=null
   "学習オーバーレイ表示エラー: ${e.javaClass.simpleName}"
  }
 }
 private fun removeLayer(){
  val view=touchLayer
  touchLayer=null
  active=false
  if(view!=null)try{wm?.removeView(view)}catch(_:Exception){}
  wm=null
 }
 fun cancelCapture(message:String="学習中止"):String{
  teachId++
  removeLayer()
  awaitingPhysicalTap=false
  physicalTapStartedAt=0L
  physicalClickAt=0L
  changedSince=0L
  before=null
  proposed=null
  visualCapture=null
  service.resumeTeachingBar()
  status(message)
  return message
 }
 private fun select(x:Int,y:Int,id:Int){
  if(!active || teachId!=id)return
  // Do not send a gesture through Google Play or a different app.
  val root=before
  val p=memory.propose(root,x,y)
  val touching=memory.scan(root)?.items?.filter{
   it.bounds.contains(x,y) && it.node.isVisibleToUser
  }.orEmpty()
  val smallNodes=touching.filter{
   it.bounds.width() in 16..430 && it.bounds.height() in 16..310
  }
  sourceNodeDescription=when{
   touching.isEmpty()->"位置付近にノードがないため画像学習へ"
   smallNodes.size>1->"ノード候補が${smallNodes.size}件。操作対象が一意ではないため画像学習へ"
   smallNodes.isEmpty()->"ノードはあるが全画面領域しか取得できず画像学習へ"
   else->"ノードはあるがボタン名／IDを特定できず画像学習へ"
  }
  proposed=p
  changed=false
  outcomeEligible=false
  outcomeKind="PENDING"
  outcomePackage=""
  visualCapture=null
  outcomeReady=false
  homeTeachSucceeded=false
  val selectedName=p.label.lowercase()
  if(TeachRulePolicy.isForbidden(selectedName)){
   removeLayer()
   proposed=null
   status("購入・インストール・シェア対象は学習モードで押しません")
   return
  }
  val isUnsafeScreen=memory.scan(root)?.items?.any{
   val name=it.label
   it.node.isVisibleToUser &&
    (name.contains("友達にシェアする") ||
     name.contains("動画スキップ券を使いますか") ||
     name.contains("カードを1枚選んでください") ||
     name.contains("持っている枚数"))
  }==true
  if(isUnsafeScreen && !p.safe){
   removeLayer()
   proposed=null
   status("この画面はガチャ・シェア・スキップ券の可能性があるため、広告の画像学習を登録しません")
   return
  }
  // The FIRST tap was intercepted to learn the coordinate. The original
  // app did not receive it. For stage 2 fully remove the selection window and
  // hide the collector, then let the actual finger touch Torima directly.
  removeLayer()
  if(!service.suspendTeachingBar()){
   failPhysicalObservation("操作バーのタッチ透過に失敗。指タップを安全に通せないため学習を中止")
   return
  }
  status(if(p.safe)
   "学習 1/2：ノードは取得済み。次は実際の指で同じ位置を押してください"
   else "$sourceNodeDescription。タップ前の画面を画像学習中")
  if(p.safe){
   startPhysicalObservation(p,id)
  }else{
   service.captureVisualTeaching {bitmap,note->
    if(teachId!=id){
     bitmap?.recycle()
     return@captureVisualTeaching
    }
    visualCapture=try{
     if(bitmap==null)null else service.visualLearnedActions.capture(
      bitmap,x,y,beforeCount,p.label)
    }catch(_:Exception){null}
    finally{bitmap?.recycle()}
    if(visualCapture==null){
     failPhysicalObservation(
      "$sourceNodeDescription。画像の学習特徴を保存できません："+
       if(bitmap==null)note else "画面が単色／押した位置が対応領域外")
    }else{
     startPhysicalObservation(p,id)
    }
   }
  }
 }
 /**
  * Stage 2 is intentionally PASSIVE. The selection overlay has been removed,
  * and no AccessibilityService dispatchGesture/ACTION_CLICK is used. Only the
  * user's own touch can advance the target app during this stage.
  *
  * Accessibility does not expose arbitrary raw touches to other apps, so
  * observe a real VIEW_CLICKED (when present) or the resulting screen change.
  */
 private fun startPhysicalObservation(p:LearnedActions.Proposal,id:Int){
  if(teachId!=id || proposed==null)return
  if(service.rootInActiveWindow?.packageName?.toString()!=APP){
   failPhysicalObservation("画面がトリマ以外に切り替わったため学習を中止しました")
   return
  }
  awaitingPhysicalTap=true
  physicalTapStartedAt=SystemClock.elapsedRealtime()
  physicalClickAt=0L
  changedSince=0L
  if(!service.suspendTeachingBar()){
   failPhysicalObservation("操作バーのタッチ透過を確認できず、指タップ学習を中止")
   return
  }
  status("学習 2/2：膜と操作バーを消しました。選んだ場所を指で直接押してください（14秒以内）")
  Toast.makeText(service,
   "学習 2/2：もう一度、同じ×／スキップを指で押してください",
   Toast.LENGTH_LONG).show()
  h.postDelayed({observePhysicalTap(p,id)},380L)
 }
 private fun observePhysicalTap(p:LearnedActions.Proposal,id:Int){
  if(teachId!=id || !awaitingPhysicalTap)return
  val now=SystemClock.elapsedRealtime()
  val pkg=service.rootInActiveWindow?.packageName?.toString().orEmpty()
  val current=memory.scan(service.rootInActiveWindow)
  val isChanged=(pkg.isNotBlank() && pkg!=APP) ||
   (pkg==APP && current!=null &&
    (current.rootCount!=beforeCount ||
     (beforeSignature!=null && current.signature!=beforeSignature)))
  // Moving visuals can alter a label temporarily; do not register from a
  // single transient scan. Only accept a stable observation.
  if(isChanged && now-physicalTapStartedAt>=600L){
   if(changedSince==0L)changedSince=now
   if(now-changedSince>=650L){
    finishPhysicalObservation(p,id,
     "本人のタップ後の画面遷移を確認")
    return
   }
  }else changedSince=0L
  // A genuine TYPE_VIEW_CLICKED with a matching small source can also
  // confirm input even when an ad SDK does not navigate on the first touch.
  if(physicalClickAt>0L && now-physicalClickAt>=1_000L){
   finishPhysicalObservation(p,id,
    "押した位置のクリックイベントを確認（画面変化は別途記録）")
   return
  }
  if(now-physicalTapStartedAt>=14_000L){
   failPhysicalObservation(
    "14秒以内に指タップによるクリック・画面変化を確認できませんでした。ルール未登録。学習し直してください")
   return
  }
  h.postDelayed({observePhysicalTap(p,id)},350L)
 }
 private fun finishPhysicalObservation(p:LearnedActions.Proposal,id:Int,detail:String){
  if(teachId!=id || !awaitingPhysicalTap)return
  awaitingPhysicalTap=false
  service.resumeTeachingBar()
  status("指の操作を確認：$detail。学習を登録中")
  recordOutcome(p)
  before=null
 }
 private fun failPhysicalObservation(detail:String){
  awaitingPhysicalTap=false
  physicalTapStartedAt=0L
  physicalClickAt=0L
  changedSince=0L
  proposed=null
  visualCapture=null
  before=null
  service.resumeTeachingBar()
  status(detail)
 }
 private fun recordOutcome(p:LearnedActions.Proposal){
  val afterRoot=service.rootInActiveWindow
  val afterPkg=afterRoot?.packageName?.toString().orEmpty()
  val verifiedPlaySheet=service.isPlayBrandedInstallPopup(afterRoot)
  val playAfterTap=TeachingOutcomePolicy.isPlayDestination(afterPkg,verifiedPlaySheet)
  if(playAfterTap){
   // Playback may bring up a Play sheet even after a dismiss tap.
   // Save this transition as an observed result, not a presumed ad close.
   service.markPlayTransitionFromTorima()
  }
  val after=memory.scan(afterRoot)
  changed=afterPkg!=APP ||
   (after!=null && (after.rootCount!=beforeCount ||
     after.signature!=beforeSignature))
  val afterStillHome=after?.signature?.let{
   it.contains("move") && it.contains("あと") && it.contains("タンク")
  }==true
  homeTeachSucceeded=changed && afterPkg==APP && !afterStillHome &&
   HomeTouchProfile.qualifies(service,beforeSignature,p.x,p.y,homeSeparatorTop)
  val closeSource=TeachingOutcomePolicy.isNamedDismissTarget(
   p.label,p.viewId,p.bounds.width(),p.bounds.height(),
   service.resources.displayMetrics.widthPixels,
   service.resources.displayMetrics.heightPixels)
  outcomeEligible=TeachingOutcomePolicy.eligibleForRule(
   p.safe,changed,afterPkg,verifiedPlaySheet,closeSource)
  outcomeKind=TeachingOutcomePolicy.outcomeName(
   afterPkg,verifiedPlaySheet,outcomeEligible)
  outcomePackage=afterPkg
  try{
   record(service,JSONObject().apply{
    put("type","teaching_tap")
    put("at",System.currentTimeMillis())
    put("x",p.x);put("y",p.y)
    put("node",p.label);put("class",p.clazz);put("id",p.viewId)
    put("bounds",p.bounds.toShortString())
    put("screenSignature",p.signature?:"NO_RELIABLE_SIGNATURE")
    put("afterPackage",afterPkg)
    put("afterNodes",after?.rootCount?:0)
    put("screenChanged",changed)
    put("eligibleForRule",outcomeEligible || homeTeachSucceeded)
    put("sourceTargetSafe",p.safe)
    put("sourceDismissTarget",closeSource)
    put("observedPlaySheet",playAfterTap)
    put("outcomeKind",outcomeKind)
    put("homeTouchCalibration",homeTeachSucceeded)
   })
  }catch(_:Exception){}
  outcomeReady=true
  // One-tap teaching: SAVE immediately after the observed outcome.
  // No hidden second 登録 action, and no growing pending-only examples.
  val learnedMessage=confirm()
  status(learnedMessage)
 }
 fun confirm():String {
  val p=proposed?:return "学習する操作がありません。『学習』からもう一度選んでください"
  if(!outcomeReady)
   return "画面変化の確認中です。数秒待ってください"
  val visual=visualCapture
  val result=when{
   homeTeachSucceeded && changed->
    HomeTouchProfile.register(service,p.x,p.y)
   // Even if Android cannot find a named target node, save the user's
   // BEFORE-tap image descriptor as a live-action rule, not a pending example.
   visual!=null->
    service.visualLearnedActions.register(visual,changed,outcomePackage)
   p.safe && (outcomePackage==APP ||
    TeachingOutcomePolicy.isPlayDestination(outcomePackage,
     outcomePackage=="com.google.android.gms"))->
    memory.register(p,if(changed)outcomeKind else "TAUGHT_EFFECT_NOT_VERIFIED")
   else->"学習を登録できませんでした：ノードが特定できず、画像特徴も取得できません。$sourceNodeDescription"
  }
  proposed=null
  visualCapture=null
  outcomeEligible=false
  outcomeKind="PENDING"
  return result
 }
 fun observedClick(event:AccessibilityEvent){
  if(event.packageName?.toString()!=APP || event.eventType!=AccessibilityEvent.TYPE_VIEW_CLICKED)return
  try{
   val source=event.source
   val rect=android.graphics.Rect();source?.getBoundsInScreen(rect)
   val p=proposed
   if(awaitingPhysicalTap && p!=null &&
    source!=null && rect.contains(p.x,p.y) &&
    rect.width() in 15..700 && rect.height() in 15..390){
    physicalClickAt=SystemClock.elapsedRealtime()
   }
   val detail=source?.contentDescription?.toString()?.take(80)
    ?:source?.text?.toString()?.take(80)?:event.text.joinToString(" ").take(80)
   record(service,JSONObject().apply{
    put("type","view_clicked_event")
    put("at",System.currentTimeMillis())
    put("label",detail)
    put("bounds",rect.toShortString())
    put("note","イベントは手動と自動の区別ができない場合があります")
   })
  }catch(_:Exception){}
 }
}
