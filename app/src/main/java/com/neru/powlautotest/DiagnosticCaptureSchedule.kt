package com.neru.powlautotest

/** Diagnostic requests reserve the next screenshot slot instead of losing to every live probe. */
internal class DiagnosticCaptureSchedule {
 var pending=false
  private set
 private var requestedAt=0L
 fun request(now:Long):Boolean {
  if(pending)return false
  pending=true;requestedAt=now;return true
 }
 fun timedOut(now:Long)=pending && now-requestedAt>=15_000L
 fun canCapture(busy:Boolean,now:Long,lastRequest:Long)=pending && !busy &&
  (lastRequest==0L || now-lastRequest>=2_000L)
 fun allowProbe()=!pending
 fun finish(){pending=false}
}
