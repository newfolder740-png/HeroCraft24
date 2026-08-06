package com.herocraft24.core.ui.widget

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.google.android.material.textfield.TextInputEditText
import com.herocraft24.core.ui.R

/**
 * Reusable search bar with built-in debounce.
 *
 * Usage in XML:
 * ```
 * <com.herocraft24.core.ui.widget.SearchBarView
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content" />
 * ```
 *
 * Usage in code:
 * ```
 * searchBar.setOnQueryListener { query -> performSearch(query) }
 * ```
 */
class SearchBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val input: TextInputEditText

    private var onQueryListener: ((String) -> Unit)? = null
    private var debounceRunnable: Runnable? = null
    private var debounceMs: Long = 300L

    init {
        LayoutInflater.from(context).inflate(R.layout.view_search_bar, this, true)
        input = findViewById(R.id.search_input)

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                debounceRunnable?.let { removeCallbacks(it) }
                debounceRunnable = Runnable {
                    onQueryListener?.invoke(s?.toString().orEmpty())
                }
                debounceRunnable?.let { postDelayed(it, debounceMs) }
            }
        })
    }

    fun setOnQueryListener(listener: (String) -> Unit) {
        onQueryListener = listener
    }

    fun setDebounce(ms: Long) {
        debounceMs = ms
    }

    fun getQuery(): String = input.text?.toString().orEmpty()

    fun setQuery(query: String) {
        input.setText(query)
    }

    fun clear() {
        input.text?.clear()
    }
}