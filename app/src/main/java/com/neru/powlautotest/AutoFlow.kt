package com.neru.powlautotest

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Point
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent

/**
 * Conservative local state machine. Every poll classifies the CURRENT screen only.
 * Unknown content, skip tickets, ads without verified close controls and share buttons
 * are never automatically tapped. One action per recognized transition.
 */
class AutoFlow(
 private val service:PowlAccessibilityService,
 private val statusCallback:(String)->Unit
) {
 companion object {
  private const val TARGET="jp.co.incrementp.milemobile"
  private const val POLL_MS=1800L
  private const val MAX_ACTIONS=350
 }
 private val h=Handler(Looper.getMainLooper())
 private val learned by lazy{LearnedActions(service)}
 private val collection by lazy{CollectionRunStore(service)}
 private var rewardPhase=RewardTabPolicy.Phase.MOVE
 private var runStartedElapsed=0L
 private var emptyHomeSince=0L
 private var emptyHomeReads=0
 private var tabSwitchStartedAt=0L
 private var tabSwitchLastTapAt=0L
 private var tabSwitchAttempts=0
 private var tabSwitchConfirmedSince=0L
 private var tabSwitchConfirmedReads=0
 private var tabSwitchInFlight=false
 private var badgeInspectionDone=false
 private var badgeInspectionSince=0L
 private var badgeProbePending=false
 private var badgeProbeLastAt=0L
 private var badgeLast:RewardBadgeDetector.Result?=null
 private var badgeReadCount=0
 private var badgeFirstReadAt=0L
 private var badgeLastObservedAt=0L
 fun collectionStatus():String=CollectionRunStore.summary(service)
 private var playRecoveryStep=0
 private var playRecoveryAt=0L
 private var playRecoveryAttempts=0
 private var running=false
 private var generation=0
 private var steps=0
 private var waitingKey:String?=null
 private var waitingSince=0L
 private var lastActionTitle=""
 private var fallbackAttempted=false
 private var homeHoldAttempted=false
 // Independent HOME touch experiments: temporary button dimming/HOME_WAIT
 // must not erase the trial, and one failed 110ms gesture must not jump to 2.4s.
 private var homeExperimentStartedAt=0L
 private var homeExperimentStartingCount:Int?=null
 private var homeTrialList=emptyList<HomeTouchTrials.Trial>()
 private var homeTrialIndex=0
 private var homeLastTrial:HomeTouchTrials.Trial?=null
 private var homeLastTrialAt=0L
 private var homeTrialInFlight=false
 private var homeClickObserved=false
 private var homeTrialGeneration=0
 private var homeNonHomeSince=0L
 private var homeNonHomeKind=""
 private var homeRecoveryAttempts=0
 private var homeRecoveryActive=false
 private var homeSwipeAttempted=false
 private var homeSwipeActive=false
 // Leave HOME only after a user-like touch; defer success until an actual
 // recognized ad/retry screen is seen, never credit an opaque window alone.
 private var deferredHomeTrial:HomeTouchTrials.Trial?=null
 private var deferredHomeSince=0L
 private val recentActions=LinkedHashMap<String,Long>()
 private var lastInfo=""
 private var unknownSince=0L
 private var visualUnknownSince=0L
 private var visualLastCaptureAt=0L
 private var liveVisualAt=0L
 private var liveVisualSince=0L
 private var liveVisualCount=0
 private var liveVisual:OverlayIconDetector.Marker?=null
 private var visualLearned:VisualLearnedActions.Match?=null
 private var visualLearnedSince=0L
 private var visualLearnedCount=0
 private var visualLearnedAt=0L
 private var visualProbePending=false
 private var visualProbeLast=0L
 // A visual X with no SDK metadata needs a known video-ad entry path.
 private var videoAdSessionActive=false
 private var lastKind=""
 private var learnedTextSeenAt=0L
 // Gacha animations and ads may both display an X; keep their phases distinct.
 private var gachaPhase=GachaSafety.Phase.NONE
 private var gachaPhaseSince=0L
 private var cardResultVisibleSince=0L
 private var verifiedAdKey=""
 private var verifiedAdSince=0L
 // Some adverts (e.g. a branded full-screen shopping endcard) expose an
 // unambiguous top-right X, but no advertiser/SDK metadata in accessibility.
 // A stable close target is a valid fallback; a transient gacha-loading X is not.
 private var stableCloseKey=""
 private var stableCloseSince=0L
 private var stableCloseReads=0
 private var stableCloseLastReadAt=0L
 // Block any new ad/learned close while the previously closed ad transitions
 // into a gacha or reward screen (including slow-network loading overlays).
 private var postAdClosedAt=0L
 private var postAdNextKind=""
 private var postAdNextSince=0L
 private var lastGachaBucket=-1L
 private var lastGachaCandidate=""
 private val poll=object:Runnable{override fun run(){check()}}

 private data class Item(val node:AccessibilityNodeInfo,val text:String,val desc:String,val rect:Rect) {
  val canPress:Boolean get()=node.isClickable && node.isEnabled && node.isVisibleToUser
 }
 private data class Snapshot(val nodes:List<Item>,val count:Int,val packageName:String,val root:AccessibilityNodeInfo){
  fun has(value:String):Boolean=nodes.any {
   it.node.isVisibleToUser && (it.text.contains(value)||it.desc.contains(value))
  }
  fun exactClickable(desc:String):List<Item> =
   nodes.filter{it.canPress && it.desc.trim()==desc}
 }
 private data class Decision(
  val kind:String,
  val display:String,
  val key:String,
  val target:Item?=null,
  val detail:String="",
  val touch:Point?=null,
  val exactLearned:Boolean=false,
  val registeredLearned:Boolean=false
 )

 private fun resetLiveVisual(){
  liveVisualAt=0L;liveVisualSince=0L;liveVisualCount=0
  liveVisual=null;visualProbePending=false;visualProbeLast=0L
  visualLearned=null;visualLearnedSince=0L
  visualLearnedCount=0;visualLearnedAt=0L
 }
 /**
  * Sample transient overlay glyphs, not screenshots of entire advertisers.
  * Never treat one screenshot or an icon alone as tap authorization.
  */
 private fun probeLiveOverlay(now:Long){
  if(visualProbePending || now-visualProbeLast<1_350L)return
  visualProbePending=true;visualProbeLast=now
  val expectedGeneration=generation
  service.probeOverlayIcons { markers,learnedMatch ->
   visualProbePending=false
   if(!running || generation!=expectedGeneration ||
     (markers==null && learnedMatch==null))return@probeOverlayIcons
   val previous=visualLearned
   val stamp=SystemClock.elapsedRealtime()
   if(learnedMatch!=null && previous!=null &&
    previous.id==learnedMatch.id &&
    kotlin.math.abs(previous.x-learnedMatch.x)<=32 &&
    kotlin.math.abs(previous.y-learnedMatch.y)<=32){
    visualLearnedCount++
   }else{
    visualLearnedCount=if(learnedMatch==null)0 else 1
    visualLearnedSince=stamp
   }
   visualLearned=learnedMatch
   visualLearnedAt=stamp
   val candidate=markers.orEmpty().filter{it.score>=85 && it.region=="top-right"}
    .maxByOrNull{it.score}
   val old=liveVisual
   if(candidate!=null && old!=null && candidate.kind==old.kind &&
    kotlin.math.abs(candidate.x-old.x)<=28 &&
    kotlin.math.abs(candidate.y-old.y)<=28){
    liveVisualCount++
   }else{
    liveVisualCount=if(candidate==null)0 else 1
    liveVisualSince=SystemClock.elapsedRealtime()
   }
   liveVisual=candidate;liveVisualAt=SystemClock.elapsedRealtime()
  }
 }
 /** TWO consecutive screenshots, local icon + surrounding scene, and
  * a known video-ad path. This is a LIVE learned action, not a saved photo.
  */
 private fun visualLearnedDecision(snap:Snapshot):Decision?{
  val hit=visualLearned?:return null
  val now=SystemClock.elapsedRealtime()
  if(now-visualLearnedAt>3_000L || visualLearnedCount<2 ||
   now-visualLearnedSince<850L)return null
  // Starting automation on an already-open advertisement has no remembered
  // entry path. In that case allow only a very strong user-taught match.
  val standaloneAd=hit.confidence>=1_750
  if(!videoAdSessionActive && trustedAdEvidence(snap)==null &&
   !standaloneAd)return null
  val labels=snap.nodes.filter{it.node.isVisibleToUser}
   .flatMap{listOf(it.text,it.desc)}
  if(StableAdClosePolicy.hasGachaOrLoadingText(labels) ||
   snap.has("カードを1枚選んでください") ||
   snap.has("友達にシェアする") ||
   snap.has("動画スキップ券を使いますか"))return null
  if(hit.confidence<VisualTemplateModel.MIN_TOTAL_SCORE)return null
  return Decision("LEARNED_VISUAL","画像学習済み：${hit.title.take(26)}",
   "LEARNED_VISUAL:${hit.id}",null,
   "画像一致 ${hit.confidence}/2000、近傍=${hit.near} 周囲=${hit.context} kind=${hit.type}",
   Point(hit.x,hit.y),false,true)
 }
 private fun visualOverlayDecision(snap:Snapshot):Decision?{
  val marker=liveVisual?:return null
  val now=SystemClock.elapsedRealtime()
  val requiredMs=if(FastAdMode.enabled(service))900L else 1_600L
  if(now-liveVisualAt>2_800L || liveVisualCount<2 ||
   now-liveVisualSince<requiredMs)return null
  val proven=trustedAdEvidence(snap)!=null
  val screenshotProven=marker.kind=="X" && marker.score>=445 &&
   videoAdSessionActive &&
   !StableAdClosePolicy.hasGachaOrLoadingText(
    snap.nodes.filter{it.node.isVisibleToUser}.flatMap{listOf(it.text,it.desc)})
  if(!proven && !screenshotProven)return null
  val x=marker.x;val y=marker.y
  val point=if(marker.kind=="NEXT")Point(x-30,y) else Point(x,y)
  val kind=if(marker.kind=="NEXT")"AD_VISUAL_NEXT" else "AD_VISUAL_X"
  return Decision(kind,"画像検出：${if(marker.kind=="NEXT")"進む二重矢印" else "×"}",
   "$kind:${point.x},${point.y}",null,
   "画像2枚の同じ位置に×／score=${marker.score}／広告SDK=$proven／動画経路=$videoAdSessionActive",
   point)
 }
 /**
  * App-side TYPE_VIEW_CLICKED is extra evidence, NOT proof that a video opened.
  * Gesture completed without a click event is useful when isolating interception.
  */
 fun onHomeClickObserved(e:AccessibilityEvent){
  if(!running || homeExperimentStartedAt==0L || homeLastTrial==null ||
   homeLastTrialAt==0L)return
  val age=SystemClock.elapsedRealtime()-homeLastTrialAt
  if(age !in 0L..5_000L)return
  homeClickObserved=true
  val node=e.source
  val rect=Rect()
  node?.getBoundsInScreen(rect)
  homeTrace("app_click_event",homeLastTrial!!.id,
   "after=${age}ms class=${node?.className?.toString()?.substringAfterLast('.')} " +
    "rect=${rect.toShortString()} desc=${node?.contentDescription?.toString()?.take(60)}")
 }
 private fun homeTrace(stage:String,trialId:String,details:String){
  TeachOverlay.recordHomeExperiment(service,stage,trialId,details)
  HomeTrialJournal.note(service,trialId,stage,details)
 }
 private fun resetHomeExperiment(){
  homeSwipeAttempted=false
  homeSwipeActive=false
  homeExperimentStartedAt=0L
  homeExperimentStartingCount=null
  homeTrialList=emptyList()
  homeTrialIndex=0
  homeLastTrial=null
  homeLastTrialAt=0L
  homeTrialInFlight=false
  homeClickObserved=false
  homeTrialGeneration++
  homeNonHomeSince=0L
  homeNonHomeKind=""
 }
 private fun clearDeferredHome(){
  deferredHomeTrial=null
  deferredHomeSince=0L
 }
 fun isRunning():Boolean=running
 fun start():String{
  if(running)return "自動操作はすでに実行中です。"
  running=true
  generation++
  steps=0
  waitingKey=null
  fallbackAttempted=false
  homeHoldAttempted=false
  resetHomeExperiment()
  clearDeferredHome()
  recentActions.clear()
  unknownSince=0L
  visualUnknownSince=0L
  visualLastCaptureAt=0L
  resetLiveVisual()
  resetStableAdClose()
  videoAdSessionActive=false
  lastKind=""
  learnedTextSeenAt=0L
  gachaPhase=GachaSafety.Phase.NONE
  gachaPhaseSince=0L
  cardResultVisibleSince=0L
  verifiedAdKey=""
  verifiedAdSince=0L
  postAdClosedAt=0L
  postAdNextKind=""
  postAdNextSince=0L
  lastGachaBucket=-1L
  lastGachaCandidate=""
  lastInfo=""
  playRecoveryStep=0
  playRecoveryAt=0L
  playRecoveryAttempts=0
  homeRecoveryAttempts=0
  homeRecoveryActive=false
  rewardPhase=RewardTabPolicy.Phase.MOVE
  runStartedElapsed=SystemClock.elapsedRealtime()
  emptyHomeSince=0L
  emptyHomeReads=0
  resetTabSwitch()
  resetBadgeInspection()
  HomeRecoveryJournal.begin(service)
  HomeTrialJournal.beginRun(service)
  collection.begin()
  update("起動中：移動タンク→歩数の順に動画回収（全体上限3時間）。学習ルール${LearnedActions.count(service)}件も照合")
  h.postDelayed(poll,450)
  return "自動操作を開始しました。不明な画面では一切タップしません。"
 }
 fun stop():String{
  val wasRunning=running
  running=false
  generation++
  h.removeCallbacks(poll)
  service.cancelHomeRecovery()
  homeRecoveryActive=false
  service.restoreHomeTouch()
  resetHomeExperiment()
  resetTabSwitch()
  resetBadgeInspection()
  playRecoveryStep=0
  gachaPhase=GachaSafety.Phase.NONE
  resetLiveVisual()
  resetStableAdClose()
  videoAdSessionActive=false
  cardResultVisibleSince=0L
  verifiedAdKey=""
  verifiedAdSince=0L
  postAdClosedAt=0L
  postAdNextKind=""
  postAdNextSince=0L
  if(wasRunning){
   HomeTrialJournal.endRun(service,"STOPPED","ユーザーが自動操作を停止")
   collection.finish("STOPPED","ユーザーが自動操作を停止")
   update("停止中（ユーザー操作で停止）")
  }
  else statusCallback("停止中")
  return "自動操作を停止しました。"
 }
 private fun update(value:String) {
  if(value==lastInfo){
   if(running)statusCallback("$value\n${collection.shortStatus()}")
   return
  }
  lastInfo=value
  TeachOverlay.recordAutomatic(service,value)
  val progress=if(collection.isActive())collection.shortStatus()
   else CollectionRunStore.summary(service)
  val msg="自動操作：$value\n$progress\n成功確認前の画面では繰り返し押しません。操作回数=$steps/$MAX_ACTIONS"
  service.report=msg
  statusCallback("$value\n$progress")
 }
 private fun next(delay:Long=POLL_MS) {
  if(running)h.postDelayed(poll,delay)
 }
 private fun readCurrent():Snapshot?{
  // Choose the *actual active window*. The previous "largest node count wins"
  // logic could select stale or background Torima windows during gacha results,
  // losing a clearly visible reward dialog and incorrectly waiting 90 seconds.
  fun snapshot(root:AccessibilityNodeInfo):Snapshot{
   val items=ArrayList<Item>()
   var visited=0
   fun walk(node:AccessibilityNodeInfo){
    if(visited>=450)return
    visited++
    val rect=Rect();node.getBoundsInScreen(rect)
    items.add(Item(node,node.text?.toString().orEmpty(),
     node.contentDescription?.toString().orEmpty(),rect))
    for(i in 0 until node.childCount){
     if(visited>=450)break
     node.getChild(i)?.let{walk(it)}
    }
   }
   walk(root)
   return Snapshot(items,visited,root.packageName?.toString().orEmpty(),root)
  }
  val foreground=service.rootInActiveWindow
  if(foreground?.packageName?.toString()==TARGET)return snapshot(foreground)
  // Only use a window fallback if Android does not expose the active root.
  // An unrelated/stale background window must never be picked by node count.
  try{
   service.windows?.forEach{window->
    if(!window.isActive && !window.isFocused)return@forEach
    val root=window.root?:return@forEach
    if(root.packageName?.toString()==TARGET)return snapshot(root)
   }
  }catch(_:Exception){}
  return null
 }
 /**
  * A generic "Close", "×" or "Skip" is not proof of an ad.
  * Require independent ad SDK or advertiser metadata to enable learned X rules.
  */
 private fun trustedAdEvidence(snap:Snapshot):String?{
  val visible=snap.nodes.filter{it.node.isVisibleToUser && it.rect.width()>0 &&
   it.rect.height()>0}
  val sdkIa=visible.any{it.desc=="IAInterstitialView"} &&
   visible.any{it.desc=="ia_endcard_video_overlay" ||
    it.node.viewIdResourceName?.endsWith(":id/ia_endcard_video_overlay")==true}
  if(sdkIa)return "IA_INTERSTITIAL"
  val ids=visible.map{it.node.viewIdResourceName.orEmpty()}
  if(TeachRulePolicy.provenWebAdFrame(ids))return "AD_WEB_DISMISS"
  val r=Rect();snap.root.getBoundsInScreen(r)
  val screenH=maxOf(service.resources.displayMetrics.heightPixels,r.bottom)
  val screenW=service.resources.displayMetrics.widthPixels
  val imageCorner=visible.any{item->
   TeachRulePolicy.isCornerImageTarget(
    item.node.className?.toString()?.substringAfterLast('.').orEmpty(),
    item.rect.left,item.rect.top,item.rect.right,item.rect.bottom,
    screenW,screenH)
  }
  if(imageCorner && TeachRulePolicy.hasImageEndcardEvidence(
   visible.flatMap{listOf(it.text,it.desc)}))return "BRANDED_IMAGE_ENDCARD"
  if(ids.any{it.endsWith("android-product-view-close-button") ||
    it.endsWith("lcec-next-button") || it.endsWith("lcec-cta") ||
    it.endsWith("image_0-fullscreen-overlay") || it.endsWith("fullscreen-overlay") ||
    it.endsWith("app_name") || it.endsWith("app_icon")})return "AD_SDK_STRUCTURE"
  val height=service.resources.displayMetrics.heightPixels
  val cta=visible.any{
   val label=it.text.trim().ifEmpty{it.desc.trim()}
   label.equals("install",true) || label=="インストール" || label=="入手"
  }
  val mainInstall=visible.any{
   val label=it.text.trim().ifEmpty{it.desc.trim()}
   (label.equals("install",true) || label=="インストール" || label=="入手") &&
    it.rect.width()>=120 && it.rect.centerY() in (height*20/100)..(height*91/100)
  }
  val adMetadata=visible.any{
   val id=it.node.viewIdResourceName.orEmpty()
   id.endsWith("install-button") || id.endsWith("app_pub_name") ||
    id.endsWith("app_contains_ads") ||
    it.text.trim() in listOf("Ratings","Reviews","Interested","Not interested",
     "Contains Ads","More Info")
  }
  if(cta && adMetadata)return "AD_INSTALL_AND_METADATA"
  val fullScreenLayout=visible.any{
   it.node.className?.toString()?.endsWith("RelativeLayout")==true &&
   it.rect.width()>=service.resources.displayMetrics.widthPixels*85/100 &&
   it.rect.height()>=height*65/100
  }
  val namedAdClose=visible.any{
   it.desc.equals("closeButton",true) || it.desc.equals("Close Ad",true) ||
    it.desc.equals("Close button",true)
  }
  if(mainInstall && fullScreenLayout && namedAdClose)return "FULLSCREEN_AD_INSTALL"
  val adCounter=visible.any{Regex("""Ad\s+[0-9]+\s+of\s+[0-9]+""",RegexOption.IGNORE_CASE)
   .containsMatchIn(it.text)}
  val metaProvider=visible.any{
   it.text.contains("Ads served by Meta",true) ||
    it.desc.contains("Ads served by Meta",true)
  }
  val metaControl=snap.has("詳細を表示") || snap.has("詳しくはこちら") ||
   visible.any{it.text.trim().equals("Skip",true)}
  if(namedAdClose && (adCounter && (metaControl || metaProvider) ||
    metaProvider && metaControl))return "META_AD_SLIDE"
  val branded=visible.any{it.text.contains("zeta",true) || it.desc.contains("zeta",true)}
  if(branded && cta && visible.any{it.text=="App icon" || it.desc=="App icon" ||
    it.text=="Privacy info" || it.desc=="Privacy info"})return "ZETA_INSTALL_CARD"
  // Shopping endcards can expose a full-width CTA and an unmistakable
  // top-right X while hiding all advertiser SDK metadata.
  val promoCta=visible.any{item->
   val name=item.text.trim().ifBlank{item.desc.trim()}
   name in listOf("今すぐチェック","商品をチェック","詳細を見る",
    "商品を見る","サイトを見る","今すぐ見る") &&
    item.rect.centerY()>=screenH*55/100
  }
  if(promoCta &&
   !StableAdClosePolicy.hasGachaOrLoadingText(
    visible.flatMap{listOf(it.text,it.desc)})){
   val x=closeButton(snap).singleOrNull()
   if(x!=null && StableAdClosePolicy.upperRightClose(
    x.rect.left,x.rect.top,x.rect.right,x.rect.bottom,
    screenW,screenH))return "PROMO_CTA_AND_TOP_RIGHT_CLOSE"
  }
  return null
 }

 private fun resetStableAdClose(){
  stableCloseKey=""
  stableCloseSince=0L
  stableCloseReads=0
  stableCloseLastReadAt=0L
 }
 /** Return false for incomplete transitions and the short-lived gacha X. */
 private fun transientGachaLoading(snap:Snapshot,now:Long):Boolean{
  val loadingText=StableAdClosePolicy.hasGachaOrLoadingText(
   snap.nodes.filter{it.node.isVisibleToUser}.flatMap{listOf(it.text,it.desc)})
  val spinner=snap.nodes.any{
   it.node.isVisibleToUser &&
    it.node.className?.toString()?.contains("ProgressBar",true)==true
  }
  val postAdQuiet=postAdClosedAt>0L &&
   PostAdTransitionPolicy.quietPeriodActive(now-postAdClosedAt)
  val d=service.resources.displayMetrics
  val candidate=closeButton(snap).singleOrNull()
  val corner=candidate!=null && StableAdClosePolicy.upperRightClose(
   candidate.rect.left,candidate.rect.top,candidate.rect.right,
   candidate.rect.bottom,d.widthPixels,d.heightPixels)
  return StableAdClosePolicy.plausibleTransientLoading(
   snap.count,spinner,loadingText,postAdQuiet,corner)
 }
 /** One observation per poll. Do not promote a single transient frame. */
 private fun observeStableAdClose(decision:Decision,snap:Snapshot,now:Long){
  val target=decision.target
  val rootBounds=Rect();snap.root.getBoundsInScreen(rootBounds)
  val display=service.resources.displayMetrics
  val width=maxOf(display.widthPixels,rootBounds.right)
  val height=maxOf(display.heightPixels,rootBounds.bottom)
  val corner=target!=null && StableAdClosePolicy.upperRightClose(
   target.rect.left,target.rect.top,target.rect.right,target.rect.bottom,
   width,height)
  val prohibited=StableAdClosePolicy.hasGachaOrLoadingText(
   snap.nodes.filter{it.node.isVisibleToUser}.flatMap{listOf(it.text,it.desc)})
  val gachaAnimation=gachaPhase==GachaSafety.Phase.AWAIT_CARDS ||
   gachaPhase==GachaSafety.Phase.AWAIT_RESULT
  val loading=transientGachaLoading(snap,now)
  if(decision.kind!="AD_CLOSE" || !corner || prohibited ||
    gachaAnimation || loading || trustedAdEvidence(snap)!=null){
   resetStableAdClose()
   return
  }
  val key="${decision.key}|nodes=${snap.count}"
  if(key!=stableCloseKey){
   stableCloseKey=key
   stableCloseSince=now
   stableCloseReads=1
   stableCloseLastReadAt=now
   // Save one visual reference of this otherwise unclassifiable ad
   // for the bundled diagnostic ZIP, not a cloud upload.
   if(visualLastCaptureAt==0L || now-visualLastCaptureAt>=35_000L){
    visualLastCaptureAt=now
    service.saveVisualScreenshot("右上×の安定判定待ち・広告候補",snap.count,false)
   }
  }else if(now>stableCloseLastReadAt){
   stableCloseReads++
   stableCloseLastReadAt=now
  }
 }
 private fun stableAdCloseEvidence(decision:Decision,snap:Snapshot,now:Long):String?{
  if(decision.kind!="AD_CLOSE" || decision.target==null ||
   stableCloseKey!="${decision.key}|nodes=${snap.count}" ||
   stableCloseSince<=0L)return null
  val elapsed=now-stableCloseSince
  val required=if(gachaPhase==GachaSafety.Phase.AWAIT_REWARD)
   StableAdClosePolicy.AFTER_GACHA_STABLE_MS else StableAdClosePolicy.STABLE_MS
  val bounds=decision.target.rect
  val screen=service.resources.displayMetrics
  val topRight=StableAdClosePolicy.upperRightClose(
   bounds.left,bounds.top,bounds.right,bounds.bottom,
   screen.widthPixels,maxOf(screen.heightPixels,bounds.bottom))
  if(!StableAdClosePolicy.isStableCloseAllowed(
    decision.kind,topRight,false,
    gachaPhase in listOf(GachaSafety.Phase.AWAIT_CARDS,
      GachaSafety.Phase.AWAIT_RESULT),
    postAdClosedAt>0L &&
      PostAdTransitionPolicy.quietPeriodActive(now-postAdClosedAt),
    transientGachaLoading(snap,now),elapsed,stableCloseReads,
    required))return null
  return "STABLE_TOP_RIGHT_X"
 }
 private fun adEvidence(decision:Decision,snap:Snapshot,now:Long):String? =
  trustedAdEvidence(snap)?:stableAdCloseEvidence(decision,snap,now)?:
   if(decision.kind=="LEARNED_VISUAL" &&
    (videoAdSessionActive || (visualLearned?.confidence?:0)>=1_750) &&
    visualLearnedCount>=2 && visualLearned?.id!=null &&
    SystemClock.elapsedRealtime()-visualLearnedAt<3_000L)
    "USER_TAUGHT_VISUAL_AD_BUTTON" else
   if(decision.kind=="AD_VISUAL_X" && videoAdSessionActive &&
    liveVisual?.score?.let{it>=445}==true && liveVisualCount>=2 &&
    !StableAdClosePolicy.hasGachaOrLoadingText(
     snap.nodes.filter{it.node.isVisibleToUser}.flatMap{listOf(it.text,it.desc)}))
    "PIXEL_X_IN_CONFIRMED_VIDEO_FLOW" else null

 private fun registeredLearnedCloseTrusted(decision:Decision,snapshot:Snapshot):Boolean{
  val gachaUiVisible=listOf("カードを1枚選んでください","ガチャWチャンスに挑戦",
   "友達にシェアする","動画を見て再チャレンジ",
   "動画スキップ","持っている枚数").any{snapshot.has(it)}
  return LearnedClosePolicy.approvedOnCurrentScreen(
   decision.kind,decision.registeredLearned,gachaUiVisible)
 }
 private fun gachaProtects(decision:Decision,now:Long,snapshot:Snapshot):Boolean{
  if(gachaPhase==GachaSafety.Phase.NONE)return false
  val adProof=adEvidence(decision,snapshot,now)
  val actualAd=GachaSafety.canDismissProvenAdAfterResult(
   gachaPhase,decision.kind,adProof!=null || registeredLearnedCloseTrusted(decision,snapshot),
   now-gachaPhaseSince)
  val accepted=GachaSafety.allows(gachaPhase,decision.kind,waitingKey==decision.key) ||
   actualAd
  // After a card result, a positively identified new video is NOT
  // another gacha animation: discard the stale AWAIT_REWARD phase.
  if(actualAd && gachaPhase==GachaSafety.Phase.AWAIT_REWARD){
   gachaPhase=GachaSafety.Phase.NONE
   update("ガチャ結果後の広告を確認。ガチャ待機を解除して広告処理へ")
   return false
  }
  val seconds=(now-gachaPhaseSince)/1000L
  if(seconds>=150L){
   stopWithReason("ガチャ中の次画面を150秒確認できなかったため停止（$gachaPhase）")
   return true
  }
  if(!accepted){
   // Reduce diagnostics spam: otherwise the 80-entry action history loses the
   // action that actually entered the stuck phase within only two minutes.
   val bucket=seconds/10L
   if(bucket!=lastGachaBucket || decision.kind!=lastGachaCandidate){
    val changed=decision.kind!=lastGachaCandidate
    lastGachaBucket=bucket
    lastGachaCandidate=decision.kind
    if(changed || bucket%3L==0L){
     TeachOverlay.recordGachaDiagnostic(service,snapshot.root,
      "$gachaPhase/${decision.kind}/${seconds}秒")
    }
    update("ガチャ保護（$gachaPhase / ${seconds}秒）：誤タップを防止して待機。画面=${decision.display}")
   }
   return true
  }
  if(gachaPhase==GachaSafety.Phase.AWAIT_REWARD &&
   decision.kind in listOf("SHARE_CLOSE","HOME","SKIP_NO","RETRY") &&
   (decision.kind=="HOME" || decision.target!=null)){
   update("ガチャ終了後の報酬／通常画面を確認：${decision.kind}")
   gachaPhase=GachaSafety.Phase.NONE
  }
  return false
 }

 private fun markGachaAction(kind:String,now:Long){
  gachaPhase=GachaSafety.newPhaseAfter(kind)?:gachaPhase
  gachaPhaseSince=now
  cardResultVisibleSince=0L
  lastGachaBucket=-1L
  lastGachaCandidate=""
  update("ガチャ進行記録：${kind} → ${gachaPhase}。演出中の×を保護")
 }

 private fun closeButton(snap:Snapshot):List<Item>{
  val w=service.resources.displayMetrics.widthPixels
  val hasIaEndCard=snap.nodes.any{
   it.desc=="IAInterstitialView"
  } && snap.nodes.any{
   it.desc=="ia_endcard_video_overlay" ||
    it.node.viewIdResourceName?.endsWith(":id/ia_endcard_video_overlay")==true
  }
  return snap.nodes.filter {n->
   if(!n.canPress)return@filter false
   val id=n.node.viewIdResourceName.orEmpty()
   // Reuse the common, successfully taught IA endcard close ID across advertisers.
   if(hasIaEndCard && id=="jp.co.incrementp.milemobile:id/ia_clickable_close_button"){
    return@filter n.node.className?.toString()?.substringAfterLast('.')=="FrameLayout" &&
     n.rect.width() in 25..140 && n.rect.height() in 25..140 &&
     n.rect.left>=w*80/100 && n.rect.right<=w && n.rect.top in 0..370
   }
   val name=n.desc.trim().ifEmpty{n.text.trim()}
   if(name.equals("Close Ad",ignoreCase=true)){
    return@filter n.rect.width() in 20..380 && n.rect.height() in 20..220 &&
     n.rect.left>=w*65/100 && n.rect.right<=w && n.rect.top in 0..450
   }
   if(name.equals("Close button",ignoreCase=true)){
    return@filter n.node.className?.toString()?.substringAfterLast('.')=="Button" &&
     n.rect.width() in 24..180 && n.rect.height() in 24..180 &&
     n.rect.left>=w*70/100 && n.rect.right<=w && n.rect.top in 0..400
   }
   // Newly observed: ImageView desc=closeButton [975,60][1050,135].
   if(name.equals("closeButton",ignoreCase=true) ||
      name.equals("close_btn",ignoreCase=true) ||
      name in listOf("×","✕","✖")){
    return@filter n.rect.width() in 20..160 && n.rect.height() in 20..160 &&
     n.rect.left>=w*70/100 && n.rect.right<=w && n.rect.top in 0..430
   }
   false
  }.distinctBy{"${it.node.windowId}/${it.rect.toShortString()}"}
 }
 private fun resetBadgeInspection(){
  badgeInspectionDone=false
  badgeInspectionSince=0L
  badgeProbePending=false
  badgeProbeLastAt=0L
  badgeLast=null
  badgeReadCount=0
  badgeFirstReadAt=0L
  badgeLastObservedAt=0L
 }
 private fun resetTabSwitch(){
  tabSwitchStartedAt=0L
  tabSwitchLastTapAt=0L
  tabSwitchAttempts=0
  tabSwitchConfirmedSince=0L
  tabSwitchConfirmedReads=0
  tabSwitchInFlight=false
 }

 private fun selectedRewardTab(snap:Snapshot):RewardTabPolicy.Tab{
  val top=snap.nodes.filter{it.node.isVisibleToUser}
  val selectedMove=top.any{
   it.node.isSelected &&
    (it.desc.trim().equals("move",true) || it.text.trim()=="移動")
  }
  val selectedSteps=top.any{
   it.node.isSelected && (it.text.trim()=="歩数" ||
    it.desc.trim().lowercase() in setOf("walk","step","steps","stepscount","歩数"))
  }
  val stepHero=snap.has("今日の歩数") ||
   (snap.has("今日の") && snap.has("歩数") && !snap.has("タンクが満タン"))
  val emptyMove=snap.has("たくさん移動してマイルを獲得しよう")
  val fromNodes=RewardTabPolicy.selectedTab(
   selectedMove,selectedSteps,stepHero,emptyMove)
  if(fromNodes!=RewardTabPolicy.Tab.UNKNOWN)return fromNodes
  val image=badgeLast
  val now=SystemClock.elapsedRealtime()
  // Use screenshot-selected tab only during INITIAL inspection. Do not trust
  // a stale orange highlight after changing tabs.
  return if(rewardPhase==RewardTabPolicy.Phase.MOVE &&
   !badgeInspectionDone && tabSwitchAttempts==0 &&
   image!=null && now-badgeLastObservedAt<3_500L)
    image.selected else RewardTabPolicy.Tab.UNKNOWN
 }

 private fun rewardSeparator(snap:Snapshot):Item?=
  snap.nodes.filter{it.node.isVisibleToUser &&
   it.text.trim()=="または" && it.rect.width()>0 &&
   it.rect.height()>0}
   .distinctBy{it.rect.toShortString()}.singleOrNull()

 private fun upperVideoButtonEnabled(snap:Snapshot,sep:Item):Boolean{
  val screen=service.resources.displayMetrics
  val w=screen.widthPixels
  val h=screen.heightPixels
  if(w<=0||h<=0)return false
  return snap.nodes.any{v->
   val r=v.rect
   v.canPress && r.left>=0 && r.right<=w &&
    r.width() in (w*65/100)..(w*96/100) &&
    r.height() in (h*3/100)..(h*12/100) &&
    r.centerX() in (w*37/100)..(w*63/100) &&
    sep.rect.top-r.bottom in 0..maxOf(120,h*6/100)
  }
 }

 private fun currentRemaining(snap:Snapshot):Int? =
  snap.nodes.asSequence().filter{it.node.isVisibleToUser}
   .mapNotNull{CollectionTaskPolicy.remaining(it.text)}.firstOrNull()

 private fun blockedRewardObservation(snap:Snapshot):Boolean=
  gachaPhase!=GachaSafety.Phase.NONE ||
   snap.has("友達にシェアする") ||
   snap.has("カードを1枚選んでください") ||
   snap.has("持っている枚数") ||
   snap.has("ガチャWチャンスに挑戦") ||
   snap.has("動画を見て再チャレンジ") ||
   snap.has("動画スキップ券を使いますか") ||
   snap.has("読み込み中")

 /**
  * Locate the actual top navigation label first. Only when the user supplied
  * fixed-layout HOME header is verified may the known fractional position be
  * used as fallback; never guess coordinates on an unknown screen.
  */
 private fun rewardTabTarget(snap:Snapshot,tab:RewardTabPolicy.Tab):Pair<Item?,Point>?{
  if(tab==RewardTabPolicy.Tab.UNKNOWN)return null
  val d=service.resources.displayMetrics
  val w=d.widthPixels;val h=d.heightPixels
  if(w<350 || h<650)return null
  val labels=if(tab==RewardTabPolicy.Tab.STEPS)
   setOf("歩数","walk","step","steps") else setOf("移動","move")
  fun allowed(r:Rect):Boolean=RewardTabPolicy.tabPointAllowed(
   r.centerX(),r.centerY(),w,h,tab) &&
   r.width() in 16..(w*23/100) && r.height() in 16..(h*15/100)
  val nodes=snap.nodes.filter{n->
   n.node.isVisibleToUser &&
    (n.text.trim().lowercase() in labels ||
     n.desc.trim().lowercase() in labels) &&
    allowed(n.rect)
  }
  val chosen=nodes.sortedWith(compareByDescending<Item>{it.canPress}
   .thenBy{it.rect.width()*it.rect.height()}).firstOrNull()
  if(chosen!=null){
   var candidate:Item=chosen
   var parent=chosen.node.parent
   var depth=0
   while(parent!=null && depth<3){
    val rect=Rect();parent.getBoundsInScreen(rect)
    if(allowed(rect) && parent.isVisibleToUser && parent.isClickable &&
      parent.isEnabled){
     candidate=Item(parent,parent.text?.toString().orEmpty(),
      parent.contentDescription?.toString().orEmpty(),rect)
     break
    }
    parent=parent.parent;depth++
   }
   return Pair(candidate,Point(candidate.rect.centerX(),candidate.rect.centerY()))
  }
  // Fallback is only reachable once the current HOME tab and divider are
  // independently verified by monitorCollection, not on opaque ad/gacha UI.
  val fx=if(tab==RewardTabPolicy.Tab.STEPS)RewardTabPolicy.TAB_RELATIVE_X else 0.07f
  val x=(w*fx).toInt();val y=(h*RewardTabPolicy.TAB_RELATIVE_Y).toInt()
  return if(RewardTabPolicy.tabPointAllowed(x,y,w,h,tab))Pair(null,Point(x,y))
   else null
 }

 private fun clickRewardTab(snap:Snapshot,desired:RewardTabPolicy.Tab,now:Long):Boolean{
  val target=rewardTabTarget(snap,desired)?:run{
   stopWithReason("${desired.name}タブの安全な位置を特定できないため停止")
   return true
  }
  if(tabSwitchInFlight)return true
  val previous=selectedRewardTab(snap)
  if(previous==RewardTabPolicy.Tab.UNKNOWN || previous==desired ||
   rewardSeparator(snap)==null || blockedRewardObservation(snap))return false
  if(tabSwitchAttempts>=RewardTabPolicy.MAX_STEP_SWITCH_TAPS){
   update("${desired.name}タブへの切り替え確認中。連打せず待機")
   return true
  }
  if(tabSwitchLastTapAt>0L &&
   now-tabSwitchLastTapAt<RewardTabPolicy.STEP_SWITCH_RETRY_MS){
   return true
  }
  tabSwitchInFlight=true
  tabSwitchAttempts++
  tabSwitchLastTapAt=now
  val requestGeneration=generation
  update(if(desired==RewardTabPolicy.Tab.STEPS)
   "移動回収が完了。上の「歩数」タブへ切り替え（${tabSwitchAttempts}回目）"
   else "移動を先に回収するため、上の「移動」タブへ戻る")
  service.prepareHomeTouch(target.second.x,target.second.y){ready,note->
   if(!running || generation!=requestGeneration)return@prepareHomeTouch
   if(!ready){
    tabSwitchInFlight=false
    stopWithReason("タブ切替時の操作バー透過に失敗：$note")
    return@prepareHomeTouch
   }
   val fresh=readCurrent()
   if(fresh==null || selectedRewardTab(fresh)!=previous ||
    rewardSeparator(fresh)==null || blockedRewardObservation(fresh)){
    tabSwitchInFlight=false
    update("タブ操作前に画面が変わったので旧位置を押さず再判定")
    next(850)
    return@prepareHomeTouch
   }
   val latest=rewardTabTarget(fresh,desired)
   if(latest==null){
    tabSwitchInFlight=false
    update("タブの位置が変わったため一旦操作を中止して再判定")
    next()
    return@prepareHomeTouch
   }
   val node=latest.first
   val clicked=if(node!=null && node.canPress)
    node.node.performAction(AccessibilityNodeInfo.ACTION_CLICK) else false
   if(!clicked){
    val xy=latest.second
    val rect=Rect(xy.x-28,xy.y-28,xy.x+28,xy.y+28)
    gestureTap(rect,"タブ切替 ${desired.name}",110L,xy)
   }
   update("${desired.name}タブ切替を試行。画面が実際に変わったか再確認")
   tabSwitchInFlight=false
   next(850L)
  }
  return true
 }

 private fun observeEmptyHome(now:Long,reason:String):Boolean{
  if(emptyHomeSince==0L)emptyHomeSince=now
  emptyHomeReads++
  if(!RewardTabPolicy.shouldFinishEmpty(emptyHomeSince,emptyHomeReads,now)){
   update("$reason。ボタン無効の状態を複数回再確認中")
   next(900L)
   return false
  }
  emptyHomeSince=0L;emptyHomeReads=0
  return true
 }
 private fun clearEmptyObservation(){
  emptyHomeSince=0L;emptyHomeReads=0
 }

 private fun beginStepTabSwitch(reason:String){
  if(collection.isActive())collection.finish("COMPLETED",reason)
  rewardPhase=RewardTabPolicy.Phase.SWITCHING_TO_STEPS
  resetTabSwitch()
  tabSwitchStartedAt=SystemClock.elapsedRealtime()
  clearEmptyObservation()
  resetHomeExperiment()
  waitingKey=null
  fallbackAttempted=false
  resetLiveVisual()
  clearDeferredHome()
  update("移動の確認結果：$reason。続けてHOME上部の歩数タブへ移動")
  next(850)
 }

 /**
  * On a STEPS starting screen, inspect both notification dots on TWO live
  * screenshots before blindly switching to MOVE. Unknown results fall back
  * to visiting MOVE and inspecting its gray button/remaining count.
  */
 private fun inspectBadgesWhenStartingOnSteps(snap:Snapshot,now:Long):Boolean{
  val active=selectedRewardTab(snap)
  val headerUnknown=active==RewardTabPolicy.Tab.UNKNOWN &&
   snap.has("歩数") && snap.has("移動")
  if(badgeInspectionDone || rewardPhase!=RewardTabPolicy.Phase.MOVE ||
   (active!=RewardTabPolicy.Tab.STEPS && !headerUnknown) ||
   rewardSeparator(snap)==null || blockedRewardObservation(snap))return false
  if(badgeInspectionSince==0L)badgeInspectionSince=now
  val result=badgeLast
  if(result!=null && badgeReadCount>=2 &&
   now-badgeFirstReadAt>=750L){
   badgeInspectionDone=true
   if(RewardBadgeDetector.canSkipMoveOnStepStart(result,badgeReadCount)){
    collection.finish("SKIPPED",
     "移動の赤丸なし／歩数の赤丸ありを${badgeReadCount}枚確認。移動画面は未訪問")
    update("赤丸を確認：移動なし・歩数あり。歩数の回収へ進む")
    beginStepTabSwitch("移動の赤丸がないため歩数から回収（画像2枚で確認）")
    return true
   }
   update("赤丸検査：${result.describe()}。移動の実際の報酬を確認")
   return false
  }
  if(now-badgeInspectionSince>8_000L){
   badgeInspectionDone=true
   update("赤丸が判定不能。移動タブを開いて残り回数を確認")
   return false
  }
  if(!badgeProbePending && now-badgeProbeLastAt>=1_150L){
   badgeProbePending=true
   badgeProbeLastAt=now
   val expectedGeneration=generation
   service.probeRewardBadges{observed->
    if(!running || generation!=expectedGeneration)return@probeRewardBadges
    badgeProbePending=false
    val stamped=SystemClock.elapsedRealtime()
    if(observed!=null){
     val previous=badgeLast
     if(previous!=null && RewardBadgeDetector.same(previous,observed)){
      badgeReadCount++
     }else{
      badgeReadCount=1
      badgeFirstReadAt=stamped
     }
     badgeLast=observed
     badgeLastObservedAt=stamped
     TeachOverlay.recordAutomatic(service,
      "タブ赤丸検出：${observed.describe()} 連続${badgeReadCount}枚")
    }
   }
  }
  update("歩数から開始：移動・歩数の赤丸を画像で確認中（${badgeReadCount}/2枚）")
  next(750L)
  return true
 }
 private fun switchToSteps(snap:Snapshot,now:Long):Boolean{
  val tab=selectedRewardTab(snap)
  if(blockedRewardObservation(snap))return false
  if(tab==RewardTabPolicy.Tab.STEPS){
   if(tabSwitchConfirmedSince==0L)tabSwitchConfirmedSince=now
   tabSwitchConfirmedReads++
   if(RewardTabPolicy.confirmedStepSwitch(
    tabSwitchConfirmedSince,tabSwitchConfirmedReads,now)){
    rewardPhase=RewardTabPolicy.Phase.STEPS
    collection.begin("torima.steps_video","トリマ：歩数動画回収",
     "歩数タブの残回数と報酬取得確認")
    resetTabSwitch()
    clearEmptyObservation()
    resetHomeExperiment()
    waitingKey=null
    recentActions.clear()
    update("歩数タブへの切り替え完了。紫ボタンから残りを回収")
    next(750)
   }else{
    update("歩数タブに切り替わったことを確認中")
    next(750)
   }
   return true
  }
  tabSwitchConfirmedSince=0L;tabSwitchConfirmedReads=0
  if(tabSwitchStartedAt>0L &&
   now-tabSwitchStartedAt>=RewardTabPolicy.STEP_SWITCH_TIMEOUT_MS){
   stopWithReason("歩数タブへの切り替えを28秒確認できず停止")
   return true
  }
  if(tab!=RewardTabPolicy.Tab.MOVE)return false
  return clickRewardTab(snap,RewardTabPolicy.Tab.STEPS,now)
 }
 private fun choose(snap:Snapshot):Decision{
  val n=snap.nodes
  if(snap.has("友達にシェアする")){
   // The reward pane has a stable private-use close glyph at top right in three samples.
   // Never touch the large button labelled "友達にシェアする".
   val safeClose=n.filter{
    it.canPress && it.desc=="" &&
    it.rect.left in 850..950 && it.rect.right in 950..1040 &&
    it.rect.top in 420..520 && it.rect.bottom in 540..640 &&
    it.rect.width() in 90..150 && it.rect.height() in 95..150
   }
   return Decision("SHARE_CLOSE","報酬画面右上の閉じる","SHARE_CLOSE",
    safeClose.singleOrNull(),"共有ボタンは押さない／閉じる候補=${safeClose.size}")
  }
  if(snap.has("動画スキップ") && snap.has("持っている枚数")){
   val skip=n.filter{it.canPress && it.desc.trim()=="使わない" &&
     it.rect.left in 70..160 && it.rect.right in 940..1010 &&
     it.rect.top in 1500..1650}
   return Decision("SKIP_NO","スキップ券を使わない","SKIP_NO",
    skip.singleOrNull(),"『使わない』だけを押す／候補=${skip.size}")
  }
  if(snap.has("ガチャWチャンスに挑戦")){
   val hits=snap.exactClickable("ガチャWチャンスに挑戦")
   return Decision("W_CHANCE","Wチャンス","W_CHANCE",hits.singleOrNull(),
    "『ガチャWチャンスに挑戦』候補=${hits.size}")
  }
  if(snap.has("動画を見て再チャレンジ")){
   val hits=snap.exactClickable("動画を見て再チャレンジ")
   return Decision("RETRY","再チャレンジ","RETRY",hits.singleOrNull(),
    "『動画を見て再チャレンジ』候補=${hits.size}")
  }
  val cardHeading=snap.has("カードを1枚選んでください")
  if(cardHeading){
   val candidates=n.filter{
    it.canPress && it.rect.left in 40..1050 &&
     it.rect.top in 670..1500 &&
     it.rect.width() in 170..330 && it.rect.height() in 250..390
   }
   val rowTop=candidates.minOfOrNull{it.rect.top}
   val firstRow=if(rowTop==null) emptyList() else candidates.filter{
    kotlin.math.abs(it.rect.top-rowTop)<=12
   }
   val target=if(candidates.size==12&&firstRow.size==4) firstRow.maxByOrNull{it.rect.left} else null
   return Decision("CARDS","カード選択","CARDS",target,
    "カード候補=${candidates.size} 上段=${firstRow.size}／右上のみ対象")
  }
  val cardResult=n.filter {
   it.canPress && it.desc.trim()=="閉じる" && it.rect.left in 70..200 &&
    it.rect.right in 850..1020 && it.rect.top in 1670..2000 &&
    it.rect.height() in 110..230
  }
  // Require the full lower result control plus its visible "閉じる" caption.
  // An animation's floating X cannot satisfy this result-screen structure.
  val resultCaption=cardResult.any{button->
   n.any{node->node.node.isVisibleToUser && node.text.trim()=="閉じる" &&
    button.rect.contains(node.rect.centerX(),node.rect.centerY())}
  }
  if(cardResult.isNotEmpty() && resultCaption){
   return Decision("CARD_RESULT","カード結果","CARD_RESULT",cardResult.singleOrNull(),
    "結果ボタン＋閉じる文字を確認／候補=${cardResult.size}")
  }
  // Identify the top reward-video button from its position above the "または"
  // separator, not fixed vertical pixels. The two large buttons must be distinct.
  val activeTab=selectedRewardTab(snap)
  val recognizedHome=activeTab!=RewardTabPolicy.Tab.UNKNOWN
  val tank=snap.has("タンクが満タン")
  val remaining=n.firstOrNull{
   it.node.isVisibleToUser && Regex("あと\\s*[0-9０-９]+\\s*回獲得できます")
    .containsMatchIn(it.text)
  }?.text?.trim().orEmpty()
  val separator=n.filter{
   it.node.isVisibleToUser && it.text.trim()=="または" &&
    it.rect.width()>0 && it.rect.height()>0
  }.distinctBy{it.rect.toShortString()}.singleOrNull()
  val physical=Rect();snap.root.getBoundsInScreen(physical)
  val width=maxOf(service.resources.displayMetrics.widthPixels,physical.width())
  val height=maxOf(service.resources.displayMetrics.heightPixels,physical.bottom)
  val wideButtons=n.filter{
   val r=it.rect
   it.canPress && r.left>=0 && r.right<=width && r.top>=0 && r.bottom<=height &&
    r.width()>=width*68/100 && r.width()<=width*94/100 &&
    r.height()>=height*4/100 && r.height()<=height*11/100 &&
    kotlin.math.abs(r.centerX()-width/2)<=width*10/100 &&
    r.top>=height*30/100 && r.bottom<=height*78/100
  }.distinctBy{it.rect.toShortString()}
  // Two wide buttons flank the exact "または". Use their distance to this
  // separator rather than displayMetrics height (which excludes system bars).
  val above=if(separator!=null)wideButtons.filter{
   val gap=separator.rect.top-it.rect.bottom
   gap in 0..maxOf(100,height*5/100) &&
    it.rect.centerY()<separator.rect.top
  }else wideButtons.filter{
   it.rect.centerY() in (height*42/100)..(height*59/100)
  }
  val below=if(separator!=null)wideButtons.filter{
   val gap=it.rect.top-separator.rect.bottom
   gap in 0..maxOf(100,height*5/100) &&
    it.rect.centerY()>separator.rect.bottom
  }else emptyList()
  val confirmedHome=recognizedHome && remaining.isNotBlank() &&
   (tank || (separator!=null && above.size==1 && below.size==1))
  val tabName=if(activeTab==RewardTabPolicy.Tab.STEPS)"歩数" else "移動"
  if(confirmedHome){
   val button=above.singleOrNull()
   return Decision("HOME","$tabName／紫ボタン",
    "HOME:${activeTab.name}:$remaining",button,
    "現在タブ=${activeTab.name} / 動画ボタン候補=${above.size} / " +
     "下のボタン=${below.size} / または=${separator!=null} / " +
     "タンク=$tank / $remaining")
  }
  if(recognizedHome && remaining.isNotBlank() && (tank || separator!=null)){
   return Decision("HOME_WAIT","$tabName／紫ボタンが未特定",
    "HOME_WAIT:${activeTab.name}",null,
    "現在タブ=${activeTab.name} / ボタン候補=${above.size} / " +
     "幅広ボタン=${wideButtons.size} / 下のボタン=${below.size} / " +
     "または=${separator!=null} / タンク=$tank")
  }
  // A registered rule is more specific than the generic close-button guess.
  // Its target is already matched by screen features, ID/class and bounds.
  val memory=learned.match(snap.root)
  if(memory!=null){
   val item=Item(memory.node,memory.node.text?.toString().orEmpty(),
    memory.node.contentDescription?.toString().orEmpty(),memory.bounds)
   // Distinguish successive slides (Ad 1 of 2 / Ad 2 of 2) even when
   // their close button and learned title/coordinates are identical.
   val counter=n.firstOrNull{it.node.isVisibleToUser &&
    Regex("""Ad\s+[0-9]+\s+of\s+[0-9]+""",RegexOption.IGNORE_CASE)
     .containsMatchIn(it.text)}?.text?.trim().orEmpty()
   return Decision(if(memory.visualIcon)"LEARNED_ICON"
    else if(memory.gestureOnly)"LEARNED_TEXT" else "LEARNED",memory.title,
    "LEARNED:${memory.bounds.toShortString()}:${memory.title}:$counter",item,
    "本人が登録した画面と一意の対象を再照合済み",memory.tapPoint,
    memory.exact,true)
  }
  val close=closeButton(snap)
  val adCounter=n.any{it.node.isVisibleToUser &&
   Regex("""Ad\s+[0-9]+\s+of\s+[0-9]+""",RegexOption.IGNORE_CASE)
    .containsMatchIn(it.text)}
  val skipOnClose=close.singleOrNull()?.let{button->
   adCounter && n.any{it.node.isVisibleToUser && it.text.trim().equals("Skip",true) &&
    button.rect.contains(it.rect.centerX(),it.rect.centerY())}
  }?:false
  if(skipOnClose){
   val counter=n.firstOrNull{it.node.isVisibleToUser &&
    Regex("""Ad\s+[0-9]+\s+of\s+[0-9]+""",RegexOption.IGNORE_CASE)
     .containsMatchIn(it.text)}?.text?.trim().orEmpty()
   return Decision("AD_SKIP","動画広告のSkip","AD_SKIP:$counter",
    close.single(),"広告に表示されたSkip。スキップ券は押さない")
  }
  val widthPixels=service.resources.displayMetrics.widthPixels
  val topPlay=n.filter{it.canPress &&
   (it.text.contains("Google Play",true)||it.desc.contains("Google Play",true)) &&
   it.rect.left>=widthPixels*70/100 && it.rect.top in 0..400 &&
   it.rect.width() in 40..350}
  if(topPlay.size==1 && trustedAdEvidence(snap)!=null){
   return Decision("AD_GOOGLE_PLAY","広告上部のGoogle Playへ進む",
    "AD_GOOGLE_PLAY",topPlay.single(),"上部ナビゲーションのみ")
  }
  if(close.isNotEmpty()){
   val counter=n.firstOrNull{it.node.isVisibleToUser &&
    Regex("""Ad\s+[0-9]+\s+of\s+[0-9]+""",RegexOption.IGNORE_CASE)
     .containsMatchIn(it.text)}?.text?.trim().orEmpty()
   return Decision("AD_CLOSE","広告の終了ボタン",
    "AD_CLOSE:$counter:${close.first().desc.trim()}:${close.first().rect.toShortString()}",
    close.singleOrNull(),"閉じる候補=${close.size}")
  }
  visualLearnedDecision(snap)?.let{return it}
  visualOverlayDecision(snap)?.let{return it}
  if(snap.count<=2){
   return Decision("OPAQUE","広告または不明画面（UIが${snap.count}件）","OPAQUE",null,
    "アクセシビリティで×を取得できない")
  }
  return Decision("UNKNOWN","不明／広告再生中（${snap.count}ノード）","UNKNOWN",null,
   "識別できる終了・次へボタンなし")
 }
 /**
  * HOME only: try a few short, spaced real gestures. A pressed/faded button
  * without navigation is a failed trial, not proof that anything was collected.
  * Keep the experiment active across transient HOME_WAIT (button not clickable).
  */
 /**
  * Run one HOME hypothesis at a time, using the exact CURRENT button node.
  * The collector bar becomes non-touchable for a short period; its normal
  * touch handling could otherwise intercept a gesture at HOME's y-coordinate.
  */
 private fun tryHomePattern(decision:Decision,now:Long):Boolean{
  val button=decision.target?:return true
  val trial=homeTrialList.getOrNull(homeTrialIndex)?:return true
  if(steps>=MAX_ACTIONS){
   stopWithReason("操作回数の上限に達したためHOME試行を停止")
   return true
  }
  val bounds=button.rect
  fun pointFor(rect:Rect):Point=when(trial.position){
   HomeTouchTrials.Position.UPPER_BUTTON_RIGHT_CENTER->HomeTouchProfile.rightCenter(rect)
   HomeTouchTrials.Position.UPPER_BUTTON_CENTER->Point(rect.centerX(),rect.centerY())
   HomeTouchTrials.Position.LEARNED->HomeTouchProfile.current(service,rect)
  }
  val point=pointFor(bounds)
  if(!bounds.contains(point.x,point.y) ||
    !bounds.contains(point.x+trial.tinyDriftPx,point.y)){
   stopWithReason("HOME試行位置が紫ボタンの範囲外のため安全停止")
   return true
  }
  homeLastTrial=trial
  homeLastTrialAt=now
  homeTrialIndex++
  homeTrialGeneration++
  val trialToken=homeTrialGeneration
  homeTrialInFlight=true
  homeClickObserved=false
  steps++
  val gen=generation
  val label="HOME試行 ${homeTrialIndex}/${homeTrialList.size}：${trial.label}"
  HomeTrialJournal.beginAttempt(service,trial.id,trial.label,trial.mode.name,
   "対象座標=(${point.x},${point.y}) ボタン=${bounds.toShortString()} 試行${homeTrialIndex}")
  val node=button.node
  homeTrace("before",trial.id,"button=${bounds.toShortString()} " +
   "tap=(${point.x},${point.y}) class=${node.className?.toString()?.substringAfterLast('.')} " +
   "click=${node.isClickable} enabled=${node.isEnabled} visible=${node.isVisibleToUser} " +
   "selected=${node.isSelected} children=${node.childCount} " +
   "windowId=${node.windowId} mode=${trial.mode}")
  // Check other windows (dialogues, accessibility overlays) that can
  // receive or obscure touches at precisely the HOME coordinate.
  try{
   val summary=service.windows.orEmpty().take(9).joinToString(" ; "){w->
    val r=Rect();w.getBoundsInScreen(r)
    "type=${w.type},layer=${w.layer},active=${w.isActive},focus=${w.isFocused}," +
     "coversPoint=${r.contains(point.x,point.y)},bounds=${r.toShortString()}," +
     "pkg=${w.root?.packageName?.toString().orEmpty().take(40)}"
   }
   homeTrace("window_layers",trial.id,summary)
  }catch(e:Exception){
   homeTrace("window_layers",trial.id,"window API error=${e.javaClass.simpleName}")
  }
  service.saveVisualScreenshot("HOME before ${trial.id}",0,false)
  update("$label。操作バーのタッチを透過して${HomeTouchTrials.OVERLAY_SETTLE_MS}ms待機")
  service.prepareHomeTouch(point.x,point.y){ready,overlayNote->
   if(!running || generation!=gen || trialToken!=homeTrialGeneration)return@prepareHomeTouch
   homeTrace("overlay",trial.id,overlayNote)
   if(!ready){
    homeTrialInFlight=false
    HomeTrialJournal.outcome(service,trial.id,HomeOutcomePolicy.ABORTED,
     "操作バーのタッチ透過に失敗: $overlayNote")
    stopWithReason("HOME診断：操作バーのタッチ透過に失敗。誤操作防止で停止")
    return@prepareHomeTouch
   }
   val freshSnap=readCurrent()
   val fresh=if(freshSnap!=null)choose(freshSnap) else null
   if(fresh?.kind!="HOME" || fresh.target==null){
    homeTrialInFlight=false
    HomeTrialJournal.outcome(service,trial.id,HomeOutcomePolicy.ABORTED,
     "入力前にHOME画面が変化したため操作を見送り")
    homeTrace("screen_changed_before_touch",trial.id,
     "kind=${fresh?.kind ?: "NO_ROOT"}; stale node is never clicked")
    update("HOME操作前の再判定で画面が変化。旧座標でのタップは禁止")
    return@prepareHomeTouch
   }
   val active=fresh.target
   val rect=active.rect
   val xy=pointFor(rect)
   if(!rect.contains(xy.x,xy.y) || rect.width()<100 ||
     !rect.contains(xy.x+trial.tinyDriftPx,xy.y)){
    homeTrialInFlight=false
    HomeTrialJournal.outcome(service,trial.id,HomeOutcomePolicy.ABORTED,
     "入力直前の最新ボタン座標が試行条件と一致しない")
    homeTrace("geometry_rejected",trial.id,"fresh=${rect.toShortString()} xy=${xy.x},${xy.y}")
    update("HOME再判定でボタン位置が不適切。今回の操作を見送り")
    return@prepareHomeTouch
   }
   homeLastTrialAt=SystemClock.elapsedRealtime()
   homeTrace("dispatch",trial.id,
    "mode=${trial.mode} xy=(${xy.x},${xy.y}) freshBounds=${rect.toShortString()} " +
     "freshNodes=${freshSnap?.count} remaining=${fresh.detail.take(100)}")
   when(trial.mode){
    HomeTouchTrials.Mode.GESTURE->{
     update("$label／タッチ (${xy.x},${xy.y}) ${trial.durationMs}ms")
     gestureTap(rect,"HOME試行 ${trial.id}",trial.durationMs,xy,trial.tinyDriftPx)
    }
    HomeTouchTrials.Mode.NODE_CLICK->{
     val ok=active.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
     homeTrialInFlight=false
     homeTrace("action_click_result",trial.id,"button ACTION_CLICK=$ok")
     update("$label／ボタンACTION_CLICK=$ok（遷移は未確認）")
    }
    HomeTouchTrials.Mode.PARENT_CLICK->{
     val parent=active.node.parent
     val parentRect=Rect()
     parent?.getBoundsInScreen(parentRect)
     val valid=HomeTouchTrials.parentIsNarrow(rect.width(),rect.height(),
      parentRect.width(),parentRect.height(),parentRect.contains(rect),
      parent!=null && parent.isClickable && parent.isEnabled && parent.isVisibleToUser)
     val ok=if(valid)parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true else false
     homeTrialInFlight=false
     homeTrace("parent_click_result",trial.id,
      "parentRect=${parentRect.toShortString()} parentValid=$valid ACTION_CLICK=$ok")
     update("$label／親ノード有効=$valid／ACTION_CLICK=$ok（遷移は未確認）")
    }
   }
   h.postDelayed({
    if(running && generation==gen){
     val after=readCurrent()
     val state=if(after!=null)choose(after) else null
     homeTrace("after_2s",trial.id,
      "kind=${state?.kind ?: "NO_ROOT"} nodes=${after?.count ?: 0} " +
       "tapEvent=$homeClickObserved detail=${state?.detail?.take(160) ?: ""}")
     if(after!=null)service.saveVisualScreenshot("HOME after ${trial.id}",
      after.count,false)
    }
   },2_450L)
  }
  return true
 }
 /**
  * Try a single, small pull-down *outside* the purple button before forcing
  * any restart. Input injection occurs only while Torima's current HOME
  * button is uniquely identified. Both swipe and retry use fresh geometry.
  */
 private fun swipeThenRetapHome(decision:Decision,now:Long):Boolean{
  val firstButton=decision.target?:return true
  val display=service.resources.displayMetrics
  val initial=HomeSwipeRecoveryPolicy.strokeFor(
   firstButton.rect.left,firstButton.rect.top,firstButton.rect.right,
   display.widthPixels,display.heightPixels)
  homeSwipeAttempted=true
  if(initial==null){
   homeTrace("swipe_unavailable",HomeSwipeRecoveryPolicy.ID,
    "purple=${firstButton.rect.toShortString()} screen=${display.widthPixels}x${display.heightPixels}")
   update("HOMEの短い上スワイプに適切な余白がないため、安全に再起動へ切替")
   return recoverStalledHome(now)
  }
  homeSwipeActive=true
  homeTrialInFlight=true
  homeTrialGeneration++
  val touchToken=homeTrialGeneration
  val generationAtStart=generation
  fun stillActive():Boolean=running && generation==generationAtStart &&
   touchToken==homeTrialGeneration && homeSwipeActive
  fun abort(reason:String){
   if(!stillActive())return
   homeSwipeActive=false
   homeTrialInFlight=false
   service.restoreHomeTouch()
   homeTrace("swipe_aborted",HomeSwipeRecoveryPolicy.ID,reason)
   update("HOMEスワイプ復旧：$reason。画面を再判定し、必要なら再起動")
   next(450L)
  }
  homeTrace("swipe_before_restart",HomeSwipeRecoveryPolicy.ID,
   "initialFailed=${homeTrialIndex} old=${firstButton.rect.toShortString()} " +
    "from=(${initial.x},${initial.fromY}) to=(${initial.x},${initial.toY}) " +
    "durationMs=${HomeSwipeRecoveryPolicy.SWIPE_DURATION_MS}")
  update("HOMEの紫ボタンを1回押しても進まず。まず短い上スワイプ→再タップを試行")
  service.prepareHomeTouch(initial.x,initial.fromY){ready,note->
   if(!stillActive())return@prepareHomeTouch
   if(!ready){
    abort("操作バー透過ができず：$note")
    return@prepareHomeTouch
   }
   val freshSnap=readCurrent()
   val fresh=if(freshSnap!=null)choose(freshSnap)else null
   val target=fresh?.target
   if(fresh?.kind!="HOME" || target==null){
    abort("スワイプ直前にHOME以外へ変化：${fresh?.kind ?: "NO_ROOT"}")
    return@prepareHomeTouch
   }
   val sw=HomeSwipeRecoveryPolicy.strokeFor(
    target.rect.left,target.rect.top,target.rect.right,
    display.widthPixels,display.heightPixels)
   if(sw==null){
    abort("最新画面では、紫ボタンに触れないスワイプ経路が取れない")
    return@prepareHomeTouch
   }
   val path=Path().apply{
    moveTo(sw.x.toFloat(),sw.fromY.toFloat())
    lineTo(sw.x.toFloat(),sw.toY.toFloat())
   }
   val gesture=GestureDescription.Builder().addStroke(
    GestureDescription.StrokeDescription(path,0,
     HomeSwipeRecoveryPolicy.SWIPE_DURATION_MS)).build()
   val accepted=service.dispatchGesture(gesture,
    object:AccessibilityService.GestureResultCallback(){
     override fun onCancelled(gestureDescription:GestureDescription?){
      abort("短い上スワイプがキャンセル")
     }
     override fun onCompleted(gestureDescription:GestureDescription?){
      if(!stillActive())return
      homeTrace("swipe_completed",HomeSwipeRecoveryPolicy.ID,
       "verticalFingerMove=${sw.toY-sw.fromY}px complete != content moved")
      h.postDelayed({
       if(!stillActive())return@postDelayed
       val after=readCurrent()
       val nextDecision=if(after!=null)choose(after)else null
       val nextButton=nextDecision?.target
       if(nextButton==null || !HomeSwipeRecoveryPolicy.freshlyIdentifiedHome(
         nextDecision?.kind.orEmpty(),nextButton.rect.left,nextButton.rect.top,
         nextButton.rect.right,nextButton.rect.bottom,
         display.widthPixels,display.heightPixels)){
        abort("スワイプ後は${nextDecision?.kind ?: "NO_ROOT"}。古い座標では押さない")
        return@postDelayed
       }
       val point=HomeTouchProfile.current(service,nextButton.rect)
       val shift=HomeSwipeRecoveryPolicy.measuredButtonShift(
        firstButton.rect.top,nextButton.rect.top)
       val moved=HomeSwipeRecoveryPolicy.buttonActuallyMoved(
        firstButton.rect.top,nextButton.rect.top)
       homeTrace("swipe_fresh_home",HomeSwipeRecoveryPolicy.ID,
        "old=${firstButton.rect.toShortString()} " +
         "new=${nextButton.rect.toShortString()} dy=${shift}px " +
         "contentMoved=${moved} tap=(${point.x},${point.y})")
       update(if(moved)
        "HOMEを短く上スワイプ：紫ボタンが縦に${shift}px変化。移動後の位置で再タップ"
       else "HOMEを短く上スワイプ：ボタンの位置変化は未確認。現在の位置を再取得して再タップ")
       service.prepareHomeTouch(point.x,point.y){tapReady,tapNote->
        if(!stillActive())return@prepareHomeTouch
        if(!tapReady){
         abort("再タップ前の操作バー透過に失敗：$tapNote")
         return@prepareHomeTouch
        }
        val latestSnapshot=readCurrent()
        val latest=if(latestSnapshot!=null)choose(latestSnapshot)else null
        val current=latest?.target
        if(current==null || !HomeSwipeRecoveryPolicy.freshlyIdentifiedHome(
          latest?.kind.orEmpty(),current.rect.left,current.rect.top,
          current.rect.right,current.rect.bottom,
          display.widthPixels,display.heightPixels)){
         abort("タップ直前は${latest?.kind ?: "NO_ROOT"}。再操作を中止")
         return@prepareHomeTouch
        }
        val currentPoint=HomeTouchProfile.current(service,current.rect)
        if(!current.rect.contains(currentPoint.x,currentPoint.y)){
         abort("再取得した紫ボタン内に学習タップ位置がない")
         return@prepareHomeTouch
        }
        val trial=HomeTouchTrials.swipeRecoveryRetry
        homeLastTrial=trial
        homeLastTrialAt=SystemClock.elapsedRealtime()
        homeTrialIndex++
        homeClickObserved=false
        HomeTrialJournal.beginAttempt(service,trial.id,trial.label,
         trial.mode.name,"上スワイプ後の最新紫ボタン ${current.rect.toShortString()}" +
          " point=(${currentPoint.x},${currentPoint.y})")
        homeTrace("swipe_retry_dispatch",trial.id,
         "tap=${currentPoint.x},${currentPoint.y} " +
          "button=${current.rect.toShortString()} " +
          "swipeDone=true methods=${homeTrialIndex}")
        homeSwipeActive=false
        // gestureTap's callback will clear homeTrialInFlight after completion.
        gestureTap(current.rect,"HOME試行 ${trial.id}",
         trial.durationMs,currentPoint)
        next(500L)
       }
      },HomeSwipeRecoveryPolicy.AFTER_SWIPE_SETTLE_MS)
     }
    },null)
   if(!accepted)abort("短い上スワイプがAndroidに拒否された")
  }
  return true
 }
 /**
  * HOME failure recovery is triggered only while the CURRENT screen is HOME.
  * Settings UI is verified before any Force stop is pressed; a blocked OEM
  * settings screen produces a logged soft relaunch rather than blind taps.
  */
 private fun recoverStalledHome(now:Long):Boolean{
  if(homeRecoveryActive)return true
  if(homeRecoveryAttempts>=HomeRecoveryPolicy.MAX_RECOVERIES_PER_RUN)return false
  val last=homeLastTrial?.id ?: "NONE"
  homeTrace("restart_requested",last,
   "trials=$homeTrialIndex homeForMs=${now-homeLastTrialAt} currentHome=true")
  homeLastTrial?.let{HomeTrialJournal.outcome(service,it.id,
   HomeOutcomePolicy.NO_NAVIGATION,"HOMEが変わらないため再起動復旧へ切替")}
  homeRecoveryAttempts++
  homeRecoveryActive=true
  val epoch=generation
  update("HOMEの入力${homeTrialIndex}方式で進まないため、トリマを一度だけ再起動")
  val accepted=service.runHomeRecovery {reopened,stopUi,reason->
   if(!running || generation!=epoch)return@runHomeRecovery
   homeRecoveryActive=false
   homeTrace("restart_result",last,
    "reopened=$reopened forceStopUi=$stopUi reason=$reason")
   if(!reopened){
    stopWithReason("HOME復旧でトリマを再起動できず：$reason")
   }else{
    resetHomeExperiment()
    clearDeferredHome()
    waitingKey=null
    fallbackAttempted=false
    gachaPhase=GachaSafety.Phase.NONE
    gachaPhaseSince=0L
    postAdClosedAt=0L
    postAdNextKind=""
    postAdNextSince=0L
    recentActions.clear()
    update("HOME復旧：${if(stopUi)"設定画面で強制停止操作後に再起動" else "通常の開き直し"}。HOMEを再確認")
    next(1200L)
   }
  }
  if(!accepted){
   homeRecoveryActive=false
   stopWithReason("HOME復旧の開始を拒否されたため停止")
  }
  return true
 }
 private fun handleHomeTrial(decision:Decision,snapshot:Snapshot,now:Long):Boolean{
  if(homeExperimentStartedAt==0L){
   if(decision.kind!="HOME" || decision.target==null)return false
   // No in-flight old action may be reused as a HOME click.
   waitingKey=null
   fallbackAttempted=false
   homeHoldAttempted=false
   homeExperimentStartedAt=now
   homeExperimentStartingCount=CollectionTaskPolicy.remaining(decision.detail)
   homeTrialList=HomeTouchTrials.ordered(HomeTouchProfile.bestTrialId(service))
   if(homeRecoveryAttempts>0 && HomeRecoveryPolicy.enabled(service))
    homeTrialList=homeTrialList.take(HomeRecoveryPolicy.POST_RESTART_TRIALS)
   homeTrialIndex=0
   homeLastTrial=null
   homeLastTrialAt=0L
   homeTrialInFlight=false
   homeNonHomeSince=0L
   homeNonHomeKind=""
   return tryHomePattern(decision,now)
  }
  if(HomeTouchTrials.isHomeKind(decision.kind)){
   homeNonHomeSince=0L
   homeNonHomeKind=""
   // Sometimes a reward count drops without exposing an intermediate popup.
   val previous=homeExperimentStartingCount
   val current=CollectionTaskPolicy.remaining(decision.detail)
   if(previous!=null && current!=null && current<previous){
    homeTrace("reward_decreased",homeLastTrial?.id ?: "NONE",
     "previous=$previous current=$current clickEvent=$homeClickObserved")
    homeLastTrial?.let{
     HomeTrialJournal.outcome(service,it.id,HomeOutcomePolicy.REWARD_DECREASED,
      "HOMEの残回数が${previous}→${current}に減少。操作回数とは別に観測")
     HomeTouchProfile.markSuccessfulTrial(service,it.id)
    }
    update("HOME操作後に残回数が${previous}→${current}へ減少。成功パターンを記録")
    resetHomeExperiment()
    return true
   }
   if(HomeTouchTrials.hasTimedOut(homeExperimentStartedAt,now)){
    stopWithReason("HOME診断が85秒経過。画面と試行ログを確認")
    return true
   }
   if(homeTrialInFlight){
    update("HOME試行：タッチ終了待ち。まだ画面遷移は確認できていません")
    return true
   }
   val sinceLast=if(homeLastTrialAt>0L) now-homeLastTrialAt else 0L
   // One failed HOME tap -> a short swipe-up before *any* second button
   // hypothesis or force-stop. Do not count a mere gesture callback as success.
   if(HomeSwipeRecoveryPolicy.shouldTry(
     HomeRecoveryPolicy.enabled(service),homeRecoveryAttempts,
     homeSwipeAttempted,decision.kind,decision.target!=null,
     homeTrialIndex,homeTrialInFlight,sinceLast)){
    homeLastTrial?.let{HomeTrialJournal.outcome(service,it.id,
     HomeOutcomePolicy.NO_NAVIGATION,
     "最初のタップ後もHOME。通常の二方式目より先に短い上スワイプへ")}
    return swipeThenRetapHome(decision,now)
   }
   val waitSeconds=sinceLast/1000L
   if(homeTrialIndex>0 &&
    sinceLast<HomeTouchTrials.DELAY_BETWEEN_TRIALS_MS){
    update(if(homeSwipeAttempted)
     "HOMEスワイプ後の再タップでも進まず。少し確認してから再起動復旧"
     else "HOMEの画面遷移なし。 ${waitSeconds}秒経過、上スワイプ前に確認中")
    return true
   }
   if(HomeRecoveryPolicy.shouldRecover(HomeRecoveryPolicy.enabled(service),
     homeTrialIndex,homeRecoveryAttempts,homeTrialInFlight,
     decision.kind=="HOME" && decision.target!=null,sinceLast)){
    return recoverStalledHome(now)
   }
   if(homeRecoveryAttempts>0 && HomeRecoveryPolicy.enabled(service) &&
      HomeRecoveryPolicy.exhaustedAfterRestart(
       homeTrialIndex,homeRecoveryAttempts,sinceLast)){
    homeTrace("restart_no_improvement",homeLastTrial?.id ?: "NONE",
     "afterRestartAttempts=$homeTrialIndex; no further kill or tap")
    stopWithReason("HOMEの再起動後も進まず。連打や再起動を繰り返さず停止")
    return true
   }
   if(homeTrialIndex>=homeTrialList.size){
    homeLastTrial?.let{HomeTrialJournal.outcome(service,it.id,
     HomeOutcomePolicy.NO_NAVIGATION,"全試行後もHOMEを確認。広告遷移なし")}
    homeTrace("all_methods_failed",homeLastTrial?.id ?: "NONE",
     "tries=${homeTrialIndex},clickEvent=$homeClickObserved,kind=${decision.kind},remaining=${decision.detail.take(120)}")
    stopWithReason("HOME入力方式を全7種類試しても遷移を確認できず。操作履歴と画像ZIPを確認")
    return true
   }
   if(decision.kind=="HOME_WAIT" || decision.target==null){
    update("HOME紫ボタンのクリック範囲が一時的に取得不可。座標試行せず再判定")
    return true
   }
   homeLastTrial?.let{HomeTrialJournal.outcome(service,it.id,
    HomeOutcomePolicy.NO_NAVIGATION,"7秒以上経過後もHOMEを確認。次方式へ切替")}
   return tryHomePattern(decision,now)
  }
  // The current screen no longer looks like HOME. Confirm a stable different
  // state before treating the preceding gesture as a successful button press.
  if(homeTrialInFlight){
   update("HOME試行後に画面が変化。ジェスチャーの終了を確認中")
   return true
  }
  if(homeNonHomeSince==0L || homeNonHomeKind!=decision.kind){
   homeNonHomeSince=now
   homeNonHomeKind=decision.kind
   update("HOMEから画面変化：${decision.kind}。安定表示の確認中")
   return true
  }
  // An UNKNOWN screen may be the HOME button's transient touch fade.
  // Never restart the whole trial sequence on a brief incomplete tree.
  if(decision.kind=="UNKNOWN" && now-homeNonHomeSince<4_000L){
   update("HOME試行後は画面認識が不安定。4秒以内は追加タップせず待機")
   return true
  }
  if(now-homeNonHomeSince<HomeTouchTrials.NON_HOME_CONFIRM_MS)return true
  val adEvidence=trustedAdEvidence(snapshot)
  val exactLearned=registeredLearnedCloseTrusted(decision,snapshot)
  val homeOutcome=HomeOutcomePolicy.classifyNewScreen(decision.kind,
   adEvidence!=null,exactLearned)
  val positive=HomeOutcomePolicy.isConfirmed(homeOutcome)
  homeLastTrial?.let{HomeTrialJournal.outcome(service,it.id,homeOutcome,
   "確認した画面=${decision.kind} / 広告証拠=${adEvidence ?: "なし"} / " +
    "学習済み完全一致=$exactLearned / nodes=${snapshot.count}")}
  val independentAdProof=adEvidence!=null
  homeTrace("screen_transition",homeLastTrial?.id ?: "NONE",
   "kind=${decision.kind} positive=$positive proof=$independentAdProof " +
    "nodes=${snapshot.count} clickEvent=$homeClickObserved")
  if(positive){
   val trial=homeLastTrial
   if(trial!=null){
    HomeTouchProfile.markSuccessfulTrial(service,trial.id)
    update("HOMEの画面遷移を確認：${decision.kind}。成功した${trial.durationMs}msを次回優先")
   }
  }else{
   update("HOMEから不明／未検証の画面に変化。成功パターンは保存せず安全判定へ")
  }
  if(decision.kind in listOf("UNKNOWN","OPAQUE") && homeLastTrial!=null){
   deferredHomeTrial=homeLastTrial
   deferredHomeSince=now
   update("HOME後の広告らしい画面を検出。次の明確な画面まで押し方の評価を保留")
  }
  resetHomeExperiment()
  return false
 }
 private fun gestureTap(rect:Rect,info:String,holdMs:Long=120L,
  point:Point?=null,tinyDriftPx:Int=0) {
  if(!running)return
  if(service.rootInActiveWindow?.packageName?.toString()!=TARGET){
   if(info.startsWith("HOME試行 "))homeTrialInFlight=false
   update("前面がトリマではないためジェスチャー中止")
   return
  }
  val x:Int=point?.x?:rect.centerX()
  val y:Int=point?.y?:rect.centerY()
  if(!rect.contains(x,y) || !rect.contains(x+tinyDriftPx,y)){
   stopWithReason("タップ位置が対象ボタンの範囲外のため停止")
   return
  }
  val path=Path().apply{
   moveTo(x.toFloat(),y.toFloat())
   if(tinyDriftPx!=0)lineTo((x+tinyDriftPx).toFloat(),y.toFloat())
  }
  val gesture=GestureDescription.Builder()
   .addStroke(GestureDescription.StrokeDescription(path,0,holdMs)).build()
  val id=generation
  val accepted=service.dispatchGesture(gesture,object:AccessibilityService.GestureResultCallback(){
   override fun onCompleted(gestureDescription:GestureDescription?){
    if(id!=generation||!running)return
    if(info.startsWith("HOME試行 ")){
     homeTrialInFlight=false
     homeTrace("gesture_completed",homeLastTrial?.id ?: "NONE",
      "x=$x y=$y holdMs=$holdMs clickEvent=$homeClickObserved (completion != app click)")
    }
    update("$info／ジェスチャー完了 ($x,$y) ${holdMs}ms／移動${tinyDriftPx}px。画面遷移確認待ち")
   }
   override fun onCancelled(gestureDescription:GestureDescription?){
    if(id!=generation||!running)return
    if(info.startsWith("HOME試行 "))homeTrialInFlight=false
    stopWithReason("ジェスチャーがキャンセルされたため停止")
   }
  },null)
  if(!accepted)stopWithReason("ジェスチャーが拒否されたため停止")
 }
 private fun stopWithReason(reason:String){
  running=false
  generation++
  h.removeCallbacks(poll)
  service.cancelHomeRecovery()
  homeRecoveryActive=false
  service.restoreHomeTouch()
  resetHomeExperiment()
  clearDeferredHome()
  postAdClosedAt=0L
  postAdNextKind=""
  postAdNextSince=0L
  HomeTrialJournal.endRun(service,"FAILED",reason)
  collection.finish("FAILED",reason)
  update("停止：$reason")
 }
 private fun completeCollection(reason:String){
  if(collection.isActive())collection.finish("COMPLETED",reason)
  running=false
  generation++
  h.removeCallbacks(poll)
  service.cancelHomeRecovery()
  homeRecoveryActive=false
  service.restoreHomeTouch()
  resetHomeExperiment()
  resetTabSwitch()
  HomeTrialJournal.endRun(service,"COMPLETED","移動タンク→歩数を両方回収済み：$reason")
  update("移動タンクと歩数の動画回収が完了。$reason")
 }

 /**
  * A text-only 0 is not required for the move tab: the user's provided MOVE
  * screenshot has NO "あとN回" at all, two gray reward buttons, and an
  * explicit "たくさん移動してマイルを獲得しよう！" empty-state message.
  *
  * The STEP screenshot has "今日の歩数", two valid reward buttons and
  * "あと6回獲得できます". Only advance/finish from a verified CURRENT tab.
  */
 private fun monitorCollection(snap:Snapshot,now:Long):Boolean{
  if(blockedRewardObservation(snap))return false
  if(rewardPhase==RewardTabPolicy.Phase.SWITCHING_TO_STEPS){
   return switchToSteps(snap,now)
  }
  val tab=selectedRewardTab(snap)
  val desired=if(rewardPhase==RewardTabPolicy.Phase.MOVE)
   RewardTabPolicy.Tab.MOVE else RewardTabPolicy.Tab.STEPS
  val divider=rewardSeparator(snap)
  if(tab!=desired){
   clearEmptyObservation()
   if(inspectBadgesWhenStartingOnSteps(snap,now))return true
   // If the user started from the step tab, return to MOVE before processing.
   // Similarly recover the step tab if they manually selected MOVE mid-run.
   if(divider!=null && tab!=RewardTabPolicy.Tab.UNKNOWN){
    if(tabSwitchStartedAt==0L)tabSwitchStartedAt=now
    if(now-tabSwitchStartedAt>RewardTabPolicy.STEP_SWITCH_TIMEOUT_MS){
     stopWithReason("${desired.name}タブに28秒間戻れず停止")
     return true
    }
    return clickRewardTab(snap,desired,now)
   }
   return false
  }
  if(tabSwitchStartedAt!=0L)resetTabSwitch()
  if(divider==null || !collection.isActive()){
   clearEmptyObservation()
   return false
  }
  val count=currentRemaining(snap)
  val videoEnabled=upperVideoButtonEnabled(snap,divider)
  val moveEmptyPrompt=snap.has("たくさん移動してマイルを獲得しよう")
  val stepHero=snap.has("今日の歩数") ||
   (snap.has("今日の") && snap.has("歩数"))
  if(count!=null){
   clearEmptyObservation()
   val finished=collection.observeHome(count,now)
   if(finished){
    if(rewardPhase==RewardTabPolicy.Phase.MOVE){
     beginStepTabSwitch("移動HOMEで残り0回を複数回確認")
    }else{
     completeCollection("歩数HOMEで残り0回を複数回確認")
    }
    return true
   }
   if(count==0){
    update(if(rewardPhase==RewardTabPolicy.Phase.MOVE)
     "移動の残り0回を確認。歩数へ切り替える前に再確認"
     else "歩数の残り0回を確認。終了前に再確認")
    next(850L)
    return true
   }
   return false
  }
  val moveEmpty=RewardTabPolicy.moveOutOfRewards(
   count,tab==RewardTabPolicy.Tab.MOVE,true,moveEmptyPrompt,
   videoEnabled,snap.has("タンクが満タン"),false)
  val stepEmpty=RewardTabPolicy.stepOutOfRewards(
   count,tab==RewardTabPolicy.Tab.STEPS,true,stepHero,
   videoEnabled,false) && snap.count>=15
  val expectedEmpty=if(rewardPhase==RewardTabPolicy.Phase.MOVE)
   moveEmpty else stepEmpty
  if(expectedEmpty){
   val stable=observeEmptyHome(now,if(rewardPhase==RewardTabPolicy.Phase.MOVE)
    "移動画面で残回数表示が消え、報酬ボタンが灰色" else
    "歩数画面で残回数表示が消え、動画ボタンが無効")
   if(stable){
    if(rewardPhase==RewardTabPolicy.Phase.MOVE)
     beginStepTabSwitch("移動タンク：灰色ボタンと空状態を複数回確認")
    else completeCollection("歩数：灰色ボタンと回収不能な状態を複数回確認")
   }
   return true
  }
  clearEmptyObservation()
  return false
 }

 // Play Store may appear as another app or as an accessible modal window.
 private fun activePackage():String{
  val foregroundRoot=service.rootInActiveWindow
  val active=foregroundRoot?.packageName?.toString().orEmpty()
  if(active=="com.android.vending" || service.isPlayBrandedInstallPopup(foregroundRoot)){
   return "com.android.vending"
  }
  try{
   val modal=service.windows?.firstOrNull{window->
    if(!window.isActive && !window.isFocused)return@firstOrNull false
    val root=window.root?:return@firstOrNull false
    val pkg=root.packageName?.toString().orEmpty()
    if(pkg=="com.android.vending")return@firstOrNull true
    // Some install pop-ups are rendered by Google Play services.
    // Require Play branding AND an install call to action, not merely a GMS process.
    if(pkg=="com.google.android.gms"){
     val labels=ArrayList<String>()
     var count=0
     fun walk(node:AccessibilityNodeInfo){
      if(count>=90)return
      count++
      labels.add(node.text?.toString().orEmpty())
      labels.add(node.contentDescription?.toString().orEmpty())
      for(i in 0 until node.childCount){
       if(count>=90)break
       node.getChild(i)?.let{walk(it)}
      }
     }
     walk(root)
     return@firstOrNull labels.any{it.contains("Google Play",true) || it.contains("Play ストア",true)} &&
      labels.any{it.contains("インストール") || it.contains("Install",true)}
    }
    false
   }
   if(modal!=null)return "com.android.vending"
  }catch(_:Exception){}
  return active
 }
 private fun returnToTorima():Boolean{
  return try{
   val intent=service.packageManager.getLaunchIntentForPackage(TARGET)?:return false
   intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
    Intent.FLAG_ACTIVITY_SINGLE_TOP)
   service.startActivity(intent)
   true
  }catch(_:Exception){false}
 }
 /**
  * Explicit user opt-in: while automation is running, ALWAYS return from a verified
  * Google Play app or branded install popup, even if the originating app is unknown.
  * Never tap an install, purchase, or permission button.
  * Try Back, then foreground Torima; stop if repeated attempts cannot restore it.
  */
 private fun recoverPlay(now:Long):Boolean{
  val pkg=activePackage()
  if(pkg==TARGET){
   if(playRecoveryStep!=0){
    resetHomeExperiment()
    collection.playReturned()
    update("Google Playからトリマへの復帰を確認。現在画面を再判定")
    waitingKey=null
    fallbackAttempted=false
    homeHoldAttempted=false
    playRecoveryStep=0
    recentActions.clear()
    lastKind=""
   }
   return false
  }
  if(pkg=="com.android.vending"){
   if(playRecoveryStep==0){
    resetHomeExperiment()
    if(playRecoveryAttempts>=4){
     stopWithReason("Google Playへの復帰が連続4回発生したため停止")
     return true
    }
    playRecoveryAttempts++
    service.consumePlayTransitionProof()
    playRecoveryStep=1
    playRecoveryAt=now
    waitingKey=null
    fallbackAttempted=false
    homeHoldAttempted=false
    val accepted=service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    update("Google Playを検出。遷移元に関係なく戻る1回目=$accepted（復帰$playRecoveryAttempts/4）")
    if(!accepted){
     playRecoveryStep=2
     playRecoveryAt=now-2200L
    }
    return true
   }
   val elapsed=now-playRecoveryAt
   if(playRecoveryStep==1 && elapsed>=1800L){
    playRecoveryStep=2
    playRecoveryAt=now
    val launched=returnToTorima()
    update("Google Playが残存。トリマを前面に起動=$launched")
    if(!launched){
     playRecoveryStep=3
     playRecoveryAt=now-2400L
    }
    return true
   }
   if(playRecoveryStep==2 && elapsed>=2400L){
    playRecoveryStep=3
    playRecoveryAt=now
    val accepted=service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    update("まだGoogle Play。最後のAndroid戻る=$accepted")
    return true
   }
   if(playRecoveryStep==3 && elapsed>=2300L){
    playRecoveryStep=4
    playRecoveryAt=now
    val launched=returnToTorima()
    update("トリマを再度前面へ戻す=$launched")
    if(!launched)stopWithReason("トリマを起動できませんでした")
    return true
   }
   if(playRecoveryStep==4 && elapsed>=7000L){
    stopWithReason("Google Playから戻れなかったため停止")
   }
   return true
  }
  if(playRecoveryStep in 1..4){
   val elapsed=now-playRecoveryAt
   if(playRecoveryStep<4 && elapsed>=2600L){
    playRecoveryStep=4
    playRecoveryAt=now
    val launched=returnToTorima()
    update("Playを閉じたがトリマ未確認。前面復帰=$launched")
    if(!launched)stopWithReason("トリマへの復帰に失敗")
   }else if(playRecoveryStep==4 && elapsed>=7000L){
    stopWithReason("復帰先がトリマではないため停止")
   }else{
    update("Playからの画面切り替えを確認中: "+pkg.take(55))
   }
   return true
  }
  return false
 }

 /**
  * A HOME touch can open a video with zero useful accessibility nodes.
  * Record the successful *navigation*, not reward completion, only once a
  * recognisable screen beyond that video is actually observed.
  */
 private fun verifyDeferredHome(decision:Decision,snapshot:Snapshot,now:Long){
  val trial=deferredHomeTrial?:return
  if(now-deferredHomeSince>120_000L || decision.kind in listOf("HOME","HOME_WAIT")){
   clearDeferredHome()
   return
  }
  val recognisableReward=decision.target!=null &&
   decision.kind in listOf("RETRY","SKIP_NO","W_CHANCE","SHARE_CLOSE")
  val provenAd=decision.target!=null &&
   decision.kind in listOf("AD_CLOSE","LEARNED","LEARNED_TEXT","LEARNED_ICON") &&
   trustedAdEvidence(snapshot)!=null
  if(recognisableReward || provenAd){
   HomeTrialJournal.outcome(service,trial.id,
    if(provenAd)HomeOutcomePolicy.AD_OPEN_CONFIRMED
    else HomeOutcomePolicy.NEXT_SCREEN_CONFIRMED,
    "不明画面の後に${decision.kind}を確認。広告証拠=${trustedAdEvidence(snapshot) ?: "なし"}")
   HomeTouchProfile.markSuccessfulTrial(service,trial.id)
   update("HOMEの${trial.durationMs}msタッチ後に${decision.kind}を確認。操作方法を次回優先（報酬回収は未確定）")
   clearDeferredHome()
  }
 }
 /**
  * A closed video can temporarily expose a bottom X during gacha loading.
  * Protect the four seconds right after exit. Once the next *actual* screen
  * is stable, even if it is another ad, normal screen-specific actions resume.
  */
 private fun protectPostAdTransition(decision:Decision,snapshot:Snapshot,now:Long):Boolean{
  if(FastAdMode.enabled(service)){
   // Explicit experimental option: no post-ad quiet time, stable-screen
   // wait or 90-second transition lock. Other gacha UI stays distinct.
   postAdClosedAt=0L
   postAdNextKind=""
   postAdNextSince=0L
   return false
  }
  if(postAdClosedAt==0L)return false
  val waited=now-postAdClosedAt
  if(PostAdTransitionPolicy.timedOut(waited)){
   stopWithReason("広告終了後の遷移先を90秒識別できず、安全停止")
   return true
  }
  if(PostAdTransitionPolicy.quietPeriodActive(waited)){
   postAdNextKind=""
   postAdNextSince=0L
   update("広告終了後のガチャ読み込み保護：${waited/1000L}/4秒。×を押さず待機")
   return true
  }
  val proof=adEvidence(decision,snapshot,now)
  val exact=registeredLearnedCloseTrusted(decision,snapshot)
  val current=decision.kind
  val hasTarget=decision.target!=null || decision.touch!=null
  val cleanNext=current in PostAdTransitionPolicy.nextScreens &&
   decision.target!=null && proof==null
  val nextAd=current in PostAdTransitionPolicy.adActions &&
   hasTarget && (proof!=null || exact)
  if(!cleanNext && !nextAd){
   postAdNextKind=""
   postAdNextSince=0L
   update("広告後の画面を再確認中：${current}。不明な×は押さない")
   return true
  }
  val stableKey="${decision.key}|${proof ?: if(exact) "EXACT" else "NEXT"}"
  if(postAdNextKind!=stableKey){
   postAdNextKind=stableKey
   postAdNextSince=now
   update("広告後に${current}を確認。表示の安定を待機")
   return true
  }
  val stableFor=now-postAdNextSince
  val ready=if(nextAd)
   PostAdTransitionPolicy.canContinueOnVerifiedAd(current,hasTarget,
    proof!=null || exact,waited,stableFor)
  else PostAdTransitionPolicy.canLeaveTransition(current,decision.target!=null,
   proof!=null,stableFor)
  if(!ready){
   update("広告後の${current}確認中：${stableFor/1000L}秒。再確認してから操作")
   return true
  }
  postAdClosedAt=0L
  postAdNextKind=""
  postAdNextSince=0L
  // Never retry an unchanged previous close target as a *new* reward cycle.
  if(waitingKey!=decision.key){
   waitingKey=null
   fallbackAttempted=false
  }
  verifiedAdKey=""
  verifiedAdSince=0L
  update("広告後の${current}を確認。通常の判定に復帰")
  return false
 }
 private fun check(){
  if(!running)return
  try {
   val now=SystemClock.elapsedRealtime()
   if(collection.budgetExceeded() ||
    (runStartedElapsed>0L &&
     now-runStartedElapsed>=CollectionTaskPolicy.BUDGET_MS)){
    stopWithReason("移動＋歩数の実行時間が合計3時間に達したため未完了として停止")
    return
   }
   if(homeRecoveryActive){
    next(950)
    return
   }
   if(recoverPlay(now)){
    next(950)
    return
   }
   val top=activePackage()
   if(top!=TARGET){
    update("他アプリ／画面遷移中：自動操作は待機（package="+top.take(60)+"）")
    next()
    return
   }
   val snapshot=readCurrent()
   if(snapshot==null){
    waitingKey=null
    update("トリマ以外の画面。操作なし（トリマへ戻ると再判定）")
    next()
    return
   }
   if(monitorCollection(snapshot,now))return
   val initial=choose(snapshot)
   if(gachaPhase==GachaSafety.Phase.AWAIT_REWARD ||
    initial.kind in listOf("UNKNOWN","OPAQUE","AD_VISUAL_X","AD_VISUAL_NEXT") ||
   (VisualLearnedActions.count(service)>0 &&
     (videoAdSessionActive || initial.kind in listOf("AD_CLOSE","AD_SKIP",
      "AD_VISUAL_X","AD_VISUAL_NEXT")))){
    probeLiveOverlay(now)
   }
   val decision=choose(snapshot)
   if(decision.kind in listOf("HOME","HOME_WAIT","W_CHANCE","CARDS",
     "CARD_RESULT","SHARE_CLOSE"))videoAdSessionActive=false
   observeStableAdClose(decision,snapshot,now)
   // A visual glyph requires independent ad evidence and stable frames.
   // Do not photograph continuously; save at most one difficult screen per 35s.
   if(decision.kind=="UNKNOWN" || decision.kind=="OPAQUE"){
    if(visualUnknownSince==0L)visualUnknownSince=now
    if(now-visualUnknownSince>=6_000L &&
      (visualLastCaptureAt==0L || now-visualLastCaptureAt>=35_000L)){
     visualLastCaptureAt=now
     service.saveVisualScreenshot("自動検出不能：${decision.kind}",
      snapshot.count,false)
    }
   }else visualUnknownSince=0L
   verifyDeferredHome(decision,snapshot,now)
   collection.stage(decision.kind)
   if(protectPostAdTransition(decision,snapshot,now)){
    next(900)
    return
   }
   // Bound repeated Play loops within a single ad, but do not penalize valid new reward cycles.
   if(decision.kind in listOf("HOME","SKIP_NO","RETRY","W_CHANCE",
     "CARDS","CARD_RESULT","SHARE_CLOSE")){
    playRecoveryAttempts=0
   }
   if(decision.kind!=lastKind){
    lastKind=decision.kind
    if(LearnedClosePolicy.isLearnedKind(decision.kind))learnedTextSeenAt=now
    if(decision.kind=="UNKNOWN" || decision.kind=="OPAQUE" ||
     decision.kind=="HOME_WAIT" || decision.kind=="SHARE")unknownSince=now else unknownSince=0L
   }
   // An ongoing gacha cycle has absolute priority over all ad-close and
   // learned rules. Never dismiss an animation just because a clickable X exists.
   if(gachaProtects(decision,now,snapshot)){
    next()
    return
   }
   // HOME experiments are independent from the generic click fallback.
   // This must also run for HOME_WAIT, which occurs while the button fades.
   if(handleHomeTrial(decision,snapshot,now)){
    next(900)
    return
   }
   if(decision.kind=="CARD_RESULT" && gachaPhase==GachaSafety.Phase.AWAIT_RESULT){
    if(cardResultVisibleSince==0L)cardResultVisibleSince=now
    if(now-cardResultVisibleSince<2500L){
     update("ガチャ結果を確認中：閉じる前に2.5秒安定表示を待機")
     next(850)
     return
    }
   }else if(decision.kind!="CARD_RESULT"){
    cardResultVisibleSince=0L
   }
   // Button captions alone are unsafe: distinguish an ad SDK from a gacha.
   if(GachaSafety.requiresAdEvidence(decision.kind)){
    val proof=adEvidence(decision,snapshot,now)
    val exactLearned=registeredLearnedCloseTrusted(decision,snapshot)
    if(proof==null && !exactLearned){
     verifiedAdKey=""
     verifiedAdSince=0L
     if(unknownSince==0L)unknownSince=now
     if(now-unknownSince>=120000L){
      stopWithReason("画面を2分間識別できないため停止。診断ZIPで読み込み画面を確認")
      return
     }
     if(transientGachaLoading(snapshot,now)){
      update("ガチャ演出／画面読み込み中を検出。短時間の×は押さず再確認")
      next(StableAdClosePolicy.POLL_WHEN_UNSURE_MS)
      return
     }
     if(decision.kind=="AD_CLOSE" && stableCloseSince>0L){
      val seconds=(now-stableCloseSince)/1000.0
      val minMs=if(gachaPhase==GachaSafety.Phase.AWAIT_REWARD)
       StableAdClosePolicy.AFTER_GACHA_STABLE_MS else StableAdClosePolicy.STABLE_MS
      update("広告の右上×を確認：${"%.1f".format(seconds)}秒 / ${minMs/1000}秒の安定待ち。完了後に自動で押す")
      next(StableAdClosePolicy.POLL_WHEN_UNSURE_MS)
      return
     }
     update(if(decision.kind=="AD_CLOSE")
      "下部または不明な×を検出。ガチャの読み込み用×と区別して再確認中"
      else "広告固有の情報を取得できず。現在の画面を再判定中")
     next(StableAdClosePolicy.POLL_WHEN_UNSURE_MS)
     return
    }
    unknownSince=0L
    // Gacha animation might momentarily expose an X. Require a stable, clearly
    // identified ad *and target* over consecutive polls before acting.
    val proofSource=proof?: "LEARNED_REGISTERED"
    val key="$proofSource|${decision.key}"
    if(verifiedAdKey!=key){
     verifiedAdKey=key
     verifiedAdSince=now
    }
    val stableWait=if(decision.registeredLearned)
     LearnedClosePolicy.STABLE_FOR_MS else 1_700L
    if(now-verifiedAdSince<stableWait){
     update(if(decision.registeredLearned)
      "登録済み操作を照合完了。画面安定を約1.4秒確認してからタップ"
     else "閉じる操作の安定待ち：$proofSource。1.7秒継続してから操作")
     next(850)
     return
    }
   }else{
    verifiedAdKey=""
    verifiedAdSince=0L
   }
   if(waitingKey!=null){
    if(waitingKey==decision.key){
     val seconds=(now-waitingSince)/1000
     if(decision.kind in listOf("W_CHANCE","CARDS","CARD_RESULT")){
      // A second click during animation can skip the gacha. Never retry here.
      if(seconds>=25){
       stopWithReason("ガチャ操作後の画面が25秒変わらないため停止")
       return
      }
      update("ガチャ操作後の遷移待機 ${seconds}秒：重複タップ禁止")
      next()
      return
     }

     val elapsed=now-waitingSince
     val canRetry=if(decision.registeredLearned)
      LearnedClosePolicy.canRetry(decision.kind,true,fallbackAttempted,elapsed)
     else !fallbackAttempted && elapsed>=4_000L
     if(canRetry && decision.target!=null){
      fallbackAttempted=true
      if(decision.registeredLearned){
       // The first action uses the taught touch point. If an unchanged
       // CURRENT target is visible, try a different input method just once.
       val clicked=decision.target.node.isClickable &&
        decision.target.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
       update("${decision.display}：数秒後も同じ画面。再照合して1回だけ再試行（ACTION_CLICK=$clicked）")
       if(!clicked)gestureTap(decision.target.rect,decision.display,
        120L,decision.touch)
      }else{
       update("${decision.display}：命令後も同じ画面。確認済み箇所を1回だけタップ")
       gestureTap(decision.target.rect,decision.display,
        point=if(decision.kind in listOf("LEARNED_TEXT","LEARNED_ICON"))decision.touch else null)
      }
      next()
      return
     }
     val shouldStop=if(decision.registeredLearned)
      LearnedClosePolicy.shouldStopWaiting(decision.kind,elapsed)
     else elapsed>=13_000L
     if(shouldStop){
      stopWithReason("$lastActionTitle の操作後も画面が変わらないため、安全停止")
      return
     }
     update("${decision.display}：画面遷移確認待ち ${seconds}秒")
     next()
     return
    }
    waitingKey=null
    fallbackAttempted=false
    homeHoldAttempted=false
   }
   // Registered rules already had a short stable-screen check above.
   // The old unconditional extra 4s delay made learning ineffective.
   if(decision.target==null &&
     decision.kind in listOf("AD_VISUAL_X","AD_VISUAL_NEXT","LEARNED_VISUAL")){
    val point=decision.touch?:run{
     stopWithReason("画像学習候補にタップ座標がないため停止")
     return
    }
    val width=service.resources.displayMetrics.widthPixels
    val height=service.resources.displayMetrics.heightPixels
    val permitted=if(decision.kind=="LEARNED_VISUAL"){
     val kind=visualLearned?.type.orEmpty()
     if(kind=="TOP_CLOSE_OR_SKIP")point!=null &&
      point.y in 0..(height*34/100) &&
      (point.x>=width*57/100 || point.x<=width*21/100)
     else kind=="BOTTOM_AD_CLOSE" && point!=null &&
      point.y in (height*72/100)..(height*96/100)
    }else point!=null && point.x in (width*72/100)..width &&
     point.y in 0..(height*18/100)
    if(!permitted){
     stopWithReason("画像候補が画面上部の許可領域外。停止")
     return
    }
    val previous=recentActions[decision.key]?:0L
    val quiet=if(decision.kind=="LEARNED_VISUAL")3_500L else 6_000L
    if(previous>0L && now-previous<quiet){
     update("画像マークの連続タップを抑制。再判定中")
     next()
     return
    }
    if(steps>=MAX_ACTIONS){
     stopWithReason("画像操作の上限に達したため停止")
     return
    }
    recentActions[decision.key]=now
    steps++
    waitingKey=decision.key
    waitingSince=now
    lastActionTitle=decision.display
    fallbackAttempted=false
    if(FastAdMode.shouldStartPostAdGuard(FastAdMode.enabled(service),
      PostAdTransitionPolicy.isAdTransitionAction(decision.kind))){
     postAdClosedAt=now
     postAdNextKind=""
     postAdNextSince=0L
    }
    liveVisual=null
    liveVisualCount=0
    val safeBounds=Rect(point.x-30,point.y-30,point.x+31,point.y+31)
    update(if(decision.kind=="LEARNED_VISUAL")
     "${decision.display}：現在の画面と画像学習が一致、教えた位置を操作"
     else "${decision.display}：確定した画面右上のマークを操作")
    gestureTap(safeBounds,decision.display,120L,point)
    next()
    return
   }
   if(decision.target==null){
    update("${decision.display}：操作なし。${decision.detail}")
    if(unknownSince>0 && now-unknownSince>=120000L){
     stopWithReason("不明画面が2分以上続いたため停止（${decision.display}）")
     return
    }
    next()
    return
   }
   val last=recentActions[decision.key]?:0L
   val cooldown=if(FastAdMode.enabled(service) &&
    decision.kind in listOf("AD_CLOSE","AD_VISUAL_X","AD_VISUAL_NEXT"))
    2_500L else LearnedClosePolicy.repeatCooldown(decision.kind)
   if(now-last<cooldown){
    update("${decision.display}：同一画面の連続操作を抑止中")
    next()
    return
   }
   if(steps>=MAX_ACTIONS){
    stopWithReason("一度の起動で${MAX_ACTIONS}操作に達したため停止")
    return
   }
   recentActions[decision.key]=now
   while(recentActions.size>35)recentActions.remove(recentActions.keys.first())
   steps++
   waitingKey=decision.key
   waitingSince=now
   lastActionTitle=decision.display
   fallbackAttempted=false
   homeHoldAttempted=false

   if(decision.kind in listOf("W_CHANCE","CARDS","CARD_RESULT")){
    markGachaAction(decision.kind,now)
   }
   if(decision.kind in listOf("SKIP_NO","RETRY")){
    videoAdSessionActive=true
    resetLiveVisual()
    update("動画再生へ進む操作を実行。広告表示中は画像の×検出も使用")
   }
   if(PostAdTransitionPolicy.isAdTransitionAction(decision.kind)){
    if(FastAdMode.enabled(service)){
     postAdClosedAt=0L
     postAdNextKind=""
     postAdNextSince=0L
     update("高速実験モード：広告終了後の4秒保護・次画面待機をスキップ")
    }else{
     postAdClosedAt=now
     postAdNextKind=""
     postAdNextSince=0L
     update("広告の終了操作を実行：次の画面へ切り替わるまで追加の×を禁止")
    }
   }
   if(decision.registeredLearned){
    // Use the user's taught coordinate even if the node advertises
    // ACTION_CLICK: click callbacks may be true but ignored by ad SDK.
    update("${decision.display}：学習したタップ位置を優先して操作")
    gestureTap(decision.target.rect,decision.display,120L,decision.touch)
    next()
    return
   }
   val clicked=decision.target.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
   update("${decision.display}：ACTION_CLICK=$clicked。遷移確認待ち")
   if(!clicked){
    fallbackAttempted=true
    gestureTap(decision.target.rect,decision.display,
     point=if(decision.kind in listOf("LEARNED_TEXT","LEARNED_ICON"))decision.touch else null)
   }
   next()
  }catch(e:Exception){
   stopWithReason("例外 ${e.javaClass.simpleName}。誤操作防止のため停止")
  }
 }
}
