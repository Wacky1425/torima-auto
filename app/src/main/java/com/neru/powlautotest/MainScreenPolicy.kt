package com.neru.powlautotest

/** Main-screen identity uses fixed header/navigation bands, never map/ad content. */
internal object MainScreenPolicy {
 enum class Screen { HOME, MAP, UNKNOWN }
 data class Label(val text:String,val x:Int,val y:Int,val selected:Boolean=false)
 data class Result(val screen:Screen,val evidence:String,val allowVisual:Boolean=true)
 fun identify(labels:List<Label>,width:Int,height:Int):Result {
  if(width<=0 || height<=0)return Result(Screen.UNKNOWN,"画面サイズ不明")
  val valid=labels.filter{it.x in 0 until width && it.y in 0 until height}
  val bottom=valid.filter{it.y>=height*85/100}
  fun home(s:String)=s.trim().lowercase() in setOf("ホーム","home","マイル")
  fun map(s:String)=s.trim().lowercase() in setOf("マップ","map")
  if(bottom.none{home(it.text)} || bottom.none{map(it.text)})
   return Result(Screen.UNKNOWN,"下部HOME/MAPナビ未確認")
  val top=valid.filter{it.y in (height*3/100)..(height*24/100)}.map{it.text.trim()}
  val homeHeader=("移動" in top && "歩数" in top) || top.any{it.contains("今日の歩数")}
  val mapHeader=top.any{map(it) || it=="今日" || it=="現在地"}
  val homeSelected=bottom.any{home(it.text) && it.selected}
  val mapSelected=bottom.any{map(it.text) && it.selected}
  val screen=when {
   homeSelected && mapSelected->Screen.UNKNOWN
   homeHeader && mapHeader->Screen.UNKNOWN
   mapSelected && homeHeader->Screen.UNKNOWN
   homeSelected && mapHeader->Screen.UNKNOWN
   mapSelected || mapHeader->Screen.MAP
   homeSelected || homeHeader->Screen.HOME
   else->Screen.UNKNOWN
  }
  return Result(screen,"下部選択 HOME=$homeSelected MAP=$mapSelected / 上部 HOME=$homeHeader MAP=$mapHeader",!homeSelected && !homeHeader)
 }
}
