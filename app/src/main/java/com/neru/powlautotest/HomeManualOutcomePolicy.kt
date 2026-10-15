package com.neru.powlautotest

/** A click callback never proves that HOME opened a video. */
internal object HomeManualOutcomePolicy {
 fun classify(sawAdEvidence:Boolean,sawNextScreen:Boolean,finalState:String):String=
  when{
   sawAdEvidence->"AD_CONFIRMED"
   sawNextScreen->"NEXT_CONFIRMED"
   finalState=="HOME"->"HOME_REMAINED"
   else->"UNKNOWN"
  }
}
