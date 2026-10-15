package com.neru.powlautotest

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Finds UI overlay glyphs, not whole-ad templates. The video/content may change
 * on every frame. A visual candidate alone NEVER authorizes a click.
 *
 * A white X is one near-square connected component. Two adjacent > glyphs
 * are two narrow, equally tall white connected components. Other text,
 * Japanese titles and keyboard controls must not be treated as overlays.
 */
internal object OverlayIconDetector {
 data class Marker(val kind:String,val x:Int,val y:Int,val score:Int,val region:String)
 private data class Blob(val x:Int,val y:Int,val width:Int,val height:Int,val area:Int){
  val cx:Int get()=x+width/2
  val cy:Int get()=y+height/2
 }
 private fun lum(c:Int):Int {
  val r=c ushr 16 and 255
  val g=c ushr 8 and 255
  val b=c and 255
  return (r*30+g*59+b*11)/100
 }
 fun detect(pixels:IntArray,width:Int,height:Int):List<Marker>{
  if(width<300 || height<450 || pixels.size!=width*height)return emptyList()
  val x0=width*75/100
  val x1=width*99/100
  val y0=max(14,height*2/100)
  val y1=min(height*14/100,width*56/100)
  val visited=BooleanArray(width*y1)
  val queue=IntArray(width*y1)
  fun bright(x:Int,y:Int)=lum(pixels[y*width+x])>=180
  val blobs=ArrayList<Blob>()
  for(y in y0 until y1)for(x in x0 until x1){
   val index=y*width+x
   if(visited[index] || !bright(x,y))continue
   var head=0;var tail=1
   queue[0]=index;visited[index]=true
   var left=x;var right=x;var top=y;var bottom=y
   while(head<tail){
    val current=queue[head++]
    val px=current%width;val py=current/width
    left=min(left,px);right=max(right,px)
    top=min(top,py);bottom=max(bottom,py)
    for(dy in -1..1)for(dx in -1..1){
     if(dx==0&&dy==0)continue
     val xx=px+dx;val yy=py+dy
     if(xx<x0 || xx>=x1 || yy<y0 || yy>=y1)continue
     val neighbor=yy*width+xx
     if(!visited[neighbor] && bright(xx,yy)){
      visited[neighbor]=true
      queue[tail++]=neighbor
     }
    }
   }
   if(tail>=8)blobs.add(Blob(left,top,right-left+1,bottom-top+1,tail))
  }
  val results=ArrayList<Marker>()
  for(b in blobs){
   val area=b.width*b.height
   val occupancy=b.area.toDouble()/area
   if(b.cx>=width*90/100 && b.y>=height*2/100 &&
    b.width in 14..34 && b.height in 14..34 &&
    abs(b.width-b.height)<=5 && occupancy in .22.. .49){
    // Both crossing diagonals are bright, but cardinal directions near the
    // center are dark. A plus, solid square, or circle must fail.
    val inset=max(2,b.width/5)
    val lx=b.x+inset;val rx=b.x+b.width-1-inset
    val ty=b.y+inset;val by=b.y+b.height-1-inset
    val cx=b.cx;val cy=b.cy
    val corners=listOf(bright(lx,ty),bright(rx,ty),bright(lx,by),bright(rx,by))
    val axes=listOf(bright(lx,cy),bright(rx,cy),bright(cx,ty),bright(cx,by))
    if(corners.count{it}>=3 && axes.count{it}<=1 && bright(cx,cy)){
     results.add(Marker("X",cx,cy,180,"top-right"))
    }
   }
  }
  val chevrons=blobs.filter{b->
   val occ=b.area.toDouble()/(b.width*b.height)
   b.x>=width*85/100 && b.y<=height*10/100 &&
    b.width in 7..17 && b.height in 18..34 &&
    b.width*100 in (28*b.height)..(70*b.height) &&
    occ in .15.. .42
  }
  for(a in chevrons)for(b in chevrons){
   if(b.cx<=a.cx)continue
   if(b.cx-a.cx !in 8..24 || abs(a.y-b.y)>4 ||
    abs(a.height-b.height)>4 || abs(a.width-b.width)>4)continue
   // The > point is bright in the vertical middle. The two top/bottom
   // left arms are separated by dark space in their center.
   fun check(c:Blob):Boolean{
    val left=c.x+1
    val tip=c.x+c.width-2
    val middle=c.cy
    return bright(left,c.y+2) && bright(left,c.y+c.height-3) &&
     bright(tip,middle) && !bright(left,middle)
   }
   if(check(a)&&check(b)){
    results.add(Marker("NEXT",b.cx,b.cy,170,"top-right"))
   }
  }
  return results.groupBy{it.kind}.values.mapNotNull{it.maxByOrNull{v->v.score}}
 }
}
