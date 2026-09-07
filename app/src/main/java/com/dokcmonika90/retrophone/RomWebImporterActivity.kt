package com.dokcmonika90.retrophone

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.regex.Pattern

/**
 * Web ROM importer for legally distributable ROMs (homebrew, public-domain,
 * or files the user has permission to download). It only follows direct file
 * links exposed by the supplied HTML page; it does not bypass authentication
 * or DRM.
 */
class RomWebImporterActivity : Activity() {
    private val romDirectory by lazy { File(filesDir, "roms").apply { mkdirs() } }
    private lateinit var urlBox: EditText
    private lateinit var status: TextView
    private lateinit var links: LinearLayout

    private val supported = setOf(
        "nes", "fds", "sfc", "smc", "fig", "gb", "gbc", "gba", "md", "gen",
        "smd", "sms", "gg", "n64", "z64", "v64", "nds", "a26", "cue", "iso",
        "img", "pbp", "chd", "cdi", "gdi", "zip", "7z"
    )

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(18, 18, 18))
        }
        val title = TextView(this).apply {
            text = "ROM WEBSITE IMPORT"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        urlBox = EditText(this).apply {
            hint = "https://example.com/roms"
            setSingleLine(true)
            setText(intent?.data?.toString() ?: intent?.getStringExtra("url") ?: "")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        val scan = Button(this).apply {
            text = "SCAN WEBSITE"
            setOnClickListener { scanWebsite() }
        }
        val back = Button(this).apply {
            text = "BACK TO RETRO PHONE"
            setOnClickListener { finish() }
        }
        status = TextView(this).apply {
            text = "Only download ROMs you are legally allowed to obtain."
            setTextColor(Color.LTGRAY)
            setPadding(0, 16, 0, 16)
        }
        links = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(links) }
        root.addView(title)
        root.addView(urlBox)
        root.addView(scan)
        root.addView(status)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(back)
        setContentView(root)
    }

    private fun scanWebsite() {
        val address = urlBox.text.toString().trim()
        if (!address.startsWith("https://") && !address.startsWith("http://")) {
            status.text = "Enter a valid http:// or https:// URL."
            return
        }
        status.text = "Scanning..."
        links.removeAllViews()
        Thread {
            try {
                val connection = (URL(address).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Retro-Phone/1.0")
                }
                val html = connection.inputStream.bufferedReader().use { it.readText() }
                val found = linkedSetOf<String>()
                val pattern = Pattern.compile("href\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']", Pattern.CASE_INSENSITIVE)
                val matcher = pattern.matcher(html)
                while (matcher.find()) {
                    val raw = matcher.group(1) ?: continue
                    val candidate = try { URL(URL(address), raw).toString() } catch (_: Exception) { continue }
                    val path = try { URL(candidate).path.lowercase() } catch (_: Exception) { "" }
                    val ext = path.substringAfterLast('.', "")
                    if (ext in supported) found.add(candidate)
                }
                connection.disconnect()
                runOnUiThread {
                    status.text = if (found.isEmpty()) "No supported direct ROM links found." else "Found ${found.size} downloadable ROM link(s)."
                    found.forEach { addLink(it) }
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Scan failed: ${e.message ?: "network error"}" }
            }
        }.start()
    }

    private fun addLink(link: String) {
        val button = Button(this).apply {
            text = URLDecoder.decode(link.substringAfterLast('/').ifBlank { link }, "UTF-8")
            setOnClickListener { downloadRom(link) }
        }
        links.addView(button)
    }

    private fun downloadRom(link: String) {
        status.text = "Downloading..."
        Thread {
            try {
                val connection = (URL(link).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 30000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Retro-Phone/1.0")
                }
                val name = URLDecoder.decode(link.substringAfterLast('/').substringBefore('?').ifBlank { "download.rom" }, "UTF-8")
                val safeName = name.replace(Regex("[^A-Za-z0-9._() -]"), "_")
                val target = File(romDirectory, safeName)
                connection.inputStream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                connection.disconnect()
                runOnUiThread {
                    status.text = "Downloaded: ${target.name}\nSaved in Retro Phone ROM storage."
                    Toast.makeText(this, "ROM downloaded", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Download failed: ${e.message ?: "network error"}" }
            }
        }.start()
    }
}
