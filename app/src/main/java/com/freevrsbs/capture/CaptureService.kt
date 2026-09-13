package com.freevrsbs.capture
import android.app.*
import android.content.*
import android.media.projection.MediaProjectionManager
import android.os.*
import androidx.core.app.NotificationCompat
import com.freevrsbs.R
import com.freevrsbs.vr.CaptureSurfaceRegistry
class CaptureService:Service(){ private var manager:ScreenCaptureManager?=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int { val data=intent?.getParcelableExtra<Intent>(EXTRA_DATA) ?: return START_NOT_STICKY; val code=intent.getIntExtra(EXTRA_CODE,Activity.RESULT_CANCELED); startForeground(7,notification()); val mp=getSystemService(MediaProjectionManager::class.java).getMediaProjection(code,data) ?: return START_NOT_STICKY; val metrics=resources.displayMetrics; val surface=CaptureSurfaceRegistry.surface ?: return START_NOT_STICKY; manager=ScreenCaptureManager(this,mp){stopSelf()}; manager?.start(surface,metrics.widthPixels,metrics.heightPixels,metrics.densityDpi); return START_NOT_STICKY }
 override fun onBind(i:Intent?)=null
 override fun onDestroy(){manager?.release();super.onDestroy()}
 private fun notification():Notification { val channel=NotificationChannel("capture",getString(R.string.notification_channel),NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager::class.java).createNotificationChannel(channel);return NotificationCompat.Builder(this,"capture").setSmallIcon(android.R.drawable.presence_video_online).setContentTitle(getString(R.string.app_name)).setContentText(getString(R.string.notification_text)).build() }
 companion object { const val EXTRA_DATA="data";const val EXTRA_CODE="code";fun start(context:Context,code:Int,data:Intent){ val i=Intent(context,CaptureService::class.java).putExtra(EXTRA_CODE,code).putExtra(EXTRA_DATA,data); context.startForegroundService(i) } }
}
