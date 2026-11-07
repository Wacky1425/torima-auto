package com.neru.powlautotest

/** A single-button tank layout. Mode and remaining-count anchors must be on HOME. */
internal object TaipaHomePolicy {
 data class Box(val left:Int,val top:Int,val right:Int,val bottom:Int){
  val width get()=right-left
  val height get()=bottom-top
  val centerX get()=(left+right)/2
 }
 fun modeLabel(text:String)=text.contains("タイパモード")
 fun verified(onHome:Boolean,modeBottom:Int,countTop:Int,w:Int,h:Int,blocked:Boolean):Boolean =
  onHome && !blocked && w>0 && h>0 && modeBottom in h*20/100..h*65/100 &&
   countTop>modeBottom && countTop-modeBottom in h*4/100..h*25/100 &&
   countTop<=h*80/100
 fun button(box:Box,modeBottom:Int,countTop:Int,w:Int,h:Int):Boolean =
  box.left>=0 && box.right<=w &&
   box.width in w*68/100..w*94/100 &&
   box.height in h*3/100..h*11/100 &&
   kotlin.math.abs(box.centerX-w/2)<=w*10/100 &&
   box.top>=modeBottom-h/100 && box.bottom<=countTop+h/100
}
