package com.paperplan.app.core.gpu

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

enum class ScanFilterMode(val shaderId: Int, val label: String) {
    ORIGINAL_WARP(0, "Perspective Warp"),
    CLEAN_COLOR(1, "GPU Shadow Clean"),
    GRAYSCALE(2, "GPU Grayscale"),
    ADAPTIVE_BW(3, "GPU Adaptive B&W")
}

data class GpuProcessResult(
    val bitmap: Bitmap,
    val gpuRenderer: String,
    val gpuVendor: String,
    val elapsedMs: Long,
    val outputWidth: Int,
    val outputHeight: Int,
    val mode: ScanFilterMode
)

/**
 * Offscreen OpenGL ES 2.0 FBO image processor.
 * Runs perspective homography rectification, bright-weighted local illumination estimation,
 * shadow removal, and adaptive thresholding directly in GPU fragment shaders.
 */
object GlImageProcessor {

    suspend fun processOnGpu(
        source: Bitmap,
        corners: QuadCorners,
        mode: ScanFilterMode,
        maxDimension: Int = 2048
    ): GpuProcessResult = withContext(Dispatchers.Default) {
        val startMs = SystemClock.elapsedRealtime()
        val invH = HomographySolver.solveInverseHomography(corners)

        var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var context: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        val allocatedTextures = mutableListOf<Int>()
        val allocatedFbos = mutableListOf<Int>()
        val allocatedPrograms = mutableListOf<Int>()

        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display !== EGL14.EGL_NO_DISPLAY) { "No EGL display available." }

