package com.herocraft24.feature.characters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.herocraft24.core.model.Metamagic
import com.herocraft24.core.ui.render.CardBuilder
import com.herocraft24.feature.characters.databinding.DialogSpellDetailSheetBinding

class MetamagicDetailSheetDialog : BottomSheetDialogFragment() {

    private var _binding: DialogSpellDetailSheetBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by activityViewModels()

    private var metamagicId: String = ""

    companion object {
        private const val ARG_METAMAGIC_ID = "metamagicId"

        fun newInstance(metamagicId: String): MetamagicDetailSheetDialog {
            return MetamagicDetailSheetDialog().apply {
                arguments = Bundle().apply { putString(ARG_METAMAGIC_ID, metamagicId) }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogSpellDetailSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        metamagicId = arguments?.getString(ARG_METAMAGIC_ID) ?: ""

        val metamagic = vm.repository.getMetamagic(metamagicId)
        if (metamagic == null) {
            binding.toolbar.title = "Метамагия не найдена"
            CardBuilder.showNotFound(requireContext(), binding.detailContent, "Метамагия не найдена")
            return
        }
        render(metamagic)
    }

    private fun render(metamagic: Metamagic) {
        binding.toolbar.title = metamagic.name.get()
        binding.toolbar.setNavigationOnClickListener { dismiss() }

        val ctx = requireContext()
        val target = binding.detailContent
        target.removeAllViews()

        CardBuilder.addSection(ctx, target, "Краткая информация") {
            CardBuilder.addRow(this, "Стоимость", metamagic.cost)
        }

        CardBuilder.addSection(ctx, target, "Описание") {
            addView(buildTextView(metamagic.description.get()))
        }
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
