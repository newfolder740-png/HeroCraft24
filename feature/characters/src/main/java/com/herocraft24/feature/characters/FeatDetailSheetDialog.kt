package com.herocraft24.feature.characters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.herocraft24.core.model.Feat
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.render.CardBuilder
import com.herocraft24.feature.characters.databinding.DialogSpellDetailSheetBinding

class FeatDetailSheetDialog : BottomSheetDialogFragment() {

    private var _binding: DialogSpellDetailSheetBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by activityViewModels()

    private var featId: String = ""

    companion object {
        private const val ARG_FEAT_ID = "featId"

        fun newInstance(featId: String): FeatDetailSheetDialog {
            return FeatDetailSheetDialog().apply {
                arguments = Bundle().apply { putString(ARG_FEAT_ID, featId) }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogSpellDetailSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        featId = arguments?.getString(ARG_FEAT_ID) ?: ""

        val feat = vm.repository.getFeat(featId)
        if (feat == null) {
            binding.toolbar.title = "Черта не найдена"
            CardBuilder.showNotFound(requireContext(), binding.detailContent, "Черта не найдена")
            return
        }
        render(feat)
    }

    private fun render(feat: Feat) {
        binding.toolbar.title = feat.name.get()
        binding.toolbar.setNavigationOnClickListener { dismiss() }

        val ctx = requireContext()
        val target = binding.detailContent
        target.removeAllViews()

        CardBuilder.addSection(ctx, target, "Краткая информация") {
            CardBuilder.addRow(this, "Категория", UiLocalizer.category(feat.category))
            val prerequisite = feat.prerequisite?.get()
            if (!prerequisite.isNullOrBlank()) CardBuilder.addRow(this, "Требования", prerequisite)
            if (feat.repeatable) CardBuilder.addRow(this, "Повторяемая", "Да")
        }

        CardBuilder.addSection(ctx, target, "Описание") {
            addView(buildTextView(feat.description.get()))
        }

        if (feat.benefits.isNotEmpty()) {
            CardBuilder.addSection(ctx, target, "Преимущества") {
                for (benefit in feat.benefits) {
                    benefit.name?.get()?.takeIf { it.isNotBlank() }?.let { CardBuilder.addText(this, it) }
                    addView(buildTextView(benefit.description.get()))
                }
            }
        }

        CardBuilder.addSourceSection(ctx, target, "Источник", feat.source)
    }

    private fun buildTextView(text: String): TextView {
        return TextView(requireContext()).apply {
            setText(text)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 4, 0, 4)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
