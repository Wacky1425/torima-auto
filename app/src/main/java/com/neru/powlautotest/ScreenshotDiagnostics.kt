package com.neru.powlautotest

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.Display
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Private, bounded visual diagnostics of Torima only. No cloud upload,
 * no MediaProjection, no storage permission and no image-driven auto tapping.
 */
internal class ScreenshotDiagnostics(private val service:PowlAccessibilityService){
 companion object{
  private const val TARGET="jp.co.incrementp.milemobile"
  private const val FOLDER="visual_diagnostics"
  private const val MAX_IMAGES=24
  const val MAX_SAVED_IMAGES=24
  private fun folder(ctx:Context)=File(ctx.cacheDir,FOLDER).also{it.mkdirs()}
  private fun index(ctx:Context)=File(folder(ctx),"index.json")
  private fun entries(ctx:Context):JSONArray=try{
   JSONArray(index(ctx).takeIf{it.exists()}?.readText(Charsets.UTF_8)?:"[]")
  }catch(_:Exception){JSONArray()}
  fun count(ctx:Context):Int=entries(ctx).length()
  /** Insert all retained images into a single diagnostic report ZIP. */
  fun appendToDiagnosticZip(ctx:Context,zip:ZipOutputStream):Int{
   val records=entries(ctx)
   val dir=folder(ctx)
   zip.putNextEntry(ZipEntry("screenshots/index.json"))
   zip.write(records.toString(2).toByteArray(Charsets.UTF_8))
   zip.closeEntry()
   var included=0
   for(i in 0 until records.length()){
    val name=records.optJSONObject(i)?.optString("file").orEmpty()
    if(!Regex("""screen_[0-9]+[.]jpg""").matches(name))continue
    val source=File(dir,name)
    if(!source.isFile)continue
    zip.putNextEntry(ZipEntry("screenshots/$name"))
    source.inputStream().use{it.copyTo(zip)}
    zip.closeEntry()
    included++
   }
   return included
  }

  fun exportInfo(ctx:Context):String=JSONObject().apply{
   put("note","Images remain private until manually shared; detections are visual hypotheses")
   put("images",entries(ctx))
  }.toString(2)
  /** Called only by the user-facing share control. */
  fun createShareZip(ctx:Context):File?{
   val records=entries(ctx)
   if(records.length()==0)return null
   val dir=folder(ctx)
   val result=File(dir,"torima-visual-diagnostics.zip")
   ZipOutputStream(FileOutputStream(result)).use{zip->
    zip.putNextEntry(ZipEntry("index.json"))
    zip.write(records.toString(2).toByteArray(Charsets.UTF_8))
    zip.closeEntry()
    for(i in 0 until records.length()){
     val info=records.optJSONObject(i)?:continue
     val name=info.optString("file")
     if(!Regex("""screen_[0-9]+[.]jpg""").matches(name))continue
     val source=File(dir,name)
     if(!source.isFile)continue
     zip.putNextEntry(ZipEntry(name))
     source.inputStream().use{it.copyTo(zip)}
     zip.closeEntry()
    }
   }
   return result
  }
  fun clear(ctx:Context){
   val dir=folder(ctx)
   dir.listFiles()?.filter{it.name.startsWith("screen_") ||
    it.name=="torima-visual-diagnostics.zip" || it.name=="index.json"}?.forEach{it.delete()}
  }
 }
 private val io=Executors.newSingleThreadExecutor()
 private val captureSchedule=DiagnosticCaptureSchedule()
 private val captureHandler=android.os.Handler(android.os.Looper.getMainLooper())
 private var busy=false
 private var lastRequest=0L
 private var lastProbeRequest=0L
 private var badgeProbeBusy=false
 private var badgeProbeAt=0L

