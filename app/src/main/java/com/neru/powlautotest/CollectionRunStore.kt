package com.neru.powlautotest

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable collection run journal: task progress is separate from touch actions.
 * Dispatching a click is NEVER proof that a reward was received.
 */
internal class CollectionRunStore(private val ctx:Context){
 companion object{
  private const val PREF="torima_collection_sessions"
  private const val ACTIVE="active"
  private const val HISTORY="history"
  private const val MAX_HISTORY=30
  private fun pref(ctx:Context)=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE)
  private fun json(value:String?):JSONObject?=try{
   if(value.isNullOrBlank())null else JSONObject(value)
  }catch(_:Exception){null}
  private fun recent(ctx:Context):JSONArray=try{
   JSONArray(pref(ctx).getString(HISTORY,"[]"))
  }catch(_:Exception){JSONArray()}
  private fun latest(ctx:Context):JSONObject?{
   val items=recent(ctx)
   return if(items.length()>0)items.optJSONObject(items.length()-1) else null
  }
  fun export(ctx:Context):String=JSONObject().apply{
   put("scope","Torima tank video collection task only")
   put("active",json(pref(ctx).getString(ACTIVE,null)))
   put("history",recent(ctx))
  }.toString(2)
  private fun elapsedText(millis:Long):String{
   val minutes=maxOf(0L,millis/60_000L)
   return if(minutes>=60L) "${minutes/60}時間${minutes%60}分" else "${minutes}分"
  }
  fun summary(ctx:Context):String{
   val active=json(pref(ctx).getString(ACTIVE,null))
   val session=active?:latest(ctx)?:return "回収タスク：実行記録なし"
   val status=session.optString("status","UNKNOWN")
   val start=session.optLong("startedAt",0L)
   val end=if(active!=null)System.currentTimeMillis() else session.optLong("endedAt",start)
   val count=session.optInt("lastRemaining",-1)
   val initial=session.optInt("initialRemaining",-1)
   val observed=session.optInt("observedDecrements",0)
   val remaining=if(count>=0)"あと${count}回" else "残回数未確認"
   val first=if(initial>=0)"開始${initial}回" else "開始回数未確認"
   val note=session.optString("reason","").take(65)
   return "タンク動画 [$status] ${elapsedText(end-start)} | $remaining (元$first) | 観測減少${observed}回"+
    if(note.isNotBlank())" | $note" else ""
  }
  private fun push(ctx:Context,run:JSONObject){
   val existing=recent(ctx)
   existing.put(run)
   while(existing.length()>MAX_HISTORY)existing.remove(0)
   pref(ctx).edit().putString(HISTORY,existing.toString()).commit()
  }
 }
 private var current:JSONObject?=null
 private var firstZeroAt=0L
 private var zeroReads=0
 fun isActive():Boolean=current?.optString("status")=="RUNNING"
 fun begin(){
  val previous=json(pref(ctx).getString(ACTIVE,null))
  if(previous!=null && previous.optString("status")=="RUNNING"){
   previous.put("status","INTERRUPTED")
   previous.put("reason","前回の実行が終了記録なしで中断されました")
   previous.put("endedAt",System.currentTimeMillis())
   push(ctx,previous)
  }
  val now=System.currentTimeMillis()
  current=JSONObject().apply{
   put("taskId",CollectionTaskPolicy.TASK_ID)
   put("taskName",CollectionTaskPolicy.TASK_TITLE)
   put("scope","タンク動画の残回数のみ。その他の回収対象は未対応")
   put("status","RUNNING")
   put("startedAt",now)
   put("budgetMs",CollectionTaskPolicy.BUDGET_MS)
   put("initialRemaining",-1)
   put("lastRemaining",-1)
   put("observedDecrements",0)
   put("homeObservations",0)
   put("playRecoveries",0)
   put("lastStage","開始")
   put("reason","")
  }
  firstZeroAt=0L
  zeroReads=0
  persist()
 }
 private fun persist(){
  val run=current?:return
  pref(ctx).edit().putString(ACTIVE,run.toString()).commit()
 }
 fun budgetExceeded():Boolean{
  val run=current?:return false
  return System.currentTimeMillis()-run.optLong("startedAt")>=CollectionTaskPolicy.BUDGET_MS
 }
 fun observeHome(remaining:Int,now:Long):Boolean{
  val run=current?:return false
  if(!isActive())return false
  val old=run.optInt("lastRemaining",-1).takeIf{it>=0}
  if(run.optInt("initialRemaining",-1)<0)run.put("initialRemaining",remaining)
  if(old!=remaining || run.optInt("homeObservations")==0){
   if(old!=null && remaining<old){
    val delta=CollectionTaskPolicy.inferredDecrement(old,remaining)
    run.put("observedDecrements",run.optInt("observedDecrements")+delta)
   }
   run.put("lastRemaining",remaining)
   run.put("lastStage","HOME：あと${remaining}回")
   run.put("lastObservedAt",System.currentTimeMillis())
   persist()
  }
  run.put("homeObservations",run.optInt("homeObservations")+1)
  if(remaining==0){
   if(firstZeroAt==0L)firstZeroAt=now
   zeroReads++
  }else{
   firstZeroAt=0L
   zeroReads=0
  }
  if(CollectionTaskPolicy.zeroVerified(firstZeroAt,now,zeroReads)){
   finish("COMPLETED","HOME上で残り0回を複数回確認")
   return true
  }
  return false
 }
 fun stage(kind:String){
  val run=current?:return
  if(!isActive() || kind==run.optString("lastStage"))return
  if(kind !in setOf("HOME","HOME_WAIT","SKIP_NO","RETRY","W_CHANCE",
    "CARDS","CARD_RESULT","SHARE_CLOSE","AD_CLOSE"))return
  run.put("lastStage",kind)
  persist()
 }
 fun playReturned(){
  val run=current?:return
  if(!isActive())return
  run.put("playRecoveries",run.optInt("playRecoveries")+1)
  persist()
 }
 fun finish(status:String,reason:String){
  val run=current?:return
  if(!isActive())return
  run.put("status",status)
  run.put("reason",reason.take(240))
  run.put("endedAt",System.currentTimeMillis())
  run.put("elapsedMs",run.optLong("endedAt")-run.optLong("startedAt"))
  push(ctx,run)
  current=null
  pref(ctx).edit().remove(ACTIVE).commit()
 }
 fun shortStatus():String{
  val run=current?:return summary(ctx)
  val remaining=run.optInt("lastRemaining",-1)
  val currentText=if(remaining>=0)"あと${remaining}回" else "回数未確認"
  val mins=(System.currentTimeMillis()-run.optLong("startedAt"))/60_000L
  val estimated=run.optInt("observedDecrements")
  return "動画回収 ${mins}分／$currentText／画面上の回数減少$estimated"
 }
}
