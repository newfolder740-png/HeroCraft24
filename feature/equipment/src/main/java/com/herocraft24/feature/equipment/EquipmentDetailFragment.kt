package com.herocraft24.feature.equipment

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import com.herocraft24.core.model.Cost
import com.herocraft24.core.model.Item
import com.herocraft24.core.model.Weight
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.util.ItemLinkifier
import com.herocraft24.feature.equipment.databinding.FragmentEquipmentDetailBinding

class EquipmentDetailFragment : Fragment() {

    private var _binding: FragmentEquipmentDetailBinding? = null
    private val binding get() = _binding!!
    private val vm: EquipmentViewModel by viewModels()
    private var itemId: String = ""

    private val magicItemCategories = setOf("wand", "rod", "potion", "ring", "staff", "scroll", "wondrous_item")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEquipmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        itemId = arguments?.getString("itemId") ?: ""
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val item = vm.getItem(itemId)
        if (item == null) {
            binding.toolbar.title = "Not Found"
            binding.detailContent.addView(TextView(requireContext()).apply {
                text = "Item not found."
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setPadding(32, 64, 32, 32)
            })
            return
        }
        render(item)
    }

    private fun render(i: Item) {
        binding.toolbar.title = i.name.get()

        val ac = i.armor_class
        val dmg = i.damage
        val subcat = i.subcategory
        val cost = i.cost
        val weight = i.weight

        addSection("Быстрая информация") {
            addRow("Категория", localizeCategory(i.category))
            if (subcat.isNotEmpty() && i.category !in magicItemCategories) addRow("Подкатегории", subcat.joinToString(", ") { localizeSubcategory(it) })
            addRow("Редкость", localizeRarity(i.rarity))
            if (i.attunement) addRow("Настройка", "Требуется")
            if (cost != null) addRow("Стоимость", formatCost(cost))
            if (weight != null) addRow("Вес", formatWeight(weight))
            if (i.properties.isNotEmpty()) addRow("Свойства", i.properties.joinToString(", ") { localizeProperty(it) })
            if (dmg != null) {
                addRow("Урон", "${dmg.damage_dice} ${localizeDamageType(dmg.damage_type)}")
                val vd = dmg.versatile_dice
                if (vd != null) addRow("Универсальное", vd)
            }
            if (ac != null) {
                addRow("КЗ", "${ac.base}${if (ac.dex_bonus) " + Ловкость" else ""}")
                val maxDex = ac.max_dex
                val minStr = ac.min_strength
                if (maxDex != null) addRow("Макс Ловкость", "+$maxDex")
                if (minStr != null) addRow("Мин Сила", "$minStr")
                if (ac.stealth_disadvantage) addRow("Скрытность", "Помеха")
            }
        }

        addSection("Описание") { addLinkedText(i.description.get(), itemId) }

        if (i.effects.isNotEmpty()) {
            addSection("Эффекты") {
                for (e in i.effects) addText("• ${e.get()}")
            }
        }

        addSection("Источник") {
            addText("${i.source.book.get()} (${i.source.abbreviation})${if (i.source.page != null) ", с. ${i.source.page}" else ""}")
        }
    }

    private fun addSection(title: String, block: LinearLayout.() -> Unit) {
        val tv = TextView(requireContext()).apply {
            text = title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setPadding(0, 24, 0, 8)
        }
        binding.detailContent.addView(tv)
        val c = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, 8) }
        c.block()
        binding.detailContent.addView(c)
    }

    private fun LinearLayout.addRow(label: String, value: String) {
        addView(TextView(requireContext()).apply {
            val t = "$label: $value"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 4, 0, 4)
            text = SpannableString(t).apply { setSpan(StyleSpan(Typeface.BOLD), 0, label.length + 1, 0) }
        })
    }

    private fun LinearLayout.addText(text: String) {
        addView(TextView(requireContext()).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 4, 0, 4)
        })
    }

    private fun LinearLayout.addLinkedText(text: String, currentFullId: String) {
        addView(TextView(requireContext()).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 4, 0, 4)
            movementMethod = LinkMovementMethod.getInstance()
            setText(linkify(text, currentFullId))
        })
    }

    private fun linkify(text: String, currentFullId: String): SpannableStringBuilder {
        val marker = ItemLinkifier.stripMarkers(text)
        val clean = marker.text
        val spannable = SpannableStringBuilder(clean)
        val combinedMap = HashMap<String, String>()
        combinedMap.putAll(vm.itemNameToIdMap.filter { it.value != currentFullId })
        combinedMap.putAll(vm.spellNameToIdMap)
        combinedMap.putAll(vm.conditionNameToIdMap)
        val matches = ItemLinkifier.findRanges(clean, combinedMap, marker.excludedRanges)
        for ((start, end, fullId) in matches) {
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val entryType = vm.getEntryTypeSafely(fullId)
                    when (entryType) {
                        "spell" -> {
                            val b = Bundle().apply { putString("spellId", fullId) }
                            Navigation.findNavController(widget).navigate(R.id.equipmentSpellDetail, b)
                        }
                        "condition" -> {
                            val b = Bundle().apply { putString("conditionId", fullId) }
                            Navigation.findNavController(widget).navigate(R.id.equipmentConditionDetail, b)
                        }
                        else -> {
                            val b = Bundle().apply { putString("itemId", fullId) }
                            Navigation.findNavController(widget).navigate(R.id.equipmentDetail, b)
                        }
                    }
                }
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = true
                    ds.color = ds.linkColor
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (link in marker.explicitLinks) {
            val range = link.range
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val entryType = vm.getEntryTypeSafely(link.fullId)
                    when (entryType) {
                        "spell" -> {
                            val b = Bundle().apply { putString("spellId", link.fullId) }
                            Navigation.findNavController(widget).navigate(R.id.equipmentSpellDetail, b)
                        }
                        "condition" -> {
                            val b = Bundle().apply { putString("conditionId", link.fullId) }
                            Navigation.findNavController(widget).navigate(R.id.equipmentConditionDetail, b)
                        }
                        else -> {
                            val b = Bundle().apply { putString("itemId", link.fullId) }
                            Navigation.findNavController(widget).navigate(R.id.equipmentDetail, b)
                        }
                    }
                }
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = true
                    ds.color = ds.linkColor
                }
            }, range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }

    private fun formatAmount(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()
    }

    private fun formatCost(cost: Cost): String {
        val unit = when (cost.unit.lowercase()) {
            "gp" -> "ЗМ"
            "sp" -> "СМ"
            "cp" -> "ММ"
            "pp" -> "ПМ"
            else -> cost.unit.uppercase()
        }
        return "${formatAmount(cost.amount)} $unit"
    }

    private fun formatWeight(weight: Weight): String =
        "${formatAmount(weight.amount)} ${UiLocalizer.weightUnit(weight.unit)}"

    private fun localizeProperty(p: String): String = UiLocalizer.property(p)

    private fun localizeDamageType(type: String): String = UiLocalizer.damageType(type)

    private fun localizeCategory(c: String): String = UiLocalizer.category(c)

    private fun localizeSubcategory(s: String): String = UiLocalizer.subcategory(s)

    private fun localizeRarity(rarity: String): String = UiLocalizer.rarity(rarity)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}