 /**
  * Probe the two tiny header red dots from the FULL DISPLAY, not a cropped
  * application window (whose origin may omit the system status bar).
  * Only runs on a verified Torima foreground. Pixels live in RAM.
  */
 fun probeRewardBadges(done:(RewardBadgeDetector.Result?)->Unit){
  val root=service.rootInActiveWindow
  if(root?.packageName?.toString()!=TARGET || Build.VERSION.SDK_INT<30){
   done(null);return
  }
  val now=SystemClock.elapsedRealtime()
  if(!captureSchedule.allowProbe() || badgeProbeBusy || busy || now-badgeProbeAt<1_100L){
   done(null);return
  }
  badgeProbeBusy=true
  badgeProbeAt=now
  fun finish(image:Bitmap?){
   if(image==null){
    badgeProbeBusy=false
    done(null)
    return
   }
   io.execute{
    val result=try{
     val w=540
     val h=(image.height.toLong()*w/image.width).toInt()
     val scaled=Bitmap.createScaledBitmap(image,w,h,true)
     try{
      val pixels=IntArray(w*h)
      scaled.getPixels(pixels,0,w,0,0,w,h)
      RewardBadgeDetector.detect(pixels,w,h)
     }finally{if(scaled!==image)scaled.recycle()}
    }catch(_:Exception){null}
    finally{image.recycle()}
    service.mainExecutor.execute{
     badgeProbeBusy=false
     done(result)
    }
   }
  }
  try{
   service.takeScreenshot(Display.DEFAULT_DISPLAY,service.mainExecutor,
    object:AccessibilityService.TakeScreenshotCallback{
     override fun onSuccess(result:AccessibilityService.ScreenshotResult){
      finish(toSoftwareBitmap(result))
     }
     override fun onFailure(errorCode:Int){finish(null)}
    })
  }catch(_:Exception){finish(null)}
 }


 /** Scan overlay marks in RAM. No screenshots are persisted by this path. */
 fun probeOverlay(done:(List<OverlayIconDetector.Marker>?,VisualLearnedActions.Match?)->Unit){
  val root=service.rootInActiveWindow
  if(root?.packageName?.toString()!=TARGET || Build.VERSION.SDK_INT<30){done(null,null);return}
  val now=SystemClock.elapsedRealtime()
  if(!captureSchedule.allowProbe() || busy || now-lastProbeRequest<1_300L ||
   (lastRequest>0 && now-lastRequest<1_300L)){done(null,null);return}
  busy=true;lastProbeRequest=now
  val windowId=root.windowId
  fun finish(image:Bitmap?){
   if(image==null){busy=false;done(null,null);return}
   io.execute{
    val analysis=try{
     val width=540
     val height=(image.height.toLong()*width/image.width).toInt()
     val small=Bitmap.createScaledBitmap(image,width,height,true)
     val pixels=IntArray(width*height)
     small.getPixels(pixels,0,width,0,0,width,height)
     if(small!==image)small.recycle()
     val fromNodesOrComponents=OverlayIconDetector.detect(pixels,width,height)
     val pixelX=PixelAdCloseDetector.detectWhiteX(pixels,width,height)
      ?.let{OverlayIconDetector.Marker("X",it.x,it.y,it.score,"top-right")}
     val markers=(fromNodesOrComponents+listOfNotNull(pixelX))
      .groupBy{it.kind}.values.mapNotNull{group->group.maxByOrNull{it.score}}
      .map{it.copy(x=it.x*image.width/width,y=it.y*image.height/height)}
     val learned=service.matchVisualLearning(pixels,width,height)?.let{
      it.copy(x=it.x*image.width/width,y=it.y*image.height/height)
     }
     Pair(markers,learned)
    }catch(_:Exception){null}
    finally{image.recycle()}
    service.mainExecutor.execute{
     busy=false
     done(analysis?.first,analysis?.second)
    }
   }
  }
  fun display(){
   try{service.takeScreenshot(Display.DEFAULT_DISPLAY,service.mainExecutor,
    object:AccessibilityService.TakeScreenshotCallback{
     override fun onSuccess(result:AccessibilityService.ScreenshotResult){
      finish(toSoftwareBitmap(result))
     }
     override fun onFailure(errorCode:Int){finish(null)}
    })}catch(_:Exception){finish(null)}
  }
  // Teaching source uses full-display screenshots. Keep the same coordinate
  // system when matching a taught visual rule, including system-bar offsets.
  if(VisualLearnedActions.count(service)>0)display()
  else if(Build.VERSION.SDK_INT>=34 && windowId>=0){
   try{service.takeScreenshotOfWindow(windowId,service.mainExecutor,
    object:AccessibilityService.TakeScreenshotCallback{
     override fun onSuccess(result:AccessibilityService.ScreenshotResult){
      finish(toSoftwareBitmap(result))
     }
     override fun onFailure(errorCode:Int){
      if(service.rootInActiveWindow?.packageName?.toString()==TARGET)display()
      else finish(null)
     }
    })}catch(_:Exception){display()}
  }else display()
 }


