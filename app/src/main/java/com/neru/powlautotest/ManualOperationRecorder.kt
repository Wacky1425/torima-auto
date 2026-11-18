package com.neru.powlautotest

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors

/** Passive application notifications + bounded screenshots. Never intercepts or injects touches. */
internal class ManualOperationRecorder(private val service:PowlAccessibilityService,private val status:(String)->Unit){
 private val h=Handler(Looper.getMainLooper())
 private val io=Executors.newSingleThreadExecutor()
 var active=false
  private set
 var saving=false
  private set
 val busy get()=active || saving
 private var token=0
 private var budgetStart=0L
 private var started=0L
 private var startedAt=0L
 private var epoch=0
 private var onTarget=false
 private var capturing=false
 private var finalizing=false
 private var folder:File?=null
 private var events=JSONArray()
 private var frames=JSONArray()
 private val timings=ArrayList<ManualRecordingPolicy.Frame>()
 private var lastScrollKey="";private var lastScrollAt=0L
 private var coalesced=0;private var failures=0
 private var latestFailure=""
 private var checkpointPosted=false
 private val checkpoint=Runnable{checkpointPosted=false;if(active || saving)persist()}
 private fun now()=SystemClock.uptimeMillis()
 private fun foreground()=if(runCatching{service.manualRecordingRoot()}.getOrNull()!=null)ManualRecordingPolicy.TARGET else ""
 private fun text(value:CharSequence?):String=value?.toString().orEmpty()
  .replace(Regex("https?://\\S+",RegexOption.IGNORE_CASE),"[URL]")
  .replace(Regex("\\b[0-9]{10,}\\b"),"[LONG_NUMBER]").replace('\n',' ').take(100)
 private fun metadata(state:String,reason:String=""):String {
  val linked=JSONArray()
  for(i in 0 until events.length()){
   val event=JSONObject(events.getJSONObject(i).toString())
   val pair=ManualRecordingPolicy.pair(timings,event.optLong("uptime"),event.optInt("epoch"))
   event.put("beforeFrame",pair.before?:JSONObject.NULL).put("afterFrame",pair.after?:JSONObject.NULL)
   if(pair.before==null)event.put("beforeUnavailable",true)
   if(pair.after==null)event.put("afterUnavailable",true)
   linked.put(event)
  }
  return JSONObject().put("formatVersion",1).put("startedAt",startedAt)
   .put("startUptime",started).put("savedAt",System.currentTimeMillis())
   .put("status",state).put("reason",reason).put("events",linked).put("frames",frames)
   .put("coordinateSpace","node bounds: display coordinates; screenshots: application window with windowBoundsAtRequest")
   .put("rawFingerCoordinatesAvailable",false).put("inputSourceConfirmed",false)
   .put("automaticOperationPaused",true).put("automaticLearning",false)
   .put("captureFailures",failures).put("latestCaptureFailure",latestFailure)
   .put("coalescedScrollNotifications",coalesced)
   .put("framePairing","same foreground epoch; before received strictly before event; after requested strictly after event; max age 4s")
   .put("screenChangeDoesNotConfirmReward",true).toString()
 }
 private fun scheduleCheckpoint(){
  if(checkpointPosted || finalizing)return
  checkpointPosted=true;h.postDelayed(checkpoint,1000)
 }
 private fun persist(){
  if(finalizing)return
  val dir=folder?:return
  val json=metadata("RECORDING")
  io.execute{runCatching{ManualRecordingStore.writeMeta(dir,json)}}
 }
 private fun publish(){
  if(saving){status("手動記録を保存中…");return}
  val mode=if(!onTarget)"トリマ外のため一時停止" else if(frames.length()>=ManualRecordingPolicy.MAX_FRAMES)"画像は上限・操作情報は記録中" else if(frames.length()==0 && failures>0)
   "画像未取得・操作情報は記録中" else "普段どおり操作してください"
  status("● 手動記録中 ${events.length()}件・画像${frames.length()}枚／$mode")
 }
 fun start():String {
  if(busy)return if(saving)"手動記録を保存中です" else "手動記録はすでに開始しています"
  if(foreground()!=ManualRecordingPolicy.TARGET)return "記録したいトリマの画面を開いてください"
  token++;started=now();budgetStart=SystemClock.elapsedRealtime();startedAt=System.currentTimeMillis();epoch=1;onTarget=true
  events=JSONArray();frames=JSONArray();timings.clear();capturing=false;finalizing=false
  coalesced=0;failures=0;latestFailure="";lastScrollKey="";lastScrollAt=0L
  active=true
  try{folder=ManualRecordingStore.begin(service,metadata("RECORDING"))}catch(_:Exception){active=false;return "手動記録の保存先を作成できませんでした"}
  h.post(poll);publish()
  return "● 手動記録を開始。普段どおり操作し、終わったら「手動記録を保存」。最大3分／位置はボタン範囲として記録"
 }
 private fun presence(target:Boolean){
  if(onTarget==target)return
  epoch++;onTarget=target
  events.put(JSONObject().put("type",if(target)"returned_to_torima" else "left_torima")
   .put("uptime",now()).put("epoch",epoch).put("externalAppContentsRecorded",false))
  scheduleCheckpoint();publish()
 }
 fun onEvent(e:AccessibilityEvent){
  if(!active)return
  val kind=when(e.eventType){
   AccessibilityEvent.TYPE_VIEW_CLICKED->"click_notification"
   AccessibilityEvent.TYPE_VIEW_LONG_CLICKED->"long_click_notification"
   AccessibilityEvent.TYPE_VIEW_SCROLLED->"scroll_notification"
   AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED->"window_notification"
   else->return
  }
  if(e.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
   val p=e.packageName?.toString().orEmpty()
   if(p.isNotBlank() && p!=ManualRecordingPolicy.TARGET && p!=service.packageName){presence(false);return}
  }
  val top=foreground();presence(top==ManualRecordingPolicy.TARGET)
  if(!ManualRecordingPolicy.accepts(e.packageName?.toString().orEmpty(),top))return
  if(ManualRecordingPolicy.limit(SystemClock.elapsedRealtime()-budgetStart,events.length())){stop("記録上限に達しました");return}
  val source=e.source;val rect=Rect();source?.getBoundsInScreen(rect)
  val id=source?.viewIdResourceName.orEmpty();val key="$kind|$id|${rect.toShortString()}"
  val stamp=if(e.eventTime>0L)minOf(now(),e.eventTime) else now()
  if(stamp<started)return
  if(kind=="scroll_notification"){
   if(ManualRecordingPolicy.coalesceScroll(key==lastScrollKey,lastScrollAt,stamp)){coalesced++;return}
   lastScrollKey=key;lastScrollAt=stamp
  }
  val redact=e.isPassword || source?.isPassword==true || source?.isEditable==true
  events.put(JSONObject().put("type",kind).put("uptime",stamp).put("epoch",epoch)
   .put("eventEvidence","application accessibility notification during manual-only session")
   .put("viewId",id.take(180)).put("class",source?.className?.toString().orEmpty().take(100))
   .put("label",if(redact)"[REDACTED]" else text(source?.text))
   .put("description",if(redact)"[REDACTED]" else text(source?.contentDescription))
   .put("bounds",if(source!=null && !rect.isEmpty)JSONArray(listOf(rect.left,rect.top,rect.right,rect.bottom)) else JSONObject.NULL)
   .put("positionEvidence",if(source!=null && !rect.isEmpty)"node bounds; not a finger coordinate" else "unavailable")
   .put("scrollX",e.scrollX).put("scrollY",e.scrollY).put("maxScrollX",e.maxScrollX).put("maxScrollY",e.maxScrollY)
   .put("fromIndex",e.fromIndex).put("toIndex",e.toIndex))
  scheduleCheckpoint();publish()
  h.removeCallbacks(eventPhoto);h.postDelayed(eventPhoto,450)
 }
 private val eventPhoto=Runnable{if(active)capture()}
 private val poll=object:Runnable{override fun run(){
  if(!active)return
  presence(foreground()==ManualRecordingPolicy.TARGET)
  if(ManualRecordingPolicy.limit(SystemClock.elapsedRealtime()-budgetStart,events.length())){stop("記録上限に達したため保存しました");return}
  capture();h.postDelayed(this,ManualRecordingPolicy.SAMPLE_MS)
 }}
 private fun ui():JSONObject {
  val root=service.manualRecordingRoot()
  val items=JSONArray();var scanned=0
  fun scan(node:android.view.accessibility.AccessibilityNodeInfo){
   if(scanned++>=180)return
   if(node.isVisibleToUser && items.length()<45 && (node.isClickable || node.isScrollable)){
    val r=Rect();node.getBoundsInScreen(r);val secret=node.isPassword || node.isEditable
    items.put(JSONObject().put("bounds",JSONArray(listOf(r.left,r.top,r.right,r.bottom)))
     .put("viewId",node.viewIdResourceName.orEmpty().take(180)).put("enabled",node.isEnabled)
     .put("label",if(secret)"[REDACTED]" else text(node.text))
     .put("description",if(secret)"[REDACTED]" else text(node.contentDescription)))
   }
   for(i in 0 until node.childCount){if(scanned>=180)break;node.getChild(i)?.let{scan(it)}}
  }
  if(root?.packageName?.toString()==ManualRecordingPolicy.TARGET)scan(root)
  return JSONObject().put("observedUptime",now()).put("mainScreen",MainScreenReader.read(root).screen.name).put("controls",items)
 }
 private fun capture(done:(()->Unit)?=null){
  if(capturing || frames.length()>=ManualRecordingPolicy.MAX_FRAMES || !onTarget || foreground()!=ManualRecordingPolicy.TARGET){done?.invoke();return}
  val dir=folder?:run{done?.invoke();return}
  capturing=true
  val expected=token;val sourceEpoch=epoch;val requested=now();val id=frames.length()+1
  val windowBounds=Rect()
  runCatching{service.manualRecordingRoot()?.let{root->
   val window=service.windows.orEmpty().firstOrNull{it.id==root.windowId}
   if(window!=null)window.getBoundsInScreen(windowBounds) else root.getBoundsInScreen(windowBounds)
  }}
  service.captureManualRecordingFrame{bitmap,note->
   if(token!=expected || (!active && !saving)){bitmap?.recycle();return@captureManualRecordingFrame}
   val received=now()
   if(bitmap==null || sourceEpoch!=epoch || foreground()!=ManualRecordingPolicy.TARGET){
    bitmap?.recycle();capturing=false;failures++;latestFailure=note.ifBlank{"撮影中にトリマ外へ移動"}
    if(active){scheduleCheckpoint();publish()};done?.invoke();return@captureManualRecordingFrame
   }
   val state=runCatching{ui()}.getOrElse{JSONObject().put("unavailable",true)}
   io.execute{
    val info=try{encode(dir,id,bitmap)}catch(_:Exception){null}finally{bitmap.recycle()}
    h.post{
     if(token!=expected || (!active && !saving))return@post
     capturing=false
     if(info!=null){
      frames.put(info.put("id",id).put("requestedUptime",requested).put("receivedUptime",received)
       .put("epoch",sourceEpoch).put("controlsObservedAfterCapture",state)
       .put("windowBoundsAtRequest",JSONArray(listOf(windowBounds.left,windowBounds.top,windowBounds.right,windowBounds.bottom))))
      timings.add(ManualRecordingPolicy.Frame(id,requested,received,sourceEpoch))
     }else{failures++;latestFailure="画像を保存できませんでした"}
     if(active){scheduleCheckpoint();publish()};done?.invoke()
    }
   }
  }
 }
 private fun encode(dir:File,id:Int,source:Bitmap):JSONObject {
  for((width,quality) in listOf(720 to 80,540 to 74,360 to 65)){
   val w=minOf(width,source.width);val small=Bitmap.createScaledBitmap(source,w,maxOf(1,source.height*w/source.width),true)
   val bytes=try{ByteArrayOutputStream().use{check(small.compress(Bitmap.CompressFormat.JPEG,quality,it));it.toByteArray()}}
    finally{if(small!==source)small.recycle()}
   if(bytes.size<=256_000){
    ManualRecordingStore.writeFrame(dir,id,bytes)
    return JSONObject().put("file","frame-%04d.jpg".format(id)).put("width",w).put("height",source.height*w/source.width)
     .put("sourceWidth",source.width).put("sourceHeight",source.height)
   }
  }
  error("画像サイズ上限")
 }
 fun close(){
  val unfinished=busy
  active=false;saving=false;finalizing=true;token++
  h.removeCallbacksAndMessages(null)
  val dir=folder
  if(unfinished && dir!=null){
   val json=metadata("INTERRUPTED","サービス終了。取得済みの情報を保存")
   io.execute{runCatching{ManualRecordingStore.writeMeta(dir,json)}}
  }
  io.shutdown()
 }
 fun stop(reason:String="ユーザーが手動記録を保存",finalPhoto:Boolean=true):String {
  if(!active)return if(saving)"手動記録を保存中です" else "手動記録は停止しています"
  active=false;saving=true;h.removeCallbacks(poll);h.removeCallbacks(eventPhoto)
  status("手動記録を保存中…")
  val requested=now();val expected=token
  fun finish(){
   if(finalizing || token!=expected)return
   finalizing=true;h.removeCallbacks(checkpoint);checkpointPosted=false
   val dir=folder?:return
   val json=metadata("FINISHED",reason)
   io.execute{
    val ok=runCatching{ManualRecordingStore.writeMeta(dir,json)}.isSuccess
    h.post{if(token==expected){saving=false
     status(if(ok)"手動記録を保存：${events.length()}件・画像${frames.length()}枚。「Driveに診断を送る」で共有できます"
      else "手動記録の最終保存に失敗。途中記録は診断に残っています")}}
   }
  }
  fun waitForLast(){
   if(finalizing || token!=expected)return
   if(!finalPhoto || now()-requested>4500L){finish();return}
   if(capturing){h.postDelayed({waitForLast()},150);return}
   presence(foreground()==ManualRecordingPolicy.TARGET)
   capture{finish()}
  }
  waitForLast()
  return "手動記録を保存中…"
 }
}
