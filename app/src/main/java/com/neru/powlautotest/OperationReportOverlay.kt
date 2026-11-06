package com.neru.powlautotest

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Select a position, then let the person operate Torima and request the after photo. */
internal class OperationReportOverlay(private val service:PowlAccessibilityService){
 private val h=Handler(Looper.getMainLooper())
 private val wm get()=service.getSystemService(WindowManager::class.java)
 private var layer:View?=null
 private var before:Bitmap?=null
 private var epoch=0
 var active=false
  private set
 private fun isTorima()=service.rootInActiveWindow?.packageName?.toString()=="jp.co.incrementp.milemobile"
 private fun remove(){
  val view=layer?:return
  layer=null
  runCatching{wm?.removeView(view)}
 }
 fun cancel(){
  if(!active)return
  active=false;epoch++;remove()
  before?.recycle();before=null
  service.resumeTeachingBar()
 }
 private fun fail(message:String){cancel();service.reportNavigationTeaching(message)}
 private fun show(view:View,width:Int,height:Int,gravity:Int):Boolean=try{
  val manager=checkNotNull(wm)
  manager.addView(view,WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{this.gravity=gravity})
  layer=view;true
 }catch(_:Exception){false}

 fun begin(memo:String){
  cancel()
  if(!isTorima()){service.reportNavigationTeaching("報告したいトリマの画面を開いてください");return}
  active=true
  val id=++epoch
  if(!service.suspendTeachingBar()){fail("操作バーを隠せませんでした。もう一度お試しください");return}
  var selecting=false
  val picker=FrameLayout(service).apply{
   setBackgroundColor(Color.TRANSPARENT)
   setOnTouchListener{_,event->
    if(event.actionMasked==MotionEvent.ACTION_UP && !selecting && epoch==id){
     selecting=true
     val x=event.rawX.toInt();val y=event.rawY.toInt()
     getChildAt(0).visibility=View.INVISIBLE
     service.captureNavigationFrame{image,note->
      if(epoch!=id){image?.recycle();return@captureNavigationFrame}
      if(image==null || !isTorima() || x !in 0 until image.width || y !in 0 until image.height){
       image?.recycle();fail("操作前の記録ができませんでした：$note");return@captureNavigationFrame
      }
      before=image;remove()
      showAfterControls(id,memo,x,y)
     }
    }
    true
   }
   addView(Button(service).apply{
    text="報告を中止";setOnClickListener{fail("操作の報告を中止しました")}
   },FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.CENTER_HORIZONTAL))
  }
  if(!show(picker,-1,-1,Gravity.TOP)){fail("報告画面を表示できませんでした");return}
  service.reportNavigationTeaching("1/2：報告するボタンの位置を選んでください。まだ実画面は押しません")
  h.postDelayed({if(epoch==id && active)fail("操作報告の時間切れ。もう一度お試しください")},90_000L)
 }
 private fun showAfterControls(id:Int,memo:String,x:Int,y:Int){
  val controls=LinearLayout(service).apply{
   addView(Button(service).apply{
    text="操作後を保存";setOnClickListener{
     if(!isTorima()){
      service.reportNavigationTeaching("トリマの操作後の画面を開いてから保存してください")
     }else{
      remove()
      service.captureNavigationFrame{image,note->
       if(epoch!=id){image?.recycle();return@captureNavigationFrame}
       if(image==null || !isTorima()){
        image?.recycle();fail("操作後の記録ができませんでした：$note");return@captureNavigationFrame
       }
       val source=checkNotNull(before)
       before=null
       Thread{
        val saved=try{OperationReportStore.save(service,memo,x,y,source,image);true}
         catch(_:Exception){false}finally{source.recycle();image.recycle()}
        h.post{
         if(epoch==id){
          cancel()
          if(saved)service.offerOperationReportSend()
          else service.reportNavigationTeaching("操作報告を保存できませんでした")
         }
        }
       }.start()
      }
     }
    }
   })
   addView(Button(service).apply{text="中止";setOnClickListener{fail("操作の報告を中止しました")}})
  }
  val edge=if(y < (before?.height?:0)/2)Gravity.BOTTOM else Gravity.TOP
  if(!show(controls,-2,-2,edge or Gravity.CENTER_HORIZONTAL)){
   fail("操作後の保存ボタンを表示できませんでした");return
  }
  service.reportNavigationTeaching("2/2：同じボタンを実際に押し、画面が落ち着いたら「操作後を保存」")
 }
}