 /**
  * Teaching uses the live source frame *before* its touch is injected.
  * This is independent of ordinary diagnostic screenshot rate limits.
  * The caller must hide its teaching overlay and collector panel first.
  */
 fun captureForVisualTeaching(done:(Bitmap?,String)->Unit){
  val root=service.rootInActiveWindow
  if(root?.packageName?.toString()!=TARGET){
   done(null,"学習元がトリマではないため画像を取得できません");return
  }
  if(Build.VERSION.SDK_INT<30){
   done(null,"端末のAndroidが画像取得APIに非対応");return
  }
  val now=SystemClock.elapsedRealtime()
  if(busy){
   done(null,"ほかの画像解析中。数秒後にもう一度学習してください");return
  }
  busy=true
  try{
   service.takeScreenshot(Display.DEFAULT_DISPLAY,service.mainExecutor,
    object:AccessibilityService.TakeScreenshotCallback{
     override fun onSuccess(screenshot:AccessibilityService.ScreenshotResult){
      busy=false
      val image=toSoftwareBitmap(screenshot)
      done(image,if(image==null)"画像バッファ取得失敗" else "タップ前の画面画像を取得")
     }
     override fun onFailure(errorCode:Int){
      busy=false
      done(null,"画面取得が拒否されました(code=$errorCode)")
     }
    })
  }catch(e:Exception){
   busy=false
   done(null,"画像取得例外：${e.javaClass.simpleName}")
  }
 }

 fun capture(stage:String,nodeCount:Int,manual:Boolean,done:(String)->Unit){
  if(!captureSchedule.request(SystemClock.elapsedRealtime())){
   done("診断撮影を予約済み。現在の画像処理が終わるまで待機")
   return
  }
  DiagnosticJournal.record(service,"screenshots","requested",JSONObject().put("screenStage",stage))
  fun attempt(){
   val now=SystemClock.elapsedRealtime()
   if(captureSchedule.timedOut(now)){
    captureSchedule.finish()
    DiagnosticJournal.record(service,"screenshots","queue_timeout",JSONObject().put("busy",busy).put("screenStage",stage))
    done("診断撮影を15秒待っても開始できませんでした。撮影診断に記録しました")
    return
   }
   if(!captureSchedule.canCapture(busy,now,maxOf(lastRequest,lastProbeRequest,badgeProbeAt))){
    captureHandler.postDelayed({attempt()},250L)
    return
   }
   // Keep the reservation until completion so live probes cannot take the next slot.
   captureNow(stage,nodeCount,manual){message->
    captureSchedule.finish()
    DiagnosticJournal.record(service,"screenshots","completed",JSONObject().put("screenStage",stage).put("result",message))
    done(message)
   }
  }
  attempt()
 }
 private fun captureNow(stage:String,nodeCount:Int,manual:Boolean,done:(String)->Unit){
  val root=service.rootInActiveWindow
  if(root?.packageName?.toString()!=TARGET){
   done("トリマが前面にないため、画像は取得していません")
   return
  }
  if(Build.VERSION.SDK_INT<30){
   done("この端末は画像取得API（Android 11以上）に非対応")
   return
  }
  val now=SystemClock.elapsedRealtime()
  if(busy || (lastRequest>0 && now-lastRequest<2_000L)){
   done("画像取得中／連続撮影の間隔が短いため待機")
   return
  }
  busy=true
  lastRequest=now
  val windowId=root.windowId
  var delivered=false
  fun deliver(message:String){
   if(delivered)return
   delivered=true;busy=false
   done(message)
  }
  captureHandler.postDelayed({
   if(!delivered)deliver("診断撮影が15秒以内に完了せず終了。遅延した結果は次の撮影を上書きしません")
  },15_000L)
  fun onResult(error:String?=null,image:Bitmap?=null){
   if(delivered){image?.recycle();return}
   if(image==null){
    deliver(error?:"画像を取得できませんでした")
    return
   }
   io.execute{
    val result=try{store(image,stage,nodeCount,manual)}
     catch(e:Exception){"画像の解析・保存に失敗：${e.javaClass.simpleName}"}
    finally{image.recycle()}
    service.mainExecutor.execute{
     deliver(result)
    }
   }
  }
  fun requestDisplay(){
   try{
    service.takeScreenshot(Display.DEFAULT_DISPLAY,service.mainExecutor,
     object:AccessibilityService.TakeScreenshotCallback{
      override fun onSuccess(screenshot:AccessibilityService.ScreenshotResult){
       onResult(image=toSoftwareBitmap(screenshot))
      }
      override fun onFailure(errorCode:Int){
       onResult(error="画面取得に失敗（code=$errorCode）。保護された画面の可能性あり")
      }
     })
   }catch(e:Exception){onResult(error="画面取得を開始できません：${e.javaClass.simpleName}")}
  }
  // On Android 14+ capture the app window, not the accessibility overlay.
  if(Build.VERSION.SDK_INT>=34 && windowId>=0){
   try{
    service.takeScreenshotOfWindow(windowId,service.mainExecutor,
     object:AccessibilityService.TakeScreenshotCallback{
      override fun onSuccess(screenshot:AccessibilityService.ScreenshotResult){
       onResult(image=toSoftwareBitmap(screenshot))
      }
      override fun onFailure(errorCode:Int){
       if(service.rootInActiveWindow?.packageName?.toString()==TARGET)requestDisplay()
       else onResult(error="トリマ以外の画面に移動したため撮影を中止")
      }
     })
   }catch(_:Exception){requestDisplay()}
  }else requestDisplay()
 }

