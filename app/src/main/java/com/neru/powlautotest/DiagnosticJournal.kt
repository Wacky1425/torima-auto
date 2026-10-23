package com.neru.powlautotest

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Separate bounded journals: ordinary auto-state messages cannot evict teaching evidence. */
internal object DiagnosticJournal {
 @Synchronized fun record(ctx:Context,channel:String,stage:String,detail:JSONObject=JSONObject()) {
  try {
   val file=File(ctx.filesDir,"torima_${channel}_diagnostics.json")
   val events=try{JSONArray(file.takeIf{it.exists()}?.readText()?:"[]")}catch(_:Exception){JSONArray()}
   while(events.length()>=160)events.remove(0)
   events.put(JSONObject().put("at",System.currentTimeMillis()).put("stage",stage).put("detail",detail))
   file.writeText(events.toString(),Charsets.UTF_8)
  }catch(_:Exception){}
 }
 @Synchronized fun export(ctx:Context,channel:String):String = try {
  File(ctx.filesDir,"torima_${channel}_diagnostics.json").takeIf{it.exists()}?.readText()?:"[]"
 }catch(_:Exception){"[]"}
}
