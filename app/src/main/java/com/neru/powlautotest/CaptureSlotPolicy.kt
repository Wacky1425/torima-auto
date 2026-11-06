package com.neru.powlautotest
/** Admission depends on the last actual platform capture, never its own queued request. */
internal object CaptureSlotPolicy {
 const val PROBE_TIMEOUT_MS=4_000L
 const val RAW_TIMEOUT_MS=2_200L
 // Each request owns its completion. A late callback must never release
 // another request's busy flag. All claims happen on the main thread.
 class Completion {
  var finished=false
   private set
  fun claim():Boolean{
   if(finished)return false
   finished=true
   return true
  }
 }
 fun ready(busy:Boolean,now:Long,lastPlatformCapture:Long)=!busy &&
  (lastPlatformCapture==0L || now-lastPlatformCapture>=600L)
 fun expired(queuedAt:Long,now:Long)=now-queuedAt>=8000L
}
