package com.neru.powlautotest

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

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
  private const val MAX_ACTIONS=30
 }
 private val h=Handler(Looper.getMainLooper())
 private val learned by lazy{LearnedActions(service)}
 private var adContextUntil=0L
 private var playRecoveryStep=0
 private var playRecoveryAt=0L
 private var lastPlayDetectedAt=0L
 private var playReturned=false
 private var outsidePackage=""
 private var running=false
 private var generation=0
 private var steps=0
 private var waitingKey:String?=null
 private var waitingSince=0L
 private var lastActionTitle=""
 private var fallbackAttempted=false
 private val recentActions=LinkedHashMap<String,Long>()
 private var lastInfo=""
 private var unknownSince=0L
 private var lastKind=""
 private val poll=object:Runnable{override fun run(){check()}}

 private data class Item(val node:AccessibilityNodeInfo,val text:String,val desc:String,val rect:Rect) {
  val canPress:Boolean get()=node.isClickable && node.isEnabled && node.isVisibleToUser
 }
 private data class Snapshot(val nodes:List<Item>,val count:Int,val packageName:String){
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
  val detail:String=""
 )

 fun isRunning():Boolean=running
 fun start():String{
  if(running)return "自動操作はすでに実行中です。"
  running=true
  generation++
  steps=0
  waitingKey=null
  fallbackAttempted=false
  recentActions.clear()
  unknownSince=0L
  lastKind=""
  lastInfo=""
  adContextUntil=0L
  playRecoveryStep=0
  playRecoveryAt=0L
  lastPlayDetectedAt=0L
  playReturned=false
  outsidePackage=""
  update("起動中：トリマの画面判定を開始")
  h.postDelayed(poll,450)
  return "自動操作を開始しました。不明な画面では一切タップしません。"
 }
 fun stop():String{
  val wasRunning=running
  running=false
  generation++
  h.removeCallbacks(poll)
  playRecoveryStep=0
  if(wasRunning)update("停止中（ユーザー操作で停止）")
  else statusCallback("停止中")
  return "自動操作を停止しました。"
 }
 private fun update(value:String) {
  if(value==lastInfo)return
  lastInfo=value
  val msg="自動操作：$value\n成功確認前の画面では繰り返し押しません。操作回数=$steps/$MAX_ACTIONS"
  service.report=msg
  statusCallback(value)
 }
 private fun next(delay:Long=POLL_MS) {
  if(running)h.postDelayed(poll,delay)
 }
 private fun readCurrent():Snapshot?{
  val roots=ArrayList<AccessibilityNodeInfo>()
  service.rootInActiveWindow?.let {if(it.packageName?.toString()==TARGET)roots.add(it)}
  try{
   service.windows?.forEach {window->
    window.root?.let{if(it.packageName?.toString()==TARGET)roots.add(it)}
   }
  }catch(_:Exception){}
  var selected:Snapshot?=null
  for(root in roots){
   val items=ArrayList<Item>()
   var visited=0
   fun walk(node:AccessibilityNodeInfo){
    if(visited>=450)return
    visited++
    val rect=Rect();node.getBoundsInScreen(rect)
    items.add(Item(node,node.text?.toString().orEmpty(),node.contentDescription?.toString().orEmpty(),rect))
    for(i in 0 until node.childCount){
     if(visited>=450)break
     node.getChild(i)?.let{walk(it)}
    }
   }
   walk(root)
   val snap=Snapshot(items,visited,root.packageName?.toString().orEmpty())
   if(selected==null || snap.count>selected.count)selected=snap
  }
  return selected
 }
 private fun closeButton(snap:Snapshot):List<Item>{
  val w=service.resources.displayMetrics.widthPixels
  return snap.nodes.filter {n->
   if(!n.canPress)return@filter false
   val name=n.desc.trim()
   if(name.equals("Close Ad",ignoreCase=true)){
    return@filter n.rect.width() in 20..260 && n.rect.height() in 20..220 &&
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
  if(cardResult.isNotEmpty()){
   return Decision("CARD_RESULT","カード結果","CARD_RESULT",cardResult.singleOrNull(),
    "『閉じる』候補=${cardResult.size}")
  }
  val move=n.any{it.canPress && it.desc=="move"}
  val tank=snap.has("タンクが満タン")
  val remaining=n.firstOrNull{
   it.node.isVisibleToUser && Regex("あと\\s*[0-9０-９]+\\s*回獲得できます")
    .containsMatchIn(it.text)
  }?.text?.trim().orEmpty()
  if(move && tank && remaining.isNotBlank()){
   val candidates=n.filter{
    it.canPress && it.rect.left>=0 && it.rect.right<=1080 &&
    it.rect.top in 1100..1300 &&
    it.rect.width()>=600 && it.rect.height() in 90..230
   }
   val button=candidates.singleOrNull()
   val key="HOME:$remaining"
   return Decision("HOME","HOME／紫ボタン",key,button,
    "紫ボタン候補=${candidates.size}、$remaining")
  }
  val close=closeButton(snap)
  if(close.isNotEmpty()){
   return Decision("AD_CLOSE","広告の終了ボタン","AD_CLOSE:${close.first().desc.trim()}:${close.first().rect.toShortString()}",
    close.singleOrNull(),"閉じる候補=${close.size}")
  }
  val memory=learned.match(service.rootInActiveWindow)
  if(memory!=null){
   val item=Item(memory.node,memory.node.text?.toString().orEmpty(),
    memory.node.contentDescription?.toString().orEmpty(),memory.bounds)
   return Decision("LEARNED",memory.title,
    "LEARNED:${memory.bounds.toShortString()}:${memory.title}",item,
    "ユーザーが明示的に登録した画面・ノードだけを操作")
  }
  if(snap.count<=2){
   return Decision("OPAQUE","広告または不明画面（UIが${snap.count}件）","OPAQUE",null,
    "アクセシビリティで×を取得できない")
  }
  return Decision("UNKNOWN","不明／広告再生中（${snap.count}ノード）","UNKNOWN",null,
   "識別できる終了・次へボタンなし")
 }
 private fun gestureTap(rect:Rect,info:String) {
  if(!running)return
  val x=rect.exactCenterX()
  val y=rect.exactCenterY()
  val path=Path().apply{moveTo(x,y)}
  val gesture=GestureDescription.Builder()
   .addStroke(GestureDescription.StrokeDescription(path,0,120)).build()
  val id=generation
  val accepted=service.dispatchGesture(gesture,object:AccessibilityService.GestureResultCallback(){
   override fun onCompleted(gestureDescription:GestureDescription?){
    if(id!=generation||!running)return
    update("$info／中央タップ完了。遷移確認待ち")
   }
   override fun onCancelled(gestureDescription:GestureDescription?){
    if(id!=generation||!running)return
    stopWithReason("ジェスチャーがキャンセルされたため停止")
   }
  },null)
  if(!accepted)stopWithReason("ジェスチャーが拒否されたため停止")
 }
 private fun stopWithReason(reason:String){
  running=false
  generation++
  h.removeCallbacks(poll)
  update("停止：$reason")
 }

 // Play Store may appear as another app or as an accessible modal window.
 private fun activePackage():String{
  val active=service.rootInActiveWindow?.packageName?.toString().orEmpty()
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
  * Never interact with Google Play unless the recent running target screen was an ad.
  * Sequence: Back, wait, Back, wait, launch Torima, verify. No store install clicks.
  */
 private fun recoverPlay(now:Long):Boolean{
  val pkg=activePackage()
  if(pkg==TARGET){
   if(playRecoveryStep!=0){
    update("Google Playからトリマへの復帰を確認")
    waitingKey=null
    fallbackAttempted=false
    playRecoveryStep=0
    playReturned=true
    recentActions.clear()
   }
   return false
  }
  if(pkg=="com.android.vending"){
   if(now>adContextUntil && playRecoveryStep==0){
    update("Google Playを検出。ただし広告由来と確認できないため操作しません")
    return true
   }
   lastPlayDetectedAt=now
   if(playRecoveryStep==0){
    playRecoveryStep=1
    playRecoveryAt=now
    val accepted=service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    update("広告由来のGoogle Playを検出。Android戻る1回: $accepted")
    if(!accepted)stopWithReason("Androidの戻るが拒否されました")
    return true
   }
   if(now-playRecoveryAt>=2600 && playRecoveryStep==1){
    playRecoveryStep=2;playRecoveryAt=now
    val accepted=service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    update("まだGoogle Play。Android戻る2回目: $accepted")
    if(!accepted)stopWithReason("Androidの戻る2回目が拒否されました")
    return true
   }
   if(now-playRecoveryAt>=2600 && playRecoveryStep==2){
    playRecoveryStep=3;playRecoveryAt=now
    val started=returnToTorima()
    update("Google Playからトリマを前面復帰: $started")
    if(!started)stopWithReason("トリマを起動できませんでした")
    return true
   }
   if(playRecoveryStep==3 && now-playRecoveryAt>6500){
    stopWithReason("Google Playからトリマへ戻れなかったため停止")
   }
   return true
  }
  if(playRecoveryStep in 1..3){
   if(pkg.isBlank() && now-playRecoveryAt<6000){
    update("Google Playを閉じた後の画面遷移を確認中")
    return true
   }
   if(now-playRecoveryAt>3200){
    if(playRecoveryStep!=3){
     playRecoveryStep=3;playRecoveryAt=now
     val started=returnToTorima()
     update("Playから離れたのでトリマを復帰: $started")
     if(!started)stopWithReason("トリマへの復帰ができませんでした")
    }else if(now-playRecoveryAt>6500){
     stopWithReason("トリマの再表示を確認できないため停止")
    }
   }
   return true
  }
  return false
 }

 private fun check(){
  if(!running)return
  try {
   val now=SystemClock.elapsedRealtime()
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
   val decision=choose(snapshot)
   if(steps>0 && (decision.kind=="AD_CLOSE" || decision.kind=="OPAQUE" ||
    (decision.kind=="UNKNOWN" && snapshot.nodes.any {
     it.text.contains("インストール")||it.desc.contains("Install",true)||
      it.desc.contains("More",true)
    }))){
    adContextUntil=now+45000L
   }
   if(decision.kind!=lastKind){
    lastKind=decision.kind
    if(decision.kind=="UNKNOWN" || decision.kind=="OPAQUE" ||
     decision.kind=="SHARE")unknownSince=now else unknownSince=0L
   }
   if(waitingKey!=null){
    if(waitingKey==decision.key){
     val seconds=(now-waitingSince)/1000
     if(!fallbackAttempted && seconds>=4 && decision.target!=null){
      fallbackAttempted=true
      update("${decision.display}：命令後も同じ画面。確認済み箇所を1回だけタップ")
      gestureTap(decision.target.rect,decision.display)
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
   val clicked=decision.target.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
   update("${decision.display}：ACTION_CLICK=$clicked。遷移確認待ち")
   if(!clicked){
    fallbackAttempted=true
    gestureTap(decision.target.rect,decision.display)
   }
   next()
  }catch(e:Exception){
   stopWithReason("例外 ${e.javaClass.simpleName}。誤操作防止のため停止")
  }
 }
}
