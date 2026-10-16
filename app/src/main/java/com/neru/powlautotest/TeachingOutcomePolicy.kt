package com.neru.powlautotest

/**
 * A teaching sample is judged by the SOURCE target and the OBSERVED result,
 * not by which app happens to be visible later when the user presses 登録.
 *
 * Some ad-dismiss controls open a branded Play sheet on their first tap.
 * That is a real navigation result, although it does not prove that the
 * ad was fully dismissed; AutoFlow will handle Play recovery separately.
 */
internal object TeachingOutcomePolicy {
 const val TARGET="jp.co.incrementp.milemobile"
 const val PLAY="com.android.vending"
 const val GMS="com.google.android.gms"

 fun isPlayDestination(packageName:String,verifiedGmsPlaySheet:Boolean):Boolean =
  packageName==PLAY || (packageName==GMS && verifiedGmsPlaySheet)

 /** The candidate was uniquely identified in LearnedActions.propose().
  * Only an explicit close or dismiss target may be re-used after navigating
  * out to Play. A promotion CTA or arbitrary target is never learnable.
  */
 fun isNamedDismissTarget(label:String,viewId:String,targetWidth:Int,
  targetHeight:Int,screenWidth:Int,screenHeight:Int):Boolean{
  if(screenWidth<=0 || screenHeight<=0 || targetWidth<=0 || targetHeight<=0 ||
    targetWidth>screenWidth*45/100 || targetHeight>screenHeight*15/100)return false
  val name=label.trim().lowercase()
  val id=viewId.substringAfterLast('/').lowercase()
  val names=setOf("×","✕","✖","x","close","closed","dismiss",
   "閉じる","閉じるボタン","広告を閉じる","skip","スキップ")
  // "Skip" is a real video-ad skip only if LearnedActions.propose()
  // already established a safe, uniquely identified target.
  val forbidden=listOf("install","インストール","purchase","購入","share",
   "シェア","ticket","スキップ券","play-store","market","cta")
  if(forbidden.any{name.contains(it) || id.contains(it)})return false
  return name in names || id.contains("dismiss") || id.contains("close") ||
   id in setOf("close_btn","btn_close","close_button")
 }

 fun eligibleForRule(sourceSafe:Boolean,observedChange:Boolean,
  destinationPackage:String,verifiedPlaySheet:Boolean,
  namedDismissSource:Boolean):Boolean{
  if(!sourceSafe || !observedChange)return false
  if(destinationPackage==TARGET)return true
  return isPlayDestination(destinationPackage,verifiedPlaySheet) &&
   namedDismissSource
 }

 fun outcomeName(destinationPackage:String,verifiedPlaySheet:Boolean,
  eligible:Boolean):String=when{
  !eligible->"PENDING"
  isPlayDestination(destinationPackage,verifiedPlaySheet)->"PLAY_AFTER_TAUGHT_DISMISS"
  destinationPackage==TARGET->"TORIMA_SCREEN_CHANGED"
  else->"PENDING"
 }
}
