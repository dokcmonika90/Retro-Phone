package com.dokcmonika90.retrophone

import android.widget.EditText

var EditText.singleLine: Boolean
    get() = maxLines == 1
    set(value) { setSingleLine(value) }

object AlertDialog {
    class Builder(context: android.content.Context) {
        private val builder = android.app.AlertDialog.Builder(context)
        fun setTitle(value: String): Builder { builder.setTitle(value); return this }
        fun setMessage(value: String): Builder { builder.setMessage(value); return this }
        fun setView(value: android.view.View): Builder { builder.setView(value); return this }
        fun setNegativeButton(value: String, listener: android.content.DialogInterface.OnClickListener?): Builder { builder.setNegativeButton(value, listener); return this }
        fun setPositiveButton(value: String, listener: android.content.DialogInterface.OnClickListener?): Builder { builder.setPositiveButton(value, listener); return this }
        fun show(): android.app.AlertDialog = builder.show()
    }
}
