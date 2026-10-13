package com.neru.powlautotest
import android.content.*
import android.os.*
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
class MainActivity:AppCompatActivity(){
 private val h=Handler(Looper.getMainLooper())
 private lateinit var results:TextView
 private lateinit var state:TextView
 private var lastShown=""
 private val refresh=object:Runnable{override fun run(){
  val s=PowlAccessibilityService.instance
  state.text=if(s==null)"Service: 未接続" else "Service: 接続済み"
  val text=s?.report.orEmpty()
  if(text.isNotBlank() && text!=lastShown){lastShown=text;results.text=text}
  h.postDelayed(this,500)
 }}
 override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_main)
  results=findViewById(R.id.results);state=findViewById(R.id.debugStatus)
  findViewById<Button>(R.id.checkUpdate).setOnClickListener{ TorimaUpdater(this,findViewById(R.id.updateStatus)).check() }
  findViewById<Button>(R.id.openSettings).setOnClickListener{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
  findViewById<Button>(R.id.checkHome).setOnClickListener{runTest(false)}
  findViewById<Button>(R.id.tapHome).setOnClickListener{runTest(true)}
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