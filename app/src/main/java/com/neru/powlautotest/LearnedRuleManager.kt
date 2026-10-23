package com.neru.powlautotest

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

/** Disabled state is separate from existing rules; no destructive data migration. */
internal object LearnedRuleManager {
 private fun prefs(ctx:Context)=ctx.getSharedPreferences("torima_learned_rule_controls",Context.MODE_PRIVATE)
 fun key(kind:String,row:JSONObject):String {
  val identity=if(kind=="画像")row.optString("id") else
   listOf("signature","label","viewId","clazz","left","top","right","bottom").joinToString("|"){row.optString(it)}
  val digest=MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
  return kind+":"+digest.joinToString(""){"%02x".format(it.toInt() and 255)}
 }
 fun disabled(ctx:Context):Set<String> = prefs(ctx).getStringSet("disabled",emptySet())?.toSet().orEmpty()
 fun setEnabled(ctx:Context,key:String,enabled:Boolean){
  val keys=disabled(ctx).toMutableSet()
  if(enabled)keys.remove(key) else keys.add(key)
  prefs(ctx).edit().putStringSet("disabled",keys).commit()
  DiagnosticJournal.record(ctx,"teaching","rule_enabled_changed",JSONObject().put("ruleKey",key).put("enabled",enabled))
 }
 data class Entry(val key:String,val title:String,val enabled:Boolean)
 fun entries(ctx:Context):List<Entry> {
  val off=disabled(ctx)
  return listOf("ノード" to LearnedActions.rulesForManagement(ctx),"画像" to VisualLearnedActions.rulesForManagement(ctx)).flatMap{(kind,rows)->
   (0 until rows.length()).mapNotNull {i->
    val row=rows.optJSONObject(i)?:return@mapNotNull null
    val key=key(kind,row)
    val label=row.optString(if(kind=="画像")"sourceLabel" else "label").ifBlank{"名前のない×／アイコン"}
    val detail=if(kind=="画像")"位置(${row.optInt("x")},${row.optInt("y")})" else
     row.optString("signature").replace('|',' ').take(65)
    Entry(key,"$kind：$label\n$detail",key !in off)
   }
  }
 }
 fun export(ctx:Context):String=JSONObject().put("disabledRuleKeys",org.json.JSONArray(disabled(ctx).toList())).toString(2)
}