            val versions = IntArray(2)
            check(EGL14.eglInitialize(display, versions, 0, versions, 1)) {
                "Failed to initialize EGL."
            }

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            check(
                EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) &&
                    numConfigs[0] > 0 && configs[0] != null
            ) { "No OpenGL ES 2.0 EGLConfig found." }

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            check(context !== EGL14.EGL_NO_CONTEXT) { "Failed to create EGLContext." }

            val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], pbufferAttribs, 0)
            check(surface !== EGL14.EGL_NO_SURFACE) { "Failed to create offscreen EGLSurface." }

            check(EGL14.eglMakeCurrent(display, surface, surface, context)) {
                "Failed to bind EGLContext."
            }

            val gpuRenderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown GPU"
            val gpuVendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown Vendor"

            val maxTexArr = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTexArr, 0)
            val effectiveMaxDim = min(maxDimension, max(1024, maxTexArr[0]))
            val (dstW, dstH) = HomographySolver.computeTargetDimensions(
                srcWidth = source.width,
                srcHeight = source.height,
                corners = corners,
                maxDimension = effectiveMaxDim
            )

            val quadPosBuf = createFloatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            val quadUvBuf = createFloatBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))

            // Upload source bitmap once to GPU texture
            val srcTex = createTexture(allocatedTextures)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, source, 0)

            // Pass 1: Perspective warp into FBO
            val (warpFbo, warpTex) = createRenderTarget(dstW, dstH, allocatedFbos, allocatedTextures)
            val warpProgram = compileProgram(VERTEX_SHADER, WARP_FRAGMENT_SHADER, allocatedPrograms)
            renderQuad(warpProgram, warpFbo, dstW, dstH, quadPosBuf, quadUvBuf) { prog ->
                bindTextureUnit(prog, "uTexture", 0, srcTex)
                val hLoc = GLES20.glGetUniformLocation(prog, "uInvH")
                GLES20.glUniformMatrix3fv(hLoc, 1, false, invH, 0)
            }

            val resultBitmap: Bitmap
            if (mode == ScanFilterMode.ORIGINAL_WARP) {
                resultBitmap = readFramebufferToBitmap(warpFbo, dstW, dstH)
            } else {
                // Pass 1b + 1c: 1/6-res separable bright-biased background illumination estimator
                val bgW = max(32, dstW / 6)
                val bgH = max(32, dstH / 6)
                val (blurHFbo, blurHTex) = createRenderTarget(bgW, bgH, allocatedFbos, allocatedTextures)
                val (blurVFbo, blurVTex) = createRenderTarget(bgW, bgH, allocatedFbos, allocatedTextures)
                val blurProgram = compileProgram(VERTEX_SHADER, BG_BLUR_FRAGMENT_SHADER, allocatedPrograms)

                val stepScale = 2.6f
                renderQuad(blurProgram, blurHFbo, bgW, bgH, quadPosBuf, quadUvBuf) { prog ->
                    bindTextureUnit(prog, "uTexture", 0, warpTex)
                    val stepLoc = GLES20.glGetUniformLocation(prog, "uTexelStep")
                    GLES20.glUniform2f(stepLoc, stepScale / bgW.toFloat(), 0f)
                }
                renderQuad(blurProgram, blurVFbo, bgW, bgH, quadPosBuf, quadUvBuf) { prog ->
                    bindTextureUnit(prog, "uTexture", 0, blurHTex)
                    val stepLoc = GLES20.glGetUniformLocation(prog, "uTexelStep")
                    GLES20.glUniform2f(stepLoc, 0f, stepScale / bgH.toFloat())
                }

                // Pass 2: Illumination normalization / Grayscale / Adaptive B&W
                val (finalFbo, _) = createRenderTarget(dstW, dstH, allocatedFbos, allocatedTextures)
                val enhanceProgram = compileProgram(VERTEX_SHADER, ENHANCE_FRAGMENT_SHADER, allocatedPrograms)
                renderQuad(enhanceProgram, finalFbo, dstW, dstH, quadPosBuf, quadUvBuf) { prog ->
                    bindTextureUnit(prog, "uWarpedTex", 0, warpTex)
                    bindTextureUnit(prog, "uBgTex", 1, blurVTex)
                    val modeLoc = GLES20.glGetUniformLocation(prog, "uMode")
                    GLES20.glUniform1i(modeLoc, mode.shaderId)
                }
                resultBitmap = readFramebufferToBitmap(finalFbo, dstW, dstH)
            }

            GLES20.glFinish()
            val elapsed = SystemClock.elapsedRealtime() - startMs

            return@withContext GpuProcessResult(
                bitmap = resultBitmap,
                gpuRenderer = gpuRenderer,
                gpuVendor = gpuVendor,
                elapsedMs = elapsed,
                outputWidth = dstW,
                outputHeight = dstH,
                mode = mode
            )
        } finally {
            if (display !== EGL14.EGL_NO_DISPLAY && context !== EGL14.EGL_NO_CONTEXT) {
                if (allocatedPrograms.isNotEmpty()) {
                    allocatedPrograms.forEach { GLES20.glDeleteProgram(it) }
                }
                if (allocatedFbos.isNotEmpty()) {
                    GLES20.glDeleteFramebuffers(allocatedFbos.size, allocatedFbos.toIntArray(), 0)
                }
                if (allocatedTextures.isNotEmpty()) {
                    GLES20.glDeleteTextures(allocatedTextures.size, allocatedTextures.toIntArray(), 0)
                }
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            }
            if (surface !== EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context !== EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            if (display !== EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(display)
        }
    }

    private fun readFramebufferToBitmap(fbo: Int, width: Int, height: Int): Bitmap {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        buffer.rewind()
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            copyPixelsFromBuffer(buffer)
        }
    }

    private fun renderQuad(
        program: Int,
        fbo: Int,
        width: Int,
        height: Int,
        posBuf: FloatBuffer,
        uvBuf: FloatBuffer,
        bindUniforms: (Int) -> Unit
    ) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program)

        val posLoc = GLES20.glGetAttribLocation(program, "aPosition")
        val uvLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, posBuf)
        GLES20.glEnableVertexAttribArray(uvLoc)
        GLES20.glVertexAttribPointer(uvLoc, 2, GLES20.GL_FLOAT, false, 0, uvBuf)

        bindUniforms(program)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(uvLoc)
    }

    private fun bindTextureUnit(program: Int, uniformName: String, unit: Int, texId: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        val loc = GLES20.glGetUniformLocation(program, uniformName)
        GLES20.glUniform1i(loc, unit)
    }

    private fun createRenderTarget(
        width: Int,
        height: Int,
        fboRegistry: MutableList<Int>,
        texRegistry: MutableList<Int>
    ): Pair<Int, Int> {
        val tex = createTexture(texRegistry)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        val fbos = IntArray(1)
        GLES20.glGenFramebuffers(1, fbos, 0)
        fboRegistry.add(fbos[0])
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbos[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex, 0
        )
        check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) {
            "FBO incomplete for ${width}x${height}"
        }
        return fbos[0] to tex
    }

    private fun createTexture(texRegistry: MutableList<Int>): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texRegistry.add(ids[0])
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    private fun compileProgram(
        vertexSrc: String,
        fragmentSrc: String,
        registry: MutableList<Int>
    ): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        val info = GLES20.glGetProgramInfoLog(program)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        check(status[0] == GLES20.GL_TRUE) { "Program link error: $info" }
        registry.add(program)
        return program
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("Shader compile failed: $log")
        }
        return shader
    }

    private fun createFloatBuffer(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(data)
            .apply { position(0) }

    private const val PRECISION_HEADER = """
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
    """

    private const val VERTEX_SHADER = """
        attribute vec2 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = vec4(aPosition, 0.0, 1.0);
            vTexCoord = aTexCoord;
        }
    """

    private const val WARP_FRAGMENT_SHADER = """
        $PRECISION_HEADER
        uniform sampler2D uTexture;
        uniform mat3 uInvH;
        varying vec2 vTexCoord;
        void main() {
            vec3 mapped = uInvH * vec3(vTexCoord, 1.0);
            vec2 uv = mapped.xy / max(mapped.z, 0.0001);
            if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);
            } else {
                gl_FragColor = texture2D(uTexture, uv);
            }
        }
    """

    private const val BG_BLUR_FRAGMENT_SHADER = """
        $PRECISION_HEADER
        uniform sampler2D uTexture;
        uniform vec2 uTexelStep;
        varying vec2 vTexCoord;
        void main() {
            vec3 acc = vec3(0.0);
            float wSum = 0.0;
            for (int i = -4; i <= 4; i++) {
                vec2 uv = clamp(vTexCoord + uTexelStep * float(i), 0.002, 0.998);
                vec3 c = texture2D(uTexture, uv).rgb;
                float y = dot(c, vec3(0.299, 0.587, 0.114));
                float w = 0.15 + y * y;
                acc += c * w;
                wSum += w;
            }
            gl_FragColor = vec4(acc / max(wSum, 0.001), 1.0);
        }
    """

    private const val ENHANCE_FRAGMENT_SHADER = """
        $PRECISION_HEADER
        uniform sampler2D uWarpedTex;
        uniform sampler2D uBgTex;
        uniform int uMode;
        varying vec2 vTexCoord;
        void main() {
            vec3 c = texture2D(uWarpedTex, vTexCoord).rgb;
            vec3 bg = max(texture2D(uBgTex, vTexCoord).rgb, vec3(0.12));
            float y = dot(c, vec3(0.299, 0.587, 0.114));
            float bgY = max(dot(bg, vec3(0.299, 0.587, 0.114)), 0.12);

            if (uMode == 1) {
                float gain = clamp(0.95 / bgY, 0.75, 3.2);
                vec3 lit = clamp(c * gain, 0.0, 1.0);
                vec3 clean = clamp((lit - 0.5) * 1.12 + 0.5, 0.0, 1.0);
                gl_FragColor = vec4(clean, 1.0);
            } else if (uMode == 2) {
                float normG = clamp((y / bgY) * 0.94, 0.0, 1.0);
                float crisp = clamp((normG - 0.52) * 1.25 + 0.52, 0.0, 1.0);
                gl_FragColor = vec4(vec3(crisp), 1.0);
            } else if (uMode == 3) {
                float ratio = y / bgY;
                float bw = smoothstep(0.74, 0.86, ratio);
                gl_FragColor = vec4(vec3(bw), 1.0);
            } else {
                gl_FragColor = vec4(c, 1.0);
            }
        }
    """
}
