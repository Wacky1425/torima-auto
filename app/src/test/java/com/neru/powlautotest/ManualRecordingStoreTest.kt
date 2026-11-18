package com.neru.powlautotest
import java.io.*
import java.util.zip.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
class ManualRecordingStoreTest {
 @get:Rule val temp=TemporaryFolder()
 @Test fun exportsOnlyTwoRecentSessionsAndCommittedAllowedFiles(){
  val sessions=(1..3).map{n->temp.newFolder("session-$n").also{dir->
   File(dir,"session.json").writeText("session-$n")
   File(dir,"frame-0001.jpg").writeText("image-$n")
   File(dir,"frame-0002.jpg.tmp").writeText("partial")
   File(dir,"unrelated.txt").writeText("private")
  }}
  val draft=temp.newFolder("session-4")
  File(draft,"session.json.tmp").writeText("partial")
  val bytes=ByteArrayOutputStream()
  ZipOutputStream(bytes).use{assertEquals(2,ManualRecordingStore.appendSessions(listOf(draft)+sessions.reversed(),it))}
  val contents=mutableMapOf<String,String>()
  ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use{zip->
   while(true){val entry=zip.nextEntry?:break;contents[entry.name]=zip.readBytes().decodeToString()}
  }
  assertEquals(setOf("manual-recordings/session-3/session.json","manual-recordings/session-3/frame-0001.jpg",
   "manual-recordings/session-2/session.json","manual-recordings/session-2/frame-0001.jpg"),contents.keys)
  assertEquals("image-3",contents["manual-recordings/session-3/frame-0001.jpg"])
 }
 @Test fun metadataOnlySessionStillExportsWhenScreenshotsAreUnavailable(){
  val dir=temp.newFolder("session-1");File(dir,"session.json").writeText("metadata-only")
  val bytes=ByteArrayOutputStream()
  ZipOutputStream(bytes).use{assertEquals(1,ManualRecordingStore.appendSessions(listOf(dir),it))}
  ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use{zip->
   assertEquals("manual-recordings/session-1/session.json",zip.nextEntry.name)
   assertEquals("metadata-only",zip.readBytes().decodeToString());assertNull(zip.nextEntry)
  }
 }
}
