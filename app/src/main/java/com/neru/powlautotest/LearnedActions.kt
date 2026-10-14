package com.neru.powlautotest

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** User-confirmed learned nodes. Coordinates alone are never replayed. */
class LearnedActions(private val ctx:Context) {
 companion object {
  private const val APP="jp.co.incrementp.milemobile"
  private const val FILE="torima_learned_actions.json"
  private val forbidden=Regex("(?i)インストール|install|友達にシェア|share|more|購入|課金|許可|同意")
  private val generic=Regex("(?i)^(\\d[\\d,.]*|[0-9.,]+%?|install|インストール|more|閉じる|マイル|広告)$")
  private fun file(ctx:Context)=File(ctx.filesDir,FILE)
  private fun read(ctx:Context):JSONArray=try {
   JSONArray(file(ctx).takeIf{it.exists()}?.readText(Charsets.UTF_8)?:"[]")
  }catch(_:Exception){JSONArray()}
  fun count(ctx:Context):Int=read(ctx).length()
  fun clear(ctx:Context){file(ctx).writeText("[]",Charsets.UTF_8)}
  fun export(ctx:Context):String{
   val list=read(ctx)
   return if(list.length()==0)"学習済みの操作はありません。" else
    JSONObject().put("count",list.length()).put("rules",list).toString(2)
  }
 }
 data class Item(val node:AccessibilityNodeInfo,val bounds:Rect,val label:String,val viewId:String,val clazz:String){
  val pressable get()=node.isClickable && node.isEnabled && node.isVisibleToUser
 }
 data class Sample(val items:List<Item>,val signature:String?,val rootCount:Int)
 data class Proposal(
  val signature:String?,val label:String,val viewId:String,val clazz:String,
  val bounds:Rect,val x:Int,val y:Int,val message:String,val safe:Boolean,
  val gestureOnly:Boolean=false
 )
 data class Match(val node:AccessibilityNodeInfo,val bounds:Rect,val title:String,val gestureOnly:Boolean=false)

