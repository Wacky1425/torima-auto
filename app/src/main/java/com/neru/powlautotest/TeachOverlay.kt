package com.neru.powlautotest

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
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
 private fun dp(n:Int)=(n*service.resources.displayMetrics.density+0.5f).toInt()

 fun begin():String{
  if(active)return "学習用のタップ指定はすでに開始しています。"
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
    setBackgroundColor(Color.argb(32,15,25,50))
    isClickable=true
    setOnTouchListener{_,event->
     if(event.actionMasked==MotionEvent.ACTION_UP){
      select(event.rawX.toInt(),event.rawY.toInt(),id)
     }
     true
    }
   }
   val label=TextView(service).apply{
    text="学習モード：次に操作したい場所を1回タップ。\n画面に戻りたい場合は「中止」（20秒で自動終了）。"
    textSize=13f
    setTextColor(Color.WHITE)
    setBackgroundColor(Color.rgb(22,39,64))
    setPadding(dp(12),dp(9),dp(12),dp(9))
   }
   layer.addView(label,FrameLayout.LayoutParams(
    dp(250),dp(80),Gravity.TOP or Gravity.LEFT))
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
    WindowManager.LayoutParams.WRAP_CONTENT,Gravity.BOTTOM or Gravity.RIGHT).apply{
     bottomMargin=dp(75)
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
   status("学習中：次に押す場所を1回タップ")
   "学習を開始しました。押したい場所を1回タップしてください。"
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
  before=null
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
  removeLayer()
  status(if(p.safe)"ノードを確認。1回の操作で自動登録します"
   else "$sourceNodeDescription。タップ前の画像を取得中")
  fun replay(){
   h.postDelayed({
    if(teachId!=id)return@postDelayed
    if(service.rootInActiveWindow?.packageName?.toString()!=APP){
     proposed=null
     status("学習元が別アプリになったため操作を中止。再度学習してください")
     return@postDelayed
    }
    val path=Path().apply{moveTo(x.toFloat(),y.toFloat())}
    val gesture=GestureDescription.Builder()
     .addStroke(GestureDescription.StrokeDescription(path,0,110)).build()
    val accepted=service.dispatchGesture(gesture,
     object:AccessibilityService.GestureResultCallback(){
      override fun onCompleted(g:GestureDescription?){
       if(teachId!=id)return
       h.postDelayed({if(teachId==id)recordOutcome(p)},1_800L)
      }
      override fun onCancelled(g:GestureDescription?){
       if(teachId!=id)return
       proposed=null
       status("学習タップがキャンセル。ルール未保存です")
      }
     },null)
    if(!accepted){
     proposed=null
     status("学習タップが拒否されました。ルール未保存です")
    }
   },180L)
  }
  if(p.safe){
   replay()
  }else{
   // The teaching overlay has already been removed: capture BEFORE touch.
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
     val reason=if(bitmap==null)note
      else "押した付近に識別可能な模様がない、または×／スキップの許可領域外"
     proposed=null
     status("$sourceNodeDescription。画像学習不可：$reason。保留記録は増やしません")
    }else{
     status("$sourceNodeDescription。画像特徴取得成功、教えた操作を確認中")
     replay()
    }
   }
  }
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
