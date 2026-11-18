package com.neru.powlautotest

import android.content.Context
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Diagnostic-only sessions. No learning or execution code reads these files. */
internal object ManualRecordingStore {
 private fun root(ctx:Context)=File(ctx.filesDir,"manual_recordings")
 private fun sessions(root:File)=root.listFiles()?.filter{
  it.isDirectory && it.name.startsWith("session-") && File(it,"session.json").isFile
 }?.sortedByDescending{it.name}.orEmpty()
 @Synchronized fun begin(ctx:Context,metadata:String):File {
  val root=root(ctx).also{check(it.isDirectory || it.mkdirs())}
  val dir=File(root,"session-${System.currentTimeMillis()}-${UUID.randomUUID()}")
  check(dir.mkdir());writeMeta(dir,metadata)
  sessions(root).drop(2).forEach{it.deleteRecursively()}
  return dir
 }
 private fun atomic(file:File,bytes:ByteArray){
  val temp=File(file.parentFile,file.name+".tmp")
  try{temp.writeBytes(bytes);check(temp.renameTo(file))}finally{temp.delete()}
 }
 @Synchronized fun writeMeta(dir:File,metadata:String)=atomic(File(dir,"session.json"),metadata.toByteArray(Charsets.UTF_8))
 @Synchronized fun writeFrame(dir:File,id:Int,bytes:ByteArray){
  require(id in 1..ManualRecordingPolicy.MAX_FRAMES && bytes.size<=256_000)
  atomic(File(dir,"frame-%04d.jpg".format(id)),bytes)
 }
 @Synchronized fun append(ctx:Context,zip:ZipOutputStream):Int=appendSessions(sessions(root(ctx)),zip)
 internal fun appendSessions(newestFirst:List<File>,zip:ZipOutputStream):Int {
  val valid=newestFirst.filter{it.name.startsWith("session-") && File(it,"session.json").isFile}.take(2)
  for(dir in valid){
   val files=listOf(File(dir,"session.json"))+dir.listFiles().orEmpty().filter{
    it.isFile && Regex("frame-[0-9]{4}\\.jpg").matches(it.name) && it.length() in 1L..256_000L
   }.sortedBy{it.name}.take(ManualRecordingPolicy.MAX_FRAMES)
   for(file in files){
    zip.putNextEntry(ZipEntry("manual-recordings/${dir.name}/${file.name}"))
    file.inputStream().use{it.copyTo(zip)};zip.closeEntry()
   }
  }
  return valid.size
 }
 @Synchronized fun index(ctx:Context):String {
  val items=org.json.JSONArray()
  for(dir in sessions(root(ctx)).take(2))runCatching{
   val j=org.json.JSONObject(File(dir,"session.json").readText())
   items.put(org.json.JSONObject().put("folder","manual-recordings/${dir.name}")
    .put("startedAt",j.optLong("startedAt")).put("status",j.optString("status"))
    .put("eventCount",j.optJSONArray("events")?.length()?:0)
    .put("frameCount",j.optJSONArray("frames")?.length()?:0))
  }
  return org.json.JSONObject().put("sessions",items).put("automaticLearning",false).toString(2)
 }
}
