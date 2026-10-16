package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class FastAdModeTest {
 @Test fun experimentalFastModeSkipsOnlyPostAdQuietAndConfirmation(){
  assertFalse(FastAdMode.shouldApplyPostAdGuard(true))
  assertTrue(FastAdMode.shouldApplyPostAdGuard(false))
  assertFalse(FastAdMode.shouldStartPostAdGuard(true,true))
  assertFalse(FastAdMode.shouldStartPostAdGuard(true,false))
  assertTrue(FastAdMode.shouldStartPostAdGuard(false,true))
  assertFalse(FastAdMode.shouldStartPostAdGuard(false,false))
 }
}
