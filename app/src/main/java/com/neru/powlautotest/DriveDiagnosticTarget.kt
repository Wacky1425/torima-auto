package com.neru.powlautotest

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

/** Uses only the single Drive document explicitly granted by Android's picker. */
internal object DriveDiagnosticTarget {
 const val FILE_NAME="torima-latest-diagnostics.zip"
 private fun prefs(ctx:Context)=ctx.getSharedPreferences("torima_drive_diagnostics",Context.MODE_PRIVATE)
 fun target(ctx:Context):Uri?=prefs(ctx).getString("uri",null)?.let{Uri.parse(it)}
 fun isDriveAuthority(authority:String?)=authority=="com.google.android.apps.docs.storage"
 fun select(ctx:Context,uri:Uri,flags:Int){
  require(isDriveAuthority(uri.authority)){"左メニューからGoogle Driveを選んでください（端末内保存は対象外）"}
  val access=flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
  require(access and Intent.FLAG_GRANT_WRITE_URI_PERMISSION!=0){"保存先への書き込み権限がありません"}
  ctx.contentResolver.takePersistableUriPermission(uri,access)
  val old=target(ctx)
  prefs(ctx).edit().putString("uri",uri.toString()).commit()
  if(old!=null && old!=uri)try{
   ctx.contentResolver.releasePersistableUriPermission(old,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
  }catch(_:Exception){}
 }
 fun send(ctx:Context,file:File):String {
  val uri=target(ctx)?:error("Driveの保存先を先に設定してください")
  require(isDriveAuthority(uri.authority)){"保存先がGoogle Driveではありません"}
  ctx.contentResolver.openOutputStream(uri,"wt")?.use{output->
   file.inputStream().use{it.copyTo(output)};output.flush()
  }?:error("Driveの保存先を開けません。保存先を再設定してください")
  val message="Driveへの書き込み完了（${file.length()} bytes）。オンライン同期後に確認できます"
  prefs(ctx).edit().putLong("lastWrittenAt",System.currentTimeMillis()).putLong("bytes",file.length()).apply()
  DiagnosticJournal.record(ctx,"drive","written",org.json.JSONObject().put("bytes",file.length()).put("file",FILE_NAME))
  return message
 }
}
