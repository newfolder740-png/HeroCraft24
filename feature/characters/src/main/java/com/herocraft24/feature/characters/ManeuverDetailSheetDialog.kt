package com.herocraft24.feature.characters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.herocraft24.core.model.Maneuvers
import com.herocraft24.core.ui.render.CardBuilder
import com.herocraft24.feature.characters.databinding.DialogSpellDetailSheetBinding

class ManeuverDetailSheetDialog : BottomSheetDialogFragment() {

    private var _binding: DialogSpellDetailSheetBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by activityViewModels()

    private var maneuverId: String = ""

    companion object {
        private const val ARG_MANEUVER_ID = "maneuverId"

        fun newInstance(maneuverId: String): ManeuverDetailSheetDialog {
            return ManeuverDetailSheetDialog().apply {
                arguments = Bundle().apply { putString(ARG_MANEUVER_ID, maneuverId) }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogSpellDetailSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        maneuverId = arguments?.getString(ARG_MANEUVER_ID) ?: ""

        val maneuver = vm.repository.getManeuvers(maneuverId)
        if (maneuver == null) {
            binding.toolbar.title = "Приём не найден"
            CardBuilder.showNotFound(requireContext(), binding.detailContent, "Приём не найден")
            return
        }
        render(maneuver)
    }

    private fun render(maneuver: Maneuvers) {
        binding.toolbar.title = maneuver.name.get()
        binding.toolbar.setNavigationOnClickListener { dismiss() }

        val ctx = requireContext()
        val target = binding.detailContent
        target.removeAllViews()

        maneuver.cost?.takeIf { it.isNotBlank() }?.let { cost ->
            CardBuilder.addSection(ctx, target, "Краткая информация") {
                CardBuilder.addRow(this, "Стоимость", cost)
            }
        }

        CardBuilder.addSection(ctx, target, "Описание") {
            addView(buildTextView(maneuver.description.get()))
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
