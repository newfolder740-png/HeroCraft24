package com.herocraft24.feature.settings

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.herocraft24.feature.settings.databinding.FragmentSettingsMainBinding
import kotlinx.coroutines.launch

class SettingsMainFragment : Fragment() {

    private var _binding: FragmentSettingsMainBinding? = null
    private val binding get() = _binding!!
    private val vm: SettingsViewModel by viewModels()
    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val file = java.io.File(it.path ?: return@let)
            lifecycleScope.launch {
                val ok = vm.restoreBackup(file)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(if (ok) "Restored" else "Error")
                    .setMessage(if (ok) "Data restored successfully" else "Failed to restore backup")
                    .setPositiveButton("OK", null).show()
            }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentSettingsMainBinding.inflate(i, c, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val content = binding.content

        section(content, "Appearance") {
            item("Theme", vm.getTheme().replaceFirstChar { it.uppercase() }) { showThemeDialog() }
            item("Language", vm.getLanguage().uppercase()) { showLanguageDialog() }
        }

        section(content, "Data") {
            item("Installed Packs", "${vm.getPackIds().size} packs") { showPacksDialog() }
            item("Create Backup", "Save all user data") {
                lifecycleScope.launch {
                    val file = vm.createBackup()
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(if (file != null) "Backup Created" else "Error")
                        .setMessage(if (file != null) "Saved to ${file.name}" else "Failed to create backup")
                        .setPositiveButton("OK", null).show()
                }
            }
            item("Restore Backup", "Restore from file") { filePicker.launch("application/json") }
        }

        section(content, "Info") {
            item("About", "Version ${vm.getAppVersion()}") {
                findNavController().navigate(R.id.about)
            }
        }
    }

    private fun section(container: LinearLayout, title: String, block: LinearLayout.() -> Unit) {
        container.addView(TextView(requireContext()).apply {
            text = title; setTypeface(null, Typeface.BOLD); setPadding(0, 24, 0, 8)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        })
        val ll = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        ll.block(); container.addView(ll)
    }

    private fun LinearLayout.item(label: String, value: String, onClick: () -> Unit) {
        val row = TextView(requireContext()).apply {
            text = "$label: $value"
            setPadding(0, 16, 0, 16)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setOnClickListener { onClick() }
        }
        addView(row)
    }

    private fun showThemeDialog() {
        val themes = arrayOf("System", "Light", "Dark")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Theme")
            .setItems(themes) { _, w ->
                val theme = themes[w].lowercase()
                vm.setTheme(theme)
                // Apply theme
                when (theme) {
                    "light" -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                        androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
                    "dark" -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                        androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
                    else -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                        androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
            }.show()
    }

    private fun showLanguageDialog() {
        val langs = arrayOf("English", "Русский")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Language")
            .setItems(langs) { _, w ->
                vm.setLanguage(if (w == 0) "en" else "ru")
                requireActivity().packageManager.getLaunchIntentForPackage(requireActivity().packageName)?.let { intent ->
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
            }.show()
    }

    private fun showPacksDialog() {
        val packs = vm.getPackIds()
        val items = packs.map { "$it (${vm.getPackObjectCount(it)} objects)" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Installed Packs")
            .setItems(items, null)
            .setPositiveButton("OK", null).show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}