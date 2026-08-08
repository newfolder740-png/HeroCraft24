package com.herocraft24.core.ui.render

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.herocraft24.core.model.SourceInfo

/**
 * Reusable card section builders used across detail screens.
 */
object CardBuilder {

    fun addSection(context: Context, renderTarget: LinearLayout, title: String, block: LinearLayout.() -> Unit) {
        val titleView = TextView(context).apply {
            text = title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setPadding(0, 24, 0, 8)
        }
        renderTarget.addView(titleView)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 8)
        }
        container.block()
        renderTarget.addView(container)
    }

    fun addRow(container: LinearLayout, label: String, value: String) {
        val row = TextView(container.context).apply {
            val text = "$label: $value"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 4, 0, 4)
            this.text = SpannableString(text).apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, label.length + 1, 0)
            }
        }
        container.addView(row)
    }

    fun addText(container: LinearLayout, text: String) {
        val tv = TextView(container.context).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 4, 0, 4)
        }
        container.addView(tv)
    }

    fun addSourceSection(context: Context, renderTarget: LinearLayout, title: String, source: SourceInfo) {
        addSection(context, renderTarget, title) {
            val page = source.page?.let { ", p. $it" } ?: ""
            addText(this, "${source.book.get()} (${source.abbreviation})$page")
        }
    }

    fun showNotFound(context: Context, container: LinearLayout, message: String) {
        container.addView(TextView(context).apply {
            text = message
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            gravity = Gravity.CENTER
            setPadding(32, 64, 32, 32)
        })
    }
}
