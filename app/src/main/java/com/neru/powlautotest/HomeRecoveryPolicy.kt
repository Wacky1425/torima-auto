package com.neru.powlautotest

import android.content.Context

/** HOME recovery runs once per collection run, after evidence of real HOME failures. */
internal object HomeRecoveryPolicy {
 const val VERSION="3.7"
 const val PRE_RESTART_TRIALS=2
 const val POST_RESTART_TRIALS=3
 const val MAX_RECOVERIES_PER_RUN=1
 const val SETTINGS_TIMEOUT_MS=10_000L
 const val CONFIRM_TIMEOUT_MS=7_000L
 const val REOPEN_TIMEOUT_MS=17_000L
 const val STRONG_STOP_SETTLE_MS=1_600L
 private const val PREF="torima_home_recovery"
 private const val KEY="enable_restart"
 fun enabled(ctx:Context)=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE)
  .getBoolean(KEY,true)
 fun setEnabled(ctx:Context,value:Boolean)=
  ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
   .putBoolean(KEY,value).apply()
 fun shouldRecover(enabled:Boolean,attemptCount:Int,priorRecoveries:Int,
  inFlight:Boolean,homeVerified:Boolean,waitedAfterLastMs:Long):Boolean =
  enabled && homeVerified && !inFlight && priorRecoveries<MAX_RECOVERIES_PER_RUN &&
   attemptCount>=PRE_RESTART_TRIALS &&
   waitedAfterLastMs>=HomeTouchTrials.DELAY_BETWEEN_TRIALS_MS
 fun exhaustedAfterRestart(attemptCount:Int,priorRecoveries:Int,waitedMs:Long):Boolean =
  priorRecoveries>=MAX_RECOVERIES_PER_RUN &&
   attemptCount>=POST_RESTART_TRIALS &&
   waitedMs>=HomeTouchTrials.DELAY_BETWEEN_TRIALS_MS
 /** Reject a full-screen parent even if an exact-text label was located inside it. */
 fun safeClickableBounds(nodeW:Int,nodeH:Int,screenW:Int,screenH:Int):Boolean =
  nodeW>0 && nodeH>0 && screenW>0 && screenH>0 &&
   nodeW<=screenW*90/100 && nodeH<=screenH*18/100
 fun exactStopLabel(label:String)=label.trim().lowercase() in
  setOf("強制停止","強制終了","force stop","force close")
 fun exactConfirmLabel(label:String)=label.trim().lowercase() in
  setOf("強制停止","強制終了","停止","ok","はい","終了","force stop")
 fun isForceStopQuestion(text:String):Boolean {
  val t=text.lowercase()
  return t.contains("強制停止") || t.contains("強制終了") ||
   t.contains("force stop") || t.contains("force stopping")
 }
 fun isTargetTitle(text:String,targetLabel:String):Boolean{
  val t=text.trim()
  val known=targetLabel.trim()
  return t=="トリマ" || (known.isNotEmpty() && t==known)
 }
}
