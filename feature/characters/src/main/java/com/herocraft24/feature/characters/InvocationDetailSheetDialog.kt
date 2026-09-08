package com.herocraft24.feature.characters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.herocraft24.core.model.Invocation
import com.herocraft24.core.ui.render.CardBuilder
import com.herocraft24.feature.characters.databinding.DialogSpellDetailSheetBinding

class InvocationDetailSheetDialog : BottomSheetDialogFragment() {

    private var _binding: DialogSpellDetailSheetBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by activityViewModels()

    private var invocationId: String = ""

    companion object {
        private const val ARG_INVOCATION_ID = "invocationId"

        fun newInstance(invocationId: String): InvocationDetailSheetDialog {
            return InvocationDetailSheetDialog().apply {
                arguments = Bundle().apply { putString(ARG_INVOCATION_ID, invocationId) }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogSpellDetailSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        invocationId = arguments?.getString(ARG_INVOCATION_ID) ?: ""

        val invocation = vm.repository.getInvocation(invocationId)
        if (invocation == null) {
            binding.toolbar.title = "Воззвание не найдено"
            CardBuilder.showNotFound(requireContext(), binding.detailContent, "Воззвание не найдено")
            return
        }
        render(invocation)
    }

    private fun render(invocation: Invocation) {
        binding.toolbar.title = invocation.name.get()
        binding.toolbar.setNavigationOnClickListener { dismiss() }

        val ctx = requireContext()
        val target = binding.detailContent
        target.removeAllViews()

        CardBuilder.addSection(ctx, target, "Краткая информация") {
            invocation.level?.let { CardBuilder.addRow(this, "Уровень воззвания", it.toString()) }
            invocation.requirements?.warlock_level?.let {
                CardBuilder.addRow(this, "Требование", "Колдун $it-го уровня")
            }
        }

        CardBuilder.addSection(ctx, target, "Описание") {
            addView(buildTextView(invocation.description.get()))
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
