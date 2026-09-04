package com.dokcmonika90.retrophone

object AlertDialog {
    class Builder(context: android.content.Context) {
        private val builder = android.app.AlertDialog.Builder(context)
        fun setTitle(value: String): Builder { builder.setTitle(value); return this }
        fun setView(value: android.view.View): Builder { builder.setView(value); return this }
        fun setNegativeButton(value: String, listener: android.content.DialogInterface.OnClickListener?): Builder { builder.setNegativeButton(value, listener); return this }
        fun show(): android.app.AlertDialog = builder.show()
    }
}
