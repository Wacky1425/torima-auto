package com.neru.powlautotest
import org.junit.Assert.*
import org.junit.Test
class AppNavigationRulesTest {
 @Test fun registrationRolesHaveExplicitSourceScreens(){
  assertEquals(MainScreenPolicy.Screen.HOME,AppNavigationRules.Role.MAP.source)
  assertEquals(MainScreenPolicy.Screen.MAP,AppNavigationRules.Role.STOCK.source)
  assertEquals(MainScreenPolicy.Screen.MAP,AppNavigationRules.Role.TODAY.source)
 }
 @Test fun stockRegistrationCannotBecomeAnAdCornerOrOrdinaryMapChest(){
  assertTrue(AppNavigationRules.allowed(AppNavigationRules.Role.STOCK,470,842,540,1200))
  assertFalse(AppNavigationRules.allowed(AppNavigationRules.Role.STOCK,510,70,540,1200))
  assertFalse(AppNavigationRules.allowed(AppNavigationRules.Role.STOCK,200,600,540,1200))
  assertFalse(AppNavigationRules.allowed(AppNavigationRules.Role.STOCK,540,842,540,1200))
 }
 @Test fun mapNavigationOnlyAcceptsTheBottomMapTab(){
  assertTrue(AppNavigationRules.allowed(AppNavigationRules.Role.MAP,380,1140,540,1200))
  assertFalse(AppNavigationRules.allowed(AppNavigationRules.Role.MAP,470,842,540,1200))
  assertFalse(AppNavigationRules.allowed(AppNavigationRules.Role.MAP,40,1140,540,1200))
 }
 @Test fun driveTargetRejectsLocalDocumentsAndUnrelatedProviders(){
  assertTrue(DriveDiagnosticTarget.isDriveAuthority("com.google.android.apps.docs.storage"))
  assertFalse(DriveDiagnosticTarget.isDriveAuthority("com.android.externalstorage.documents"))
  assertFalse(DriveDiagnosticTarget.isDriveAuthority("com.google.android.apps.docs.storage.fake"))
  assertFalse(DriveDiagnosticTarget.isDriveAuthority(null))
 }
}
