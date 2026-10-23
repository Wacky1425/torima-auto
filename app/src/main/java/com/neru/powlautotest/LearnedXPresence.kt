package com.neru.powlautotest

/** Scene/node identity never substitutes for a currently drawn X. */
internal object LearnedXPresence {
 fun visible(pixels:IntArray,w:Int,h:Int,cx:Int,cy:Int):Boolean {
  if(w<300 || h<450 || pixels.size!=w*h || cx !in 0 until w || cy !in 0 until h)return false
  fun gray(x:Int,y:Int):Int {
   val c=pixels[y*w+x]
   return ((c ushr 16 and 255)*30+(c ushr 8 and 255)*59+(c and 255)*11)/100
  }
  for(y in maxOf(15,cy-12)..minOf(h-16,cy+12))
   for(x in maxOf(15,cx-12)..minOf(w-16,cx+12))
    for(r in intArrayOf(3,4,5,6,8,10,12))for(inverse in listOf(false,true)) {
     fun ink(xx:Int,yy:Int):Int {val v=gray(xx,yy);return if(inverse)255-v else v}
     if(ink(x,y)<170)continue
     val diagonals=intArrayOf(ink(x-r,y-r),ink(x+r,y-r),ink(x-r,y+r),ink(x+r,y+r))
     val axis=(ink(x-r,y)+ink(x+r,y)+ink(x,y-r)+ink(x,y+r))/4
     if(diagonals.count{it>=170}>=3 && axis<120 &&
       diagonals.minOrNull()!!+diagonals.average().toInt()-axis>=430)return true
    }
  return false
 }
 class Gate {
  var key=""
   private set
  var since=0L
   private set
  private var lastAt=0L
  private var matches=0
  fun expect(value:String,now:Long){
   if(key!=value){key=value;since=now;lastAt=0L;matches=0}
  }
  fun observe(value:String,visible:Boolean,now:Long){
   if(value!=key)return
   matches=if(!visible)0 else if(lastAt>0L && now-lastAt in 500L..4_000L)matches+1 else 1
   lastAt=now
  }
  fun ready(value:String,now:Long)=value==key && matches>=2 && now-lastAt in 0L..900L
  fun reset(){key="";since=0L;lastAt=0L;matches=0}
 }
}
