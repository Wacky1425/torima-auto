package com.neru.powlautotest

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Local diagnostic evidence; exported only with the user's one-tap ZIP. */
internal object HomeRecoveryJournal {
 private const val FILENAME="torima_home_recovery.json"
 private const val MAX=70
 private fun file(ctx:Context)=File(ctx.filesDir,FILENAME)
 private fun load(ctx:Context):JSONObject=try{
  JSONObject(file(ctx).readText(Charsets.UTF_8))
 }catch(_:Exception){
  JSONObject().put("schema",1).put("events",JSONArray())
 }
 fun begin(ctx:Context){
  val previous=load(ctx)
  val run=previous.optInt("runNumber",0)+1
  try{file(ctx).writeText(JSONObject()
   .put("schema",1).put("runNumber",run)
   .put("startedAt",System.currentTimeMillis())
   .put("status","NO_RECOVERY_YET")
   .put("events",JSONArray()).toString(2),Charsets.UTF_8)}
  catch(_:Exception){}
 }
 fun note(ctx:Context,stage:String,details:String){
  val j=load(ctx);val a=j.optJSONArray("events")?:JSONArray()
  a.put(JSONObject().put("at",System.currentTimeMillis())
   .put("stage",stage.take(55)).put("detail",details.take(600)))
  while(a.length()>MAX)a.remove(0)
  j.put("events",a).put("status",stage)
  try{file(ctx).writeText(j.toString(2),Charsets.UTF_8)}catch(_:Exception){}
 }
 fun export(ctx:Context)=load(ctx).toString(2)
 fun summary(ctx:Context):String{
  val d=load(ctx)
  val state=d.optString("status","NO_RECOVERY_YET")
  return "HOME復旧: "+when(state){
   "NO_RECOVERY_YET"->"未実施"
   "FORCE_STOP_CONFIRMED"->"設定画面で強制停止を確認"
   "FORCE_STOP_REQUESTED"->"強制停止を要求（完了未検証）"
   "TARGET_REOPENED"->"トリマを再起動（HOME復帰は別途確認）"
   "SOFT_REOPENED"->"強制停止できず、通常の開き直しのみ"
   "FAILED"->"失敗・安全停止"
   else->state
  }
 }
}
