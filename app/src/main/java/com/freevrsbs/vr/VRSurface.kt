package com.freevrsbs.vr
import android.content.Context
import android.opengl.GLSurfaceView
class VRSurface(context:Context,onReady:(android.view.Surface)->Unit):GLSurfaceView(context){ val renderer=VRRenderer(onReady);init{setEGLContextClientVersion(3);setRenderer(renderer);renderMode=RENDERMODE_CONTINUOUSLY} override fun onDetachedFromWindow(){CaptureSurfaceRegistry.surface?.release();CaptureSurfaceRegistry.surface=null;super.onDetachedFromWindow()} }
