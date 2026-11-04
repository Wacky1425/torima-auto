package com.neru.powlautotest
/** Left-corner automation is only for a high-confidence X, never arbitrary controls. */
internal object AdCornerPolicy {
 fun accepted(kind:String,region:String,score:Int)=
  (region=="top-right" && score>=85) || (region=="top-left" && kind=="X" && score>=445)
}
