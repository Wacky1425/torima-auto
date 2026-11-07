package com.neru.powlautotest

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Reports are diagnostic evidence only. They are never read by AutoFlow. */
internal object OperationReportStore {
 private const val LIMIT=5
 private fun root(ctx:Context)=File(ctx.filesDir,"operation_reports")
 private fun reports(ctx:Context)=root(ctx).listFiles()?.filter{
  it.isDirectory && it.name.startsWith("report-")
 }?.sortedByDescending{it.name}.orEmpty()

 @Synchronized fun save(ctx:Context,memo:String,x:Int,y:Int,before:Bitmap,after:Bitmap){
  val now=System.currentTimeMillis()
  val id="$now-${UUID.randomUUID()}"
  val folder=root(ctx)
  val temp=File(folder,"draft-$id")
  check(temp.mkdirs())
  try{
   fun writeFrame(name:String,image:Bitmap){
    val width=minOf(720,image.width)
    val scaled=Bitmap.createScaledBitmap(image,width,maxOf(1,image.height*width/image.width),true)
    try{File(temp,name).outputStream().use{check(scaled.compress(Bitmap.CompressFormat.JPEG,86,it))}}
    finally{if(scaled!==image)scaled.recycle()}
   }
   writeFrame("before.jpg",before)
   writeFrame("after.jpg",after)
   File(temp,"report.json").writeText(JSONObject().apply{
    put("createdAt",now);put("memo",memo.trim().take(240))
    put("selectedX",x);put("selectedY",y)
    put("sourceWidth",before.width);put("sourceHeight",before.height)
    put("positionEvidence","user-selected before manual operation")
    put("afterEvidence","user-requested capture")
   }.toString(2))
   check(temp.renameTo(File(folder,"report-$id")))
   reports(ctx).drop(LIMIT).forEach{it.deleteRecursively()}
  }finally{temp.deleteRecursively()}
 }

 // This one memo was reviewed in development and explicitly assigned to RETRY.
 // Other operation reports remain diagnostics and never become automatic rules.
 @Synchronized fun reviewedRetryModels(ctx:Context):List<ReportedRetryModel.Model> =
  reports(ctx).mapNotNull{dir->
   try{
    val report=JSONObject(File(dir,"report.json").readText())
    if(report.optString("memo").trim()!="ガチャに再チャレンジ")return@mapNotNull null
    val image=android.graphics.BitmapFactory.decodeFile(File(dir,"before.jpg").path)?:return@mapNotNull null
    try{
     val w=540;val h=image.height*w/image.width
     val scaled=Bitmap.createScaledBitmap(image,w,h,true)
     try{
      val pixels=IntArray(w*h);scaled.getPixels(pixels,0,w,0,0,w,h)
      ReportedRetryModel.capture(pixels,w,h,
       report.getInt("selectedX")*w/report.getInt("sourceWidth"),
       report.getInt("selectedY")*h/report.getInt("sourceHeight"))
     }finally{if(scaled!==image)scaled.recycle()}
    }finally{image.recycle()}
   }catch(_:Exception){null}
  }.take(2)
 @Synchronized fun append(ctx:Context,zip:ZipOutputStream):Int=appendReports(reports(ctx),zip)
 internal fun appendReports(newestFirst:List<File>,zip:ZipOutputStream):Int{
  val names=listOf("report.json","before.jpg","after.jpg")
  val items=newestFirst.filter{dir->dir.name.startsWith("report-") && names.all{File(dir,it).isFile}}.take(LIMIT)
  for(dir in items)for(name in names){
   zip.putNextEntry(ZipEntry("operation-reports/${dir.name}/$name"))
   File(dir,name).inputStream().use{it.copyTo(zip)}
   zip.closeEntry()
  }
  return items.size
 }
}
