package com.neru.powlautotest

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A separate durable journal for the latest automated HOME experiment.
 * Does not share the 80-event rolling limit of TeachOverlay's action history.
 * The user explicitly shares it; no network transfer takes place here.
 */
internal object HomeTrialJournal {
 private const val FILE_NAME="home_trial_results.json"
 private const val MAX_EVENTS_PER_ATTEMPT=28
 private const val MAX_ATTEMPTS=30

 private fun file(ctx:Context)=File(ctx.filesDir,FILE_NAME)
 private fun read(ctx:Context):JSONObject=try{
  val f=file(ctx)
  if(f.isFile)JSONObject(f.readText(Charsets.UTF_8))
  else JSONObject().put("schema",1).put("attempts",JSONArray())
 }catch(_:Exception){
  JSONObject().put("schema",1).put("attempts",JSONArray())
 }
 private fun write(ctx:Context,data:JSONObject){
  try{file(ctx).writeText(data.toString(2),Charsets.UTF_8)}catch(_:Exception){}
 }
 fun export(ctx:Context):String=read(ctx).toString(2)
 fun beginRun(ctx:Context){
  val t=System.currentTimeMillis()
  write(ctx,JSONObject().apply{
   put("schema",1)
   put("version","3.5")
   put("runStartedAt",t)
   put("runId","HOME-$t")
   put("runState","RUNNING")
   put("attempts",JSONArray())
  })
 }
 fun beginAttempt(ctx:Context,id:String,label:String,mode:String,detail:String){
  val j=read(ctx)
  val all=j.optJSONArray("attempts")?:JSONArray()
  // A previous attempted method only becomes NO_NAVIGATION if a fresh HOME
  // was verified before beginning this new one.
  val before=all.optJSONObject(all.length()-1)
  if(before!=null && !HomeOutcomePolicy.isConfirmed(before.optString("status")) &&
    before.optString("status") !in listOf(HomeOutcomePolicy.ABORTED,HomeOutcomePolicy.NO_NAVIGATION)){
   before.put("status",HomeOutcomePolicy.NO_NAVIGATION)
   before.put("lastEvidence","新しい試行の前にもHOME画面が表示されていた")
  }
  val attempt=JSONObject().apply{
   put("number",all.length()+1)
   put("methodId",id)
   put("method",label)
   put("mode",mode)
   put("startedAt",System.currentTimeMillis())
   put("initialEvidence",detail.take(1300))
   put("status",HomeOutcomePolicy.PREPARING)
   put("lastEvidence","操作はまだ送っていない")
   put("clickEventSeen",false)
   put("gestureCompleted",false)
   put("events",JSONArray())
  }
  all.put(attempt)
  while(all.length()>MAX_ATTEMPTS)all.remove(0)
  j.put("attempts",all)
  write(ctx,j)
 }
 private fun latestFor(j:JSONObject,id:String):JSONObject?{
  val all=j.optJSONArray("attempts")?:return null
  for(i in all.length()-1 downTo 0){
   val obj=all.optJSONObject(i)?:continue
   if(obj.optString("methodId")==id)return obj
  }
  return null
 }
 fun note(ctx:Context,id:String,stage:String,detail:String){
  val j=read(ctx)
  val record=latestFor(j,id)?:return
  val events=record.optJSONArray("events")?:JSONArray()
  events.put(JSONObject().apply{
   put("at",System.currentTimeMillis())
   put("stage",stage.take(55))
   put("detail",detail.take(900))
  })
  while(events.length()>MAX_EVENTS_PER_ATTEMPT)events.remove(0)
  record.put("events",events)
  when(stage){
   "app_click_event"->record.put("clickEventSeen",true)
   "gesture_completed"->record.put("gestureCompleted",true)
   "dispatch","action_click_result","parent_click_result"->{
    if(record.optString("status")==HomeOutcomePolicy.PREPARING)
     record.put("status",HomeOutcomePolicy.SENT_UNCONFIRMED)
   }
  }
  write(ctx,j)
 }
 fun outcome(ctx:Context,id:String,result:String,detail:String){
  val j=read(ctx)
  val record=latestFor(j,id)?:return
  val previous=record.optString("status")
  // Confirmed evidence must not be replaced by a later timeout or generic
  // UNKNOWN. A stronger count-decrement may upgrade a confirmed navigation.
  if(HomeOutcomePolicy.isConfirmed(previous) &&
    result!=HomeOutcomePolicy.REWARD_DECREASED)return
  if(previous==HomeOutcomePolicy.REWARD_DECREASED)return
  if(result==HomeOutcomePolicy.NO_NAVIGATION &&
    previous==HomeOutcomePolicy.UNVERIFIED_CHANGE)return
  record.put("status",result)
  record.put("lastEvidence",detail.take(950))
  record.put("resultAt",System.currentTimeMillis())
  write(ctx,j)
 }
 fun endRun(ctx:Context,state:String,reason:String){
  val j=read(ctx)
  if(j.optString("runState")!="RUNNING")return
  j.put("runState",state.take(35))
  j.put("reason",reason.take(250))
  j.put("runEndedAt",System.currentTimeMillis())
  val attempts=j.optJSONArray("attempts")
  val last=attempts?.optJSONObject(attempts.length()-1)
  if(last!=null && last.optString("status") in listOf(
    HomeOutcomePolicy.PREPARING,HomeOutcomePolicy.SENT_UNCONFIRMED)){
   last.put("status",HomeOutcomePolicy.INTERRUPTED)
   last.put("lastEvidence","停止時点で遷移・広告を確認できなかった")
  }
  write(ctx,j)
 }
 fun shortSummary(ctx:Context):String{
  val data=read(ctx)
  val tries=data.optJSONArray("attempts")
  if(tries==null || tries.length()==0)return "HOME診断：試行記録なし"
  val winner=(0 until tries.length()).mapNotNull{tries.optJSONObject(it)}
   .lastOrNull{HomeOutcomePolicy.isConfirmed(it.optString("status"))}
  if(winner!=null)return "HOME成功：${winner.optString("method")}／" +
   HomeOutcomePolicy.label(winner.optString("status"))
  val last=tries.optJSONObject(tries.length()-1)!!
  return "HOME試行${tries.length()}件・${HomeOutcomePolicy.label(last.optString("status"))} " +
   "（方式：${last.optString("method")}）"
 }
 fun report(ctx:Context):String{
  val data=read(ctx)
  val items=data.optJSONArray("attempts")?:JSONArray()
  val formatter=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.JAPAN)
  fun time(t:Long)=if(t>0)formatter.format(Date(t)) else "不明"
  val lines=StringBuilder()
  lines.append("Torima Auto HOME診断まとめ v3.5\n")
  lines.append("開始: ${time(data.optLong("runStartedAt"))}\n")
  lines.append("実行状態: ${data.optString("runState","未開始")}\n")
  lines.append("試行数: ${items.length()}\n")
  val all=(0 until items.length()).mapNotNull{items.optJSONObject(it)}
  val winners=all.filter{HomeOutcomePolicy.isConfirmed(it.optString("status"))}
  if(winners.isNotEmpty()){
   lines.append("\n【HOMEから先に進めた方式】\n")
   for(record in winners){
    lines.append("試行${record.optInt("number")}: ${record.optString("method")}\n")
    lines.append("  証拠: ${HomeOutcomePolicy.label(record.optString("status"))}\n")
    lines.append("  詳細: ${record.optString("lastEvidence")}\n")
   }
  }else{
   lines.append("\n【HOMEから先に進めた方式】\n")
   lines.append("まだ確定できていません。タップ完了だけでは成功としません。\n")
  }
  lines.append("\n【全試行結果】\n")
  for(record in all){
   lines.append("試行${record.optInt("number")}: ${record.optString("method")} / " +
    HomeOutcomePolicy.label(record.optString("status"))+"\n")
   lines.append("  タップ開始: ${time(record.optLong("startedAt"))}\n")
   lines.append("  トリマクリックイベント: ${record.optBoolean("clickEventSeen")} / " +
    "ジェスチャー完了: ${record.optBoolean("gestureCompleted")}\n")
   lines.append("  根拠: ${record.optString("lastEvidence")}\n")
  }
  lines.append("\n注: HOMEから広告へ移れたこと、広告を視聴したこと、報酬獲得は別の事実です。\n")
  lines.append("画像とUI記録はユーザーが共有するまでは端末内に保存されます。\n")
  return lines.toString()
 }
}
