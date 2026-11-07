package com.neru.powlautotest

/** Outlined triangle + vertical stop bar (▷|). Only inspected near a verified control. */
internal object SkipGlyphPresence {
 fun visible(p:IntArray,w:Int,h:Int,cx:Int,cy:Int):Boolean{
  if(w<300 || h<450 || p.size!=w*h || cx !in 0 until w || cy !in 0 until h)return false
  fun gray(x:Int,y:Int):Int{
   val c=p[y*w+x]
   return ((c ushr 16 and 255)*30+(c ushr 8 and 255)*59+(c and 255)*11)/100
  }
  for(y in maxOf(20,cy-22)..minOf(h-21,cy+22))
   for(x in maxOf(6,cx-34)..minOf(w-40,cx+22))
    for(r in 5..9)for(gap in 3..6)for(inverse in listOf(false,true)){
     val tip=x+2*r-1;val bar=tip+gap
     val bg=gray(x-4,y)
     val outer=gray(bar+4,y)
     if(kotlin.math.abs(bg-outer)>30)continue
     // A circular rim can mimic an inverted triangle/bar around its edge.
     // The four OUTSIDE corners must share the local background too.
     if(kotlin.math.abs(gray(x-4,y-r-3)-bg)>35 ||
       kotlin.math.abs(gray(x-4,y+r+3)-bg)>35 ||
       kotlin.math.abs(gray(bar+4,y-r-3)-bg)>35 ||
       kotlin.math.abs(gray(bar+4,y+r+3)-bg)>35)continue
     fun ink(xx:Int,yy:Int):Boolean{
      var strongest=0
      for(dy in -1..1)for(dx in -1..1){
       val v=gray(xx+dx,yy+dy)
       val contrast=if(inverse)v-bg else bg-v
       strongest=maxOf(strongest,contrast)
      }
      return strongest>=70
     }
     if(!ink(bar,y-r+1) || !ink(bar,y) || !ink(bar,y+r-1))continue
     if(kotlin.math.abs(gray(x+r/2,y)-bg)>35)continue // hollow interior
     if(kotlin.math.abs(gray(bar+3,y)-bg)>35)continue
     val triangle=listOf(
      x to y-r+1,x to y,x to y+r-1,
      x+r to y-r/2,x+r to y+r/2,tip to y)
     if(triangle.all{(xx,yy)->ink(xx,yy)})return true
    }
  return false
 }
 fun dismissVisible(p:IntArray,w:Int,h:Int,x:Int,y:Int,allowSkip:Boolean):Boolean =
  LearnedXPresence.visible(p,w,h,x,y) || (allowSkip &&
   (OverlayIconDetector.skipVisibleAt(p,w,h,x,y) || visible(p,w,h,x,y)))
}
