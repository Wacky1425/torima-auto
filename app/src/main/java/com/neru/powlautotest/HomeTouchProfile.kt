package com.neru.powlautotest

import android.content.Context
import android.graphics.Point
import android.graphics.Rect

/**
 * HOME-only touch point learned with explicit user confirmation.
 * The point is normalized to screen dimensions, but is NEVER used unless
 * the currently identified HOME video button actually contains that point.
 */
object HomeTouchProfile {
 private const val PREF="torima_home_touch_profile"
 private const val K_X="x_fraction"
 private const val K_Y="y_fraction"
 private const val K_CONFIRMED="confirmed"
 fun current(ctx:Context,bounds:Rect):Point {
  val w=ctx.resources.displayMetrics.widthPixels
  val h=ctx.resources.displayMetrics.heightPixels
  val prefs=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE)
  // Default is based on the user's successful 110ms guided tap at (624,1253)
  // on a 1080x2412 screen. Relative to the 900px-wide video button: x=59.3%.
  val defaultX=bounds.left+(bounds.width()*0.593f).toInt()
  val defaultY=bounds.top+bounds.height()/2
  if(!prefs.getBoolean(K_CONFIRMED,false))return Point(defaultX,defaultY)
  val x=(prefs.getFloat(K_X,0.5778f)*w).toInt()
  val y=(prefs.getFloat(K_Y,0.5195f)*h).toInt()
  val padX=maxOf(12,bounds.width()/12)
  val padY=maxOf(9,bounds.height()/8)
  if(x in (bounds.left+padX)..(bounds.right-padX) &&
   y in (bounds.top+padY)..(bounds.bottom-padY)){
   return Point(x,y)
  }
  return Point(defaultX,defaultY)
 }
 fun qualifies(ctx:Context,beforeSignature:String?,x:Int,y:Int):Boolean {
  if(beforeSignature==null)return false
  if(!beforeSignature.contains("move") ||
   !beforeSignature.contains("あと") ||
   !beforeSignature.contains("タンク"))return false
  val w=ctx.resources.displayMetrics.widthPixels
  val h=ctx.resources.displayMetrics.heightPixels
  if(w<=0 || h<=0)return false
  // Must be an interior position of the upper reward-video button, never the
  // lower "または" button, the tab bar or an install ad.
  return x in (w*18/100)..(w*85/100) &&
   y in (h*46/100)..(h*56/100)
 }
 fun register(ctx:Context,x:Int,y:Int):String {
  val w=ctx.resources.displayMetrics.widthPixels
  val h=ctx.resources.displayMetrics.heightPixels
  if(w<=0 || h<=0)return "HOME操作位置の取得に失敗しました"
  ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
   .putFloat(K_X,x.toFloat()/w)
   .putFloat(K_Y,y.toFloat()/h)
   .putBoolean(K_CONFIRMED,true).apply()
  return "HOMEの成功したタップ位置 ($x,$y) を登録。次回から自動で使用します"
 }
 fun hasCalibration(ctx:Context):Boolean =
  ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean(K_CONFIRMED,false)
}
