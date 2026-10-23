package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class DiagnosticCaptureScheduleTest {
 @Test fun queuedDiagnosticGetsNextSlotAfterLiveAnalysis(){
  val s=DiagnosticCaptureSchedule()
  assertTrue(s.allowProbe())
  assertTrue(s.request(1000))
  assertFalse(s.canCapture(true,1250,0))
  // New polls cannot repeatedly start a live probe ahead of the queued save.
  assertFalse(s.allowProbe())
  assertTrue(s.canCapture(false,1500,0))
  s.finish()
  assertTrue(s.allowProbe())
 }
 @Test fun repeatedRequestsCannotExtendQueueDeadline(){
  val s=DiagnosticCaptureSchedule()
  assertTrue(s.request(1000))
  assertFalse(s.request(15000))
  assertFalse(s.timedOut(15999))
  assertTrue(s.timedOut(16000))
  s.finish()
  assertTrue(s.request(17000))
  assertFalse(s.timedOut(17000))
 }
 @Test fun cooldownWaitsRatherThanDroppingTheDiagnostic(){
  val s=DiagnosticCaptureSchedule();s.request(1000)
  assertFalse(s.canCapture(false,2999,1000))
  assertTrue(s.canCapture(false,3000,1000))
  assertFalse(s.allowProbe())
  s.finish()
  assertFalse(s.canCapture(false,4000,1000))
 }
}
