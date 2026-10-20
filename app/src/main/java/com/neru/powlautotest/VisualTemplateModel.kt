package com.neru.powlautotest

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Small local patch + surrounding context comparison. The image itself is
 * never uploaded. Both a 15x15 close/skip patch and an 11x11 neighborhood
 * fingerprint must resemble what the user taught. No exact full-screen hash.
 */
internal object VisualTemplateModel {
 const val CANONICAL_WIDTH=540
 const val NEAR_RADIUS=31
 const val CONTEXT_RADIUS=113
 const val NEAR_GRID=15
 const val CONTEXT_GRID=11
 const val MIN_LOCAL_TEXTURE=11
 const val MIN_LOCAL_SCORE=665
 const val MIN_CONTEXT_SCORE=455
 const val MIN_TOTAL_SCORE=1_235
 data class Patch(val data:IntArray,val texture:Int)
 data class Rule(val x:Int,val y:Int,val width:Int,val height:Int,
  val near:IntArray,val context:IntArray)
 data class Hit(val x:Int,val y:Int,val nearScore:Int,
  val contextScore:Int,val score:Int)

 private fun luminance(rgb:Int):Int{
  val r=rgb ushr 16 and 255
  val g=rgb ushr 8 and 255
  val b=rgb and 255
  return (r*30+g*59+b*11)/100
 }
 fun patch(pixels:IntArray,width:Int,height:Int,
  x:Int,y:Int,radius:Int,grid:Int):Patch?{
  if(width<220 || height<350 || pixels.size!=width*height ||
   x !in 0 until width || y !in 0 until height)return null
  val v=IntArray(grid*grid)
  var sum=0
  for(gy in 0 until grid)for(gx in 0 until grid){
   val sx=(x+(gx-(grid-1)/2.0)*2*radius/(grid-1))
    .toInt().coerceIn(0,width-1)
   val sy=(y+(gy-(grid-1)/2.0)*2*radius/(grid-1))
    .toInt().coerceIn(0,height-1)
   val c=luminance(pixels[sy*width+sx])
   v[gy*grid+gx]=c;sum+=c
  }
  val mean=sum/v.size
  val texture=v.sumOf{abs(it-mean)}/v.size
  return Patch(v,texture)
 }
 fun capture(pixels:IntArray,width:Int,height:Int,x:Int,y:Int):Rule?{
  val near=patch(pixels,width,height,x,y,NEAR_RADIUS,NEAR_GRID)?:return null
  val context=patch(pixels,width,height,x,y,CONTEXT_RADIUS,CONTEXT_GRID)?:return null
  if(near.texture<MIN_LOCAL_TEXTURE)return null
  return Rule(x,y,width,height,near.data,context.data)
 }
 fun similarity(a:IntArray,b:IntArray):Int{
  if(a.isEmpty() || a.size!=b.size)return 0
  val avgA=a.sum()/a.size
  val avgB=b.sum()/b.size
  val mae=a.indices.sumOf{i->
   abs((a[i]-avgA)-(b[i]-avgB))
  }/a.size
  return (1_000-mae*10).coerceIn(0,1_000)
 }
 fun search(rule:Rule,pixels:IntArray,width:Int,height:Int):Hit?{
  if(width<220 || height<350 || pixels.size!=width*height ||
   rule.width<220 || rule.height<350 || rule.near.size!=NEAR_GRID*NEAR_GRID ||
   rule.context.size!=CONTEXT_GRID*CONTEXT_GRID)return null
  // Position stays near the user's demonstrated point, not just anywhere
  // an advertisement happens to contain a small generic close glyph.
  val cx=(rule.x.toDouble()/rule.width*width).toInt()
  val cy=(rule.y.toDouble()/rule.height*height).toInt()
  var best:Hit?=null
  for(dy in -24..24 step 4)for(dx in -24..24 step 4){
   val px=cx+dx;val py=cy+dy
   val local=patch(pixels,width,height,px,py,NEAR_RADIUS,NEAR_GRID)
    ?:continue
   if(local.texture<MIN_LOCAL_TEXTURE)continue
   val nearScore=similarity(rule.near,local.data)
   if(nearScore<MIN_LOCAL_SCORE)continue
   val context=patch(pixels,width,height,px,py,CONTEXT_RADIUS,CONTEXT_GRID)
    ?:continue
   val contextScore=similarity(rule.context,context.data)
   val total=nearScore+contextScore
   if(contextScore>=MIN_CONTEXT_SCORE && total>=MIN_TOTAL_SCORE &&
    (best==null || total>best.score))
    best=Hit(px,py,nearScore,contextScore,total)
  }
  return best
 }
}
