package com.freevrsbs.capture
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.view.Surface
import android.util.Log
class ScreenCaptureManager(private val context:Context,private val projection:MediaProjection,private val onStopped:()->Unit) {
 private var display:VirtualDisplay?=null
 private val callback=object:MediaProjection.Callback(){override fun onStop(){ release(); onStopped() }}
 fun start(surface:Surface,width:Int,height:Int,density:Int){ projection.registerCallback(callback,null); display=projection.createVirtualDisplay("FreeVR-SBS",width,height,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,surface,null,null) }
 fun release(){ display?.release();display=null; try{projection.unregisterCallback(callback)}catch(_:Exception){} }
}
