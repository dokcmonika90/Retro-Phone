package com.dokcmonika90.retrophone

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.media.*
import android.os.Bundle
import android.view.*
import android.widget.*
import java.util.zip.ZipInputStream
import kotlin.math.max

class MainActivity : Activity() {
    private external fun nativeVersion(): String
    private external fun nativeLoad(bytes: ByteArray): Boolean
    private external fun nativeReset()
    private external fun nativeFrame(): IntArray
    private external fun nativeRunFrame()
    private external fun nativeButtons(mask: Int)
    private external fun nativeAudio(): ShortArray
    private external fun nativeIsLoaded(): Boolean
    companion object { init { System.loadLibrary("retro_recompiler") } }

    private lateinit var screen: GameView
    private lateinit var status: TextView
    private var audio: AudioTrack? = null
    private var running = false
    private val frameMs = 16L

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        screen = GameView()
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        root.addView(screen, FrameLayout.LayoutParams(-1, -1))
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(18, 10, 18, 10); setBackgroundColor(0xAA101010.toInt()) }
        val load = Button(this).apply { text = "LOAD NES"; setOnClickListener { pickRom() } }
        val reset = Button(this).apply { text = "RESET"; setOnClickListener { nativeReset(); status.text = "  Reset" } }
        status = TextView(this).apply { text = "  ${nativeVersion()}"; setTextColor(Color.WHITE); textSize = 13f; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(load); bar.addView(reset); bar.addView(status, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(bar, FrameLayout.LayoutParams(-1, 62, Gravity.TOP))
        setContentView(root)
        startAudio()
        startLoop()
    }

    private fun pickRom() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, 42)
    }

    private fun validHeader(b: ByteArray): Boolean = b.size >= 16 &&
        b[0].toInt() == 'N'.code && b[1].toInt() == 'E'.code && b[2].toInt() == 'S'.code && b[3].toInt() == 0x1A

    private fun unwrapRom(data: ByteArray): Pair<ByteArray, String>? {
        if (validHeader(data)) return data to "NES ROM"
        if (data.size >= 4 && data[0].toInt() == 0x50 && data[1].toInt() == 0x4b) {
            ZipInputStream(data.inputStream()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    if (!e.isDirectory && e.name.lowercase().endsWith(".nes")) {
                        val rom = zip.readBytes()
                        if (validHeader(rom)) return rom to e.name
                    }
                    e = zip.nextEntry
                }
            }
        }
        return null
    }

    override fun onActivityResult(req: Int, result: Int, data: Intent?) {
        super.onActivityResult(req, result, data)
        if (req != 42 || result != RESULT_OK || data?.data == null) return
        try {
            val uri = data.data!!
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            val raw = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (raw == null) { status.text = "  ROM could not be read"; Toast.makeText(this, "Could not read that file", Toast.LENGTH_LONG).show(); return }
            val unwrapped = unwrapRom(raw)
            if (unwrapped == null) {
                status.text = "  Unsupported ROM wrapper"
                Toast.makeText(this, "No valid .nes ROM was found in that file", Toast.LENGTH_LONG).show()
                return
            }
            val bytes = unwrapped.first
            val mapper = ((bytes[6].toInt() and 0xF0) shr 4) or (bytes[7].toInt() and 0xF0)
            if (mapper != 0) {
                status.text = "  Mapper $mapper not supported yet"
                Toast.makeText(this, "This ROM uses mapper $mapper. Mapper 0 is supported in this build.", Toast.LENGTH_LONG).show()
                return
            }
            val ok = nativeLoad(bytes)
            if (ok) {
                nativeReset()
                status.text = "  ROM loaded: ${unwrapped.second} (${bytes.size / 1024} KB)"
                Toast.makeText(this, "NES ROM loaded", Toast.LENGTH_SHORT).show()
            } else {
                status.text = "  ROM rejected by emulator"
                Toast.makeText(this, "NES ROM could not be loaded", Toast.LENGTH_LONG).show()
            }
            screen.invalidate()
        } catch (e: Exception) {
            status.text = "  ROM load failed"
            Toast.makeText(this, "ROM load failed: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startAudio() {
        val sr = 44100
        val min = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audio = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(max(min, sr / 4 * 2)).setTransferMode(AudioTrack.MODE_STREAM).build()
        audio?.play()
        Thread { while (!isFinishing) { val pcm = nativeAudio(); if (pcm.isNotEmpty()) audio?.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING) else Thread.sleep(5) } }.start()
    }

    private fun startLoop() {
        running = true
        Thread { while (running && !isFinishing) { val start = System.nanoTime(); if (nativeIsLoaded()) nativeRunFrame(); runOnUiThread { screen.invalidate() }; val sleep = frameMs - (System.nanoTime() - start) / 1_000_000L; if (sleep > 0) Thread.sleep(sleep) } }.start()
    }

    inner class GameView : View(this) {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val bitmap = Bitmap.createBitmap(256, 240, Bitmap.Config.ARGB_8888)
        private var lastMask = 0
        override fun onDraw(c: Canvas) {
            val pixels = nativeFrame()
            if (pixels.size == 256 * 240) bitmap.setPixels(pixels, 0, 256, 0, 0, 256, 240)
            val scale = minOf(width / 256f, height / 240f)
            val left = (width - 256f * scale) / 2f; val top = (height - 240f * scale) / 2f
            c.drawColor(Color.BLACK); c.drawBitmap(bitmap, null, RectF(left, top, left + 256f * scale, top + 240f * scale), paint); drawControls(c)
        }
        private fun drawControls(c: Canvas) {
            paint.alpha = 150; paint.color = Color.WHITE
            val r = minOf(width, height) * .09f; val cx = width * .16f; val cy = height * .77f
            c.drawCircle(cx, cy, r, paint); c.drawCircle(cx-r*1.15f, cy, r*.55f, paint); c.drawCircle(cx+r*1.15f, cy, r*.55f, paint); c.drawCircle(cx, cy-r*1.15f, r*.55f, paint); c.drawCircle(cx, cy+r*1.15f, r*.55f, paint)
            val bx = width * .84f; val by = height * .77f; c.drawCircle(bx, by, r, paint); c.drawCircle(bx-r*1.1f, by+r*.65f, r*.72f, paint)
            paint.textAlign = Paint.Align.CENTER; paint.textSize = r*.55f; paint.color = Color.DKGRAY; paint.alpha=230; c.drawText("A", bx, by+r*.2f, paint); c.drawText("B", bx-r*1.1f, by+r*.65f+r*.2f, paint)
            paint.alpha=150; c.drawRoundRect(RectF(width*.43f,height*.86f,width*.50f,height*.91f),12f,12f,paint); c.drawRoundRect(RectF(width*.51f,height*.86f,width*.58f,height*.91f),12f,12f,paint)
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            var mask=0; val x=e.x; val y=e.y; val w=width.toFloat(); val h=height.toFloat(); val d=minOf(w,h)*.16f; val cx=w*.16f; val cy=h*.77f; val bx=w*.84f; val by=h*.77f
            if (e.action != MotionEvent.ACTION_UP) {
                if ((x-cx)*(x-cx)+(y-(cy-d*.7f))*(y-(cy-d*.7f)) < d*d*.35f) mask=mask or 8
                if ((x-cx)*(x-cx)+(y-(cy+d*.7f))*(y-(cy+d*.7f)) < d*d*.35f) mask=mask or 4
                if ((x-(cx-d*.7f))*(x-(cx-d*.7f))+(y-cy)*(y-cy) < d*d*.35f) mask=mask or 2
                if ((x-(cx+d*.7f))*(x-(cx+d*.7f))+(y-cy)*(y-cy) < d*d*.35f) mask=mask or 1
                if ((x-bx)*(x-bx)+(y-by)*(y-by) < d*d*.35f) mask=mask or 16
                if ((x-(bx-d*.9f))*(x-(bx-d*.9f))+(y-(by+d*.55f))*(y-(by+d*.55f)) < d*d*.35f) mask=mask or 32
                if (x>w*.43f && x<w*.50f && y>h*.84f) mask=mask or 64
                if (x>w*.51f && x<w*.58f && y>h*.84f) mask=mask or 128
            }
            if (mask != lastMask) { lastMask=mask; nativeButtons(mask) }; invalidate(); return true
        }
    }
    override fun onDestroy() { running=false; audio?.stop(); audio?.release(); super.onDestroy() }
}
