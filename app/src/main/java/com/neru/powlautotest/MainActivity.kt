package com.neru.powlautotest
import android.content.*
import android.os.*
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
class MainActivity:AppCompatActivity(){
 private val h=Handler(Looper.getMainLooper())
 private lateinit var results:TextView
 private lateinit var state:TextView
 private lateinit var collectionOverview:TextView
 private lateinit var homeExperimentInfo:TextView
 private var lastShown=""
 private val refresh=object:Runnable{override fun run(){
  val s=PowlAccessibilityService.instance
  state.text=if(s==null)"Service: 未接続" else "Service: 接続済み"
  collectionOverview.text=CollectionRunStore.summary(this@MainActivity)
  findViewById<TextView>(R.id.visualDiagnosticsCount).text=
   "保存画像：${ScreenshotDiagnostics.count(this@MainActivity)}枚（最大10枚）"
  val preferred=HomeTouchProfile.bestTrialId(this@MainActivity)
  val style=HomeTouchTrials.variants.firstOrNull{it.id==preferred}
  homeExperimentInfo.text=if(style==null)
   "HOME診断：成功方式は未確定。操作バー透過・ノードクリック・親クリック・座標など7通りを検証"
   else "HOME診断：前回画面遷移を確認した方式：${style.label}"
  val text=s?.report.orEmpty()
  if(text.isNotBlank() && text!=lastShown){lastShown=text;results.text=text}
  h.postDelayed(this,500)
 }}
 override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_main)
  results=findViewById(R.id.results);state=findViewById(R.id.debugStatus)
  collectionOverview=findViewById(R.id.collectionOverview)
  homeExperimentInfo=findViewById(R.id.homeExperimentInfo)
  collectionOverview.text=CollectionRunStore.summary(this)
  findViewById<Button>(R.id.checkUpdate).setOnClickListener{ TorimaUpdater(this,findViewById(R.id.updateStatus)).check() }
  findViewById<Button>(R.id.openSettings).setOnClickListener{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
  findViewById<Button>(R.id.checkHome).setOnClickListener{runTest(false)}
  findViewById<Button>(R.id.tapHome).setOnClickListener{runTest(true)}

  findViewById<Button>(R.id.startCollector).setOnClickListener{
   val service=PowlAccessibilityService.instance
   val message=service?.showScreenCollector() ?:"アクセシビリティが未接続です。先に有効にしてください。"
   Toast.makeText(this,message,Toast.LENGTH_LONG).show()
  }
  findViewById<Button>(R.id.stopCollector).setOnClickListener{
   val message=PowlAccessibilityService.instance?.hideScreenCollector() ?: "記録バーは表示されていません"
   Toast.makeText(this,message,Toast.LENGTH_LONG).show()
  }
  findViewById<Button>(R.id.copyCollector).setOnClickListener{
   val data=ScreenCollector.export(this)
   (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
    .setPrimaryClip(ClipData.newPlainText("Torima screen samples",data))
   Toast.makeText(this,"${ScreenCollector.count(this)}画面分のログをコピーしました（${data.length}文字）",Toast.LENGTH_LONG).show()
  }
  findViewById<Button>(R.id.shareVisualDiagnostics).setOnClickListener{
   if(ScreenshotDiagnostics.count(this)==0){
    Toast.makeText(this,"画像記録がありません。トリマの操作バーで「画像」を押してください",Toast.LENGTH_LONG).show()
   }else{
    Toast.makeText(this,"画像のZIPを準備中…",Toast.LENGTH_SHORT).show()
    Thread{
     try{
      val zip=ScreenshotDiagnostics.createShareZip(this)
      runOnUiThread{
       if(zip==null){
        Toast.makeText(this,"画像記録がありません",Toast.LENGTH_LONG).show()
       }else{
        val uri=FileProvider.getUriForFile(this,"$packageName.fileprovider",zip)
        val intent=Intent(Intent.ACTION_SEND).apply{
         type="application/zip"
         putExtra(Intent.EXTRA_STREAM,uri)
         addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent,"Torima画像診断を共有"))
       }
      }
     }catch(e:Exception){
      runOnUiThread{Toast.makeText(this,
       "ZIP共有に失敗：${e.javaClass.simpleName}",Toast.LENGTH_LONG).show()}
     }
    }.start()
   }
  }
  findViewById<Button>(R.id.copyVisualDiagnostics).setOnClickListener{
   val data=ScreenshotDiagnostics.exportInfo(this)
   (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
    .setPrimaryClip(ClipData.newPlainText("Torima 画像診断の候補",data))
   Toast.makeText(this,"候補座標をコピーしました（画像本体はZIP共有）",Toast.LENGTH_LONG).show()
  }
  findViewById<Button>(R.id.clearVisualDiagnostics).setOnClickListener{
   AlertDialog.Builder(this)
    .setTitle("画像診断を削除しますか？")
    .setMessage("保存した画像と候補座標の記録が消えます。共有済みのZIPは削除されません。")
    .setNegativeButton("キャンセル",null)
    .setPositiveButton("削除"){_,_->
     ScreenshotDiagnostics.clear(this)
     findViewById<TextView>(R.id.visualDiagnosticsCount).text="保存画像：0枚"
     Toast.makeText(this,"画像診断を削除しました",Toast.LENGTH_SHORT).show()
    }.show()
  }
  findViewById<Button>(R.id.clearCollector).setOnClickListener{
   AlertDialog.Builder(this)
    .setTitle("収集ログを全件削除しますか？")
    .setMessage("コピー前に削除すると元に戻せません。")
    .setNegativeButton("キャンセル",null)
    .setPositiveButton("削除"){_,_->
     ScreenCollector.clear(this)
     Toast.makeText(this,"収集ログを削除しました",Toast.LENGTH_SHORT).show()
    }.show()
  }

  findViewById<Button>(R.id.copyCollectionHistory).setOnClickListener{
   val data=CollectionRunStore.export(this)
   (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
    .setPrimaryClip(ClipData.newPlainText("Torima 回収タスク履歴",data))
   Toast.makeText(this,"回収履歴をコピーしました",Toast.LENGTH_LONG).show()
  }
  findViewById<Button>(R.id.copyActionHistory).setOnClickListener{
   val data=TeachOverlay.export(this)
   (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
    .setPrimaryClip(ClipData.newPlainText("Torima 操作履歴",data))
   Toast.makeText(this,"操作履歴をコピーしました（${data.length}文字）",Toast.LENGTH_LONG).show()
  }
  findViewById<Button>(R.id.copyLearnedRules).setOnClickListener{
   val data=LearnedActions.export(this)
   (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
    .setPrimaryClip(ClipData.newPlainText("Torima 学習済み操作",data))
   Toast.makeText(this,"学習済み操作をコピーしました（${LearnedActions.count(this)}件）",Toast.LENGTH_LONG).show()
  }
  findViewById<Button>(R.id.clearLearnedRules).setOnClickListener{
   AlertDialog.Builder(this)
    .setTitle("学習した自動操作をすべて削除？")
    .setMessage("学習した操作だけを削除します。通常の画面収集ログはそのまま残ります。")
    .setNegativeButton("キャンセル",null)
    .setPositiveButton("削除"){_,_->
     PowlAccessibilityService.instance?.stopAutomaticOperation()
     LearnedActions.clear(this)
     Toast.makeText(this,"学習した操作ルールを削除しました",Toast.LENGTH_SHORT).show()
    }.show()
  }
  findViewById<Button>(R.id.scanAd).setOnClickListener{runAdScan(false)}
  findViewById<Button>(R.id.scanAdTap).setOnClickListener{runAdScan(true)}
  findViewById<Button>(R.id.runSequence).setOnClickListener{
   val service=PowlAccessibilityService.instance
   if(service==null)Toast.makeText(this,"アクセシビリティをONにしてください",Toast.LENGTH_LONG).show()
   else{service.startSequence();Toast.makeText(this,"5秒以内にトリマ HOMEへ。紫ボタンが押せたか自動確認します",Toast.LENGTH_LONG).show()}
  }
  findViewById<Button>(R.id.stopSequence).setOnClickListener{PowlAccessibilityService.instance?.stopSequence()}
  findViewById<Button>(R.id.copyResults).setOnClickListener{
   (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("トリマ HOME result",results.text.toString()))
   Toast.makeText(this,"コピーしました",Toast.LENGTH_SHORT).show()
  }
 }
 private fun runAdScan(tap:Boolean){
  val s=PowlAccessibilityService.instance
  if(s==null){Toast.makeText(this,"アクセシビリティを有効にしてください",Toast.LENGTH_LONG).show();return}
  s.scheduleAdScan(tap)
  val hint=if(tap)"15秒以内に広告へ。識別できる×があれば1回タップします" else "15秒以内に広告へ。15/17/19秒で3回診断（操作なし）"
  Toast.makeText(this,hint,Toast.LENGTH_LONG).show()
 }
 private fun runTest(tap:Boolean){val s=PowlAccessibilityService.instance
  if(s==null){Toast.makeText(this,"アクセシビリティを有効にしてください",Toast.LENGTH_LONG).show();return}
  s.scheduleHomeTest(tap)
  Toast.makeText(this,"5秒以内にトリマ HOMEへ移動",Toast.LENGTH_LONG).show()
 }
 override fun onResume(){super.onResume();h.removeCallbacks(refresh);h.post(refresh)}
 override fun onPause(){h.removeCallbacks(refresh);super.onPause()}
}