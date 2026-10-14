package com.neru.powlautotest

/**
 * Small, testable definition for the first collection task.
 * Future apps/tasks should provide their own progress reader and completion proof.
 */
internal object CollectionTaskPolicy {
 const val TASK_ID="torima.tank_video"
 const val TASK_TITLE="トリマ：タンク動画回収"
 const val BUDGET_MS=3L*60L*60L*1000L
 const val ZERO_CONFIRM_DELAY_MS=1_700L

 // A numeral is only meaningful alongside the exact on-screen "あとN回獲得できます".
 private val countPattern=Regex("""あと\s*([0-9０-９]+)\s*回獲得できます""")
 fun remaining(text:String):Int?{
  val digits=countPattern.find(text)?.groupValues?.get(1)?:return null
  return try{
   val normalized=digits.map{
    when(it){
     in '０'..'９' -> ('0'.code+(it.code-'０'.code)).toChar()
     else -> it
    }
   }.joinToString("")
   normalized.toIntOrNull()?.takeIf{it in 0..9999}
  }catch(_:Exception){null}
 }
 fun inferredDecrement(previous:Int?,current:Int):Int=
  if(previous!=null && current<previous)previous-current else 0

 // Completion needs two distinct, spaced zero observations on the verified HOME.
 fun zeroVerified(firstZeroAt:Long,now:Long,reads:Int):Boolean =
  firstZeroAt>0L && reads>=2 && now-firstZeroAt>=ZERO_CONFIRM_DELAY_MS
}