 private fun canon(input:String):String=input.trim().replace(Regex("\\s+")," ")
  .replace(Regex("\\d{2,}"),"#").take(90)
 fun scan(root:AccessibilityNodeInfo?):Sample?{
  if(root?.packageName?.toString()!=APP)return null
  val items=ArrayList<Item>()
  var count=0
  val width=ctx.resources.displayMetrics.widthPixels
  val height=ctx.resources.displayMetrics.heightPixels
  fun visit(n:AccessibilityNodeInfo){
   if(count>=450)return
   count++
   val rect=Rect();n.getBoundsInScreen(rect)
   if(n.isVisibleToUser && rect.width()>0 && rect.height()>0 &&
    rect.left<width && rect.right>0 && rect.top<height && rect.bottom>0){
    val label=canon(n.contentDescription?.toString().orEmpty().ifBlank{n.text?.toString().orEmpty()})
    items.add(Item(n,rect,label,n.viewIdResourceName.orEmpty(),
     n.className?.toString()?.substringAfterLast('.').orEmpty()))
   }
   for(i in 0 until n.childCount){
    if(count>=450)break
    n.getChild(i)?.let{visit(it)}
   }
  }
  visit(root)
  val labels=items.flatMap{listOf(it.node.text?.toString().orEmpty(),
     it.node.contentDescription?.toString().orEmpty())}
   .map{canon(it)}
   .filter{it.length in 4..90 && !generic.matches(it) &&
    !it.startsWith("http",true) && !it.startsWith("d%3Fid",true) &&
    !it.contains("enifd=") && !it.contains("utm_")}
   .distinct().sorted().take(20)
  val sig=if(count>=3 && labels.size>=2 && labels.any{it.length>=8})
   labels.joinToString("|") else null
  return Sample(items,sig,count)
 }
 private fun blocked(item:Item):Boolean{
  val w=ctx.resources.displayMetrics.widthPixels
  val h=ctx.resources.displayMetrics.heightPixels
  return forbidden.containsMatchIn(item.label) ||
   item.bounds.width()>w*0.65 || item.bounds.height()>h*0.30
 }
 fun propose(root:AccessibilityNodeInfo?,x:Int,y:Int):Proposal{
  val sample=scan(root)
  if(sample==null)return Proposal(null,"","","",Rect(),x,y,"トリマの画面情報を取得できません。",false)
  val clickable=sample.items.filter{it.pressable && it.bounds.contains(x,y) && !blocked(it)}
   .minByOrNull{it.bounds.width().toLong()*it.bounds.height()}
  // Video SDKs may expose "閉じる" as text only, not clickable. User-taught, uniquely identified only.
  val textProxy=sample.items.filter{
   it.node.isVisibleToUser && it.bounds.contains(x,y) && !blocked(it) &&
   it.label in listOf("閉じる","×","✕","✖","Close","close") &&
   it.bounds.width() < ctx.resources.displayMetrics.widthPixels*0.40 &&
   it.bounds.height() in 20..180
  }.minByOrNull{it.bounds.width().toLong()*it.bounds.height()}
  val pick=clickable?:textProxy
  val textOnly=clickable==null && textProxy!=null
  val safe=sample.signature!=null && pick!=null &&
    (pick.label.isNotBlank()||pick.viewId.isNotBlank()) &&
    sample.items.count{node->
     (if(textOnly)node.node.isVisibleToUser else node.pressable) && node.clazz==pick.clazz &&
     ((pick.viewId.isNotBlank() && node.viewId==pick.viewId) ||
      (pick.viewId.isBlank() && pick.label.isNotBlank() && node.label==pick.label))
    }==1
  val reason=when{
   sample.rootCount<=2->"UIが${sample.rootCount}件。画面を識別できず、自動再現は無効。"
   pick==null->"押した位置に識別可能なボタンや閉じる表示がなく、自動再現は無効。"
   sample.signature==null->"画面を他の広告から区別する特徴が不足し、自動再現は無効。"
   !safe->"操作対象の識別名が曖昧なため自動再現は無効。"
   textOnly->"終了文字を確認。実際に閉じられた場合、画面限定のジェスチャーを登録可能。"
   else->"画面と固有の操作ボタンを識別できた。保存すると次回から使用可能。"
  }
  return Proposal(sample.signature,pick?.label.orEmpty(),pick?.viewId.orEmpty(),
   pick?.clazz.orEmpty(),pick?.bounds?:Rect(),x,y,reason,safe,textOnly)
 }
 fun register(p:Proposal):String{
  val sig=p.signature
  if(!p.safe || sig==null)return "特徴が不足したため操作ルールには登録できません。"
  val current=read(ctx)
  val kept=JSONArray()
  for(i in 0 until current.length()){
   val item=current.optJSONObject(i)?:continue
   if(item.optString("signature")!=sig)kept.put(item)
  }
  if(kept.length()>=32)kept.remove(0)
  kept.put(JSONObject().apply{
   put("signature",sig);put("label",p.label);put("viewId",p.viewId);put("clazz",p.clazz)
   put("gestureOnly",p.gestureOnly)
   put("left",p.bounds.left);put("top",p.bounds.top)
   put("right",p.bounds.right);put("bottom",p.bounds.bottom)
  })
  file(ctx).writeText(kept.toString(),Charsets.UTF_8)
  return "学習した操作を保存（合計${kept.length()}件）"
 }
 fun match(root:AccessibilityNodeInfo?):Match?{
  val sample=scan(root)?:return null
  val sig=sample.signature?:return null
  val rows=read(ctx)
  val matches=(0 until rows.length()).mapNotNull{rows.optJSONObject(it)}
   .filter{it.optString("signature")==sig}
  if(matches.size!=1)return null
  val rule=matches.single()
  val id=rule.optString("viewId");val label=rule.optString("label")
  val clazz=rule.optString("clazz")
  val gestureOnly=rule.optBoolean("gestureOnly",false)
  val found=sample.items.filter{n->
   (if(gestureOnly)n.node.isVisibleToUser else n.pressable) &&
   !blocked(n) && n.clazz==clazz &&
   (if(id.isNotBlank())n.viewId==id else label.isNotBlank() && n.label==label) &&
   kotlin.math.abs(n.bounds.left-rule.optInt("left"))<40 &&
   kotlin.math.abs(n.bounds.top-rule.optInt("top"))<40 &&
   kotlin.math.abs(n.bounds.right-rule.optInt("right"))<40 &&
   kotlin.math.abs(n.bounds.bottom-rule.optInt("bottom"))<40}
  val item=found.singleOrNull()?:return null
  return Match(item.node,item.bounds,"学習済み: "+item.label.ifBlank{item.viewId}.take(30),gestureOnly)
 }
}
