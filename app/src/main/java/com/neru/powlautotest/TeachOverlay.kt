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
 private var proposed:LearnedActions.Proposal?=null
 private var changed=false
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
  proposed=null
  changed=false
  outcomeReady=false
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
  proposed=p
  changed=false
  outcomeReady=false
  val selectedName=p.label.lowercase()
  if(selectedName.contains("インストール")||selectedName.contains("install")||
   selectedName.contains("友達にシェア")||selectedName.contains("購入")){
   removeLayer()
   proposed=null
   status("購入・インストール・シェア対象は学習モードで押しません")
   return
  }
  removeLayer()
  status("タップ位置 ($x,$y) を取得。実際の画面遷移を記録中")
  h.postDelayed({
   if(teachId!=id)return@postDelayed
   if(service.rootInActiveWindow?.packageName?.toString()!=APP){
    proposed=null
    status("対象がトリマ以外になったため操作をキャンセルしました。")
    return@postDelayed
   }
   val path=Path().apply{moveTo(x.toFloat(),y.toFloat())}
   val gesture=GestureDescription.Builder()
    .addStroke(GestureDescription.StrokeDescription(path,0,110)).build()
   val accepted=service.dispatchGesture(gesture,object:AccessibilityService.GestureResultCallback(){
    override fun onCompleted(g:GestureDescription?){
     if(teachId!=id)return
     h.postDelayed({if(teachId==id)recordOutcome(p)},1800)
    }
    override fun onCancelled(g:GestureDescription?){
     if(teachId!=id)return
     proposed=null
     status("操作の再現がキャンセルされました。保存していません")
    }
   },null)
   if(!accepted){
    proposed=null
    status("操作の再現が拒否されました。保存していません")
   }
  },220)
 }
 private fun recordOutcome(p:LearnedActions.Proposal){
  val afterRoot=service.rootInActiveWindow
  val afterPkg=afterRoot?.packageName?.toString().orEmpty()
  val after=memory.scan(afterRoot)
  changed=afterPkg!=APP ||
   (after!=null && (after.rootCount!=beforeCount ||
     after.signature!=beforeSignature))
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
    put("eligibleForRule",p.safe && changed && afterPkg==APP)
   })
  }catch(_:Exception){}
  outcomeReady=true
  val ok=p.safe&&changed&&afterPkg==APP
  status(if(ok)
   "操作を記録しました。正しい動きならバーの『登録』を押してください。"
   else "操作例を記録しましたが、安全な自動再現は未対応です。${p.message}")
 }
 fun confirm():String {
  val p=proposed?:return "保存できる操作がありません。先に『学習』してください。"
  if(!outcomeReady)return "まだタップ後の画面判定中です。2秒ほど待って『登録』を押してください。"
  val afterRoot=service.rootInActiveWindow
  val afterPkg=afterRoot?.packageName?.toString().orEmpty()
  val current=memory.scan(afterRoot)
  val changeConfirmed=changed || (current!=null &&
   (current.rootCount!=beforeCount || current.signature!=beforeSignature))
  proposed=null
  val msg=if(p.safe&&changeConfirmed&&afterPkg==APP)memory.register(p)
   else "操作履歴は保存済み。ただしこの画面を誤認しない自動ルールは登録できません。"
  status(msg)
  return msg
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
