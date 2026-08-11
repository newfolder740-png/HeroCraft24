package com.herocraft24.feature.characters

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.herocraft24.core.model.Cost
import com.herocraft24.core.model.Item
import com.herocraft24.core.model.Weight
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.util.FormatUtils
import com.herocraft24.core.ui.util.ItemLinkifier
import com.herocraft24.feature.characters.databinding.DialogBackpackItemDetailBinding

class BackpackItemDetailDialogFragment : DialogFragment() {

    private var _binding: DialogBackpackItemDetailBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by activityViewModels()

    private var itemId: String = ""

    private val magicItemCategories = setOf("wand", "rod", "potion", "ring", "staff", "scroll", "wondrous_item")

    companion object {
        private const val ARG_ITEM_ID = "itemId"

        fun newInstance(itemId: String): BackpackItemDetailDialogFragment {
            return BackpackItemDetailDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_ITEM_ID, itemId) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        itemId = arguments?.getString(ARG_ITEM_ID) ?: ""
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogBackpackItemDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { dismiss() }

        val item = vm.getItem(itemId)
        if (item == null) {
            binding.toolbar.title = "Не найдено"
            binding.detailContent.addView(TextView(requireContext()).apply {
                text = "Предмет не найден"
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
        val view = i.view
        val cost = i.cost
        val weight = i.weight

        addSection("Быстрая информация") {
            addRow("Категория", localizeCategory(i.category))
            if (subcat.isNotEmpty() && i.category !in magicItemCategories) addRow("Подкатегории", subcat.joinToString(", ") { localizeSubcategory(it) })
            if (view.isNotEmpty()) addLinkedRow("Вид", view.joinToString(", "), i)
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

        addSection("Описание") { addLinkedText(i.description.get(), i) }

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

    private fun LinearLayout.addLinkedRow(label: String, value: String, item: Item) {
        addView(TextView(requireContext()).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 4, 0, 4)
            movementMethod = LinkMovementMethod.getInstance()
            val builder = SpannableStringBuilder()
            builder.append(SpannableString("$label: ").apply { setSpan(StyleSpan(Typeface.BOLD), 0, label.length + 1, 0) })
            builder.append(linkify(value, item))
            text = builder
        })
    }

    private fun LinearLayout.addText(text: String) {
        addView(TextView(requireContext()).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 4, 0, 4)
        })
    }

    private fun LinearLayout.addLinkedText(text: String, item: Item) {
        addView(TextView(requireContext()).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 4, 0, 4)
            movementMethod = LinkMovementMethod.getInstance()
            setText(linkify(text, item))
        })
    }

    private fun linkify(text: String, currentItem: Item): SpannableStringBuilder {
        val marker = ItemLinkifier.stripMarkers(text)
        val clean = marker.text
        val spannable = SpannableStringBuilder(clean)
        val combinedMap = HashMap<String, String>()
        combinedMap.putAll(buildItemNameMap().filter { it.value != currentItem.id })
        combinedMap.putAll(buildSpellNameMap())
        combinedMap.putAll(buildConditionNameMap())
        val matches = ItemLinkifier.findRanges(clean, combinedMap, marker.excludedRanges)
        for ((start, end, fullId) in matches) {
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    openItemDetail(fullId)
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (link in marker.explicitLinks) {
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    openItemDetail(link.fullId)
                }
            }, link.range.first, link.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }

    private fun openItemDetail(fullId: String) {
        val dialog = newInstance(fullId)
        dialog.show(parentFragmentManager, "BackpackItemDetail")
    }

    private fun buildItemNameMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (fullId in vm.getItemIds()) {
            val entry = vm.repository.getManifestEntry(fullId) ?: continue
            entry.name.en?.takeIf { it.isNotBlank() }?.let { map[it] = fullId }
            entry.name.ru?.takeIf { it.isNotBlank() }?.let { map[it] = fullId }
        }
        return map
    }

    private fun buildSpellNameMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (fullId in vm.getSpellIds()) {
            val entry = vm.repository.getManifestEntry(fullId) ?: continue
            entry.name.en?.takeIf { it.isNotBlank() }?.let { map[it] = fullId }
            entry.name.ru?.takeIf { it.isNotBlank() }?.let { map[it] = fullId }
        }
        return map
    }

    private fun buildConditionNameMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (fullId in vm.repository.getConditionIds()) {
            if (fullId.endsWith(":condition")) continue
            val entry = vm.repository.getManifestEntry(fullId) ?: continue
            entry.name.en?.takeIf { it.isNotBlank() }?.let { map[it] = fullId }
            entry.name.ru?.takeIf { it.isNotBlank() }?.let { map[it] = fullId }
        }
        return map
    }

    private fun formatCost(cost: Cost): String =
        "${FormatUtils.formatAmount(cost.amount)} ${UiLocalizer.costUnit(cost.unit)}"

    private fun formatWeight(weight: Weight): String =
        "${FormatUtils.formatAmount(weight.amount)} ${UiLocalizer.weightUnit(weight.unit)}"

    private fun localizeProperty(p: String): String = UiLocalizer.property(p)
    private fun localizeDamageType(type: String): String = UiLocalizer.damageType(type)
    private fun localizeCategory(c: String): String = UiLocalizer.category(c)
    private fun localizeSubcategory(s: String): String = UiLocalizer.subcategory(s)
    private fun localizeRarity(rarity: String): String = UiLocalizer.rarity(rarity)

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
