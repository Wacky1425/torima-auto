package com.neru.powlautotest
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
internal object MainScreenReader {
 fun read(root:AccessibilityNodeInfo?):MainScreenPolicy.Result {
  if(root==null || root.packageName?.toString()!="jp.co.incrementp.milemobile")
   return MainScreenPolicy.Result(MainScreenPolicy.Screen.UNKNOWN,"トリマ以外",false)
  val bounds=Rect();root.getBoundsInScreen(bounds)
  val labels=ArrayList<MainScreenPolicy.Label>();var scanned=0
  fun scan(node:AccessibilityNodeInfo){
   if(scanned++>=1200)return
   val r=Rect();node.getBoundsInScreen(r)
   if(node.isVisibleToUser && r.width()>0 && r.height() in 1..(bounds.height()*20/100)){
    var selected=node.isSelected;var parent=node.parent;var depth=0
    while(parent!=null && depth<3){
     val pr=Rect();parent.getBoundsInScreen(pr)
     if(pr.height()>bounds.height()*20/100)break
     selected=selected || parent.isSelected;parent=parent.parent;depth++
    }
    listOf(node.text,node.contentDescription).mapNotNull{it?.toString()?.takeIf{v->v.isNotBlank()}}.forEach{
     labels.add(MainScreenPolicy.Label(it,r.centerX()-bounds.left,r.centerY()-bounds.top,selected))
    }
   }
   for(i in 0 until node.childCount){if(scanned>=1200)break;node.getChild(i)?.let{scan(it)}}
  }
  scan(root)
  return MainScreenPolicy.identify(labels,bounds.width(),bounds.height())
 }
}
