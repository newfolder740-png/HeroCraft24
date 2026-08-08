package com.herocraft24.core.ui.render

import android.content.Context
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import com.herocraft24.core.ui.util.dp

/**
 * Reusable expandable card used across detail screens.
 *
 * Builds a MaterialCardView with a title, optional subtitle, and a collapsible body.
 * Supports optional open/close state persistence via [openId] + [openIdsSet].
 */
object ExpandableCard {

    /**
     * Creates an expandable card.
     *
     * @param context Android context
     * @param title Card title text
     * @param subtitle Optional subtitle shown under the title
     * @param openId Optional id used to persist expanded state in [openIdsSet]
     * @param openIdsSet Optional set of ids for expanded items
     * @param onExpand Optional callback invoked once the first time the body is expanded.
     *                 Useful for lazy-initializing expensive content (e.g. linkified text).
     * @param bodyBuilder Block that populates the card body
     * @return The created card and its body container
     */
    fun createExpandableCard(
        context: Context,
        title: String,
        subtitle: String? = null,
        openId: String? = null,
        openIdsSet: MutableSet<String>? = null,
        onExpand: (() -> Unit)? = null,
        bodyBuilder: LinearLayout.(body: LinearLayout) -> Unit
    ): Pair<MaterialCardView, LinearLayout> {
        val card = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 12.dp(context))
            }
            radius = 12.dp(context).toFloat()
            cardElevation = 2.dp(context).toFloat()
        }

        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(context), 16.dp(context), 16.dp(context), 16.dp(context))
        }
        card.addView(inner)

        inner.addView(TextView(context).apply {
            this.text = title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTypeface(null, Typeface.BOLD)
        })

        subtitle?.takeIf { it.isNotBlank() }?.let {
            inner.addView(TextView(context).apply {
                text = it
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setPadding(0, 4.dp(context), 0, 0)
            })
        }

        val initialOpen = openId != null && openIdsSet != null && openId in openIdsSet
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isVisible = initialOpen
        }
        inner.addView(body)

        var expandedOnce = initialOpen
        if (initialOpen) {
            onExpand?.invoke()
        }

        card.setOnClickListener {
            val expanding = !body.isVisible
            body.isVisible = expanding
            if (expanding) {
                if (!expandedOnce) {
                    onExpand?.invoke()
                    expandedOnce = true
                }
                if (openId != null && openIdsSet != null) {
                    openIdsSet.add(openId)
                }
            } else {
                if (openId != null && openIdsSet != null) {
                    openIdsSet.remove(openId)
                }
            }
        }

        body.bodyBuilder(body)
        return card to body
    }
}
