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
 private var busy=false
 private var lastRequest=0L
 private var lastProbeRequest=0L

 /** Scan overlay marks in RAM. No screenshots are persisted by this path. */
 fun probeOverlay(done:(List<OverlayIconDetector.Marker>?)->Unit){
  val root=service.rootInActiveWindow
  if(root?.packageName?.toString()!=TARGET || Build.VERSION.SDK_INT<30){done(null);return}
  val now=SystemClock.elapsedRealtime()
  if(busy || now-lastProbeRequest<1_300L ||
   (lastRequest>0 && now-lastRequest<1_300L)){done(null);return}
  busy=true;lastProbeRequest=now
  val windowId=root.windowId
  fun finish(image:Bitmap?){
   if(image==null){busy=false;done(null);return}
   io.execute{
    val markers=try{
     val width=540
     val height=(image.height.toLong()*width/image.width).toInt()
     val small=Bitmap.createScaledBitmap(image,width,height,true)
     val pixels=IntArray(width*height)
     small.getPixels(pixels,0,width,0,0,width,height)
     if(small!==image)small.recycle()
     val fromNodesOrComponents=OverlayIconDetector.detect(pixels,width,height)
     val pixelX=PixelAdCloseDetector.detectWhiteX(pixels,width,height)
      ?.let{OverlayIconDetector.Marker("X",it.x,it.y,it.score,"top-right")}
     (fromNodesOrComponents+listOfNotNull(pixelX))
      .groupBy{it.kind}.values.mapNotNull{group->group.maxByOrNull{it.score}}
      .map{it.copy(x=it.x*image.width/width,y=it.y*image.height/height)}
    }catch(_:Exception){null}
    finally{image.recycle()}
    service.mainExecutor.execute{busy=false;done(markers)}
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
  if(Build.VERSION.SDK_INT>=34 && windowId>=0){
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


 fun capture(stage:String,nodeCount:Int,manual:Boolean,done:(String)->Unit){
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
  fun onResult(error:String?=null,image:Bitmap?=null){
   if(image==null){
    busy=false
    done(error?:"画像を取得できませんでした")
    return
   }
   io.execute{
    val result=try{store(image,stage,nodeCount,manual)}
     catch(e:Exception){"画像の解析・保存に失敗：${e.javaClass.simpleName}"}
    finally{image.recycle()}
    service.mainExecutor.execute{
     busy=false
     done(result)
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
  return "画像保存：$filename（nodes=$nodeCount／×形状の候補${results.size}件）。アプリの「画像診断を共有」から確認できます。候補は自動で押しません。"
 }
}
