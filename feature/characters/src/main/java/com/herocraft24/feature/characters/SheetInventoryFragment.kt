package com.herocraft24.feature.characters

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.herocraft24.core.model.Item
import com.herocraft24.core.ui.util.dp
import com.herocraft24.feature.characters.databinding.CardFeatureCreateBinding
import kotlinx.coroutines.launch

class SheetInventoryFragment : Fragment() {

    private val vm: CharactersViewModel by activityViewModels()
    private var charId: String? = null
    private var equipmentExpandedPosition: Int = -1
    private var proficiencyExpandedPosition: Int = -1
    private var magicItemExpandedPosition: Int = -1

    data class EquipmentSlot(
        val title: String,
        val items: List<Pair<String, Item>>,
        val selectedId: String?,
        val onSelected: (String?) -> Unit
    )

    data class MagicItemSlot(
        val index: Int,
        val title: String,
        val items: Map<String, Item>,
        val selectedId: String?,
        val onSelected: (String?) -> Unit
    )

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        LayoutInflater.from(requireContext()).inflate(R.layout.fragment_sheet_inventory, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        charId = arguments?.getString("characterId") ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            vm.characters.collect { list ->
                charId?.let { id ->
                    list.find { it.id == id }?.let { render(it) }
                }
            }
        }
    }

    private fun render(char: CharacterData) {
        val view = view ?: return
        val scrollView = view as? ScrollView
        val content = view.findViewById<LinearLayout>(R.id.content) ?: return
        val scrollY = scrollView?.scrollY ?: 0
        content.removeAllViews()
        val ctx = requireContext()
        val cls = vm.getClassInfo(char.classId)

        // Auto-populate starting equipment for characters created before this feature
        if (char.equipment.isEmpty()) {
            val startingEquipment = vm.calculateStartingEquipment(char)
            if (startingEquipment.isNotEmpty()) {
                save(char.copy(equipment = startingEquipment))
                return
            }
        }

        // Resolve backpack items with details
        android.util.Log.d("InventoryDebug", "char.equipment ids: ${char.equipment.map { it.itemId }}")
        val backpackItems = char.equipment.mapNotNull { inv ->
            val item = vm.repository.getItem(inv.itemId)
            if (item != null) inv.itemId to item else null
        }
        android.util.Log.d("InventoryDebug", "backpackItems count: ${backpackItems.size}, categories: ${backpackItems.map { it.second.category }}")

        // ── Equipment — expandable cards in 2x2 grid, expanding to full width ──
        sectionTitle(content, "Экипировано")

        val armorItems = backpackItems.filter { it.second.category == "armor" }
        val shieldItems = backpackItems.filter { it.second.category == "shield" }
        val weaponItems = backpackItems.filter { it.second.category == "weapon" }
        android.util.Log.d("InventoryDebug", "armor: ${armorItems.size}, shield: ${shieldItems.size}, weapon: ${weaponItems.size}")

        val equipmentSlots = listOf(
            EquipmentSlot("Доспех", armorItems, char.equippedArmor) { selectedId ->
                save(char.copy(equippedArmor = selectedId))
            },
            EquipmentSlot("Щит", shieldItems, char.equippedShield) { selectedId ->
                save(char.copy(equippedShield = selectedId))
            },
            EquipmentSlot("Оружие 1", weaponItems, char.equippedWeapon1) { selectedId ->
                save(char.copy(equippedWeapon1 = selectedId))
            },
            EquipmentSlot("Оружие 2", weaponItems, char.equippedWeapon2) { selectedId ->
                save(char.copy(equippedWeapon2 = selectedId))
            }
        )

        val equipmentRecycler = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutManager = LinearLayoutManager(ctx)
            adapter = EquipmentAdapter(equipmentSlots)
            isNestedScrollingEnabled = false
            itemAnimator = null
        }
        content.addView(equipmentRecycler)

        // ── Magic Items ──
        val magicItemsById = backpackItems.filter { it.second.magic }.toMap()
        sectionTitle(content, "Магические предметы")
        val selectedMagicIds = char.equippedMagicItems.take(24)
        val attunedCount = selectedMagicIds.count { magicItemsById[it]?.attunement == true }

        // Counter: 3 cells
        val counterRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        repeat(3) { i ->
            counterRow.addView(statBox(ctx, "", if (i < attunedCount) "●" else "○", filled = i < attunedCount))
        }
        content.addView(counterRow)

        // Magic item cards: one per selected item plus one empty slot, up to 24
        val magicCardCount = minOf(selectedMagicIds.size + 1, 24)
        val magicItemSlots = (0 until magicCardCount).map { index ->
            val selectedId = selectedMagicIds.getOrNull(index)
            MagicItemSlot(
                index = index,
                title = selectedId?.let { vm.resolveName(it) ?: it.substringAfterLast(":") } ?: "Магический предмет ${index + 1}",
                items = magicItemsById,
                selectedId = selectedId,
                onSelected = { selectedId ->
                    val current = selectedMagicIds.toMutableList()
                    when {
                        selectedId == null && index < current.size -> current.removeAt(index)
                        selectedId != null && index < current.size -> current[index] = selectedId
                        selectedId != null -> current.add(selectedId)
                    }
                    save(char.copy(equippedMagicItems = current.take(24)))
                }
            )
        }
        val magicRecycler = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutManager = LinearLayoutManager(ctx)
            adapter = MagicItemAdapter(magicItemSlots)
            isNestedScrollingEnabled = false
            itemAnimator = null
        }
        content.addView(magicRecycler)

        // ── Equipment Proficiencies ──
        sectionTitle(content, "Владения экипировкой")
        val profArmor = cls?.starting_proficiencies?.armor?.filterNotNull() ?: emptyList()
        val profWeapons = cls?.starting_proficiencies?.weapons?.filterNotNull() ?: emptyList()
        val profTools = cls?.starting_proficiencies?.tools?.filterNotNull() ?: emptyList()

        val profRecycler = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutManager = LinearLayoutManager(ctx)
            adapter = ProficiencyAdapter(listOf(
                "Доспехи" to profArmor,
                "Оружие" to profWeapons,
                "Инструменты" to profTools
            ))
            isNestedScrollingEnabled = false
            itemAnimator = null
        }
        content.addView(profRecycler)

        // ── Currency ──
        sectionTitle(content, "Монеты")
        val currencyRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        currencyRow.addView(statBox(ctx, "ММ", "${char.currency.cp}"))
        currencyRow.addView(statBox(ctx, "СМ", "${char.currency.sp}"))
        currencyRow.addView(statBox(ctx, "ЭМ", "0"))
        currencyRow.addView(statBox(ctx, "ЗМ", "${char.currency.gp}"))
        currencyRow.addView(statBox(ctx, "ПМ", "${char.currency.pp}"))
        content.addView(currencyRow)

        // ── Backpack ──
        val totalWeight = calculateTotalWeight(backpackItems)
        val unit = backpackItems.mapNotNull { it.second.weight?.unit }.firstOrNull() ?: "lb"
        val backpackHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val backpackTitle = TextView(ctx).apply {
            text = "Рюкзак (Общий вес: ${formatWeight(totalWeight, unit)})"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true
            isFocusable = true
            setOnClickListener { openBackpackPicker(char, addMode = false) }
        }
        backpackHeader.addView(backpackTitle)
        backpackHeader.addView(androidx.appcompat.widget.AppCompatButton(ctx).apply {
            text = "+"
            setPadding(16.dp(ctx), 0, 16.dp(ctx), 0)
            setOnClickListener { openBackpackPicker(char, addMode = true) }
        })
        content.addView(backpackHeader)
        val backpackItemsList = backpackItems.map { it.second }
        if (backpackItemsList.isEmpty()) {
            content.addView(TextView(ctx).apply {
                text = "Пусто"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setPadding(0, 4.dp(ctx), 0, 0)
            })
        } else {
            content.addView(TextView(ctx).apply {
                text = backpackItemsList.joinToString(", ") { it.name.get() }
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setPadding(0, 4.dp(ctx), 0, 0)
            })
        }
        scrollView?.post { scrollView.scrollTo(0, scrollY) }
    }

    private fun calculateTotalWeight(items: List<Pair<String, Item>>): Double {
        var total = 0.0
        items.forEach { (id, item) ->
            total += weightOf(id, item)
        }
        return total
    }

    private fun weightOf(id: String, item: Item): Double {
        if (item.category == "pack" && item.contents.isNotEmpty()) {
            return item.contents.sumOf { content ->
                val contentItem = vm.getItem(content.item_id)
                if (contentItem != null) content.quantity * weightOf(content.item_id, contentItem) else 0.0
            }
        }
        return item.weight?.amount ?: 0.0
    }

    private fun formatWeight(amount: Double, unit: String): String {
        val value = if (amount == amount.toInt().toDouble()) amount.toInt().toString() else amount.toString()
        return "$value $unit"
    }

    private fun save(char: CharacterData) {
        vm.saveCharacter(char)
        render(char)
    }

    private fun openBackpackPicker(char: CharacterData, addMode: Boolean) {
        val dialog = BackpackItemPickerDialogFragment.newInstance(char.id, addMode)
        dialog.show(childFragmentManager, "BackpackItemPicker")
    }

    private fun buildEquipmentExpandedContent(ctx: Context, slot: EquipmentSlot): View {
        val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val descView = TextView(ctx).apply {
            text = slot.items.find { it.first == slot.selectedId }?.second?.description?.get() ?: ""
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setPadding(0, 4.dp(ctx), 0, 0)
        }

        val displayNames: List<String> = listOf("—") + slot.items.map { vm.resolveName(it.first) ?: it.first.substringAfterLast(":") }
        val idByName: Map<String, String> = slot.items.map { (vm.resolveName(it.first) ?: it.first.substringAfterLast(":")) to it.first }.toMap()

        val dropdownLayout = TextInputLayout(ctx).apply {
            this.hint = "Выберите ${slot.title}"
            setPadding(0, 8.dp(ctx), 0, 4.dp(ctx))
        }
        val dropdown = MaterialAutoCompleteTextView(ctx).apply {
            inputType = android.text.InputType.TYPE_NULL
            threshold = 0
            isFocusableInTouchMode = false
            isClickable = true
            isFocusable = true
            setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, displayNames))
            val currentName = slot.items.find { it.first == slot.selectedId }?.let { vm.resolveName(it.first) ?: it.first.substringAfterLast(":") }
            setText(currentName ?: "—", false)
            setOnClickListener { showDropDown() }
            setOnItemClickListener { _, _, position, _ ->
                val selectedName = displayNames.getOrNull(position) ?: "—"
                val selectedItemId = idByName[selectedName]
                if (position == 0 || selectedItemId == null) {
                    descView.text = ""
                    slot.onSelected(null)
                } else {
                    val item = slot.items.find { it.first == selectedItemId }?.second
                    descView.text = item?.description?.get() ?: ""
                    slot.onSelected(selectedItemId)
                }
            }
        }
        dropdownLayout.addView(dropdown)
        content.addView(dropdownLayout)
        content.addView(descView)

        return content
    }

    inner class EquipmentAdapter(private val slots: List<EquipmentSlot>) : RecyclerView.Adapter<EquipmentAdapter.VH>() {

        inner class VH(val binding: CardFeatureCreateBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = CardFeatureCreateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun getItemCount() = slots.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val slot = slots[position]
            val isExpanded = position == equipmentExpandedPosition
            val selectedName = slot.items.find { it.first == slot.selectedId }?.let {
                vm.resolveName(it.first) ?: it.first.substringAfterLast(":")
            }
            holder.binding.featureTitle.text = selectedName ?: slot.title
            holder.binding.expandedContent.removeAllViews()
            holder.binding.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
            if (isExpanded) {
                val expandedContent = buildEquipmentExpandedContent(holder.binding.root.context, slot)
                holder.binding.expandedContent.addView(expandedContent)
            }
            holder.binding.headerRow.setOnClickListener {
                equipmentExpandedPosition = if (isExpanded) -1 else position
                notifyDataSetChanged()
            }
        }
    }

    inner class ProficiencyAdapter(private val proficiencies: List<Pair<String, List<String>>>) : RecyclerView.Adapter<ProficiencyAdapter.VH>() {

        inner class VH(val binding: CardFeatureCreateBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = CardFeatureCreateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun getItemCount() = proficiencies.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (title, items) = proficiencies[position]
            val isExpanded = position == proficiencyExpandedPosition
            holder.binding.featureTitle.text = title
            holder.binding.expandedContent.removeAllViews()
            holder.binding.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
            if (isExpanded) {
                val content = LinearLayout(holder.binding.root.context).apply { orientation = LinearLayout.VERTICAL }
                if (items.isEmpty()) {
                    content.addView(TextView(content.context).apply {
                        text = "Нет"
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                        setPadding(0, 4.dp(content.context), 0, 0)
                    })
                } else {
                    items.forEach { item ->
                        content.addView(TextView(content.context).apply {
                            text = "• $item"
                            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                            setPadding(0, 2.dp(content.context), 0, 2.dp(content.context))
                        })
                    }
                }
                holder.binding.expandedContent.addView(content)
            }
            holder.binding.headerRow.setOnClickListener {
                proficiencyExpandedPosition = if (isExpanded) -1 else position
                notifyDataSetChanged()
            }
        }
    }

    private fun buildMagicItemExpandedContent(ctx: Context, slot: MagicItemSlot): View {
        val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val selectedItem = slot.selectedId?.let { slot.items[it] }

        val descView = TextView(ctx).apply {
            text = selectedItem?.description?.get() ?: ""
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setPadding(0, 4.dp(ctx), 0, 0)
        }

        val displayNames: List<String> = listOf("—") + slot.items.values.map { it.name.get() }
        val idByName: Map<String, String> = slot.items.entries.map { it.value.name.get() to it.key }.toMap()

        val dropdownLayout = TextInputLayout(ctx).apply {
            this.hint = "Выберите магический предмет"
            setPadding(0, 8.dp(ctx), 0, 4.dp(ctx))
        }
        val dropdown = MaterialAutoCompleteTextView(ctx).apply {
            inputType = android.text.InputType.TYPE_NULL
            threshold = 0
            isFocusableInTouchMode = false
            isClickable = true
            isFocusable = true
            setAdapter(android.widget.ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, displayNames))
            setText(selectedItem?.name?.get() ?: "—", false)
            setOnClickListener { showDropDown() }
            setOnItemClickListener { _, _, position, _ ->
                val selectedName = displayNames.getOrNull(position) ?: "—"
                val selectedId = idByName[selectedName]
                descView.text = selectedId?.let { slot.items[it]?.description?.get() } ?: ""
                slot.onSelected(selectedId)
            }
        }
        dropdownLayout.addView(dropdown)
        content.addView(dropdownLayout)
        content.addView(descView)

        return content
    }

    inner class MagicItemAdapter(private val slots: List<MagicItemSlot>) : RecyclerView.Adapter<MagicItemAdapter.VH>() {

        inner class VH(val binding: CardFeatureCreateBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = CardFeatureCreateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun getItemCount() = slots.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val slot = slots[position]
            val isExpanded = position == magicItemExpandedPosition
            val selectedName = slot.selectedId?.let { vm.resolveName(it) ?: it.substringAfterLast(":") }
            holder.binding.featureTitle.text = selectedName ?: slot.title
            holder.binding.expandedContent.removeAllViews()
            holder.binding.expandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
            if (isExpanded) {
                val expandedContent = buildMagicItemExpandedContent(holder.binding.root.context, slot)
                holder.binding.expandedContent.addView(expandedContent)
            }
            holder.binding.headerRow.setOnClickListener {
                magicItemExpandedPosition = if (isExpanded) -1 else position
                notifyDataSetChanged()
            }
        }
    }

    private fun statBox(ctx: Context, label: String, value: String, filled: Boolean = false): MaterialCardView {
        return MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(4.dp(ctx), 4.dp(ctx), 4.dp(ctx), 4.dp(ctx))
            }
            radius = 8f
            setCardBackgroundColor(if (filled) 0xFF6750A4.toInt() else resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHigh))
            strokeWidth = 0
            val inner = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(8.dp(ctx), 8.dp(ctx), 8.dp(ctx), 8.dp(ctx))
            }
            if (label.isNotEmpty()) {
                inner.addView(TextView(ctx).apply {
                    text = label
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                })
            }
            inner.addView(TextView(ctx).apply {
                text = value
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                if (filled) setTextColor(0xFFFFFFFF.toInt())
            })
            addView(inner)
        }
    }

    private fun sectionTitle(container: LinearLayout, title: String) {
        container.addView(TextView(requireContext()).apply {
            text = title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setPadding(0, 12.dp(context), 0, 4.dp(context))
        })
    }

    private fun resolveColor(attr: Int): Int {
        val ta = requireContext().theme?.obtainStyledAttributes(intArrayOf(attr))
        val color = ta?.getColor(0, 0) ?: 0
        ta?.recycle()
        return color
    }
}
