package com.freevrsbs.vr

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

object CaptureSurfaceRegistry {
    @Volatile var surface: Surface? = null
}

/** Renders the captured external texture once into each half of the display. */
class VRRenderer(private val onReady: (Surface) -> Unit) : GLSurfaceView.Renderer {
    private var textureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var programId = 0
    @Volatile private var frameAvailable = false
    private var frames = 0
    var fps = 0
        private set
    private var fpsStartNanos = System.nanoTime()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        textureId = createExternalTexture()
        surfaceTexture = SurfaceTexture(textureId).also { texture ->
            texture.setOnFrameAvailableListener { frameAvailable = true }
            val captureSurface = Surface(texture)
            CaptureSurfaceRegistry.surface = captureSurface
            onReady(captureSurface)
        }
        programId = createProgram()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (frameAvailable) {
            surfaceTexture?.updateTexImage()
            frameAvailable = false
        }
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(programId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        val position = GLES30.glGetAttribLocation(programId, "aPosition")
        val texCoord = GLES30.glGetAttribLocation(programId, "aTexCoord")
        GLES30.glEnableVertexAttribArray(position)
        GLES30.glVertexAttribPointer(position, 2, GLES30.GL_FLOAT, false, 0, POSITIONS)
        GLES30.glEnableVertexAttribArray(texCoord)
        GLES30.glVertexAttribPointer(texCoord, 2, GLES30.GL_FLOAT, false, 0, TEX_COORDS)

        val viewport = IntArray(4)
        GLES30.glGetIntegerv(GLES30.GL_VIEWPORT, viewport, 0)
        val halfWidth = viewport[2] / 2
        GLES30.glViewport(0, 0, halfWidth, viewport[3])
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glViewport(halfWidth, 0, viewport[2] - halfWidth, viewport[3])
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        frames += 1
        val now = System.nanoTime()
        if (now - fpsStartNanos >= 1_000_000_000L) {
            fps = frames
            frames = 0
            fpsStartNanos = now
        }
    }

    private fun createExternalTexture(): Int = IntArray(1).also { texture ->
        GLES30.glGenTextures(1, texture, 0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0])
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }[0]

    private fun createProgram(): Int {
        val vertex = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        return GLES30.glCreateProgram().also { program ->
            GLES30.glAttachShader(program, vertex)
            GLES30.glAttachShader(program, fragment)
            GLES30.glLinkProgram(program)
        }
    }

    private fun compileShader(type: Int, source: String): Int = GLES30.glCreateShader(type).also { shader ->
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
    }

    private companion object {
        fun buffer(values: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }
        val POSITIONS = buffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        val TEX_COORDS = buffer(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))
        const val VERTEX_SHADER = """#version 300 es
            in vec2 aPosition; in vec2 aTexCoord; out vec2 vTexCoord;
            void main() { vTexCoord = aTexCoord; gl_Position = vec4(aPosition, 0.0, 1.0); }"""
        const val FRAGMENT_SHADER = """#version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float; in vec2 vTexCoord; uniform samplerExternalOES uTexture; out vec4 outColor;
            void main() { outColor = texture(uTexture, vTexCoord); }"""
    }
}
