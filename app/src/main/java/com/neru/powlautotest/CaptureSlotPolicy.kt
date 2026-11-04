package com.neru.powlautotest
/** Admission depends on the last actual platform capture, never its own queued request. */
internal object CaptureSlotPolicy {
 fun ready(busy:Boolean,now:Long,lastPlatformCapture:Long)=!busy &&
  (lastPlatformCapture==0L || now-lastPlatformCapture>=600L)
 fun expired(queuedAt:Long,now:Long)=now-queuedAt>=8000L
}
