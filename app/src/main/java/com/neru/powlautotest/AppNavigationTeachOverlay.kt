package com.neru.powlautotest
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*

/** Single consumed position tap registers a named app action. It never presses the target. */
internal class AppNavigationTeachOverlay(private val service:PowlAccessibilityService){
 private val handler=Handler(Looper.getMainLooper())
 private var layer:View?=null
 private var epoch=0
 fun cancel(){
  epoch++;val old=layer;layer=null
  if(old!=null)try{(service.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(old)}catch(_:Exception){}
 }
 fun begin(role:AppNavigationRules.Role,definition:AppNavigationRules.Definition=AppNavigationRules.definition(role)):String {
  cancel()
  if(service.rootInActiveWindow?.packageName?.toString()!="jp.co.incrementp.milemobile")return "トリマの${role.source}画面を開いてから登録してください"
  DiagnosticJournal.record(service,"navigation_teaching","begin",org.json.JSONObject().put("role",role.name).put("name",definition.name).put("screenName",definition.screenName).put("destination",definition.destination))
  val id=epoch
  val manager=service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  val frame=FrameLayout(service).apply{
   setBackgroundColor(Color.TRANSPARENT);isClickable=true
   setOnTouchListener{_,event->
    if(event.actionMasked==MotionEvent.ACTION_UP && layer!=null){
     val x=event.rawX.toInt();val y=event.rawY.toInt()
     cancel();val captureId=epoch
     service.captureNavigationFrame {bitmap,note->
      if(epoch!=captureId){bitmap?.recycle();return@captureNavigationFrame}
      val message=try{
       if(bitmap==null)"登録失敗：$note" else AppNavigationRules.register(service,role,bitmap,x,y,definition)
      }catch(e:Exception){"登録失敗：${e.message}"}finally{bitmap?.recycle()}
      DiagnosticJournal.record(service,"navigation_teaching","result",org.json.JSONObject().put("role",role.name).put("message",message))
      service.reportNavigationTeaching(message)
     }
    };true
   }
  }
  val cancelButton=Button(service).apply{text="登録を中止";setOnClickListener{cancel();service.reportNavigationTeaching("アプリ内操作の登録を中止")}}
  frame.addView(cancelButton,FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
  return try{
   manager.addView(frame,WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT))
   layer=frame
   handler.postDelayed({if(epoch==id)cancel()},20000L)
   "アプリ内：${definition.name}（${definition.screenName}）の位置を1回選んで登録（実画面は押しません）。広告の×は別の学習を選んでください"
  }catch(e:Exception){"登録画面を表示できません：${e.javaClass.simpleName}"}
 }
}
