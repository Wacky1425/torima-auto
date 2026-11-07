package com.neru.powlautotest

/** A specifically supported, reviewed report: compare button, offer header and receive button.
 * Embedded ad pixels between them are deliberately excluded. */
internal object ReportedRetryModel {
 data class Model(val x:Int,val y:Int,val w:Int,val h:Int,val patches:List<IntArray>)
 private fun anchors(x:Int,y:Int,w:Int,h:Int)=listOf(
  Triple(x,y,31),Triple(w*70/100,y-h*46/100,55),Triple(w/2,y+h*9/100,45))
 fun capture(p:IntArray,w:Int,h:Int,x:Int,y:Int):Model?{
  if(w!=540 || h<900 || x !in w*25/100..w*75/100 || y !in h*60/100..h*78/100)return null
  if(p.size!=w*h)return null
  var purple=0
  for(yy in y-15..y+15)for(xx in x-65..x+65){
   val color=p[yy*w+xx]
   val r=color ushr 16 and 255;val g=color ushr 8 and 255;val b=color and 255
   if(b>=95 && b>=g*12/10 && r>=g)purple++
  }
  // Count the button background, excluding its white caption.
  if(purple<1700)return null
  val patches=anchors(x,y,w,h).map{(xx,yy,radius)->
   VisualTemplateModel.patch(p,w,h,xx,yy,radius,15)?:return null
  }
  if(patches.any{it.texture<11})return null
  return Model(x,y,w,h,patches.map{it.data})
 }
 fun matches(model:Model,p:IntArray,w:Int,h:Int):Boolean{
  if(w!=model.w || h!=model.h || p.size!=w*h)return false
  return anchors(model.x,model.y,w,h).zip(model.patches).all{(anchor,saved)->
   val (x,y,radius)=anchor
   val current=VisualTemplateModel.patch(p,w,h,x,y,radius,15)?:return false
   kotlin.math.abs(saved.average()-current.data.average())<=18 &&
    VisualTemplateModel.similarity(saved,current.data)>=870
  }
 }
}
