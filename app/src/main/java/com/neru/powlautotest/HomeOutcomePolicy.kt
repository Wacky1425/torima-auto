package com.neru.powlautotest

/**
 * HOME diagnostics describe observed navigation, never inferred earnings.
 * A dispatch callback, a click accessibility event, or UNKNOWN alone is not
 * enough to call a HOME trial successful.
 */
internal object HomeOutcomePolicy {
 const val PREPARING="PREPARING"
 const val SENT_UNCONFIRMED="SENT_UNCONFIRMED"
 const val NO_NAVIGATION="NO_NAVIGATION"
 const val UNVERIFIED_CHANGE="UNVERIFIED_CHANGE"
 const val AD_OPEN_CONFIRMED="AD_OPEN_CONFIRMED"
 const val NEXT_SCREEN_CONFIRMED="NEXT_SCREEN_CONFIRMED"
 const val REWARD_DECREASED="REWARD_DECREASED"
 const val ABORTED="ABORTED"
 const val INTERRUPTED="INTERRUPTED"

 private val confirmedAds=setOf("AD_CLOSE","AD_SKIP","AD_GOOGLE_PLAY",
  "AD_VISUAL_X","AD_VISUAL_NEXT","LEARNED","LEARNED_TEXT","LEARNED_ICON")
 private val confirmedNext=setOf("SKIP_NO","RETRY","W_CHANCE","SHARE_CLOSE",
  "CARD_RESULT","CARDS")
 fun classifyNewScreen(kind:String,adEvidence:Boolean,exactLearned:Boolean):String =
  when{
   kind in confirmedAds && (adEvidence || exactLearned)->AD_OPEN_CONFIRMED
   kind in confirmedNext->NEXT_SCREEN_CONFIRMED
   else->UNVERIFIED_CHANGE
  }
 fun isConfirmed(status:String)=status in setOf(AD_OPEN_CONFIRMED,
  NEXT_SCREEN_CONFIRMED,REWARD_DECREASED)
 fun label(status:String):String=when(status){
  PREPARING->"操作準備中"
  SENT_UNCONFIRMED->"入力送信済み（遷移未確認）"
  NO_NAVIGATION->"HOMEのまま・遷移なし"
  UNVERIFIED_CHANGE->"画面変化あり・広告か未確認"
  AD_OPEN_CONFIRMED->"HOME→広告画面を確認"
  NEXT_SCREEN_CONFIRMED->"HOME→別の操作画面を確認"
  REWARD_DECREASED->"HOMEの残回数減少を確認"
  ABORTED->"入力前に見送り"
  INTERRUPTED->"終了時点で結果未確認"
  else->"結果不明"
 }
}
