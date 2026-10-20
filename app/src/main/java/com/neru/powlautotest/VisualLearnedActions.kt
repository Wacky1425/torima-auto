package com.neru.powlautotest

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * On-device visual training. A user-selected X/skip point is recorded from the
 * BEFORE-tap image, rather than storing a useless screenshot after navigating.
 * Each rule is a tiny grayscale fingerprint and point, NOT a raw picture.
 *
 * Matching searches only a small neighborhood around the user-taught point
 * and requires enough texture plus similarity of both the icon and surrounding
 * scene. AutoFlow additionally checks the current screen and gacha state.
 */
internal class VisualLearnedActions(private val ctx:Context){
 companion object{
  private const val FILENAME="torima_visual_learned_rules.json"
  private const val MAX_RULES=28
  private fun file(ctx:Context)=File(ctx.filesDir,FILENAME)
  private fun read(ctx:Context):JSONArray=try{
   JSONArray(file(ctx).takeIf{it.exists()}?.readText(Charsets.UTF_8)?:"[]")
  }catch(_:Exception){JSONArray()}
  fun count(ctx:Context)=read(ctx).length()
  fun export(ctx:Context):String{
   val items=read(ctx)
   return JSONObject().put("ruleCount",items.length())
    .put("note","Small grayscale descriptors; saved BEFORE touch; live 2-frame match required")
    .put("rules",items).toString(2)
  }
 }
 data class Captured(val model:VisualTemplateModel.Rule,val originalX:Int,
  val originalY:Int,val type:String,val nodeCount:Int,val label:String)
 data class Match(val id:String,val x:Int,val y:Int,val confidence:Int,
  val near:Int,val context:Int,val type:String,val title:String)
 private fun json(arr:IntArray)=JSONArray().apply{arr.forEach{put(it)}}
 private fun array(v:JSONArray?,expected:Int):IntArray?{
  if(v==null || v.length()!=expected)return null
  return IntArray(expected){i->v.optInt(i,-1)}.takeIf{a->a.all{it in 0..255}}
 }
 fun capture(image:Bitmap,x:Int,y:Int,nodeCount:Int,label:String):Captured?{
  if(image.width<300 || image.height<450 ||
   x !in 0 until image.width || y !in 0 until image.height)return null
  val width=VisualTemplateModel.CANONICAL_WIDTH
  val height=(image.height.toLong()*width/image.width).toInt()
  val small=Bitmap.createScaledBitmap(image,width,height,true)
  return try{
   val pixels=IntArray(width*height)
   small.getPixels(pixels,0,width,0,0,width,height)
   val px=(x.toLong()*width/image.width).toInt().coerceIn(0,width-1)
   val py=(y.toLong()*height/image.height).toInt().coerceIn(0,height-1)
   val model=VisualTemplateModel.capture(pixels,width,height,px,py)?:return null
   // The UI only asks to teach an ad X / Skip. Never interpret a central
   // purchase CTA, a full-screen parent or bottom skip-ticket selection as it.
   val top=py<=height*34/100 && (px>=width*57/100 || px<=width*21/100)
   val bottom=py>=height*72/100 && py<=height*96/100
   if(!top && !bottom)return null
   Captured(model,x,y,if(top)"TOP_CLOSE_OR_SKIP" else "BOTTOM_AD_CLOSE",
    nodeCount,label.take(80))
  }finally{
   if(small!==image)small.recycle()
  }
 }
 fun register(captured:Captured,changed:Boolean,afterPackage:String):String{
  val incoming=read(ctx)
  val r=captured.model
  val id="visual_"+System.currentTimeMillis().toString()
  val obj=JSONObject().apply{
   put("id",id)
   put("kind",captured.type)
   put("x",r.x);put("y",r.y)
   put("imageWidth",r.width);put("imageHeight",r.height)
   put("near",json(r.near));put("context",json(r.context))
   put("sourceNodeCount",captured.nodeCount)
   put("sourceLabel",captured.label.take(80))
   put("observedChange",changed)
   put("afterPackage",afterPackage.take(75))
   put("registeredAt",System.currentTimeMillis())
  }
  val out=JSONArray()
  val slots=ArrayList<JSONObject>()
  for(i in 0 until incoming.length()){
   val old=incoming.optJSONObject(i)?:continue
   // Replace only a visually similar rule near the very same location.
   val dx=kotlin.math.abs(old.optInt("x")-r.x)
   val dy=kotlin.math.abs(old.optInt("y")-r.y)
   val oldNear=array(old.optJSONArray("near"),r.near.size)
   val same=dx<=12 && dy<=12 && oldNear!=null &&
    VisualTemplateModel.similarity(oldNear,r.near)>=840
   if(!same)slots.add(old)
  }
  slots.takeLast(MAX_RULES-1).forEach{out.put(it)}
  out.put(obj)
  file(ctx).writeText(out.toString(2),Charsets.UTF_8)
  return "画像学習を登録しました（計${out.length()}件）。次回は×／スキップ付近の画像と周囲を照合して押します。"
 }
 fun matchCanonical(pixels:IntArray,width:Int,height:Int):Match?{
  val rules=read(ctx)
  var chosen:Match?=null
  var runnerUp=0
  for(i in 0 until rules.length()){
   val item=rules.optJSONObject(i)?:continue
   val near=array(item.optJSONArray("near"),
    VisualTemplateModel.NEAR_GRID*VisualTemplateModel.NEAR_GRID)?:continue
   val context=array(item.optJSONArray("context"),
    VisualTemplateModel.CONTEXT_GRID*VisualTemplateModel.CONTEXT_GRID)?:continue
   val rule=VisualTemplateModel.Rule(
    item.optInt("x"),item.optInt("y"),
    item.optInt("imageWidth"),item.optInt("imageHeight"),near,context)
   val hit=VisualTemplateModel.search(rule,pixels,width,height)?:continue
   // The rule identifies a close-like region, not a random screen center.
   val px=hit.x.toDouble()/width
   val py=hit.y.toDouble()/height
   val kind=item.optString("kind")
   val legal=if(kind=="TOP_CLOSE_OR_SKIP")
    py<=.34 && (px>=.57 || px<=.21)
   else kind=="BOTTOM_AD_CLOSE" && py in .72.. .96
   if(!legal)continue
   val candidate=Match(item.optString("id"),hit.x,hit.y,hit.score,
    hit.nearScore,hit.contextScore,kind,
    item.optString("sourceLabel").ifBlank{"画像で学習した×／スキップ"})
   if(chosen==null || candidate.confidence>chosen.confidence){
    runnerUp=chosen?.confidence?:0
    chosen=candidate
   }else runnerUp=maxOf(runnerUp,candidate.confidence)
  }
  // Competing learned templates for distinctly different positions are not
  // safe to choose unless one is materially more similar.
  val best=chosen?:return null
  if(runnerUp>0 && best.confidence-runnerUp<90)return null
  return best
 }
}
