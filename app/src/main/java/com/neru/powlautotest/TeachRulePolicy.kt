package com.neru.powlautotest

/**
 * Explicitly learned ad-dismiss targets.
 * A close-like word by itself is not enough: gacha results use the same word.
 */
internal object TeachRulePolicy {
 const val TAUGHT_CORNER_AD_IMAGE="__taught_ad_corner_image_close__"
 /**
  * A user-demonstrated unlabeled image X can be learned ONLY on the same
  * distinctive Google-Play-branded ad creative, in a small upper-right area.
  * Opaque one-node screens and gacha frames never qualify.
  */
 fun isTaughtAdCornerImage(clazz:String,left:Int,top:Int,right:Int,bottom:Int,
  width:Int,height:Int,signature:String?):Boolean{
  if(clazz!="ImageView" || width<=0 || height<=0)return false
  val tags=signature?.split("|")?:return false
  if(tags.none{it.equals("Google Play",true)} || tags.none{it=="ダウンロード"})return false
  if(tags.none{it.length>=15 && it!="Google Play" && it!="ダウンロード"})return false
  val w=right-left
  val h=bottom-top
  return left>=width*82/100 && right<=width &&
   top>=0 && top<=height*16/100 &&
   w in 28..180 && h in 28..180 &&
   right>left && bottom>top
 }
 private val closers=setOf("閉じる","×","✕","✖","close","dismiss")
 private val sensitive=Regex("(?i)インストール|install|友達にシェア|share|購入|課金|許可|同意|subscribe|購入する")
 fun isDismissLabel(label:String):Boolean = label.trim().lowercase() in closers
 fun isForbidden(label:String):Boolean = sensitive.containsMatchIn(label)
 fun isForbiddenTargetId(id:String):Boolean{
  val simple=id.substringAfterLast('/').substringAfterLast(':').lowercase()
  return listOf("install","purchase","subscribe","buy","share","lcec-cta",
   "skip-ticket","skip_ticket").any{simple.contains(it)}
 }
 /**
  * Some ad endcards draw their top-right X as an unlabeled ImageView.
  * A user-taught spot may qualify only with unusually strong creative markers,
  * a small unique corner ImageView and an unchanged screen signature.
  */
 fun hasImageEndcardEvidence(labels:Collection<String>):Boolean{
  val all=labels.map{it.trim()}.filter{it.isNotEmpty()}.distinct()
  val play=all.any{it.equals("Google Play",true)}
  val download=all.any{it=="ダウンロード"}
  val creative=all.filter{
   it.length>=13 && !it.equals("Google Play",true) &&
    it!="ダウンロード" && !it.contains("インストール") &&
    !it.contains("友達にシェア") &&
    !it.contains("カードを1枚") &&
    !it.contains("ガチャ") &&
    !it.contains("タンク")
  }
  return play && download && creative.size>=2
 }
 fun canPromoteRecordedIcon(signature:String,clazz:String,
  x:Int,y:Int,left:Int,top:Int,right:Int,bottom:Int,
  width:Int,height:Int,screenChanged:Boolean):Boolean{
  return screenChanged &&
   hasImageEndcardEvidence(signature.split("|")) &&
   isCornerImageTarget(clazz,left,top,right,bottom,width,height) &&
   x in left until right && y in top until bottom
 }
 fun isCornerImageTarget(clazz:String,left:Int,top:Int,right:Int,bottom:Int,
   width:Int,height:Int):Boolean {
  val w=right-left;val h=bottom-top
  return clazz=="ImageView" && w in 42..165 && h in 42..165 &&
   left>=width*78/100 && right<=width && top>=0 &&
   bottom<=height*20/100 && h>0 && w>0
 }
 fun isWebAdDismissId(id:String):Boolean =
  id.substringAfterLast('/').lowercase()=="dismiss-button"
 /** Both the interstitial frame and advertiser creative must be present. */
 fun provenWebAdFrame(ids:Collection<String>):Boolean{
  val parts=ids.map{it.substringAfterLast('/').lowercase()}.toSet()
  return "dismiss-button" in parts && "app-interstitial-slot" in parts &&
   ("mys-creative" in parts || "mys-wrapper" in parts || "towerVideo".lowercase() in parts)
 }
 fun canLearnDismissByCoordinate(
  isKnownWebAd:Boolean,
  targetId:String,
  hasVisibleCloseCaption:Boolean,
  withinTarget:Boolean
 ):Boolean=
  isKnownWebAd && isWebAdDismissId(targetId) &&
   hasVisibleCloseCaption && withinTarget
}
