package com.herocraft24.feature.spells

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import com.herocraft24.feature.spells.databinding.FragmentSpellDetailBinding

class ConditionReferenceFragment : Fragment() {

    private var _binding: FragmentSpellDetailBinding? = null
    private val binding get() = _binding!!
    private val vm: SpellsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpellDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val conditionId = arguments?.getString("conditionId") ?: ""
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val condition = vm.getCondition(conditionId)
        if (condition == null) {
            binding.toolbar.title = "Статья не найдена"
            binding.detailContent.addView(TextView(requireContext()).apply {
                text = "Состояние не найдено."
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setPadding(32, 64, 32, 32)
            })
            return
        }
        render(condition, conditionId)
    }

    private fun render(c: com.herocraft24.core.model.Condition, currentConditionId: String) {
        binding.toolbar.title = c.name.get()

        addSection("Описание") { addView(buildLinkedTextView(c.description.get(), currentConditionId)) }

        if (c.effects.isNotEmpty()) {
            addSection("Эффекты") {
                for (e in c.effects) addView(buildLinkedTextView("• ${e.get()}", currentConditionId))
            }
        }
    }

    private fun buildLinkedTextView(text: String, currentId: String): TextView {
        val marker = com.herocraft24.core.ui.util.ItemLinkifier.stripMarkers(text)
        val spannable = SpannableStringBuilder(marker.text)
        val links = vm.conditionNameToIdMap
            .entries
            .filter { it.key.isNotBlank() && it.value != currentId }
            .sortedByDescending { it.key.length }
        for ((name, fullId) in links) {
            var idx = 0
            while (idx <= spannable.length - name.length) {
                val start = spannable.toString().indexOf(name, idx, ignoreCase = true)
                if (start < 0) break
                val end = start + name.length
                if (marker.excludedRanges.none { it.first < end && start < it.last }) {
                    spannable.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            val b = Bundle().apply { putString("conditionId", fullId) }
                            Navigation.findNavController(widget).navigate(R.id.spellConditionDetail, b)
                        }
                        override fun updateDrawState(ds: TextPaint) {
                            super.updateDrawState(ds)
                            ds.isUnderlineText = true
                            ds.color = ds.linkColor
                        }
                    }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                idx = end
            }
        }
        return TextView(requireContext()).apply {
            setText(spannable)
            movementMethod = LinkMovementMethod.getInstance()
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 4, 0, 4)
        }
    }

    private fun addSection(title: String, block: LinearLayout.() -> Unit) {
        val tv = TextView(requireContext()).apply {
            text = title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setPadding(0, 24, 0, 8)
        }
        binding.detailContent.addView(tv)
        val c = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 8)
        }
        c.block()
        binding.detailContent.addView(c)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}