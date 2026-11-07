package com.neru.powlautotest
import org.junit.Assert.*
import org.junit.Test
class DismissProbePolicyTest {
 @Test fun onlyCurrentRegisteredAdActionsCanUseTheCaptionEvidence(){
  assertTrue(DismissProbePolicy.registeredCaptionSkip("LEARNED",true))
  assertTrue(DismissProbePolicy.registeredCaptionSkip("LEARNED_TEXT",true))
  assertFalse(DismissProbePolicy.registeredCaptionSkip("LEARNED",false))
  assertFalse(DismissProbePolicy.registeredCaptionSkip("HOME",true))
  assertFalse(DismissProbePolicy.registeredCaptionSkip("LEARNED_VISUAL",true))
 }
 @Test fun exactRegisteredAdCanResumeWithoutRememberingHomeEntry(){
  for(kind in listOf("LEARNED","LEARNED_ICON","LEARNED_TEXT"))
   assertTrue(DismissProbePolicy.exactRegisteredAd(kind,true,true,false,false))
  assertFalse(DismissProbePolicy.exactRegisteredAd("LEARNED",true,false,false,false))
  assertFalse(DismissProbePolicy.exactRegisteredAd("LEARNED",false,true,false,false))
  assertFalse(DismissProbePolicy.exactRegisteredAd("LEARNED",true,true,true,false))
  assertFalse(DismissProbePolicy.exactRegisteredAd("LEARNED",true,true,false,true))
  assertFalse(DismissProbePolicy.exactRegisteredAd("HOME",true,true,false,false))
 }
}
