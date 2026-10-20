package com.neru.powlautotest

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.os.SystemClock
import kotlin.math.abs
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** User-labeled screen samples. No automatic clicks or screen capture permission. */
class ScreenCollector(private val service:PowlAccessibilityService) {
 companion object {
  private const val FILENAME="torima_screen_samples.json"
  private const val TARGET="jp.co.incrementp.milemobile"
  private const val MAX_SAMPLES=40
  val stages=listOf(
   "HOME" to "有効な紫の動画ボタン",
   "スキップ券" to "使わない",
   "広告（上の×）" to "動画終了後の×",
   "広告（下の閉じる）" to "動画終了後の閉じる",
   "広告（その他）" to "閉じる箇所を確認",
   "再チャレンジ" to "動画を見て再チャレンジ",
   "Wチャンス" to "ガチャWチャンスに挑戦",
   "カード選択" to "右上のカードを選択",
   "カード結果" to "×で閉じる",
   "報酬・シェア" to "友達にシェアせず×で閉じる",
   "最終獲得" to "×で閉じる",
   "その他・不明" to "操作せず調べる"
  )
  private fun file(ctx:Context)=File(ctx.filesDir,FILENAME)
  private fun load(ctx:Context):JSONArray=try{
   JSONArray(file(ctx).takeIf{it.exists()}?.readText(Charsets.UTF_8)?:"[]")
  }catch(_:Exception){JSONArray()}
  fun count(ctx:Context):Int=load(ctx).length()
  fun clear(ctx:Context){file(ctx).writeText("[]",Charsets.UTF_8)}
  private fun append(ctx:Context,item:JSONObject){
   val items=load(ctx)
   if(items.length()>=MAX_SAMPLES)items.remove(0)
   items.put(item)
   file(ctx).writeText(items.toString(),Charsets.UTF_8)
  }
  fun export(ctx:Context):String{
   val samples=load(ctx)
   if(samples.length()==0)return "記録した画面はまだありません。"
   val result=StringBuilder("Torima Auto 画面データ ${samples.length()}件\n")
   for(i in 0 until samples.length()){
    val sample=samples.optJSONObject(i)?:continue
    result.append("\n===== ${i+1} ${sample.optString("label")} =====\n")
    result.append("希望操作: ${sample.optString("intended_action")}\n")
    result.append("package=${sample.optString("package")} nodes=${sample.optInt("node_count")}\n")
    if(sample.optInt("node_count")<=1)result.append("内容が取れない画面（nodes=1）\n")
    val nodes=sample.optJSONArray("nodes")?:JSONArray()
    for(j in 0 until minOf(nodes.length(),180)){
     val n=nodes.optJSONObject(j)?:continue
     result.append("#${n.optInt("index")} ${n.optString("class")} ${n.optString("bounds")}")
     result.append(" click=${n.optBoolean("clickable")} enabled=${n.optBoolean("enabled")}")
     result.append(" visible=${n.optBoolean("visible")} selected=${n.optBoolean("selected")}")
     if(n.optString("text").isNotBlank())result.append(" text=${n.optString("text")}")
     if(n.optString("desc").isNotBlank())result.append(" desc=${n.optString("desc")}")
     if(n.optString("viewId").isNotBlank())result.append(" id=${n.optString("viewId")}")
     result.append("\n")
    }
   }
   return result.toString()
  }
 }
 private var wm:WindowManager?=null
 private var bar:View?=null
 private var lp:WindowManager.LayoutParams?=null
 private var index=0
 private var top=false
 private var lastDraggedAt=0L
 private var expanded=false
 private var detailsPanel:LinearLayout?=null
 private var title:TextView?=null
 private var info:TextView?=null
 private val homeHandler=Handler(Looper.getMainLooper())
 private var homeOriginalFlags:Int?=null
 private var homeBypassEpoch=0
 private var homeRestore:Runnable?=null
 /** Allow gesture injection to reach Torima instead of our own overlay window.
  * The bar stays visible, but is deliberately untouchable for a brief period.
  * This changes the WindowManager touchability, not merely View visibility.
  */
 fun prepareHomeTouch(x:Int,y:Int,onReady:(Boolean,String)->Unit,restoreMs:Long=4_500L){
  val panel=bar
  val p=lp
  val manager=wm
  if(panel==null || p==null || manager==null){
   homeHandler.postDelayed({onReady(true,"操作バーなし")},HomeTouchTrials.OVERLAY_SETTLE_MS)
   return
  }
  val rect=Rect()
  val covered=panel.getGlobalVisibleRect(rect) && rect.contains(x,y)
  if(homeOriginalFlags==null)homeOriginalFlags=p.flags
  homeBypassEpoch++
  val epoch=homeBypassEpoch
  homeRestore?.let{homeHandler.removeCallbacks(it)}
  p.flags=p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
  try{
   manager.updateViewLayout(panel,p)
  }catch(e:Exception){
   restoreHomeTouch()
   onReady(false,"オーバーレイ透過設定失敗：${e.javaClass.simpleName}")
   return
  }
  val note="操作バー領域=${rect.toShortString()}／ボタン位置が重なる=${covered}／タッチ透過ON"
  homeHandler.postDelayed({
   if(homeBypassEpoch!=epoch)return@postDelayed
   onReady(true,note)
  },HomeTouchTrials.OVERLAY_SETTLE_MS)
  val restoration=Runnable{if(homeBypassEpoch==epoch)restoreHomeTouch()}
  homeRestore=restoration
  homeHandler.postDelayed(restoration,restoreMs)
 }
 /** Allow verified Settings controls to receive taps even under our overlay. */
 fun prepareRecoveryOverlay(onReady:(Boolean,String)->Unit){
  val d=service.resources.displayMetrics
  prepareHomeTouch(d.widthPixels/2,d.heightPixels/2,onReady,45_000L)
 }
 /** Disable the bar's touch interception for the whole passive finger test. */
 fun prepareManualHomeObservation(onReady:(Boolean,String)->Unit){
  val display=service.resources.displayMetrics
  prepareHomeTouch(display.widthPixels/2,display.heightPixels*52/100,
   onReady,20_000L)
 }
 fun restoreHomeTouch(){
  homeBypassEpoch++
  homeRestore?.let{homeHandler.removeCallbacks(it)}
  homeRestore=null
  val old=homeOriginalFlags
  homeOriginalFlags=null
  val p=lp
  val panel=bar
  if(old!=null && p!=null && panel!=null){
   p.flags=old
   try{wm?.updateViewLayout(panel,p)}catch(_:Exception){}
  }
 }
 private var autoStatus="停止中"
 fun isVisible():Boolean=bar!=null
 fun updateAutoStatus(text:String){
  autoStatus=text
  info?.text="状態: ${autoStatus.take(115)}"
 }
 private fun dp(n:Int)=(n*service.resources.displayMetrics.density+0.5f).toInt()
 private fun bindDrag(view:View){
  var downX=0f;var downY=0f;var downAt=0L
  var dragging=false
  var baseX=0;var baseY=0
  view.setOnTouchListener{_,event->
   when(event.actionMasked){
    MotionEvent.ACTION_DOWN->{
     downX=event.rawX;downY=event.rawY
     downAt=SystemClock.elapsedRealtime();dragging=false
     false
    }
    MotionEvent.ACTION_MOVE->{
     val manager=wm;val p=lp;val panel=bar
     if(manager==null || p==null || panel==null)return@setOnTouchListener false
     val age=SystemClock.elapsedRealtime()-downAt
     val distance=abs(event.rawX-downX)+abs(event.rawY-downY)
     if(!dragging && age>=440L && distance<dp(115)){
      val loc=IntArray(2);panel.getLocationOnScreen(loc)
      p.gravity=Gravity.TOP or Gravity.LEFT
      p.x=loc[0];p.y=loc[1]
      baseX=p.x;baseY=p.y
      dragging=true
      lastDraggedAt=SystemClock.elapsedRealtime()
     }
     if(dragging){
      val screen=service.resources.displayMetrics
      p.x=(baseX+(event.rawX-downX).toInt()).coerceIn(0,
       maxOf(0,screen.widthPixels-panel.width))
      p.y=(baseY+(event.rawY-downY).toInt()).coerceIn(0,
       maxOf(0,screen.heightPixels-panel.height))
      try{manager.updateViewLayout(panel,p)}catch(_:Exception){}
      lastDraggedAt=SystemClock.elapsedRealtime()
      true
     }else false
    }
    MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_UP->{
     if(dragging){
      lastDraggedAt=SystemClock.elapsedRealtime()
      dragging=false
      true
     }else false
    }
    else->false
   }
  }
 }
 private fun control(label:String,action:()->Unit)=TextView(service).apply{
  text=label
  textSize=14f
  setTextColor(Color.WHITE)
  gravity=Gravity.CENTER
  setPadding(dp(11),dp(9),dp(11),dp(9))
  setOnClickListener{
   if(SystemClock.elapsedRealtime()-lastDraggedAt>700L)action()
  }
  bindDrag(this)
 }
 fun show():String{
  if(bar!=null)return "画面記録バーはすでに表示されています。"
  return try{
   val win=service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
   val panel=LinearLayout(service).apply{
    orientation=LinearLayout.VERTICAL
    setPadding(dp(8),dp(5),dp(8),dp(5))
    background=GradientDrawable().apply{
     setColor(Color.argb(245,24,36,58))
     cornerRadius=dp(12).toFloat()
    }
   }
   val heading=TextView(service).apply{
    text="⠿ 長押しで移動   |   Torima Auto"
    textSize=12f
    setTextColor(Color.rgb(200,220,242))
    setPadding(dp(9),dp(7),dp(9),dp(9))
   }
   panel.addView(heading);title=heading
   bindDrag(heading)
   val primary=LinearLayout(service).apply{
    orientation=LinearLayout.HORIZONTAL
    gravity=Gravity.CENTER_VERTICAL
   }
   primary.addView(control("▶ 開始"){updateAutoStatus(service.startAutomaticOperation())},
    LinearLayout.LayoutParams(0,dp(49),1f))
   primary.addView(control("■ 停止"){updateAutoStatus(service.stopAutomaticOperation())},
    LinearLayout.LayoutParams(0,dp(49),1f))
   primary.addView(control("✦ 学習"){updateAutoStatus(service.beginTeachTap())},
    LinearLayout.LayoutParams(0,dp(49),1f))
   panel.addView(primary)
   val status=TextView(service).apply{
    textSize=11f;setTextColor(Color.rgb(215,228,242));maxLines=2
    setPadding(dp(8),dp(4),dp(8),dp(4))
   }
   panel.addView(status);info=status
   bindDrag(status)
   val detail=control("▾ 詳細・診断"){
    expanded=!expanded
    detailsPanel?.visibility=if(expanded)View.VISIBLE else View.GONE
   }.apply{textSize=11f}
   panel.addView(detail)
   val extras=LinearLayout(service).apply{
    orientation=LinearLayout.VERTICAL;visibility=View.GONE
   }
   detailsPanel=extras
   val tools=LinearLayout(service).apply{
    orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
   }
   tools.addView(control("◀"){index=(index+stages.size-1)%stages.size;refresh()})
   tools.addView(control("記録"){capture()})
   tools.addView(control("画像"){saveImage()})
   tools.addView(control("▶"){index=(index+1)%stages.size;refresh()})
   tools.addView(control("上下"){top=!top;position()})
   tools.addView(control("閉じる"){hide()})
   extras.addView(tools)
   val diagnostics=LinearLayout(service).apply{
    orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
   }
   diagnostics.addView(control("手動比較"){updateAutoStatus(service.startManualHomeComparison())})
   diagnostics.addView(control("旧登録"){updateAutoStatus(service.saveTaughtTap())})
   extras.addView(diagnostics)
   panel.addView(extras)
   bindDrag(panel)
   val params=WindowManager.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
    PixelFormat.TRANSLUCENT
   ).apply{
    gravity=Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    y=dp(125)
   }
   win.addView(panel,params)
   wm=win;bar=panel;lp=params
   expanded=false
   refresh()
   "画面収集バーを表示。トリマへ移動して画面ごとに記録してな。"
  }catch(e:Exception){
   bar=null;wm=null;lp=null;title=null;info=null
   "記録バーを表示できません: ${e.javaClass.simpleName}"
  }
 }
 fun hide(){
  service.stopAutomaticOperation()
  restoreHomeTouch()
  val view=bar
  bar=null;lp=null;title=null;info=null;detailsPanel=null
  if(view!=null)try{wm?.removeView(view)}catch(_:Exception){}
  wm=null
 }
 private fun position(){
  val p=lp?:return
  p.gravity=(if(top)Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL
  p.y=dp(125)
  bar?.let{try{wm?.updateViewLayout(it,p)}catch(_:Exception){}}
 }
 private fun refresh(){
  title?.text="⠿ 長押し移動 | ${stages[index].first}"
  info?.text="状態: ${autoStatus.take(115)}"
 }
 private fun sanitized(value:CharSequence?):String{
  return (value?.toString()?:"")
   .replace(Regex("(?i)https?://\\S+"),"[URL]")
   .replace(Regex("\\?utm_[^\\s]+"),"[TRACKING]")
   .replace(Regex("\\b[0-9]{10,}\\b"),"[LONG_NUMBER]")
   .replace("\n"," ").take(160)
 }
 private data class Capture(val count:Int,val nodes:JSONArray)
 private fun inspect(root:AccessibilityNodeInfo):Capture{
  val entries=JSONArray()
  var total=0
  fun walk(node:AccessibilityNodeInfo){
   if(total>=450)return
   total++
   val text=sanitized(node.text)
   val desc=sanitized(node.contentDescription)
   if(total==1||node.isClickable||text.isNotBlank()||desc.isNotBlank()){
    val r=Rect();node.getBoundsInScreen(r)
    entries.put(JSONObject().apply{
     put("index",total);put("class",node.className?.toString()?.substringAfterLast('.').orEmpty())
     put("bounds",r.toShortString());put("clickable",node.isClickable)
     put("enabled",node.isEnabled);put("visible",node.isVisibleToUser)
     put("selected",node.isSelected);put("text",text);put("desc",desc)
     put("viewId",node.viewIdResourceName?.take(100).orEmpty())
    })
   }
   for(i in 0 until node.childCount){
    if(total>=450)break
    node.getChild(i)?.let{walk(it)}
   }
  }
  walk(root)
  return Capture(total,entries)
 }
 /**
  * Capture the rendered Torima app, not only its accessibility nodes.
  * Local screenshot and visual guesses are kept private until manual sharing.
  */
 private fun saveImage(){
  val stage=stages[index].first
  val root=service.rootInActiveWindow
  if(root?.packageName?.toString()!=TARGET){
   info?.text="画像保存はトリマが前面にあるときだけ可能です"
   return
  }
  var count=0
  fun walk(n:AccessibilityNodeInfo){
   if(count>=450)return
   count++
   for(i in 0 until n.childCount){
    if(count>=450)break
    n.getChild(i)?.let{walk(it)}
   }
  }
  walk(root)
  info?.text="画像診断を取得中（$stage／nodes=$count）"
  service.saveVisualScreenshot(stage,count,true){message->
   info?.text=message
  }
 }
 private fun capture(){
  val roots=ArrayList<AccessibilityNodeInfo>()
  service.rootInActiveWindow?.let{
   if(it.packageName?.toString()=="jp.co.incrementp.milemobile")roots.add(it)
  }
  try{
   service.windows?.forEach{w->
    w.root?.let{r->
     if(r.packageName?.toString()=="jp.co.incrementp.milemobile")roots.add(r)
    }
   }
  }catch(_:Exception){}
  val best=roots.map{inspect(it)}.maxByOrNull{it.count}
  if(best==null){
   info?.text="トリマのウィンドウを取得できず。再試行してな。"
   return
  }
  val stage=stages[index]
  try{
   append(service,JSONObject().apply{
    put("label",stage.first);put("intended_action",stage.second)
    put("package","jp.co.incrementp.milemobile")
    put("timestamp",System.currentTimeMillis())
    put("node_count",best.count);put("nodes",best.nodes)
   })
   info?.text="保存OK: ${stage.first} / ${best.count} nodes（累計${count(service)}件）\n自動: $autoStatus"
  }catch(e:Exception){info?.text="保存エラー: ${e.javaClass.simpleName}"}
 }
}
