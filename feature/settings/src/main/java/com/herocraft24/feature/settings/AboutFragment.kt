package com.herocraft24.feature.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.herocraft24.feature.settings.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!
    private val vm: SettingsViewModel by viewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentAboutBinding.inflate(i, c, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val content = binding.content
        addText(content, "HeroCraft24", 24f)
        addText(content, "D&D 2024 Companion App", 14f)
        addText(content, "", 8f)
        addText(content, "Version: ${vm.getAppVersion()}", 16f)
        addText(content, "", 8f)
        addText(content, "Tech Stack:", 16f)
        addText(content, "Kotlin • Android SDK • XML Layouts", 14f)
        addText(content, "Material Design 3 • Navigation • MVVM", 14f)
        addText(content, "Room • Kotlin Serialization • Coroutines", 14f)
        addText(content, "", 16f)
        addText(content, "Data: D&D 2024 rules (PHB, DMG, MM)", 12f)
        addText(content, "Licensed under Wizards of the Coast", 12f)
        addText(content, "Fan Content Policy", 12f)
    }

    private fun addText(container: ViewGroup, text: String, size: Float) {
        if (text.isEmpty()) {
            container.addView(View(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(1, 8)
            })
            return
        }
        container.addView(TextView(requireContext()).apply {
            this.text = text; textSize = size; gravity = android.view.Gravity.CENTER
            setPadding(0, 4, 0, 4)
        })
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}