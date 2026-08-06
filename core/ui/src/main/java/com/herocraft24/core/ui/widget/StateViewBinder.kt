package com.herocraft24.core.ui.widget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.google.android.material.button.MaterialButton
import com.herocraft24.core.ui.R

/**
 * Helper for binding common state views (loading, empty, error) into any container.
 *
 * Usage:
 * ```
 * val states = StateViewBinder(container)
 * states.showLoading()
 * states.showEmpty(R.drawable.ic_empty, "No items", "Add some items to get started")
 * states.showError("Failed to load", "Check your connection") { retry() }
 * states.hideAll()
 * ```
 */
class StateViewBinder(private val container: ViewGroup) {

    private val inflater = LayoutInflater.from(container.context)

    private var loadingView: View? = null
    private var emptyView: View? = null
    private var errorView: View? = null

    fun showLoading(message: String = container.context.getString(R.string.loading)) {
        hideAll()
        if (loadingView == null) {
            val index = container.childCount
            inflater.inflate(R.layout.view_loading_state, container, true)
            loadingView = container.getChildAt(index)
        }
        loadingView?.findViewById<TextView>(R.id.message)?.text = message
        loadingView?.visibility = View.VISIBLE
    }

    fun showEmpty(
        @DrawableRes iconRes: Int = R.drawable.ic_empty,
        title: String = container.context.getString(R.string.empty_default_title),
        subtitle: String? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        hideAll()
        if (emptyView == null) {
            val index = container.childCount
            inflater.inflate(R.layout.view_empty_state, container, true)
            emptyView = container.getChildAt(index)
        }
        emptyView?.apply {
            findViewById<ImageView>(R.id.icon)?.setImageResource(iconRes)
            findViewById<TextView>(R.id.title)?.text = title
            findViewById<TextView>(R.id.subtitle)?.apply {
                if (subtitle != null) {
                    text = subtitle
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }
            findViewById<MaterialButton>(R.id.action_button)?.apply {
                if (actionLabel != null) {
                    text = actionLabel
                    visibility = View.VISIBLE
                    setOnClickListener { onAction?.invoke() }
                } else {
                    visibility = View.GONE
                }
            }
            visibility = View.VISIBLE
        }
    }

    fun showError(
        title: String = container.context.getString(R.string.error_default_title),
        subtitle: String? = container.context.getString(R.string.error_default_subtitle),
        onRetry: (() -> Unit)? = null
    ) {
        hideAll()
        if (errorView == null) {
            val index = container.childCount
            inflater.inflate(R.layout.view_error_state, container, true)
            errorView = container.getChildAt(index)
        }
        errorView?.apply {
            findViewById<TextView>(R.id.title)?.text = title
            findViewById<TextView>(R.id.subtitle)?.apply {
                if (subtitle != null) {
                    text = subtitle
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }
            findViewById<MaterialButton>(R.id.retry_button)?.apply {
                if (onRetry != null) {
                    visibility = View.VISIBLE
                    setOnClickListener { onRetry.invoke() }
                } else {
                    visibility = View.GONE
                }
            }
            visibility = View.VISIBLE
        }
    }

    fun hideAll() {
        loadingView?.visibility = View.GONE
        emptyView?.visibility = View.GONE
        errorView?.visibility = View.GONE
    }

    fun release() {
        loadingView?.let { container.removeView(it) }
        emptyView?.let { container.removeView(it) }
        errorView?.let { container.removeView(it) }
        loadingView = null
        emptyView = null
        errorView = null
    }
}