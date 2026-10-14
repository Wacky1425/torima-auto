package com.neru.powlautotest

/**
 * Explicitly learned ad-dismiss targets.
 * A close-like word by itself is not enough: gacha results use the same word.
 */
internal object TeachRulePolicy {
 private val closers=setOf("閉じる","×","✕","✖","close","dismiss")
 private val sensitive=Regex("(?i)インストール|install|友達にシェア|share|購入|課金|許可|同意|subscribe|購入する")
 fun isDismissLabel(label:String):Boolean = label.trim().lowercase() in closers
 fun isForbidden(label:String):Boolean = sensitive.containsMatchIn(label)
 fun isForbiddenTargetId(id:String):Boolean{
  val simple=id.substringAfterLast('/').substringAfterLast(':').lowercase()
  return listOf("install","purchase","subscribe","buy","share","lcec-cta",
   "skip-ticket","skip_ticket").any{simple.contains(it)}
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
