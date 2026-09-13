package com.freevrsbs.ui
import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import com.freevrsbs.R
import com.freevrsbs.bluetooth.BluetoothInputManager
import com.freevrsbs.capture.CaptureService
import com.freevrsbs.input.InputEventMonitor
import com.freevrsbs.sensors.HeadTracker
import com.freevrsbs.vr.VRSurface
class MainActivity:Activity(){ private lateinit var root:LinearLayout;private var vr:VRSurface?=null;private var tracker:HeadTracker?=null;private val captureRequest=41
 override fun onCreate(s:Bundle?){super.onCreate(s);if(!getPreferences(0).getBoolean("seen",false))intro() else home()}
 private fun intro(){ val box=vertical(); box.addView(title(getString(R.string.app_name))); listOf(R.string.intro_description,R.string.intro_capture,R.string.intro_private,R.string.intro_limit).forEach{box.addView(text(it))};box.addView(button(R.string.start){getPreferences(0).edit().putBoolean("seen",true).apply();home()});box.addView(button(R.string.permission_settings){requestCapture()});box.addView(button(R.string.later){getPreferences(0).edit().putBoolean("seen",true).apply();home()});setContentView(box) }
 private fun home(){ root=vertical();root.addView(title(getString(R.string.app_name)));root.addView(button(R.string.vr_start){startVr()}.also{it.contentDescription=getString(R.string.cd_vr_start)});root.addView(button(R.string.vr_stop){stopVr()}.also{it.contentDescription=getString(R.string.cd_vr_stop)});root.addView(title(getString(R.string.capture)));root.addView(text(R.string.permission_needed));root.addView(button(R.string.capture_permission){requestCapture()}.also{it.contentDescription=getString(R.string.cd_capture)});root.addView(title(getString(R.string.vr_settings)));listOf(R.string.ipd,R.string.eye_separation,R.string.fov,R.string.lens_distortion,R.string.barrel_distortion,R.string.curvature,R.string.head_sensitivity).forEach{root.addView(setting(it))};root.addView(button(R.string.recenter){tracker?.recenter()}.also{it.contentDescription=getString(R.string.cd_recenter)});root.addView(title(getString(R.string.performance)));root.addView(text(R.string.mode_2d));root.addView(text(R.string.privacy));root.addView(button(R.string.bluetooth_input){bluetoothDialog()}.also{it.contentDescription=getString(R.string.cd_bluetooth)});root.addView(button(R.string.accessibility_enable){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))});root.addView(text(R.string.touch_limit));root.addView(text(R.string.touch_alternative));setContentView(ScrollView(this).apply{addView(root)}) }
 private fun startVr(){ window.insetsController?.hide(WindowInsets.Type.systemBars());window.setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);vr=VRSurface(this){ };setContentView(vr);tracker=HeadTracker(this){};if(!tracker!!.start())Toast.makeText(this,R.string.gyro_missing,Toast.LENGTH_LONG).show();requestCapture() }
 private fun stopVr(){ tracker?.stop();tracker=null;stopService(Intent(this,CaptureService::class.java));window.insetsController?.show(WindowInsets.Type.systemBars());window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);home() }
 private fun requestCapture(){ val manager=getSystemService(MediaProjectionManager::class.java);startActivityForResult(manager.createScreenCaptureIntent(),captureRequest) }
 @Deprecated("Deprecated in Java") override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==captureRequest){if(c==RESULT_OK&&d!=null){CaptureService.start(this,c,d);Toast.makeText(this,R.string.permission_granted,Toast.LENGTH_SHORT).show()}else Toast.makeText(this,R.string.capture_denied,Toast.LENGTH_LONG).show()}}
 private fun bluetoothDialog(){if(android.os.Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT),9);Toast.makeText(this,R.string.bluetooth_permission,Toast.LENGTH_LONG).show();return};val devices=BluetoothInputManager(this).devices();AlertDialog.Builder(this).setTitle(R.string.connected_devices).setItems(devices.map{it.name+" · "+it.type}.ifEmpty{listOf(getString(R.string.disconnected))}.toTypedArray(),null).setPositiveButton(R.string.refresh,null).setNeutralButton(R.string.input_test){_,_-> inputTest()}.show() }
 private fun inputTest(){ val monitor=InputEventMonitor{};AlertDialog.Builder(this).setTitle(R.string.input_test).setMessage(getString(R.string.recent_events)).setPositiveButton(android.R.string.ok,null).show() }
 override fun dispatchKeyEvent(e:KeyEvent)=InputEventMonitor({}).onKey(e) || super.dispatchKeyEvent(e)
 override fun dispatchGenericMotionEvent(e:MotionEvent)=InputEventMonitor({}).onMotion(e) || super.dispatchGenericMotionEvent(e)
 private fun vertical()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(32,32,32,32)}
 private fun title(s:String)=TextView(this).apply{text=s;textSize=24f;setPadding(0,20,0,12)}
 private fun text(id:Int)=TextView(this).apply{setText(id);textSize=16f;setPadding(0,8,0,8)}
 private fun button(id:Int,click:()->Unit)=Button(this).apply{setText(id);setOnClickListener{click()}}
 private fun setting(id:Int)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;addView(text(id));addView(SeekBar(this@MainActivity).apply{max=100;progress=50})}
}