 private fun toSoftwareBitmap(screenshot:AccessibilityService.ScreenshotResult):Bitmap?{
  val buffer=screenshot.hardwareBuffer
  return try{
   Bitmap.wrapHardwareBuffer(buffer,screenshot.colorSpace)
    ?.copy(Bitmap.Config.ARGB_8888,false)
  }catch(_:Exception){null}
  finally{buffer.close()}
 }
 private fun store(image:Bitmap,stage:String,nodeCount:Int,manual:Boolean):String{
  // Sample one downscaled frame once. Analyze fixed corner/lower zones
  // without repeatedly calling Bitmap.getPixel on the full-resolution image.
  val scanWidth=360
  val scanHeight=(image.height.toLong()*scanWidth/image.width).toInt()
  val sampled=Bitmap.createScaledBitmap(image,scanWidth,scanHeight,true)
  val pixels=IntArray(scanWidth*scanHeight)
  sampled.getPixels(pixels,0,scanWidth,0,0,scanWidth,scanHeight)
  if(sampled!==image)sampled.recycle()
  val results=VisualCloseDetector.detect(pixels,scanWidth,scanHeight)
  // Store the same 540px pixel-X result that feeds automatic screen actions.
  val xWidth=540
  val xHeight=(image.height.toLong()*xWidth/image.width).toInt()
  val xImage=Bitmap.createScaledBitmap(image,xWidth,xHeight,true)
  val xPixels=IntArray(xWidth*xHeight)
  xImage.getPixels(xPixels,0,xWidth,0,0,xWidth,xHeight)
  if(xImage!==image)xImage.recycle()
  val pixelX=PixelAdCloseDetector.detectWhiteX(xPixels,xWidth,xHeight)
  val dir=folder(service)
  val now=System.currentTimeMillis()
  val filename="screen_${now}.jpg"
  val file=File(dir,filename)
  val output=if(image.width>1080)
   Bitmap.createScaledBitmap(image,1080,(image.height.toLong()*1080/image.width).toInt(),true)
  else image
  try{
   FileOutputStream(file).use{stream->
    if(!output.compress(Bitmap.CompressFormat.JPEG,86,stream))
     throw IllegalStateException("JPEG compress failed")
   }
  }finally{if(output!==image)output.recycle()}
  val old=entries(service)
  val newIndex=JSONArray()
  for(i in 0 until old.length())newIndex.put(old.get(i))
  newIndex.put(JSONObject().apply{
   put("file",filename);put("at",now)
   put("stage",stage.take(65));put("manual",manual)
   put("nodes",nodeCount);put("width",image.width);put("height",image.height)
   put("xCandidatesAreNotConfirmedButtons",true)
   put("livePixelX",if(pixelX!=null)JSONObject().apply{
    put("x",pixelX.x*image.width/xWidth)
    put("y",pixelX.y*image.height/xHeight)
    put("score",pixelX.score)
    put("requiresMultipleLiveFrames",true)
   } else JSONObject.NULL)
   put("candidates",JSONArray().also{arr->
    for(c in results)arr.put(JSONObject().apply{
     put("x",c.x*image.width/scanWidth)
     put("y",c.y*image.height/scanHeight)
     put("radius",c.radius*image.width/scanWidth)
     put("shapeScore",c.score)
     put("region",c.region)
    })
   })
  })
  while(newIndex.length()>MAX_IMAGES){
   val removed=newIndex.optJSONObject(0)?.optString("file")
   if(removed!=null && Regex("""screen_[0-9]+[.]jpg""").matches(removed))
    File(dir,removed).delete()
   newIndex.remove(0)
  }
  index(service).writeText(newIndex.toString(),Charsets.UTF_8)
  return "画像保存：$filename（nodes=$nodeCount／従来候補${results.size}件／画像から右上×=${pixelX!=null}）。複数フレームと現在の画面の照合後に自動操作にも使います。"
 }
}
