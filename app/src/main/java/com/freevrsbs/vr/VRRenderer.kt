package com.freevrsbs.vr
import android.graphics.SurfaceTexture
import android.opengl.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.view.Surface
import java.nio.ByteBuffer
object CaptureSurfaceRegistry { @Volatile var surface:Surface?=null }
class VRRenderer(private val onReady:(Surface)->Unit):GLSurfaceView.Renderer {
 private var texture=0; private var st:SurfaceTexture?=null; private var program=0; private var changed=false; private var frames=0; var fps=0; private var tick=System.nanoTime()
 override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?){ texture=createTexture(); st=SurfaceTexture(texture).also { it.setOnFrameAvailableListener { changed=true } }; val surface=Surface(st);CaptureSurfaceRegistry.surface=surface;onReady(surface);program=program() }
 override fun onSurfaceChanged(gl:GL10?,w:Int,h:Int){ GLES30.glViewport(0,0,w,h) }
 override fun onDrawFrame(gl:GL10?){ if(changed){st?.updateTexImage();changed=false}; GLES30.glClearColor(0f,0f,0f,1f);GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT); GLES30.glUseProgram(program);GLES30.glActiveTexture(GLES30.GL_TEXTURE0);GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture); val p=GLES30.glGetAttribLocation(program,"p");val uv=GLES30.glGetAttribLocation(program,"uv"); val vertices=floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f); val tex=floatArrayOf(0f,1f,1f,1f,0f,0f,1f,0f); val vb=ByteBuffer.allocateDirect(32).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().put(vertices);vb.position(0);val tb=ByteBuffer.allocateDirect(32).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().put(tex);tb.position(0);GLES30.glEnableVertexAttribArray(p);GLES30.glVertexAttribPointer(p,2,GLES30.GL_FLOAT,false,0,vb);GLES30.glEnableVertexAttribArray(uv);GLES30.glVertexAttribPointer(uv,2,GLES30.GL_FLOAT,false,0,tb); val w=GLES30.glGetIntegervArray(GLES30.GL_VIEWPORT,2); GLES30.glViewport(0,0,w[2]/2,w[3]);GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP,0,4);GLES30.glViewport(w[2]/2,0,w[2]/2,w[3]);GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP,0,4);frames++; val now=System.nanoTime();if(now-tick>1_000_000_000L){fps=frames;frames=0;tick=now} }
 private fun createTexture():Int { val a=IntArray(1);GLES30.glGenTextures(1,a,0);GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,a[0]);GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR);GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);return a[0] }
 private fun program():Int { fun shader(t:Int,s:String)=GLES30.glCreateShader(t).also{GLES30.glShaderSource(it,s);GLES30.glCompileShader(it)};val v=shader(GLES30.GL_VERTEX_SHADER,"attribute vec2 p;attribute vec2 uv;varying vec2 vUv;void main(){vUv=uv;gl_Position=vec4(p,0.,1.);}");val f=shader(GLES30.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float;varying vec2 vUv;uniform samplerExternalOES s;void main(){gl_FragColor=texture2D(s,vUv);}");return GLES30.glCreateProgram().also{GLES30.glAttachShader(it,v);GLES30.glAttachShader(it,f);GLES30.glLinkProgram(it)} }
}
private fun GLES30.glGetIntegervArray(p:Int,n:Int):IntArray=IntArray(4).also{GLES30.glGetIntegerv(p,it,0)}
