package com.neru.powlautotest

/** Pending demonstrations are diagnostic cases, not indefinitely accumulating rules. */
internal object PendingLearningPolicy {
 fun groupKey(signature:String?,clazz:String,viewId:String,label:String,
  x:Int,y:Int,screenWidth:Int,screenHeight:Int):String{
  if(viewId.isNotBlank())return "id:"+viewId+"|"+clazz
  if(clazz=="ImageView" && x>=screenWidth*75/100 &&
   y in 0 until screenHeight*25/100)return "unlabeled-upper-right-image-close"
  if(label.equals("Skip",true) || label.equals("Next",true))
   return "navigation:"+label.lowercase()+"|"+clazz+"|"+x/200+"|"+y/200
  if(signature.isNullOrBlank() || signature=="null")
   return "opaque:"+clazz+"|"+x/200+"|"+y/200
  return "signature:"+signature+"|"+clazz+"|"+label+"|"+x/200+"|"+y/200
 }
}
