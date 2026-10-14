package com.neru.powlautotest

import org.junit.Assert.*
import org.junit.Test

class CollectionTaskPolicyTest {
 @Test fun countParserRequiresTheExplicitRewardLabel(){
  assertEquals(12,CollectionTaskPolicy.remaining("あと12回獲得できます"))
  assertEquals(0,CollectionTaskPolicy.remaining("あと0回獲得できます"))
  assertEquals(11,CollectionTaskPolicy.remaining("あと１１回獲得できます"))
  assertEquals(2,CollectionTaskPolicy.remaining("あと 2 回獲得できます"))
  assertNull(CollectionTaskPolicy.remaining("動画スキップ"))
  assertNull(CollectionTaskPolicy.remaining("残り0回"))
  assertNull(CollectionTaskPolicy.remaining("120pt獲得しました"))
 }
 @Test fun progressCannotBeInferredFromAnIncrease(){
  assertEquals(1,CollectionTaskPolicy.inferredDecrement(12,11))
  assertEquals(0,CollectionTaskPolicy.inferredDecrement(11,12))
  assertEquals(0,CollectionTaskPolicy.inferredDecrement(null,0))
 }
 @Test fun zeroCannotCompleteOnOneOrTwoImmediateObservations(){
  assertFalse(CollectionTaskPolicy.zeroVerified(10_000L,10_000L,1))
  assertFalse(CollectionTaskPolicy.zeroVerified(10_000L,10_500L,2))
  assertTrue(CollectionTaskPolicy.zeroVerified(10_000L,11_800L,2))
  assertFalse(CollectionTaskPolicy.zeroVerified(0L,90_000L,100))
 }
 @Test fun defaultBudgetIsThreeHours(){
  assertEquals(10_800_000L,CollectionTaskPolicy.BUDGET_MS)
 }
}
