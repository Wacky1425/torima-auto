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

 fun newPhaseAfter(kind:String):Phase?=when(kind){
  "W_CHANCE"->Phase.AWAIT_CARDS
  "CARDS"->Phase.AWAIT_RESULT
  "CARD_RESULT"->Phase.AWAIT_REWARD
  else->null
 }

 fun requiresAdEvidence(kind:String):Boolean=
  kind=="AD_CLOSE" || kind=="LEARNED" || kind=="LEARNED_TEXT"
}
