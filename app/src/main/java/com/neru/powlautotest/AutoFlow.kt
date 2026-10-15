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
 private var visualProbePending=false
 private var visualProbeLast=0L
 private var lastKind=""
 private var learnedTextSeenAt=0L
 // Gacha animations and ads may both display an X; keep their phases distinct.
 private var gachaPhase=GachaSafety.Phase.NONE
 private var gachaPhaseSince=0L
 private var cardResultVisibleSince=0L
 private var verifiedAdKey=""
 private var verifiedAdSince=0L
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
  val exactLearned:Boolean=false
 )

 private fun resetLiveVisual(){
  liveVisualAt=0L;liveVisualSince=0L;liveVisualCount=0
  liveVisual=null;visualProbePending=false;visualProbeLast=0L
 }
 /**
  * Sample transient overlay glyphs, not screenshots of entire advertisers.
  * Never treat one screenshot or an icon alone as tap authorization.
  */
 private fun probeLiveOverlay(now:Long){
  if(visualProbePending || now-visualProbeLast<1_350L)return
  visualProbePending=true;visualProbeLast=now
  val expectedGeneration=generation
  service.probeOverlayIcons { markers ->
   visualProbePending=false
   if(!running || generation!=expectedGeneration || markers==null)return@probeOverlayIcons
   val candidate=markers.filter{it.score>=85 && it.region=="top-right"}
    .sortedWith(compareBy<OverlayIconDetector.Marker>{if(it.kind=="NEXT")0 else 1}
     .thenByDescending{it.score}).firstOrNull()
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
 private fun visualOverlayDecision(snap:Snapshot):Decision?{
  val marker=liveVisual?:return null
  val now=SystemClock.elapsedRealtime()
  if(now-liveVisualAt>2_800L || liveVisualCount<2 || now-liveVisualSince<1_600L)return null
  if(trustedAdEvidence(snap)==null)return null
  val x=marker.x;val y=marker.y
  val point=if(marker.kind=="NEXT")Point(x-30,y) else Point(x,y)
  val kind=if(marker.kind=="NEXT")"AD_VISUAL_NEXT" else "AD_VISUAL_X"
  return Decision(kind,"画像検出：${if(marker.kind=="NEXT")"進む二重矢印" else "×"}",
   kind,null,"連続${liveVisualCount}フレーム＋広告の独立証拠あり",point)
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
  TeachOverlay.recordHomeExperiment(service,"app_click_event",homeLastTrial!!.id,
   "after=${age}ms class=${node?.className?.toString()?.substringAfterLast('.')} " +
    "rect=${rect.toShortString()} desc=${node?.contentDescription?.toString()?.take(60)}")
 }
 private fun homeTrace(stage:String,trialId:String,details:String){
  TeachOverlay.recordHomeExperiment(service,stage,trialId,details)
 }
 private fun resetHomeExperiment(){
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
  collection.begin()
  update("起動中：タンク動画回収を開始（上限3時間）。学習ルール${LearnedActions.count(service)}件も照合")
  h.postDelayed(poll,450)
  return "自動操作を開始しました。不明な画面では一切タップしません。"
 }
 fun stop():String{
  val wasRunning=running
  running=false
  generation++
  h.removeCallbacks(poll)
  resetHomeExperiment()
  playRecoveryStep=0
  gachaPhase=GachaSafety.Phase.NONE
  resetLiveVisual()
  cardResultVisibleSince=0L
  verifiedAdKey=""
  verifiedAdSince=0L
  postAdClosedAt=0L
  postAdNextKind=""
  postAdNextSince=0L
  if(wasRunning){
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
  return null
 }

 private fun exactLearnedCloseTrusted(decision:Decision,snapshot:Snapshot):Boolean{
  val gachaUiVisible=listOf("カードを1枚選んでください","ガチャWチャンスに挑戦",
   "友達にシェアする","動画を見て再チャレンジ",
   "動画スキップ","持っている枚数").any{snapshot.has(it)}
  return GachaSafety.exactLearnedCloseAllowed(
   decision.kind,decision.exactLearned,gachaUiVisible)
 }
 private fun gachaProtects(decision:Decision,now:Long,snapshot:Snapshot):Boolean{
  if(gachaPhase==GachaSafety.Phase.NONE)return false
  val adProof=trustedAdEvidence(snapshot)
  val actualAd=GachaSafety.canDismissProvenAdAfterResult(
   gachaPhase,decision.kind,adProof!=null || exactLearnedCloseTrusted(decision,snapshot),
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
  val move=n.any{it.canPress && it.desc=="move"}
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
  val confirmedHome=move && remaining.isNotBlank() &&
   (tank || (separator!=null && above.size==1 && below.size==1))
  if(confirmedHome){
   val button=above.singleOrNull()
   return Decision("HOME","HOME／紫ボタン","HOME:$remaining",button,
    "動画ボタン候補=${above.size} / 下のボタン=${below.size} / " +
     "または=${separator!=null} / タンク=$tank / $remaining")
  }
  if(move && remaining.isNotBlank() && (tank || separator!=null)){
   return Decision("HOME_WAIT","HOME／紫ボタンが未特定","HOME_WAIT",null,
    "ボタン候補=${above.size} / 幅広ボタン=${wideButtons.size} / " +
     "下のボタン=${below.size} / または=${separator!=null} / タンク=$tank")
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
  visualOverlayDecision(snap)?.let{return it}
  val memory=learned.match(snap.root)
  if(memory!=null){
   val item=Item(memory.node,memory.node.text?.toString().orEmpty(),
    memory.node.contentDescription?.toString().orEmpty(),memory.bounds)
   return Decision(if(memory.visualIcon)"LEARNED_ICON"
    else if(memory.gestureOnly)"LEARNED_TEXT" else "LEARNED",memory.title,
    "LEARNED:${memory.bounds.toShortString()}:${memory.title}",item,
    "ユーザーが登録した画面と閉じる領域だけを操作",memory.tapPoint,
    memory.exact)
  }
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
  val node=button.node
  homeTrace("before",trial.id,"button=${bounds.toShortString()} " +
   "tap=(${point.x},${point.y}) class=${node.className?.toString()?.substringAfterLast('.')} " +
   "click=${node.isClickable} enabled=${node.isEnabled} visible=${node.isVisibleToUser} " +
   "selected=${node.isSelected} children=${node.childCount} " +
   "windowId=${node.windowId} mode=${trial.mode}")
  service.saveVisualScreenshot("HOME before ${trial.id}",0,false)
  update("$label。操作バーのタッチを透過して${HomeTouchTrials.OVERLAY_SETTLE_MS}ms待機")
  service.prepareHomeTouch(point.x,point.y){ready,overlayNote->
   if(!running || generation!=gen || trialToken!=homeTrialGeneration)return@prepareHomeTouch
   homeTrace("overlay",trial.id,overlayNote)
   if(!ready){
    homeTrialInFlight=false
    stopWithReason("HOME診断：操作バーのタッチ透過に失敗。誤操作防止で停止")
    return@prepareHomeTouch
   }
   val freshSnap=readCurrent()
   val fresh=if(freshSnap!=null)choose(freshSnap) else null
   if(fresh?.kind!="HOME" || fresh.target==null){
    homeTrialInFlight=false
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
    homeLastTrial?.let{HomeTouchProfile.markSuccessfulTrial(service,it.id)}
    update("HOME操作後に残回数が${previous}→${current}へ減少。成功パターンを記録")
    resetHomeExperiment()
    return true
   }
   if(HomeTouchTrials.hasTimedOut(homeExperimentStartedAt,now)){
    stopWithReason("HOMEタップ診断が48秒を超えても遷移なし（試行ログを確認）")
    return true
   }
   if(homeTrialInFlight){
    update("HOME試行：タッチ終了待ち。まだ画面遷移は確認できていません")
    return true
   }
   val waitSeconds=(now-homeLastTrialAt)/1000L
   if(homeTrialIndex>0 &&
    now-homeLastTrialAt<HomeTouchTrials.DELAY_BETWEEN_TRIALS_MS){
    update("HOMEの画面遷移なし。${waitSeconds}秒経過、次の押し方まで待機")
    return true
   }
   if(homeTrialIndex>=homeTrialList.size){
    stopWithReason("HOME紫ボタンを学習位置110／240／520msと別位置300msで試しても遷移なし")
    return true
   }
   if(decision.kind=="HOME_WAIT" || decision.target==null){
    update("HOME紫ボタンのクリック範囲が一時的に取得不可。座標試行せず再判定")
    return true
   }
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
  val independentAdProof=trustedAdEvidence(snapshot)!=null
  val positive=HomeTouchTrials.plausibleTransition(decision.kind) &&
   (!GachaSafety.requiresAdEvidence(decision.kind) || independentAdProof)
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
    if(info.startsWith("HOME試行 "))homeTrialInFlight=false
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
  resetHomeExperiment()
  clearDeferredHome()
  postAdClosedAt=0L
  postAdNextKind=""
  postAdNextSince=0L
  collection.finish("FAILED",reason)
  update("停止：$reason")
 }
 private fun completeCollection(){
  running=false
  generation++
  h.removeCallbacks(poll)
  update("タンク動画回収タスク完了。HOMEの残り0回を繰り返し確認")
 }
 /**
  * Only count progress on the selected HOME move tab, with the "または" divider
  * and the exact visible remaining-reward label. Ad and gacha screens cannot
  * prove that a task is complete.
  */
 private fun monitorCollection(snap:Snapshot,now:Long):Boolean{
  // Wait for the entire W-chance/card animation and result flow to finish
  // before accepting HOME observations as a completion certificate.
  if(gachaPhase!=GachaSafety.Phase.NONE)return false
  val home=snap.nodes.any{it.node.isVisibleToUser && it.node.isSelected && it.desc=="move"} &&
   snap.nodes.any{it.node.isVisibleToUser && it.text.trim()=="または"}
  if(!home || snap.has("友達にシェアする") ||
    snap.has("カードを1枚選んでください") || snap.has("持っている枚数") ||
    snap.has("ガチャWチャンスに挑戦") || snap.has("動画を見て再チャレンジ"))return false
  val count=snap.nodes.asSequence().filter{it.node.isVisibleToUser}
   .mapNotNull{CollectionTaskPolicy.remaining(it.text)}.firstOrNull()?:return false
  if(collection.observeHome(count,now)){
   completeCollection()
   return true
  }
  if(count==0){
   update("HOMEの残り0回を確認。誤判定防止のため再確認中")
   next(900)
   return true
  }
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
  val proof=trustedAdEvidence(snapshot)
  val exact=exactLearnedCloseTrusted(decision,snapshot)
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
   if(collection.budgetExceeded()){
    stopWithReason("実行時間が3時間に達したため未完了として停止")
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
    initial.kind in listOf("UNKNOWN","OPAQUE","AD_VISUAL_X","AD_VISUAL_NEXT")){
    probeLiveOverlay(now)
   }
   val decision=choose(snapshot)
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
    if(decision.kind in listOf("LEARNED_TEXT","LEARNED_ICON"))learnedTextSeenAt=now
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
    val proof=trustedAdEvidence(snapshot)
    val exactLearned=exactLearnedCloseTrusted(decision,snapshot)
    if(proof==null && !exactLearned){
     verifiedAdKey=""
     verifiedAdSince=0L
     if(unknownSince==0L)unknownSince=now
     if(now-unknownSince>=120000L){
      stopWithReason("広告とガチャを2分間区別できなかったため停止")
      return
     }
     update("広告かガチャか判定不能：${decision.display} の×は押さず待機")
     next()
     return
    }
    unknownSince=0L
    // Gacha animation might momentarily expose an X. Require a stable, clearly
    // identified ad *and target* over consecutive polls before acting.
    val proofSource=proof?: "LEARNED_EXACT"
    val key="$proofSource|${decision.key}"
    if(verifiedAdKey!=key){
     verifiedAdKey=key
     verifiedAdSince=now
    }
    if(now-verifiedAdSince<1700L){
     update("閉じる操作の安定待ち：$proofSource。1.7秒継続してから操作")
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

     if(!fallbackAttempted && seconds>=4 && decision.target!=null){
      fallbackAttempted=true
      update("${decision.display}：命令後も同じ画面。確認済み箇所を1回だけタップ")
      gestureTap(decision.target.rect,decision.display,
       point=if(decision.kind in listOf("LEARNED_TEXT","LEARNED_ICON"))decision.touch else null)
      next()
      return
     }
     if(seconds>=13){
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
   if(decision.kind in listOf("LEARNED_TEXT","LEARNED_ICON") && now-learnedTextSeenAt<4000L){
    update("学習した『閉じる』表示を確認。操作可能になるまで4秒待機中")
    next()
    return
   }
   if(decision.target==null &&
     decision.kind in listOf("AD_VISUAL_X","AD_VISUAL_NEXT")){
    val point=decision.touch
    val width=service.resources.displayMetrics.widthPixels
    val height=service.resources.displayMetrics.heightPixels
    if(point==null || point.x !in (width*72/100)..width ||
     point.y !in 0..(height*18/100)){
     stopWithReason("画像候補が画面上部の許可領域外。停止")
     return
    }
    val previous=recentActions[decision.key]?:0L
    if(previous>0L && now-previous<6_000L){
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
    if(PostAdTransitionPolicy.isAdTransitionAction(decision.kind)){
     postAdClosedAt=now
     postAdNextKind=""
     postAdNextSince=0L
    }
    liveVisual=null
    liveVisualCount=0
    val safeBounds=Rect(point.x-30,point.y-30,point.x+31,point.y+31)
    update("${decision.display}：確定した画面右上のマークを操作")
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
   if(now-last<18000L){
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
   if(PostAdTransitionPolicy.isAdTransitionAction(decision.kind)){
    postAdClosedAt=now
    postAdNextKind=""
    postAdNextSince=0L
    update("広告の終了操作を実行：次の画面へ切り替わるまで追加の×を禁止")
   }
   if(decision.kind in listOf("LEARNED_TEXT","LEARNED_ICON")){
    // A nonclickable close label / ad dismiss parent cannot be activated by
    // ACTION_CLICK. Use the exact user-confirmed coordinate, not the node center.
    update("${decision.display}：学習したタップ位置でジェスチャー実行")
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
