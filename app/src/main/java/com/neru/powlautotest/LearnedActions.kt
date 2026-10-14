package com.neru.powlautotest

import android.content.Context
import android.graphics.Rect
import android.graphics.Point
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** User-confirmed learned nodes. Coordinates alone are never replayed. */
class LearnedActions(private val ctx:Context) {
 companion object {
  private const val APP="jp.co.incrementp.milemobile"
  private const val FILE="torima_learned_actions.json"
  private const val DEMONSTRATIONS="torima_teaching_demonstrations.json"
  private val forbidden=Regex("(?i)インストール|install|友達にシェア|share|more|購入|課金|許可|同意")
  private val generic=Regex("(?i)^(\\d[\\d,.]*|[0-9.,]+%?|install|インストール|more|閉じる|マイル|広告)$")
  private fun file(ctx:Context)=File(ctx.filesDir,FILE)
  private fun demonstrationFile(ctx:Context)=File(ctx.filesDir,DEMONSTRATIONS)
  private fun demonstrationList(ctx:Context):JSONArray=try{
   JSONArray(demonstrationFile(ctx).takeIf{it.exists()}?.readText(Charsets.UTF_8)?:"[]")
  }catch(_:Exception){JSONArray()}
  private fun read(ctx:Context):JSONArray=try {
   JSONArray(file(ctx).takeIf{it.exists()}?.readText(Charsets.UTF_8)?:"[]")
  }catch(_:Exception){JSONArray()}
  fun count(ctx:Context):Int=read(ctx).length()
  fun demonstrationCount(ctx:Context):Int=demonstrationList(ctx).length()
  fun clear(ctx:Context){
   file(ctx).writeText("[]",Charsets.UTF_8)
   demonstrationFile(ctx).writeText("[]",Charsets.UTF_8)
  }
  fun export(ctx:Context):String{
   val list=read(ctx)
   val pending=demonstrationList(ctx)
   return JSONObject()
    .put("count",list.length())
    .put("rules",list)
    .put("pendingCount",pending.length())
    .put("pendingDemonstrations",pending).toString(2)
  }
 }
 data class Item(val node:AccessibilityNodeInfo,val bounds:Rect,val label:String,val viewId:String,val clazz:String){
  val pressable get()=node.isClickable && node.isEnabled && node.isVisibleToUser
 }
 data class Sample(val items:List<Item>,val signature:String?,val rootCount:Int)
 data class Proposal(
  val signature:String?,val label:String,val viewId:String,val clazz:String,
  val bounds:Rect,val x:Int,val y:Int,val message:String,val safe:Boolean,
  val gestureOnly:Boolean=false,
  val visualIcon:Boolean=false
 )
 data class Match(val node:AccessibilityNodeInfo,val bounds:Rect,
  val title:String,val gestureOnly:Boolean=false,val tapPoint:Point?=null,
  val visualIcon:Boolean=false)

