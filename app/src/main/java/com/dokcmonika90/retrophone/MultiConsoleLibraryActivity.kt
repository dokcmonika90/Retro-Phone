package com.dokcmonika90.retrophone

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import java.io.File

/** Multi-console ROM browser. Only installed emulator cores are playable. */
class MultiConsoleLibraryActivity : Activity() {
    private val romDirectory by lazy { File(getExternalFilesDir(null), "ROMs").apply { mkdirs() } }
    private val prefs by lazy { getSharedPreferences("rom_library", MODE_PRIVATE) }
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private lateinit var filter: Spinner
    private lateinit var status: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 12, 18, 12); setBackgroundColor(Color.rgb(18,18,18)) }
        val title = TextView(this).apply { text = "RETRO PHONE • MULTI-CONSOLE LIBRARY"; textSize = 21f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0,0,0,10) }
        search = EditText(this).apply { hint = "Search games..."; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY) }
        filter = Spinner(this).apply {
            adapter = ArrayAdapter(this@MultiConsoleLibraryActivity, android.R.layout.simple_spinner_dropdown_item,
                arrayOf("All Consoles") + EmulatorCoreRegistry.all().map { it.system }.distinct())
        }
        status = TextView(this).apply { setTextColor(Color.LTGRAY); setPadding(0,8,0,8) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        val back = Button(this).apply { text = "BACK"; setOnClickListener { finish() } }
        root.addView(title); root.addView(search); root.addView(filter); root.addView(status)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(back)
        setContentView(root)
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refresh() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        filter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { refresh() }
        }
        refresh()
    }

    private fun refresh() {
        list.removeAllViews()
        val files = romDirectory.listFiles { f -> f.isFile }?.toList().orEmpty()
        val query = search.text.toString().trim().lowercase()
        val selected = filter.selectedItem?.toString() ?: "All Consoles"
        val rows = files.mapNotNull { file ->
            val core = EmulatorCoreRegistry.chooseForFile(file.name)
            val candidates = EmulatorCoreRegistry.findForExtension(file.extension)
            if (candidates.isEmpty()) return@mapNotNull null
            val display = prefs.getString("name_${file.name}", file.name) ?: file.name
            if (query.isNotEmpty() && !display.lowercase().contains(query)) return@mapNotNull null
            if (selected != "All Consoles" && candidates.none { it.system == selected }) return@mapNotNull null
            Triple(file, display, core)
        }.sortedBy { it.second.lowercase() }

        status.text = "${rows.size} game(s) • ${rows.count { it.third != null }} playable with installed cores"
        if (rows.isEmpty()) {
            list.addView(TextView(this).apply { text = "No supported ROMs found in Retro Phone/ROMs."; textSize = 17f; setTextColor(Color.WHITE); setPadding(0,16,0,16) })
            return
        }
        rows.forEach { (file, display, core) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,7,0,7) }
            val info = TextView(this).apply {
                text = "$display\n${core?.system ?: EmulatorCoreRegistry.findForExtension(file.extension).first().system} • ${file.extension.uppercase()} • ${file.length()/1024} KB"
                textSize = 16f; setTextColor(Color.WHITE)
            }
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val play = Button(this).apply { text = if (core != null) "PLAY" else "CORE NOT INSTALLED"; isEnabled = core != null }
            val fav = Button(this).apply { text = if (prefs.getBoolean("fav_${file.name}", false)) "UNFAVORITE" else "FAVORITE" }
            val del = Button(this).apply { text = "DELETE" }
            play.setOnClickListener {
                if (core?.id == "laines" && file.extension.equals("nes", true)) {
                    // MainActivity remains the NES gameplay host; pass the library file path.
                    startActivity(android.content.Intent(this, MainActivity::class.java).putExtra("rom_path", file.absolutePath))
                }
            }
            fav.setOnClickListener { prefs.edit().putBoolean("fav_${file.name}", !prefs.getBoolean("fav_${file.name}", false)).apply(); refresh() }
            del.setOnClickListener {
                AlertDialog.Builder(this).setTitle("Delete ROM?").setMessage("Delete $display?")
                    .setNegativeButton("CANCEL", null).setPositiveButton("DELETE") { _, _ -> file.delete(); refresh() }.show()
            }
            buttons.addView(play); buttons.addView(fav); buttons.addView(del); row.addView(info); row.addView(buttons); list.addView(row)
        }
    }
}
