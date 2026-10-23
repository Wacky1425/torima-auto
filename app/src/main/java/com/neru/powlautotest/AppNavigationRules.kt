package com.neru.powlautotest

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

/** Explicit app-navigation roles. Never consulted by the advertisement close engine. */
internal object AppNavigationRules {
 enum class Role(val title:String,val source:MainScreenPolicy.Screen){
  MAP("マップを開く",MainScreenPolicy.Screen.HOME),
  TODAY("今日に戻る",MainScreenPolicy.Screen.MAP),
  STOCK("ストックを開く",MainScreenPolicy.Screen.MAP)
 }
 private fun file(ctx:Context)=File(ctx.filesDir,"torima_app_navigation_rules.json")
 fun export(ctx:Context)=try{file(ctx).takeIf{it.exists()}?.readText()?:"{}"}catch(_:Exception){"{}"}
 fun clear(ctx:Context){file(ctx).writeText("{}")}
 fun allowed(role:Role,x:Int,y:Int,w:Int,h:Int):Boolean {
  if(w<=0 || h<=0 || x !in 0 until w || y !in 0 until h)return false
  return when(role){
   Role.MAP->x in w*60/100..w*80/100 && y>=h*85/100
   Role.STOCK->x>w*60/100 && y in h*40/100..h*90/100
   Role.TODAY->y in h*20/100..h*90/100
  }
 }
 fun register(ctx:Context,role:Role,bitmap:Bitmap,x:Int,y:Int):String {
  require(allowed(role,x,y,bitmap.width,bitmap.height)){"選択位置が${role.title}の対応範囲外です"}
  val w=540;val h=(bitmap.height.toLong()*w/bitmap.width).toInt()
  val small=Bitmap.createScaledBitmap(bitmap,w,h,true)
  try{
   val pixels=IntArray(w*h);small.getPixels(pixels,0,w,0,0,w,h)
   val nodes=MainScreenReader.read((ctx as PowlAccessibilityService).rootInActiveWindow)
   val imageMap=MapStockIconDetector.onMap(pixels,w,h)
   val source=if(nodes.screen==MainScreenPolicy.Screen.UNKNOWN && nodes.allowVisual && imageMap)
    MainScreenPolicy.Screen.MAP else nodes.screen
   require(source==role.source){"${role.source}の上下表示を確認できません。正しい画面で登録してください"}
   val model=VisualTemplateModel.capture(pixels,w,h,x*w/bitmap.width,y*h/bitmap.height)
    ?:error("ボタンの画像特徴が不足しています")
   val rows=JSONObject(export(ctx))
   rows.put(role.name,JSONObject().put("x",model.x).put("y",model.y).put("width",w).put("height",h)
    .put("near",JSONArray(model.near.toList())).put("context",JSONArray(model.context.toList()))
    .put("source",role.source.name).put("registeredAt",System.currentTimeMillis()))
   file(ctx).writeText(rows.toString())
   DiagnosticJournal.record(ctx,"navigation_teaching","registered",JSONObject().put("role",role.name).put("source",source.name))
   return "アプリ内操作を登録：${role.title}。広告の×学習とは別保存です（まだ実画面は押していません）"
  }finally{if(small!==bitmap)small.recycle()}
 }
 fun match(ctx:Context,role:Role,pixels:IntArray,w:Int,h:Int):MapStockIconDetector.Point?=try{
  val row=JSONObject(export(ctx)).optJSONObject(role.name)
  if(row==null || row.optString("source")!=role.source.name)null else {
   val near=row.getJSONArray("near");val around=row.getJSONArray("context")
   val rule=VisualTemplateModel.Rule(row.getInt("x"),row.getInt("y"),row.getInt("width"),row.getInt("height"),
    IntArray(near.length()){near.getInt(it)},IntArray(around.length()){around.getInt(it)})
   VisualTemplateModel.search(rule,pixels,w,h)?.takeIf{allowed(role,it.x,it.y,w,h)}?.let{MapStockIconDetector.Point(it.x,it.y)}
  }
 }catch(_:Exception){null}
}
