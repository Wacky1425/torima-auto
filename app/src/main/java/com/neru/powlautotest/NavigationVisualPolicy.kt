package com.neru.powlautotest
import kotlin.math.abs
/** Immutable app chrome only: center map tiles never participate in screen naming. */
internal object NavigationVisualPolicy {
 fun band(p:IntArray,w:Int,h:Int,bottom:Boolean):IntArray? {
  if(w<300 || h<650 || p.size!=w*h)return null
  return IntArray(31*9){i->
   val fx=if(bottom)0.04+(i%31)*0.92/30 else 0.20+(i%31)*0.60/30
   val fy=if(bottom)0.915+(i/31)*0.055/8 else 0.045+(i/31)*0.08/8
   val c=p[(fy*h).toInt()*w+(fx*w).toInt()]
   ((c ushr 16 and 255)*30+(c ushr 8 and 255)*59+(c and 255)*11)/100
  }
 }
 fun sameBand(saved:IntArray,current:IntArray):Boolean {
  if(saved.size!=279 || current.size!=279)return false
  val avg=saved.average();val texture=saved.sumOf{abs(it-avg).toInt()}/saved.size
  return texture>=8 && abs(saved.average()-current.average())<=18 &&
   VisualTemplateModel.similarity(saved,current)>=870
 }
 fun localControl(rule:VisualTemplateModel.Rule,p:IntArray,w:Int,h:Int):MapStockIconDetector.Point? {
  if(rule.width<=0 || rule.height<=0 || rule.near.size!=225)return null
  val cx=rule.x*w/rule.width;val cy=rule.y*h/rule.height
  var best:MapStockIconDetector.Point?=null;var bestScore=849
  for(dy in -24..24 step 4)for(dx in -24..24 step 4){
   val patch=VisualTemplateModel.patch(p,w,h,cx+dx,cy+dy,31,15)?:continue
   if(patch.texture<VisualTemplateModel.MIN_LOCAL_TEXTURE ||
    abs(patch.data.average()-rule.near.average())>20)continue
   val score=VisualTemplateModel.similarity(rule.near,patch.data)
   if(score>bestScore){bestScore=score;best=MapStockIconDetector.Point(cx+dx,cy+dy)}
  }
  return best
 }
 fun label(value:String,fallback:String)=value.trim().take(40).ifBlank{fallback}
}
