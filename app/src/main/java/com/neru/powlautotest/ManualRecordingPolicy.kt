package com.neru.powlautotest

/** Uptime timestamps only; never infer a finger coordinate or a successful reward. */
internal object ManualRecordingPolicy {
 const val TARGET="jp.co.incrementp.milemobile"
 const val DURATION_MS=180_000L
 const val MAX_EVENTS=500
 const val MAX_FRAMES=90
 const val SAMPLE_MS=2_000L
 const val MAX_FRAME_AGE_MS=4_000L
 data class Frame(val id:Int,val requested:Long,val received:Long,val epoch:Int)
 data class Pairing(val before:Int?,val after:Int?)
 fun pair(frames:List<Frame>,eventAt:Long,epoch:Int):Pairing {
  val same=frames.filter{it.epoch==epoch && it.requested>=0L && it.received>=it.requested}
  return Pairing(same.filter{it.received<eventAt && eventAt-it.received<=MAX_FRAME_AGE_MS}
   .maxByOrNull{it.received}?.id,
   same.filter{it.requested>eventAt && it.requested-eventAt<=MAX_FRAME_AGE_MS}
    .minByOrNull{it.requested}?.id)
 }
 fun accepts(eventPackage:String,foreground:String)=eventPackage==TARGET && foreground==TARGET
 fun limit(elapsed:Long,events:Int)=elapsed>=DURATION_MS || events>=MAX_EVENTS
 fun coalesceScroll(sameTarget:Boolean,previous:Long,now:Long)=
  sameTarget && previous>0L && now-previous in 0L..249L
}
