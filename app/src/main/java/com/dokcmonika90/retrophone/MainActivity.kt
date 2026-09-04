package com.dokcmonika90.retrophone

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private external fun nativeStatus(): String
    private external fun nativeLoad(bytes: ByteArray): Boolean
    private external fun nativeReset()
    private external fun nativeRunFrame()
    companion object { init { System.loadLibrary("retro_core") } }

    private lateinit var status: TextView
    private val picker = 42

    override fun onCreate(state: Bundle?) { super.onCreate(state)
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(32,32,32,32) }
        val title = TextView(this).apply { text="RETRO PHONE\nNES Recompiler"; textSize=28f; gravity=Gravity.CENTER }
        status = TextView(this).apply { text=nativeStatus(); textSize=17f; gravity=Gravity.CENTER; setPadding(0,24,0,24) }
        val open = Button(this).apply { text="OPEN NES ROM"; setOnClickListener { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/octet-stream"; addCategory(Intent.CATEGORY_OPENABLE) }, picker) } }
        val run = Button(this).apply { text="RUN FRAME"; setOnClickListener { nativeRunFrame(); status.text=nativeStatus() } }
        val reset = Button(this).apply { text="RESET"; setOnClickListener { nativeReset(); status.text="CPU reset" } }
        root.addView(title); root.addView(status); root.addView(open); root.addView(run); root.addView(reset); setContentView(root)
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){ super.onActivityResult(requestCode,resultCode,data); if(requestCode!=picker||resultCode!=RESULT_OK)return; val uri=data?.data?:return; try { contentResolver.openInputStream(uri).use { input -> val bytes=input?.readBytes()?:ByteArray(0); status.text=if(nativeLoad(bytes)) "Loaded: ${displayName(uri)}\n${nativeStatus()}" else "Not a supported iNES/NES 2.0 ROM" } } catch(e:Exception){ status.text="Could not read ROM: ${e.message}" } }
    private fun displayName(uri:Uri):String { var name="ROM"; contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{ if(it.moveToFirst()) name=it.getString(0) }; return name }
}