 /**
  * Users already explicitly pressed 登録 on these examples. Upgrade only the
  * strongly identified branded image endcards; keep all other examples pending.
  * Replaying still requires the SAME signature, matching unique corner icon,
  * and independent live ad evidence.
  */
 init{
  try{promoteConfirmedImageExamples()}catch(_:Exception){}
 }
 private fun promoteConfirmedImageExamples(){
  val old=demonstrationList(ctx)
  if(old.length()==0)return
  val kept=JSONArray()
  val boundsPattern=Regex("""\[(\d+),(\d+)\]\[(\d+),(\d+)\]""")
  val rootWidth=ctx.resources.displayMetrics.widthPixels
  val rootHeight=maxOf(ctx.resources.displayMetrics.heightPixels,
   ctx.resources.displayMetrics.heightPixels+200)
  var changed=false
  for(i in 0 until old.length()){
   val entry=old.optJSONObject(i)?:continue
   val signature=entry.optString("signature")
   val match=boundsPattern.matchEntire(entry.optString("bounds"))
   if(match==null){
    kept.put(entry)
    continue
   }
   val coords=match.groupValues.drop(1).map{it.toInt()}
   val r=Rect(coords[0],coords[1],coords[2],coords[3])
   val valid=TeachRulePolicy.canPromoteRecordedIcon(
    signature,entry.optString("class"),
    entry.optInt("x"),entry.optInt("y"),
    r.left,r.top,r.right,r.bottom,
    rootWidth,maxOf(rootHeight,r.bottom),entry.optBoolean("screenChanged"))
   if(valid && read(ctx).length()<32){
    val proposal=Proposal(signature,"","","ImageView",r,
     entry.optInt("x"),entry.optInt("y"),
     "以前に登録した画像広告の閉じる位置",true,
     gestureOnly=true,visualIcon=true)
    register(proposal)
    changed=true
   }else kept.put(entry)
  }
  if(changed)demonstrationFile(ctx).writeText(kept.toString(),Charsets.UTF_8)
 }
 private fun canon(input:String):String=input.trim().replace(Regex("\\s+")," ")
  .replace(Regex("\\d{2,}"),"#").take(90)
 fun scan(root:AccessibilityNodeInfo?):Sample?{
  if(root?.packageName?.toString()!=APP)return null
  val items=ArrayList<Item>()
  var count=0
  val width=ctx.resources.displayMetrics.widthPixels
  // Accessibility coordinates may include system bars excluded by displayMetrics.
  // The Google One dismiss control is near the physical bottom of the screen.
  val rootBounds=Rect();root.getBoundsInScreen(rootBounds)
  val height=maxOf(ctx.resources.displayMetrics.heightPixels,rootBounds.bottom)
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
  val h=maxOf(ctx.resources.displayMetrics.heightPixels,item.bounds.bottom)
  return forbidden.containsMatchIn(item.label) ||
   TeachRulePolicy.isForbiddenTargetId(item.viewId) ||
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
  // Some ad SDKs use a nonclickable dismiss parent containing a close caption.
  // The user's touch may be just outside the smaller caption, but still within
  // the actual dismiss area. This is intentionally restricted to verified SDKs.
  val sdkProof=TeachRulePolicy.provenWebAdFrame(sample.items.map{it.viewId})
  val dismissParent=sample.items.filter{candidate->
   val caption=sample.items.any{child->
    child.node.isVisibleToUser &&
     TeachRulePolicy.isDismissLabel(child.label) &&
     candidate.bounds.contains(child.bounds.centerX(),child.bounds.centerY())
   }
   TeachRulePolicy.canLearnDismissByCoordinate(
    sdkProof,candidate.viewId,caption,candidate.bounds.contains(x,y)) &&
    !blocked(candidate)
  }.minByOrNull{it.bounds.width().toLong()*it.bounds.height()}
  val adCreativeText=sample.items.flatMap{listOf(it.node.text?.toString().orEmpty(),
   it.node.contentDescription?.toString().orEmpty())}
  val verifiedImageAd=TeachRulePolicy.hasImageEndcardEvidence(adCreativeText)
  val imageCandidates=sample.items.filter{item->
   item.label.isBlank() && item.bounds.contains(x,y) &&
    TeachRulePolicy.isCornerImageTarget(item.clazz,
     item.bounds.left,item.bounds.top,item.bounds.right,item.bounds.bottom,
     ctx.resources.displayMetrics.widthPixels,
     maxOf(ctx.resources.displayMetrics.heightPixels,item.bounds.bottom)) &&
    !blocked(item)
  }
  // Do not turn an arbitrary image into a button: require a single corner icon
  // on a distinctly branded app-install endcard that the user demonstrated.
  val imageClose=if(verifiedImageAd)imageCandidates.singleOrNull() else null
  val pick=dismissParent?:imageClose?:clickable?:textProxy
  val visualIcon=pick!=null && imageClose===pick
  val textOnly=dismissParent!=null || visualIcon || (clickable==null && textProxy!=null)
  val uniqueNamedTarget=pick!=null && sample.items.count{node->
   (if(textOnly)node.node.isVisibleToUser else node.pressable) &&
    node.clazz==pick.clazz &&
    ((pick.viewId.isNotBlank() && node.viewId==pick.viewId) ||
     (pick.viewId.isBlank() && pick.label.isNotBlank() && node.label==pick.label))
  }==1
  val safe=sample.signature!=null && pick!=null &&
    (if(visualIcon)verifiedImageAd && imageCandidates.size==1
     else (pick.label.isNotBlank()||pick.viewId.isNotBlank()) &&
      (!textOnly || sdkProof) && uniqueNamedTarget)
  val reason=when{
   sample.rootCount<=2->"UIが${sample.rootCount}件。画面を識別できず、自動再現は無効。"
   pick==null->"押した位置に識別可能なボタンや閉じる表示がなく、自動再現は無効。"
   sample.signature==null->"画面を他の広告から区別する特徴が不足し、自動再現は無効。"
   textOnly && !sdkProof && !visualIcon->"閉じる文字は記録可能ですが、広告固有の情報が不足。自動実行は保留。"
   !safe->"操作対象の識別名が曖昧なため自動再現は無効。"
   dismissParent!=null->"広告画面・閉じる親領域・閉じる文字を確認。押した座標を登録可能。"
   visualIcon->"広告専用画面と右上の画像ボタンを確認。教えた位置をこの画面限定で登録可能。"
   textOnly->"終了文字を確認。画面限定のジェスチャーを登録可能。"
   else->"画面と固有の操作ボタンを識別できた。保存すると次回から使用可能。"
  }
  return Proposal(sample.signature,pick?.label.orEmpty(),pick?.viewId.orEmpty(),
   pick?.clazz.orEmpty(),pick?.bounds?:Rect(),x,y,reason,safe,textOnly,visualIcon)
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
   put("visualIcon",p.visualIcon)
   put("left",p.bounds.left);put("top",p.bounds.top)
   put("right",p.bounds.right);put("bottom",p.bounds.bottom)
   put("tapX",p.x);put("tapY",p.y)
  })
  file(ctx).writeText(kept.toString(),Charsets.UTF_8)
  return "自動操作ルールに登録しました（合計${kept.length()}件）"
 }
 /**
  * Keep user demonstrations even when Android exposes only an opaque video.
  * These are evidence for future learning, NOT automatically executable rules.
  */
 fun saveDemonstration(p:Proposal,screenChanged:Boolean,reason:String):String{
  val data=demonstrationList(ctx)
  if(data.length()>=24)data.remove(0)
  data.put(JSONObject().apply{
   put("at",System.currentTimeMillis())
   put("signature",p.signature?:JSONObject.NULL)
   put("label",p.label);put("viewId",p.viewId);put("class",p.clazz)
   put("x",p.x);put("y",p.y)
   put("bounds",p.bounds.toShortString())
   put("screenChanged",screenChanged)
   put("autoExecutable",false)
   put("visualIcon",p.visualIcon)
   put("reason",reason.take(170))
  })
  demonstrationFile(ctx).writeText(data.toString(),Charsets.UTF_8)
  return "操作例を保存しました（記録合計${data.length()}件）。画面識別が不足するため自動実行は保留"
 }
 // Use stable ad-SDK and screen markers rather than volatile ad titles, image hashes,
 // randomized URLs or base64 blobs. Both the saved and current screens still need
 // >=2 shared distinctive visible markers AND exactly one matching button.
 private fun stableAnchors(signature:String):Set<String>{
  val ignored=setOf("image","app icon","contains ads","everyone","shopping",
   "more info","ratings","reviews","rating","closed","広告","閉じる","close",
   "install","インストール")
  return signature.split("|").map{canon(it)}.filter{token->
   val value=token.lowercase()
   if(value in ignored || token.length !in 4..70)return@filter false
   if(listOf("base64","svg+xml","%0a","%#a","enifd","utm_","http",
      "d%3fid","tracking").any{value.contains(it)})return@filter false
   if(value.matches(Regex("[a-z0-9+/=%._-]{28,}")) &&
    !value.startsWith("ia_"))return@filter false
   true
  }.toSet()
 }
 private data class Candidate(val rule:JSONObject,val score:Int,val target:Item)
 fun match(root:AccessibilityNodeInfo?):Match?{
  val sample=scan(root)?:return null
  val sig=sample.signature
  val activeAnchors=if(sig==null)emptySet() else stableAnchors(sig)
  val verifiedWebAd=TeachRulePolicy.provenWebAdFrame(sample.items.map{it.viewId})
  val rows=read(ctx)
  val found=ArrayList<Candidate>()
  for(i in 0 until rows.length()){
   val rule=rows.optJSONObject(i)?:continue
   val stored=rule.optString("signature")
   val exact=sig!=null && sig==stored
   val savedAnchors=stableAnchors(stored)
   val overlap=activeAnchors.intersect(savedAnchors).size
   val required=maxOf(2,(savedAnchors.size*60+99)/100)
   val contextual=sig!=null && savedAnchors.size>=2 && overlap>=required
   val id=rule.optString("viewId");val label=rule.optString("label")
   val clazz=rule.optString("clazz")
   val gestureOnly=rule.optBoolean("gestureOnly",false)
   val visualIcon=rule.optBoolean("visualIcon",false)
   val sdkDismiss=gestureOnly && TeachRulePolicy.isWebAdDismissId(id) &&
    verifiedWebAd && sample.items.any{
     it.node.isVisibleToUser && TeachRulePolicy.isDismissLabel(it.label)
    }
   if(!exact && !contextual && !sdkDismiss)continue
   if(visualIcon && (!exact || !TeachRulePolicy.hasImageEndcardEvidence(
       sample.items.flatMap{listOf(it.node.text?.toString().orEmpty(),
        it.node.contentDescription?.toString().orEmpty())})))continue
   if(id.isBlank() && (label.isBlank()||label.length>60) && !visualIcon)continue
   val candidates=sample.items.filter{n->
    (if(gestureOnly)n.node.isVisibleToUser else n.pressable) &&
    !blocked(n) && n.clazz==clazz &&
    (!sdkDismiss || sample.items.any{
     it.node.isVisibleToUser && TeachRulePolicy.isDismissLabel(it.label) &&
      n.bounds.contains(it.bounds.centerX(),it.bounds.centerY())
    }) &&
    (if(visualIcon) n.label.isBlank() &&
       TeachRulePolicy.isCornerImageTarget(n.clazz,
        n.bounds.left,n.bounds.top,n.bounds.right,n.bounds.bottom,
        ctx.resources.displayMetrics.widthPixels,
        maxOf(ctx.resources.displayMetrics.heightPixels,n.bounds.bottom))
     else if(id.isNotBlank())n.viewId==id else n.label==label) &&
    kotlin.math.abs(n.bounds.left-rule.optInt("left"))<40 &&
    kotlin.math.abs(n.bounds.top-rule.optInt("top"))<40 &&
    kotlin.math.abs(n.bounds.right-rule.optInt("right"))<40 &&
    kotlin.math.abs(n.bounds.bottom-rule.optInt("bottom"))<40
   }
   val node=candidates.singleOrNull()?:continue
   // Exact matches outrank approximate anchor matches; strong IDs outrank labels.
   val score=(if(exact)1000 else if(sdkDismiss)500 else 100+overlap*10)+
    (if(id.isNotBlank())5 else 0)
   found.add(Candidate(rule,score,node))
  }
  val bestScore=found.maxOfOrNull{it.score}?:return null
  val leaders=found.filter{it.score==bestScore}
  // Multiple rules must not authorize distinct buttons on the same screen.
  val positions=leaders.map{
   it.target.viewId+"|"+it.target.label+"|"+it.target.bounds.toShortString()
  }.distinct()
  if(positions.size!=1)return null
  val chosen=leaders.first()
  val gestureOnly=chosen.rule.optBoolean("gestureOnly",false)
  val kind=if(bestScore>=1000)"完全一致" else "共通特徴照合"
  val title="学習済み($kind): "+
   chosen.target.label.ifBlank{chosen.target.viewId}.take(36)
  val tx=chosen.rule.optInt("tapX",Int.MIN_VALUE)
  val ty=chosen.rule.optInt("tapY",Int.MIN_VALUE)
  val point=if(gestureOnly && chosen.target.bounds.contains(tx,ty))Point(tx,ty)
   else null
  return Match(chosen.target.node,chosen.target.bounds,title,gestureOnly,point,
   chosen.rule.optBoolean("visualIcon",false))
 }
}
