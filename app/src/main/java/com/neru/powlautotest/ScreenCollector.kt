package com.neru.powlautotest

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
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
 private var title:TextView?=null
 private var info:TextView?=null
 private var autoStatus="停止中"
 fun updateAutoStatus(text:String){
  autoStatus=text
  info?.text="自動: $autoStatus\n保存 ${count(service)} 件"
 }
 private fun dp(n:Int)=(n*service.resources.displayMetrics.density+0.5f).toInt()
 private fun control(label:String,action:()->Unit)=TextView(service).apply{
  text=label
  textSize=14f
  setTextColor(Color.WHITE)
  setPadding(dp(12),dp(8),dp(12),dp(8))
  setOnClickListener{action()}
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
    textSize=11f;setTextColor(Color.WHITE);maxLines=2
   }
   panel.addView(heading);title=heading
   val controls=LinearLayout(service).apply{
    orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
   }
   controls.addView(control("◀"){index=(index+stages.size-1)%stages.size;refresh()})
   controls.addView(control("記録"){capture()})
   controls.addView(control("▶"){index=(index+1)%stages.size;refresh()})
   controls.addView(control("上下"){top=!top;position()})
   controls.addView(control("終了"){hide()})
   panel.addView(controls)
   val autoControls=LinearLayout(service).apply{
    orientation=LinearLayout.HORIZONTAL
    gravity=Gravity.CENTER_VERTICAL
   }
   autoControls.addView(control("▶ 開始"){updateAutoStatus(service.startAutomaticOperation())})
   autoControls.addView(control("■ 停止"){updateAutoStatus(service.stopAutomaticOperation())})
   panel.addView(autoControls)
   val status=TextView(service).apply{
    textSize=11f;setTextColor(Color.rgb(210,225,240));maxLines=2
   }
   panel.addView(status);info=status
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
   refresh()
   "画面収集バーを表示。トリマへ移動して画面ごとに記録してな。"
  }catch(e:Exception){
   bar=null;wm=null;lp=null;title=null;info=null
   "記録バーを表示できません: ${e.javaClass.simpleName}"
  }
 }
 fun hide(){
  service.stopAutomaticOperation()
  val view=bar
  bar=null;lp=null;title=null;info=null
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
  title?.text="${stages[index].first} → ${stages[index].second}"
  info?.text="自動: $autoStatus\n保存 ${count(service)} 件（記録は手動）"
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
