package com.neru.powlautotest

/**
 * Recognize an ad-dismiss X by what remains on screen, not solely by whether
 * a particular advertiser SDK exposes its private metadata in accessibility.
 *
 * A stable, uniquely located upper-right close button can be treated as a
 * conservative fallback after three seconds on the SAME screen. A short-lived
 * loading/gacha overlay, a lower-edge X, or known gacha controls never qualify.
 *
 * This deliberately does NOT claim that a stable X alone cryptographically
 * proves an ad: it is a restricted UI heuristic for a user-approved workflow.
 */
internal object StableAdClosePolicy {
 const val STABLE_MS=3_000L
 const val MIN_SAMPLES=2
 const val POLL_WHEN_UNSURE_MS=850L
 private val GACHA_TERMS=listOf(
  "ガチャWチャンスに挑戦","カードを1枚選んでください",
  "友達にシェアする","動画スキップ券を使いますか",
  "動画スキップ","持っている枚数","動画を見て再チャレンジ",
  "ガチャ演出","読み込み中","ロード中"
 )
 fun hasGachaOrLoadingText(labels:Collection<String>):Boolean =
  labels.any{label->GACHA_TERMS.any{label.contains(it,ignoreCase=true)}}

 fun upperRightClose(left:Int,top:Int,right:Int,bottom:Int,
  width:Int,height:Int):Boolean{
  if(width<=0||height<=0 || left<0 || top<0 || right<=left || bottom<=top)return false
  val w=right-left;val h=bottom-top
  return right<=width && bottom<=height*20/100 &&
   left>=width*64/100 && (left+right)/2>=width*76/100 &&
   top<=height*18/100 && w in 20..380 && h in 20..220
 }

 fun plausibleTransientLoading(nodeCount:Int,hasProgressIndicator:Boolean,
  hasLoadingText:Boolean,postAdQuiet:Boolean):Boolean =
  postAdQuiet || hasLoadingText || hasProgressIndicator ||
   nodeCount<=2

 fun isStableCloseAllowed(
  kind:String,hasUniqueTopRightTarget:Boolean,explicitGacha:Boolean,
  gachaCardPhase:Boolean,postAdQuiet:Boolean,loading:Boolean,
  stableMs:Long,samples:Int
 ):Boolean =
  kind=="AD_CLOSE" && hasUniqueTopRightTarget && !explicitGacha &&
   !gachaCardPhase && !postAdQuiet && !loading &&
   stableMs>=STABLE_MS && samples>=MIN_SAMPLES

 /** Looser screen matching only when the unique target node/ID and bounds
  * already matched; a single common word like 'close' is NOT a screen match. */
 fun sufficientAnchorOverlap(savedAnchors:Int,overlap:Int):Boolean =
  savedAnchors>=2 && overlap>=2 &&
   overlap*100>=savedAnchors*45
}
