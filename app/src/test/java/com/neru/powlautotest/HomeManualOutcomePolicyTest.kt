package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class HomeManualOutcomePolicyTest {
 @Test fun clickOnlyAndOpaqueAreNotConfirmedNavigation(){
  assertEquals("HOME_REMAINED",HomeManualOutcomePolicy.classify(false,false,"HOME"))
  assertEquals("UNKNOWN",HomeManualOutcomePolicy.classify(false,false,"OPAQUE"))
  assertEquals("UNKNOWN",HomeManualOutcomePolicy.classify(false,false,"OTHER"))
 }
 @Test fun provenAdOrNextScreenCanConfirmManualProgress(){
  assertEquals("AD_CONFIRMED",HomeManualOutcomePolicy.classify(true,false,"OPAQUE"))
  assertEquals("AD_CONFIRMED",HomeManualOutcomePolicy.classify(true,true,"HOME"))
  assertEquals("NEXT_CONFIRMED",HomeManualOutcomePolicy.classify(false,true,"HOME"))
 }
}
