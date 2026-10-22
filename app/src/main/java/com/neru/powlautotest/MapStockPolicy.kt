package com.neru.powlautotest

/** Stock counts are independent completion evidence; missing rows are not empty. */
internal object MapStockPolicy {
 enum class Phase { OFF, BADGE, OPEN_MAP, OPEN_STOCK, LIST, DETAIL, FIRST_AD, BOTTOM, SECOND_AD, RECEIVE, RETURN }
 fun count(labels:List<String>):Int? {
  val values=labels.mapNotNull { Regex("ストック中\\s*([0-9０-９]+)\\s*個").find(it)?.groupValues?.get(1) }
   .map { value->value.map { if(it in '０'..'９') '0'+(it-'０') else it }.joinToString("").toIntOrNull() }
   .filterNotNull().distinct()
  return values.singleOrNull()
 }
 fun emptyConfirmed(count:Int?,reads:Int,elapsed:Long)=count==0 && reads>=2 && elapsed>=2500L
 fun decreased(before:Int?,after:Int?)=before!=null && after!=null && after<before
 /** Supplied bottom navigation: search around the upper-right of the map icon.
  * PRESENT only; no-dot/covered/changed geometry remains unverified (UNKNOWN).
  */
 fun hasMapDot(pixels:IntArray,w:Int,h:Int):Boolean {
  if(w<330 || h<650 || pixels.size!=w*h || h.toDouble()/w !in 1.9..2.65)return false
  var reds=0;var minX=w;var maxX=0;var minY=h;var maxY=0
  for(y in (h*0.925).toInt()..(h*0.964).toInt())
   for(x in (w*0.725).toInt()..(w*0.785).toInt()) {
    val c=pixels[y*w+x];val r=(c ushr 16) and 255;val g=(c ushr 8) and 255;val b=c and 255
    if(r>=205 && g<=85 && b<=85 && r>=g+100 && r>=b+100){
     reds++;minX=minOf(minX,x);maxX=maxOf(maxX,x);minY=minOf(minY,y);maxY=maxOf(maxY,y)
    }
   }
  return reds>=maxOf(20,w*w/12000) && maxX-minX in (w/120)..(w/28) &&
   maxY-minY in (w/120)..(w/28)
 }
}
