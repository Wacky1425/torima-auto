package com.neru.powlautotest

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * One report, one share action. Creates a local archive but never uploads it.
 * The user chooses ChatGPT or another destination in the Android share sheet.
 */
internal object DiagnosticBundleExporter {
 private const val DIR="diagnostic_exports"
 fun create(ctx:Context):File{
  val folder=File(ctx.cacheDir,DIR).also{it.mkdirs()}
  val archive=File(folder,"torima-diagnostics-v3.14.zip")
  val temp=File(folder,"torima-diagnostics-v3.14.tmp")
  if(temp.exists())temp.delete()
  val resultText=HomeTrialJournal.report(ctx)
  try{
   ZipOutputStream(FileOutputStream(temp)).use{zip->
    fun put(name:String,content:String){
     zip.putNextEntry(ZipEntry(name))
     zip.write(content.toByteArray(Charsets.UTF_8))
     zip.closeEntry()
    }
    put("00_指タップと自動の比較.txt",HomeManualComparison.compare(ctx))
    put("00_HOME_診断結果.txt",resultText)
    put("01_HOME_試行詳細.json",HomeTrialJournal.export(ctx))
    put("02_操作履歴.json",TeachOverlay.export(ctx))
    put("09_学習診断.json",DiagnosticJournal.export(ctx,"teaching"))
    put("10_撮影診断.json",DiagnosticJournal.export(ctx,"screenshots"))
    put("03_回収タスク履歴.json",CollectionRunStore.export(ctx))
    put("04_画面ノード記録.txt",ScreenCollector.export(ctx))
    put("05_学習済み操作.txt",LearnedActions.export(ctx))
    put("08_画像で学習した操作.json",VisualLearnedActions.export(ctx))
    put("06_指タップ比較イベント.json",HomeManualComparison.export(ctx))
    put("07_HOME復旧履歴.json",HomeRecoveryJournal.export(ctx))
    val screenshots=ScreenshotDiagnostics.appendToDiagnosticZip(ctx,zip)
    put("manifest.json",JSONObject().apply{
     put("formatVersion",1)
     put("appVersion",BuildConfig.VERSION_NAME)
     put("applicationId",BuildConfig.APPLICATION_ID)
     put("versionCode",BuildConfig.VERSION_CODE)
     put("createdAt",System.currentTimeMillis())
     put("screenshotCount",screenshots)
     put("nodeLearnedCount",LearnedActions.count(ctx))
     put("visualLearnedCount",VisualLearnedActions.count(ctx))
     put("containsPersonalScreenImages",screenshots>0)
     put("homeOutcomeSummary",HomeTrialJournal.shortSummary(ctx))
     put("fingerOutcomeSummary",HomeManualComparison.summary(ctx))
     put("homeRecoverySummary",HomeRecoveryJournal.summary(ctx))
     put("autoRecoveryEnabled",HomeRecoveryPolicy.enabled(ctx))
     put("experimentalFastAdModeEnabled",FastAdMode.enabled(ctx))
     put("imageRecognition","PixelAdCloseDetector 540px; confirmed ad session and repeated location")
     put("localOnlyUntilUserShares",true)
    }.toString(2))
    put("README_まず読む.txt",
     "Torima Auto v3.14 の診断データを1ファイルにまとめたZIPです。\n" +
     "07_HOME復旧履歴.json はアプリ情報・強制停止UI・再起動の成功や失敗の記録です。\n" +
     "00_指タップと自動の比較.txt を先に読むと両者の結果が分かります。\n" +
     "06_指タップ比較イベント.json は手動試行のCLICK通知・画面遷移時刻・画面情報です。\n" +
     "まず 00_HOME_診断結果.txt を開くと、HOMEから実際に先に進めた" +
     "方式（または未確認）が分かります。\n" +
     "01_HOME_試行詳細.json には押し方、入力結果、CLICKイベント、" +
     "その後の画面判定と観測根拠が記録されています。\n" +
     "02～05には操作履歴、回収履歴、UI画面記録、学習済み操作をまとめています。\n" +
     "screenshots/ に保存されていた実際の前後画像と検出結果があります。高速実験は広告後の固定保護をスキップします。\n" +
     "HOME→広告確認と報酬獲得は別です。画面を識別できない場合は成功扱いしません。\n" +
     "09_学習診断.json は位置指定・画像取得・指タップ待機・成功/失敗の専用記録です。\n" +
     "10_撮影診断.json は撮影予約・保存結果・失敗理由です。\n" +
     "ZIP内には画面画像や操作記録が含まれます。共有相手は自分で選んでください。\n")
   }
   if(archive.exists())archive.delete()
   if(!temp.renameTo(archive)){
    temp.copyTo(archive,overwrite=true)
    temp.delete()
   }
   return archive
  }catch(e:Exception){
   temp.delete()
   throw e
  }
 }
}
