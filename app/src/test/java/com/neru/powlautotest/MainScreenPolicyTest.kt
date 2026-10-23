package com.neru.powlautotest
import org.junit.Assert.*
import org.junit.Test
class MainScreenPolicyTest {
 private fun nav(selected:String="")=listOf(
  MainScreenPolicy.Label("マイル",50,1140,selected=="home"),
  MainScreenPolicy.Label("マップ",380,1140,selected=="map"))
 @Test fun mapHeaderAndNavigationWorkWithoutCurrentLocationOrToday(){
  val result=MainScreenPolicy.identify(nav()+MainScreenPolicy.Label("マップ",270,100),540,1200)
  assertEquals(MainScreenPolicy.Screen.MAP,result.screen)
 }
 @Test fun bottomMapLabelAloneDoesNotMeanMapIsSelected(){
  assertEquals(MainScreenPolicy.Screen.UNKNOWN,MainScreenPolicy.identify(nav(),540,1200).screen)
  assertEquals(MainScreenPolicy.Screen.MAP,MainScreenPolicy.identify(nav("map"),540,1200).screen)
 }
 @Test fun centerContentCannotTurnHomeIntoMap(){
  val rows=nav("home")+listOf(MainScreenPolicy.Label("移動",40,150),
   MainScreenPolicy.Label("歩数",130,150),MainScreenPolicy.Label("マップ",270,600))
  assertEquals(MainScreenPolicy.Screen.HOME,MainScreenPolicy.identify(rows,540,1200).screen)
 }
 @Test fun contradictionsAndAdWithoutNavigationAreNotMainScreens(){
  val result=MainScreenPolicy.identify(nav("home")+MainScreenPolicy.Label("マップ",270,100),540,1200)
  assertEquals(MainScreenPolicy.Screen.UNKNOWN,result.screen);assertFalse(result.allowVisual)
  assertEquals(MainScreenPolicy.Screen.UNKNOWN,MainScreenPolicy.identify(listOf(
   MainScreenPolicy.Label("マップ",270,100)),540,1200).screen)
 }
}
