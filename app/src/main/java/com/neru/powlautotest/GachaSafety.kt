package com.neru.powlautotest

/**
 * Deliberately small, Android-independent policy used before automatic touches.
 * X, Skip and remembered ad clicks are never allowed during a gacha phase.
 */
internal object GachaSafety {
 enum class Phase { NONE, AWAIT_CARDS, AWAIT_RESULT, AWAIT_REWARD }

 fun allows(phase:Phase,kind:String,waitingForSameScreen:Boolean):Boolean=when(phase){
  Phase.NONE->true
  Phase.AWAIT_CARDS->kind=="CARDS" || (kind=="W_CHANCE" && waitingForSameScreen)
  Phase.AWAIT_RESULT->kind=="CARD_RESULT" || (kind=="CARDS" && waitingForSameScreen)
  Phase.AWAIT_REWARD->kind in listOf("SHARE_CLOSE","HOME","SKIP_NO","RETRY") ||
   (kind=="CARD_RESULT" && waitingForSameScreen)
 }

 /**
  * The post-card-result phase can contain an actual full-screen video ad.
  * That ad is not a gacha animation. Allow ONLY a proven ad close, never a
  * generic X or a screen with merely "閉じる", after a short settle period.
  */
 fun canDismissProvenAdAfterResult(
  phase:Phase,kind:String,adSdkProven:Boolean,elapsedMs:Long
 ):Boolean =
  phase==Phase.AWAIT_REWARD && requiresAdEvidence(kind) && adSdkProven &&
   elapsedMs>=3_000L

 /**
  * A user-registered target is already fully verified against the CURRENT
  * screen signature, node type, resource ID/label, and bounding rectangle by
  * LearnedActions.match(). A second SDK-specific ad proof is unnecessary.
  * This exception never applies to contextual matches or explicit gacha UI.
  */
 fun exactLearnedCloseAllowed(kind:String,exact:Boolean,gachaUiVisible:Boolean):Boolean =
  exact && !gachaUiVisible &&
   kind in listOf("LEARNED","LEARNED_TEXT","LEARNED_ICON")

 /**
  * AWAIT_REWARD can include one or more actual video ads after a card result.
  * Give the gacha loading animation a brief grace period, then allow a
  * genuinely observed ad control; never click an UNKNOWN screen on a timer.
  * The caller must have freshly observed the control on THIS screen.
  */
 const val REWARD_AD_GRACE_MS=6_000L
 fun mayUseVisibleAdControlAfterGrace(
  phase:Phase,kind:String,elapsedMs:Long,gachaUiVisible:Boolean,
  nativeAdControl:Boolean,stableVisualMarker:Boolean,exactLearned:Boolean
 ):Boolean{
  if(phase!=Phase.AWAIT_REWARD || elapsedMs<REWARD_AD_GRACE_MS ||
   gachaUiVisible)return false
  return when(kind){
   "AD_SKIP","AD_CLOSE"->nativeAdControl
   "AD_VISUAL_X","AD_VISUAL_NEXT"->stableVisualMarker
   "LEARNED","LEARNED_TEXT","LEARNED_ICON"->exactLearned
   else->false
  }
 }

 fun newPhaseAfter(kind:String):Phase?=when(kind){
  "W_CHANCE"->Phase.AWAIT_CARDS
  "CARDS"->Phase.AWAIT_RESULT
  "CARD_RESULT"->Phase.AWAIT_REWARD
  else->null
 }

 fun requiresAdEvidence(kind:String):Boolean=
  kind in listOf("AD_CLOSE","AD_SKIP","AD_GOOGLE_PLAY","AD_VISUAL_X","AD_VISUAL_NEXT",
   "LEARNED","LEARNED_TEXT","LEARNED_ICON")
}
