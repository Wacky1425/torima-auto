package com.neru.powlautotest

/** Input point and current control's drawn glyph are deliberately separate. */
internal object DismissProbePolicy {
 data class Point(val x:Int,val y:Int)
 data class Box(val left:Int,val top:Int,val right:Int,val bottom:Int)
 fun point(tap:Point,box:Box?):Point = if(box!=null && box.right>box.left && box.bottom>box.top)
  Point((box.left+box.right)/2,(box.top+box.bottom)/2) else tap
 fun registeredCaptionSkip(kind:String,registered:Boolean)=
  registered && kind in setOf("LEARNED","LEARNED_ICON","LEARNED_TEXT")
 fun exactRegisteredAd(kind:String,registered:Boolean,exact:Boolean,onMain:Boolean,blocked:Boolean)=
  registered && exact && !onMain && !blocked &&
   kind in setOf("LEARNED","LEARNED_ICON","LEARNED_TEXT")
}
