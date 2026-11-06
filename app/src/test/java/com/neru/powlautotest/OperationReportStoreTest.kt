package com.neru.powlautotest

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class OperationReportStoreTest {
 @get:Rule val temp=TemporaryFolder()
 @Test fun zipKeepsCompletePairsAndExcludesCancelledAndOldReports(){
  val full=(1..6).map{n->
   temp.newFolder("report-$n").also{dir->
    for(name in listOf("report.json","before.jpg","after.jpg"))File(dir,name).writeText("$n:$name")
   }
  }
  val draft=temp.newFolder("draft-8")
  val incomplete=temp.newFolder("report-7").also{File(it,"before.jpg").writeText("partial")}
  val bytes=ByteArrayOutputStream()
  ZipOutputStream(bytes).use{
   assertEquals(5,OperationReportStore.appendReports(listOf(draft,incomplete)+full.reversed(),it))
  }
  val actual=mutableMapOf<String,String>()
  ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use{zip->
   while(true){val entry=zip.nextEntry?:break;actual[entry.name]=zip.readBytes().decodeToString()}
  }
  val expected=(2..6).flatMap{n->
   listOf("report.json","before.jpg","after.jpg").map{"operation-reports/report-$n/$it" to "$n:$it"}
  }.toMap()
  assertEquals(expected,actual)
 }
}
