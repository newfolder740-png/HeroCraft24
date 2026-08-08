package com.herocraft24.feature.reference

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.herocraft24.core.model.*
import com.herocraft24.core.ui.local.UiLocalizer
import com.herocraft24.core.ui.render.CardBuilder
import com.herocraft24.core.ui.render.ExpandableCard
import com.herocraft24.core.ui.util.ItemLinkifier
import com.herocraft24.core.ui.util.dp
import com.herocraft24.core.ui.util.resolveColor
import com.herocraft24.feature.reference.databinding.FragmentReferenceDetailBinding

class ReferenceDetailFragment : Fragment() {

    private var _binding: FragmentReferenceDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReferenceViewModel by viewModels()

    private var objectId: String = ""
    private var categoryKey: String = ""
    private var autoOpenFeatureId: String? = null
    private var selectedSubspeciesId: String? = null
    private var speciesTraitsContainer: LinearLayout? = null

    // Active render target for class tab pages (changed on ViewPager page re-render).
    private var activeRenderContainer: LinearLayout? = null

    // Scroll position persistence for class tabs across configuration changes and back‑stack navigation.
    private val classTabScrollPositions = mutableMapOf<Int, Int>()
    private var classPagerAdapter: RecyclerView.Adapter<ClassPageViewHolder>? = null
    private var lastKnownTabPage: Int = -1
    private var contentScrollPosition: Int = 0
    private var openFeatureIds = mutableSetOf<String>()
    private var openInvocationIds = mutableSetOf<String>()
    private var openSchemsIds = mutableSetOf<String>()
    private var openMetamagicIds = mutableSetOf<String>()
    private var openManeuversIds = mutableSetOf<String>()
    private var openSpeciesTraitIds = mutableSetOf<String>()
    private var openClassSubclassIds = mutableSetOf<String>()

    private val magicItemCategories = setOf("wand", "rod", "potion", "ring", "staff", "scroll", "wondrous_item")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReferenceDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        objectId = arguments?.getString("objectId") ?: ""
        categoryKey = arguments?.getString("categoryKey") ?: "classes"

        // Disable swipe navigation only for class cards (they have internal tabs)
        if (categoryKey == "classes") {
            (requireActivity() as com.herocraft24.core.ui.util.SwipeToggle).setSwipeEnabled(false)
        }

        autoOpenFeatureId = arguments?.getString("featureId")

        // Restore saved scroll positions for class tabs, if any.
        savedInstanceState?.let { bundle ->
            val array = bundle.getIntArray("classTabScrollPositions")
            if (array != null) {
                for (i in array.indices step 2) {
                    classTabScrollPositions[array[i]] = array[i + 1]
                }
            }
            val featuresArray = bundle.getStringArrayList("openFeatureIds")
            if (featuresArray != null) {
                openFeatureIds.addAll(featuresArray)
            }
            val invocationsArray = bundle.getStringArrayList("openInvocationIds")
            if (invocationsArray != null) {
                openInvocationIds.addAll(invocationsArray)
            }
            val schemsArray = bundle.getStringArrayList("openSchemsIds")
            if (schemsArray != null) {
                openSchemsIds.addAll(schemsArray)
            }
            val metamagicArray = bundle.getStringArrayList("openMetamagicIds")
            if (metamagicArray != null) {
                openMetamagicIds.addAll(metamagicArray)
            }
            val maneuversArray = bundle.getStringArrayList("openManeuversIds")
            if (maneuversArray != null) {
                openManeuversIds.addAll(maneuversArray)
            }
            val speciesTraitArray = bundle.getStringArrayList("openSpeciesTraitIds")
            if (speciesTraitArray != null) {
                openSpeciesTraitIds.addAll(speciesTraitArray)
            }
            val classSubclassArray = bundle.getStringArrayList("openClassSubclassIds")
            if (classSubclassArray != null) {
                openClassSubclassIds.addAll(classSubclassArray)
            }
            contentScrollPosition = bundle.getInt("contentScrollPosition", 0)
        }

        binding.tabLayout.visibility = View.GONE
        binding.classViewPager.visibility = View.GONE
        binding.contentScroll.visibility = View.VISIBLE

        binding.toolbar.setNavigationOnClickListener {
            if (binding.classViewPager.visibility == View.VISIBLE &&
                binding.classViewPager.currentItem > 0
            ) {
                binding.classViewPager.setCurrentItem(0, false)
            } else {
                findNavController().navigateUp()
            }
        }

        when (categoryKey) {
            "classes" -> renderClass()
            "species" -> renderSpecies()
            "backgrounds" -> renderBackground()
            "feats" -> renderFeat()
            "items" -> renderItem()
            "spells" -> renderSpell()
            "conditions" -> renderCondition()
            "mechanics" -> renderMechanic()
            "monsters" -> renderMonster()
        }

        // Restore content scroll position for non-class cards
        if (categoryKey != "classes" && contentScrollPosition > 0) {
            binding.contentScroll.post { binding.contentScroll.scrollTo(0, contentScrollPosition) }
        }
    }

    /**
     * Called by MainActivity before popping the reference nav stack.
     * If the user is on a non-first tab of a class card, switches to the first tab
     * and returns true so the back press is consumed. Otherwise returns false.
     */
    fun onClassBackPressed(): Boolean {
        if (categoryKey != "classes") return false
        if (binding.classViewPager.visibility != View.VISIBLE) return false
        if (binding.classViewPager.currentItem <= 0) return false
        binding.classViewPager.setCurrentItem(0, false)
        return true
    }

    private var selectedSubclassId: String? = null

    private fun renderClass() {
        val obj = viewModel.getClass(objectId) ?: return showNotFound()
        binding.toolbar.title = obj.name.get()

        binding.tabLayout.visibility = View.VISIBLE
        binding.tabLayout.removeAllTabs()

        val tabs = mutableListOf(
            getString(R.string.reference_class_tab) to { renderClassMainTab(obj) },
            getString(R.string.reference_description) to { renderClassDescriptionTab(obj) }
        )

        if (obj.invocations.isNotEmpty()) {
            tabs.add(getString(R.string.reference_invocations) to { renderClassInvocationsTab(obj) })
        }

        if (obj.metamagics.isNotEmpty()) {
            tabs.add(getString(R.string.reference_metamagic) to { renderClassMetamagicTab(obj) })
        }

        if (obj.maneuvers.isNotEmpty()) {
            tabs.add(getString(R.string.reference_maneuvers) to { renderClassManeuversTab(obj) })
        }
        if (obj.schems.isNotEmpty()) {
            tabs.add(getString(R.string.reference_schems) to { renderClassSchemsTab(obj) })
        }

        if (obj.wild_magic.isNotEmpty()) {
            tabs.add(getString(R.string.reference_wild_magic) to { renderClassWildMagicTab(obj) })
        }

        binding.contentScroll.visibility = View.GONE
        binding.classViewPager.visibility = View.VISIBLE

        val tabTitles = tabs.map { it.first }
        binding.tabLayout.removeAllTabs()
        tabTitles.forEach { title ->
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(title))
        }

        val adapter = object : RecyclerView.Adapter<ClassPageViewHolder>() {
            override fun getItemCount(): Int = tabs.size

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassPageViewHolder {
                val scroll = ScrollView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    isFillViewport = true
                }
                val container = LinearLayout(parent.context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dpToPx(16), dpToPx(0), dpToPx(16), dpToPx(16))
                }
                scroll.addView(container)
                return ClassPageViewHolder(scroll, container)
            }

            override fun onBindViewHolder(holder: ClassPageViewHolder, position: Int) {
                // Assign a stable id so that scroll position is saved/restored across configuration changes.
                // The id is derived from the class objectId and tab position, ensuring it's consistent
                // when the fragment is recreated (e.g., after navigating back from a spell link).
                holder.view.id = "scroll_${objectId}_$position".hashCode()
                holder.container.removeAllViews()
                activeRenderContainer = holder.container
                try {
                    tabs[position].second()
                } finally {
                    activeRenderContainer = null
                }
                // Restore scroll position from saved state after content is populated.
                // Use OnPreDrawListener to scroll BEFORE the first draw, avoiding a flicker.
                val savedY = classTabScrollPositions[position] ?: 0
                if (savedY != 0) {
                    holder.view.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            holder.view.viewTreeObserver.removeOnPreDrawListener(this)
                            holder.view.scrollTo(0, savedY)
                            return true
                        }
                    })
                }
            }
        }

        binding.classViewPager.adapter = adapter
        classPagerAdapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.classViewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()

        // Store scroll position when the page changes or when the fragment is stopped.
        binding.classViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // Save scroll position of the PREVIOUS tab (the one we're leaving), not the current.
                // On the very first call (lastKnownTabPage == -1) we don't save anything
                // to avoid overwriting a restored scroll position with 0.
                if (lastKnownTabPage >= 0 && lastKnownTabPage != position) {
                    val previousHolder = findViewHolderAt(lastKnownTabPage)
                    previousHolder?.let {
                        classTabScrollPositions[lastKnownTabPage] = it.view.scrollY
                    }
                }
                lastKnownTabPage = position
            }

            override fun onPageScrollStateChanged(state: Int) {
                // When scrolling settles (IDLE), ensure the current position is saved.
                if (state == ViewPager2.SCROLL_STATE_IDLE && lastKnownTabPage >= 0) {
                    val holder = findViewHolderAt(lastKnownTabPage)
                    val scrollY = holder?.view?.scrollY ?: return
                    // Only save if we actually scrolled (don't overwrite a restored position with 0).
                    if (scrollY > 0 || !classTabScrollPositions.containsKey(lastKnownTabPage)) {
                        classTabScrollPositions[lastKnownTabPage] = scrollY
                    }
                }
            }
        })
    }

    private class ClassPageViewHolder(val view: ScrollView, val container: LinearLayout) :
        RecyclerView.ViewHolder(view)

    private fun findViewHolderAt(position: Int): ClassPageViewHolder? {
        val recyclerView = binding.classViewPager.getChildAt(0) as? RecyclerView
            ?: return null
        // Try to get the holder directly if it's already bound
        val holder = recyclerView.findViewHolderForAdapterPosition(position) as? ClassPageViewHolder
        if (holder != null) return holder
        // If not found, the page might not be instantiated yet — return null
        return null
    }

    private fun getCurrentScrollView(): ScrollView? {
        val recyclerView = binding.classViewPager.getChildAt(0) as? RecyclerView
            ?: return null
        val currentPos = binding.classViewPager.currentItem
        val holder = recyclerView.findViewHolderForAdapterPosition(currentPos) as? ClassPageViewHolder
        return holder?.view
    }

    private fun renderClassMainTab(obj: GameClass) {
        obj.class_table?.let { addClassTable(it) }
        addAcquisition(obj)
        addKeyAttributes(obj)
        addStartingEquipment(obj)
        addClassFeatures(obj)
        addSourceSection(obj.source)
        addReferencesSection(obj.references)
    }

    private fun renderClassDescriptionTab(obj: GameClass) {
        addSection(getString(R.string.reference_description)) {
            val desc = obj.description.get()
            if (desc.isNotBlank()) {
                addText(desc)
            } else {
                addText(getString(R.string.reference_not_found))
            }
        }

        val subclasses = obj.subclasses.mapNotNull { viewModel.getSubclass(it) }
        if (subclasses.isNotEmpty()) {
            addSection(getString(R.string.reference_subclass)) {
                for (subclass in subclasses) {
                    addView(createClassSubclassCard(subclass))
                }
            }
        }
    }

    private fun createClassSubclassCard(subclass: Subclass): MaterialCardView {
        val (card, _) = ExpandableCard.createExpandableCard(
            requireContext(),
            title = subclass.name.get(),
            openId = subclass.id,
            openIdsSet = openClassSubclassIds
        ) { body ->
            body.addView(buildRichLinkedTextView(subclass.description.get()))
            subclass.table?.let { body.addView(createTableView(it)) }
            subclass.description2?.let { body.addView(buildRichLinkedTextView(it.get())) }
        }
        return card
    }

    private fun renderClassInvocationsTab(obj: GameClass) {
        addSection(getString(R.string.reference_invocations)) {
            val invocations = obj.invocations.mapNotNull { viewModel.getInvocation(it) }
            val invocationsById = invocations.associateBy { it.id }
            invocations.forEach { invocation ->
                addView(createInvocationCard(invocation, invocationsById))
            }
        }
    }

    private fun renderClassManeuversTab(obj: GameClass) {
        addSection(getString(R.string.reference_maneuvers)) {
            val maneuvers = obj.maneuvers.mapNotNull { viewModel.getManeuvers(it) }
            val maneuversById = maneuvers.associateBy { it.id }
            maneuvers.forEach { maneuvers ->
                addView(createManeuversCard(maneuvers))
            }
        }
    }
    private fun renderClassSchemsTab(obj: GameClass) {
        addSection(getString(R.string.reference_schems)) {
            val schems = obj.schems.mapNotNull { viewModel.getSchems(it) }
            val schemsById = schems.associateBy { it.id }
            schems.forEach { schems ->
                addView(createSchemsCard(schems))
            }
        }
    }

    private fun renderClassMetamagicTab(obj: GameClass) {
        addSection(getString(R.string.reference_metamagic)) {
            val metamagics = obj.metamagics.mapNotNull { viewModel.getMetamagic(it) }
            metamagics.forEach { metamagic ->
                addView(createMetamagicCard(metamagic))
            }
        }
    }

    private fun createMetamagicCard(metamagic: Metamagic): MaterialCardView {
        val title = metamagic.cost.ifBlank { null }?.let { "${metamagic.name.get()} • $it" } ?: metamagic.name.get()
        val (card, _) = ExpandableCard.createExpandableCard(
            requireContext(),
            title = title,
            openId = metamagic.id,
            openIdsSet = openMetamagicIds
        ) { body ->
            body.addView(buildRichLinkedTextView(metamagic.description.get()))
        }
        return card
    }

    private fun createManeuversCard(maneuvers: Maneuvers): MaterialCardView {
        val (card, _) = ExpandableCard.createExpandableCard(
            requireContext(),
            title = maneuvers.name.get(),
            openId = maneuvers.id,
            openIdsSet = openManeuversIds
        ) { body ->
            body.addView(buildRichLinkedTextView(maneuvers.description.get()))
        }
        return card
    }
    private fun createSchemsCard(schems: Schems): MaterialCardView {
        val (card, _) = ExpandableCard.createExpandableCard(
            requireContext(),
            title = schems.name.get(),
            openId = schems.id,
            openIdsSet = openSchemsIds
        ) { body ->
            schems.description?.let { body.addView(buildRichLinkedTextView(it.get())) }
            schems.table?.let { body.addView(createTableView(it)) }
            schems.description2?.let { body.addView(buildRichLinkedTextView(it.get())) }
        }
        return card
    }

    private fun createTableView(table: Table): View {
        val context = requireContext()
        val surface = resolveColor(com.google.android.material.R.attr.colorSurface)
        val surfaceVariant = resolveColor(com.google.android.material.R.attr.colorSurfaceVariant)
        val onSurface = resolveColor(com.google.android.material.R.attr.colorOnSurface)

        // Корневой контейнер с горизонтальной прокруткой
        val scrollView = android.widget.HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
        }

        // Контейнер для таблицы
        val tableContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        scrollView.addView(tableContainer)

        // Создаём TableLayout
        val tableLayout = android.widget.TableLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isStretchAllColumns = false
        }
        tableContainer.addView(tableLayout)

        // Заголовок таблицы
        val headerRow = android.widget.TableRow(context).apply {
            layoutParams = android.widget.TableLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(surfaceVariant)
        }
        table.columns.forEach { column ->
            headerRow.addView(TextView(context).apply {
                text = column.name.get()
                setTextColor(onSurface)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                setSingleLine(false)
                maxLines = 2
            })
        }
        tableLayout.addView(headerRow)

        // Строки таблицы
        table.rows.forEachIndexed { index, row ->
            val rowView = android.widget.TableRow(context).apply {
                layoutParams = android.widget.TableLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(if (index % 2 == 0) surface else surfaceVariant)
            }
            table.columns.forEach { column ->
                val cellValue = row.values[column.key] ?: "—"
                // Используем buildRichLinkedTextView для ячеек таблицы
                val textView = buildRichLinkedTextView(cellValue)
                textView.apply {
                    setTextColor(onSurface)
                    gravity = Gravity.START
                    setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                    setSingleLine(false)
                    maxWidth = dpToPx(200)
                }
                rowView.addView(textView)
            }
            tableLayout.addView(rowView)
        }

        return scrollView
    }

    private fun renderClassWildMagicTab(obj: GameClass) {
        addSection(getString(R.string.reference_wild_magic)) {
            val card = MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dpToPx(12))
                }
                radius = dpToPx(12).toFloat()
                cardElevation = dpToPx(2).toFloat()
            }
            val inner = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            }
            card.addView(inner)

            obj.wild_magic.forEach { entry ->
                inner.addView(TextView(requireContext()).apply {
                    val rangeSpanned = SpannableString("${entry.range}. ")
                    rangeSpanned.setSpan(StyleSpan(Typeface.BOLD), 0, rangeSpanned.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text = SpannableStringBuilder()
                        .append(rangeSpanned)
                        .append(buildRichLinkedSpannable(entry.description.get()))
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    movementMethod = LinkMovementMethod.getInstance()
                    setPadding(0, dpToPx(6), 0, dpToPx(6))
                })
            }

            addView(card)
        }
    }

    private fun createInvocationCard(
        invocation: Invocation,
        invocationsById: Map<String, Invocation>
    ): MaterialCardView {
        val subtitleParts = mutableListOf<String>()
        invocation.level?.let { subtitleParts.add("уровень Колдуна $it") }
        invocation.requirements?.invocation_id?.let { reqId ->
            val reqName = invocationsById[reqId]?.name?.get() ?: reqId
            subtitleParts.add("«$reqName»")
        }
        val (card, _) = ExpandableCard.createExpandableCard(
            requireContext(),
            title = invocation.name.get(),
            subtitle = subtitleParts.joinToString(" • ").ifBlank { null },
            openId = invocation.id,
            openIdsSet = openInvocationIds
        ) { body ->
            body.addView(buildRichLinkedTextView(invocation.description.get()))
            invocation.table?.let { body.addView(createTableView(it)) }
            invocation.description2?.let { body.addView(buildRichLinkedTextView(it.get())) }
        }
        return card
    }

    private fun addClassTable(table: ClassTable) {
        addSection(getString(R.string.reference_progression_table)) {
            val surface = resolveColor(com.google.android.material.R.attr.colorSurface)
            val surfaceVariant = resolveColor(com.google.android.material.R.attr.colorSurfaceVariant)
            val onSurface = resolveColor(com.google.android.material.R.attr.colorOnSurface)

            val root = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            // Fixed left column: level
            val leftTable = android.widget.TableLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setPadding(dpToPx(8), dpToPx(8), 0, dpToPx(8))
            }
            root.addView(leftTable)

            // Scrollable right columns
            val scroll = android.widget.HorizontalScrollView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                isHorizontalScrollBarEnabled = false
            }
            val rightTable = android.widget.TableLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setPadding(0, dpToPx(8), dpToPx(8), dpToPx(8))
            }
            scroll.addView(rightTable)
            root.addView(scroll)

            // Header
            val headerHeight = dpToPx(48)
            val leftHeader = android.widget.TableRow(requireContext()).apply {
                layoutParams = android.widget.TableLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, headerHeight)
                setBackgroundColor(surfaceVariant)
            }
            leftHeader.addView(makeTableCell("Уровень", headerHeight, onSurface, isHeader = true))
            leftTable.addView(leftHeader)

            val rightHeader = android.widget.TableRow(requireContext()).apply {
                layoutParams = android.widget.TableLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, headerHeight)
                setBackgroundColor(surfaceVariant)
            }
            for (col in table.columns) {
                rightHeader.addView(makeTableCell(col.name.get(), headerHeight, onSurface, isHeader = true))
            }
            rightTable.addView(rightHeader)

            // Body rows
            val rowHeight = dpToPx(40)
            val sortedRows = table.rows.sortedBy { it.level }
            sortedRows.forEachIndexed { index, row ->
                val rowColor = if (index % 2 == 0) surface else surfaceVariant

                val leftRow = android.widget.TableRow(requireContext()).apply {
                    layoutParams = android.widget.TableLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, rowHeight)
                    setBackgroundColor(rowColor)
                }
                leftRow.addView(makeTableCell(row.level.toString(), rowHeight, onSurface))
                leftTable.addView(leftRow)

                val rightRow = android.widget.TableRow(requireContext()).apply {
                    layoutParams = android.widget.TableLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, rowHeight)
                    setBackgroundColor(rowColor)
                }
                for (col in table.columns) {
                    rightRow.addView(makeTableCell(row.values[col.key] ?: "—", rowHeight, onSurface))
                }
                rightTable.addView(rightRow)
            }

            addView(root)
        }
    }

    private fun makeTableCell(text: String, height: Int, textColor: Int, isHeader: Boolean = false): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(textColor)
            gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            if (isHeader) setTypeface(null, Typeface.BOLD)
            layoutParams = android.widget.TableRow.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, height)
            minWidth = dpToPx(40)
        }
    }

    private fun addKeyAttributes(classObj: GameClass) {
        addSection(getString(R.string.reference_key_attributes)) {
            val card = MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dpToPx(8), 0, dpToPx(8))
                }
                radius = dpToPx(12).toFloat()
                cardElevation = dpToPx(2).toFloat()
            }
            val inner = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            }
            card.addView(inner)

            val attrs: List<Pair<String, String>> = if (classObj.key_attributes.isNotEmpty()) {
                classObj.key_attributes.map { it.key to it.value }
            } else {
                val list = mutableListOf<Pair<String, String>>()
                list.add("Основная характеристика" to classObj.primary_ability.replaceFirstChar { it.uppercase() })
                list.add("Кость хитов" to "d${classObj.hit_die}")
                list.add("Спасброски" to classObj.saving_throws.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } })
                list.toList()
            }

            for ((label, value) in attrs) {
                inner.addView(TextView(requireContext()).apply {
                    val span = SpannableString("$label: $value")
                    span.setSpan(StyleSpan(Typeface.BOLD), 0, label.length + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text = span
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    setPadding(0, dpToPx(4), 0, dpToPx(4))
                })
            }

            addView(card)
        }
    }

    private fun addAcquisition(classObj: GameClass) {
        val acquisition = classObj.acquisition ?: return
        addSection(getString(R.string.reference_acquisition)) {
            val card = MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dpToPx(8), 0, dpToPx(8))
                }
                radius = dpToPx(12).toFloat()
                cardElevation = dpToPx(2).toFloat()
            }
            val inner = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            }
            card.addView(inner)

            val radioGroup = RadioGroup(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val firstRadio = RadioButton(requireContext()).apply {
                id = View.generateViewId()
                text = "Первый класс персонажа"
                isChecked = true
            }
            val multiRadio = RadioButton(requireContext()).apply {
                id = View.generateViewId()
                text = "Мультикласс"
            }
            radioGroup.addView(firstRadio)
            radioGroup.addView(multiRadio)
            inner.addView(radioGroup)

            val textView = TextView(requireContext()).apply {
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setPadding(0, dpToPx(12), 0, 0)
            }
            inner.addView(textView)
            textView.text = acquisition.first_class.get()

            radioGroup.setOnCheckedChangeListener { _, checkedId ->
                textView.text = if (checkedId == multiRadio.id) acquisition.multiclass.get() else acquisition.first_class.get()
            }

            addView(card)
        }
    }

    private fun addStartingEquipment(classObj: GameClass) {
        if (classObj.starting_equipment.isEmpty()) return
        addSection(getString(R.string.reference_starting_equipment)) {
            val card = MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dpToPx(8), 0, dpToPx(8))
                }
                radius = dpToPx(12).toFloat()
                cardElevation = dpToPx(2).toFloat()
            }
            val inner = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            }
            card.addView(inner)

            for (choice in classObj.starting_equipment) {
                choice.description?.get()?.let { desc ->
                    inner.addView(TextView(requireContext()).apply {
                        text = desc
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                        setPadding(0, 0, 0, dpToPx(8))
                    })
                }

                for (option in choice.options) {
                    inner.addView(TextView(requireContext()).apply {
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                        movementMethod = LinkMovementMethod.getInstance()
                        text = buildStartingEquipmentSpannable(option)
                        setPadding(0, 0, 0, dpToPx(8))
                    })
                }
            }

            addView(card)
        }
    }

    private fun buildStartingEquipmentSpannable(option: EquipmentOption): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        option.description?.get()?.let { sb.append(it) }
        val children = option.options
        if (children.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append(": ")
            for ((index, child) in children.withIndex()) {
                if (index > 0) sb.append(", ")
                appendStartingEquipmentText(sb, child)
            }
        }
        
        // Apply item links to the entire text using ItemLinkifier.
        val text = sb.toString()
        val itemMap = viewModel.getItemNameMap()
        val matches = ItemLinkifier.findRanges(text, itemMap)
        
        // Remove any existing ClickableSpans to avoid duplicates.
        val existingSpans = sb.getSpans(0, sb.length, ClickableSpan::class.java)
        for (span in existingSpans) {
            sb.removeSpan(span)
        }
        
        // Add new links from ItemLinkifier.
        for ((start, end, fullId) in matches) {
            sb.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        navigateToItem(fullId)
                    }
                },
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        return sb
    }

    private fun appendStartingEquipmentText(sb: SpannableStringBuilder, option: EquipmentOption) {
        val prefix = if (option.quantity > 1 && option.quantity != 1) "${option.quantity}x " else ""
        
        when {
            option.item_id == "phb2024:book" -> {
                sb.append(prefix).append("Книга заклинаний")
            }
            option.item_id != null -> {
                val name = viewModel.resolveName(option.item_id!!) ?: option.item_id!!
                sb.append(prefix).append(name)
            }
            else -> {
                val desc = option.description?.get() ?: ""
                sb.append(prefix).append(desc)
            }
        }

        if (option.options.isNotEmpty()) {
            sb.append(" (")
            for ((i, child) in option.options.withIndex()) {
                if (i > 0) sb.append(", ")
                appendStartingEquipmentText(sb, child)
            }
            sb.append(")")
        }
    }

    private var classFeaturesContainer: LinearLayout? = null

    private fun addClassFeatures(classObj: GameClass) {
        addSection(getString(R.string.reference_features)) {
            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dpToPx(8), 0, dpToPx(8))
            }
            classFeaturesContainer = container
            addView(container)
            rebuildClassFeatures(classObj, container)
        }
    }

    private fun rebuildClassFeatures(classObj: GameClass, container: LinearLayout) {
        container.removeAllViews()
        val selectedSubclass = selectedSubclassId?.let { viewModel.getSubclass(it) }
        val items = buildFeatureList(classObj, selectedSubclass)
        val featureCards = mutableListOf<Pair<Feature, Pair<MaterialCardView, LinearLayout>>>()
        for (item in items) {
            val pair = createFeatureCard(classObj, item.feature, item.source, selectedSubclass)
            featureCards.add(item.feature to pair)
            container.addView(pair.first)
        }
        autoOpenFeatureId?.let { targetId ->
            featureCards.find { it.first.id == targetId }?.let { (_, pair) ->
                val (card, body) = pair
                body.isVisible = true
                card.post { card.requestFocus() }
            }
            autoOpenFeatureId = null
        }
    }

    private data class FeatureItem(
        val feature: Feature,
        val source: String,
        val sortOrder: Int
    )

    private fun buildFeatureList(classObj: GameClass, selectedSubclass: Subclass?): List<FeatureItem> {
        val base = classObj.features.mapNotNull { viewModel.getFeature(it) }
        if (selectedSubclass == null) {
            return base.mapIndexed { index, feature -> FeatureItem(feature, "base", index) }
        }

        val remainingByLevel = selectedSubclass.features
            .mapNotNull { viewModel.getFeature(it) }
            .groupBy { it.level }
            .mapValues { entry -> entry.value.toMutableList() }
            .toMutableMap()

        val result = mutableListOf<FeatureItem>()
        var sortOrder = 0

        for (feature in base) {
            if (feature.is_placeholder) {
                // Replace placeholder with subclass features earned at this level.
                val subs = remainingByLevel.remove(feature.level)
                if (!subs.isNullOrEmpty()) {
                    for (sub in subs) {
                        result.add(FeatureItem(sub, "subclass", sortOrder++))
                    }
                }
                continue
            }

            result.add(FeatureItem(feature, "base", sortOrder++))

            // If the base feature itself shares a level with subclass features
            // (e.g. the subclass choice feature at subclass_level), append them after it.
            val subs = remainingByLevel.remove(feature.level)
            if (!subs.isNullOrEmpty()) {
                for (sub in subs) {
                    result.add(FeatureItem(sub, "subclass", sortOrder++))
                }
            }
        }

        // Any leftover subclass features go at the end, sorted by level.
        val remaining = remainingByLevel.values.flatten().sortedBy { it.level }
        for (sub in remaining) {
            result.add(FeatureItem(sub, "subclass", sortOrder++))
        }

        return result
    }

    private fun createFeatureCard(classObj: GameClass, feature: Feature, source: String, selectedSubclass: Subclass?): Pair<MaterialCardView, LinearLayout> {
        val rawDescription = feature.description.get()
        var descriptionLinked = false
        val onExpandHolder = object { var callback: (() -> Unit)? = null }

        val (card, body) = ExpandableCard.createExpandableCard(
            requireContext(),
            title = "Уровень ${feature.level}: ${feature.name.get()}",
            openId = feature.id,
            openIdsSet = openFeatureIds,
            onExpand = { onExpandHolder.callback?.invoke() }
        ) { body ->
            feature.table?.let { body.addView(createTableView(it)) }
            feature.description2?.let { body.addView(buildRichLinkedTextView(it.get())) }
            if (feature.is_subclass_choice && classObj.subclasses.isNotEmpty()) {
                body.addView(createSubclassSpinner(classObj, selectedSubclass))
            }
            if (rawDescription.isNotBlank()) {
                body.addView(TextView(requireContext()).apply {
                    tag = "feature_desc"
                    text = rawDescription
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    setPadding(0, 4, 0, 4)
                })
            }
        }

        onExpandHolder.callback = {
            if (!descriptionLinked && rawDescription.isNotBlank()) {
                val existingPlaceholder = body.findViewWithTag<TextView>("feature_desc")
                if (existingPlaceholder != null) {
                    val linkedView = buildRichLinkedTextView(rawDescription)
                    val index = body.indexOfChild(existingPlaceholder)
                    body.removeViewAt(index)
                    body.addView(linkedView, index)
                }
                descriptionLinked = true
            }
        }

        if (body.isVisible) {
            onExpandHolder.callback?.invoke()
        }

        val hasContent = rawDescription.isNotBlank() ||
                (feature.is_subclass_choice && classObj.subclasses.isNotEmpty()) ||
                feature.table != null || feature.description2 != null
        if (!hasContent) {
            card.setOnClickListener(null)
            card.isClickable = false
        }

        return card to body
    }

    private fun createSubclassSpinner(classObj: GameClass, selectedSubclass: Subclass?): Spinner {
        val spinner = Spinner(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dpToPx(8), 0, dpToPx(8))
            }
        }
        val subclassRefs = classObj.subclasses.mapNotNull { fullId ->
            viewModel.getSubclass(fullId)?.let { fullId to it }
        }
        val names = listOf("Выберите подкласс") + subclassRefs.map { it.second.name.get() }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newId = if (position == 0) null else subclassRefs[position - 1].first
                if (newId != selectedSubclassId) {
                    selectedSubclassId = newId
                    classFeaturesContainer?.let { rebuildClassFeatures(classObj, it) }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinner.onItemSelectedListener = listener

        selectedSubclassId?.let { id ->
            val index = subclassRefs.indexOfFirst { it.first == id }
            if (index >= 0) {
                spinner.onItemSelectedListener = null
                spinner.setSelection(index + 1)
                spinner.onItemSelectedListener = listener
            }
        }

        return spinner
    }

    private fun localizeCategory(category: String?): String = when (category) {
        "origin" -> "Черта происхождения"
        "universal" -> "Универсальная черта"
        "fighting_style" -> "Боевой стиль"
        "epic_boon" -> "Эпический дар"
        "dragonmark" -> "Драконья метка"
        "dark_gift" -> "Тёмный дар"
        "weapon" -> "Оружие"
        "armor" -> "Броня"
        "shield" -> "Щит"
        "adventuring_gear" -> "Снаряжение приключений"
        "pack" -> "Набор"
        "tool" -> "Ремесленный инструмент"
        "instrument" -> "Инструмент"
        "focus" -> "Фокусировка"
        "wand" -> "Волшебная палочка"
        "rod" -> "Жезл"
        "potion" -> "Зелье"
        "ring" -> "Кольцо"
        "staff" -> "Посох"
        "scroll" -> "Свиток"
        "wondrous_item" -> "Чудесная вещь"
        "ammunition" -> "Боеприпасы"
        "gear" -> "Снаряжение"
        else -> category?.replaceFirstChar { it.uppercase() } ?: ""
    }

    private fun localizeItemRarity(rarity: String?): String = UiLocalizer.rarity(rarity)

    private fun localizeDamageType(type: String): String = UiLocalizer.damageType(type)

    private fun localizeProperty(property: String): String = UiLocalizer.property(property)


    private fun localizeWeapon(weapon: String): String = weapon.replace("_", " ").replaceFirstChar { it.uppercase() }
    private fun localizeArmor(armor: String): String = armor.replace("_", " ").replaceFirstChar { it.uppercase() }

    private fun dpToPx(dp: Int): Int = dp.dp(requireContext())

    private fun resolveColor(attrRes: Int): Int = requireContext().resolveColor(attrRes)

    private fun linkColor(): Int = resolveColor(android.R.attr.colorAccent)

    private fun renderSpecies() {
        val obj = viewModel.getSpecies(objectId) ?: return showNotFound()
        binding.toolbar.title = obj.name.get()
        addSection(getString(R.string.reference_quick_info)) {
            addRow(getString(R.string.type_label), UiLocalizer.type(obj.creature_type))
            addRow(getString(R.string.size_label), UiLocalizer.size(obj.size))
            addRow(getString(R.string.speed_label), "${obj.speed} ${getString(R.string.feet)}")
            val dv = obj.darkvision
            if (dv != null) addRow(getString(R.string.darkvision_label), "$dv ${getString(R.string.feet)}")
        }
        addSection(getString(R.string.reference_description)) { addLinkedItemDescription(obj.description.get(), "") }

        if (!obj.subspecies.isNullOrEmpty()) {
            addSection("Род") { addView(createSubspeciesSpinner(obj)) }
        }

        addSection(getString(R.string.reference_traits)) {
            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dpToPx(8), 0, dpToPx(8))
            }
            speciesTraitsContainer = container
            addView(container)
            rebuildSpeciesTraits(obj, container)
        }
        obj.table?.let { addSection("Таблица") { addView(createTableView(it)) } }
        obj.description2?.let { addSection("") { addView(buildRichLinkedTextView(it.get())) } }
        addSourceSection(obj.source)
    }

    private fun createSubspeciesSpinner(species: Species): Spinner {
        val spinner = Spinner(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val subspecies = species.subspecies ?: emptyList()
        val names = listOf("Выберите род") + subspecies.map { it.name.get() }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newId = if (position == 0) null else subspecies[position - 1].id
                if (newId != selectedSubspeciesId) {
                    selectedSubspeciesId = newId
                    speciesTraitsContainer?.let { rebuildSpeciesTraits(species, it) }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinner.onItemSelectedListener = listener

        selectedSubspeciesId?.let { id ->
            val index = subspecies.indexOfFirst { it.id == id }
            if (index >= 0) {
                spinner.onItemSelectedListener = null
                spinner.setSelection(index + 1)
                spinner.onItemSelectedListener = listener
            }
        }

        return spinner
    }

    private fun rebuildSpeciesTraits(species: Species, container: LinearLayout) {
        container.removeAllViews()
        val selected = selectedSubspeciesId?.let { id -> species.subspecies?.find { it.id == id } }

        val asis = mutableListOf<AbilityScoreIncrease>()
        asis.addAll(species.ability_score_increases)
        selected?.ability_score_increases?.let { asis.addAll(it) }
        if (asis.isNotEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = getString(R.string.reference_ability_score_increases)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                setPadding(0, dpToPx(8), 0, dpToPx(4))
            })
            for (asi in asis) {
                container.addView(TextView(requireContext()).apply {
                    text = "• ${localizeAbility(asi.ability)} +${asi.increase}${if (asi.optional) " (по выбору)" else ""}"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    setPadding(0, 0, 0, dpToPx(4))
                })
            }
        }

        for (trait in species.traits) {
            if (trait.is_placeholder) {
                if (selected != null) {
                    for (subTrait in selected.traits) {
                        container.addView(createSpeciesTraitCard(subTrait))
                    }
                } else {
                    container.addView(createSpeciesTraitCard(trait))
                }
            } else {
                container.addView(createSpeciesTraitCard(trait))
            }
        }
    }

    private fun createSpeciesTraitCard(trait: SpeciesTrait): MaterialCardView {
        val traitKey = trait.name.get()
        val levelSuffix = trait.level?.let { "Уровень $it: " } ?: ""
        val (card, _) = ExpandableCard.createExpandableCard(
            requireContext(),
            title = "$levelSuffix$traitKey",
            openId = traitKey,
            openIdsSet = openSpeciesTraitIds
        ) { body ->
            body.addView(buildRichLinkedTextView(trait.description.get()))
            trait.table?.let { body.addView(createTableView(it)) }
            trait.description2?.let { body.addView(buildRichLinkedTextView(it.get())) }
        }
        return card
    }

    private fun renderBackground() {
        val obj = viewModel.getBackground(objectId) ?: return showNotFound()
        binding.toolbar.title = obj.name.get()
        addSection(getString(R.string.reference_description)) { addText(obj.description.get()) }
        if (obj.ability_score_increases.isNotEmpty()) {
            addSection(getString(R.string.reference_ability_score_increases)) {
                if (obj.ability_score_choice) {
                    val abilities = obj.ability_score_increases.joinToString(", ") { localizeAbility(it.ability) }
                    addText(getString(R.string.background_asi_choice, abilities))
                } else {
                    for (asi in obj.ability_score_increases) {
                        addText("• ${localizeAbility(asi.ability)} +${asi.increase}${if (asi.optional) " (по выбору)" else ""}")
                    }
                }
            }
        }
        if (obj.skill_proficiencies.isNotEmpty()) {
            addSection(getString(R.string.reference_skills)) { addText(obj.skill_proficiencies.joinToString(", ") { UiLocalizer.skill(it) }) }
        }
        if (obj.tool_item_ids.isNotEmpty()) {
            addSection("Инструменты") { addLinkedIds(obj.tool_item_ids) }
        }
        if (obj.equipment_items.isNotEmpty()) {
            addSection("Снаряжение") { addLinkedIds(obj.equipment_items) }
        } else if (obj.equipment.isNotEmpty()) {
            addSection("Снаряжение") {
                val card = MaterialCardView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, dpToPx(8), 0, dpToPx(8))
                    }
                    radius = dpToPx(12).toFloat()
                    cardElevation = dpToPx(2).toFloat()
                }
                val inner = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                }
                card.addView(inner)

                for (choice in obj.equipment) {
                    for ((optIndex, option) in choice.options.withIndex()) {
                        inner.addView(TextView(requireContext()).apply {
                            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                            movementMethod = LinkMovementMethod.getInstance()
                            text = buildBackgroundEquipmentSpannable(option)
                            setPadding(0, 0, 0, dpToPx(8))
                        })
                        if (optIndex < choice.options.size - 1) {
                            inner.addView(TextView(requireContext()).apply {
                                text = "или"
                                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                                setPadding(0, 0, 0, dpToPx(4))
                            })
                        }
                    }
                }

                addView(card)
            }
        }
        val featId = obj.feat
        if (featId != null) {
            addSection(getString(R.string.reference_feature)) {
                val spannable = SpannableStringBuilder()
                val name = viewModel.resolveName(featId) ?: featId
                val start = spannable.length
                spannable.append(name)
                spannable.setSpan(
                    object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            val bundle = Bundle().apply {
                                putString("objectId", featId)
                                putString("categoryKey", "feats")
                            }
                            findNavController().navigate(R.id.referenceDetail, bundle)
                        }
                    },
                    start,
                    spannable.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                val tv = TextView(requireContext()).apply {
                    text = spannable
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    setPadding(0, 4, 0, 4)
                    movementMethod = LinkMovementMethod.getInstance()
                }
                addView(tv)
            }
        }
        obj.table?.let { addSection("Таблица") { addView(createTableView(it)) } }
        obj.description2?.let { addSection("") { addView(buildRichLinkedTextView(it.get())) } }
        addSourceSection(obj.source)
    }

    private fun renderItem() {
        val obj = viewModel.getItem(objectId) ?: return showNotFound()
        binding.toolbar.title = obj.name.get()
        addSection(getString(R.string.reference_quick_info)) {
            addRow(getString(R.string.category_label), localizeCategory(obj.category))
            if (obj.category !in magicItemCategories) {
                if (obj.subcategory.isNotEmpty()) {
                    addRow(getString(R.string.subcategory_label), obj.subcategory.joinToString(", ") { UiLocalizer.subcategory(it) })
                }
            }
            addRow(getString(R.string.rarity_label), localizeItemRarity(obj.rarity))
            if (obj.magic) addRow(getString(R.string.magic_item_label), getString(R.string.yes))
            if (obj.attunement) {
                val attReq = obj.attunement_requirements?.get()?.takeIf { it.isNotBlank() }
                addRow(getString(R.string.attunement_label), attReq ?: getString(R.string.required))
            }
            obj.cost?.let { addRow(getString(R.string.cost_label), "${it.amount} ${UiLocalizer.costUnit(it.unit)}") }
            obj.weight?.let { addRow(getString(R.string.weight_label), "${it.amount} ${UiLocalizer.weightUnit(it.unit)}") }
            obj.damage?.let {
                val damageText = buildString {
                    append(it.damage_dice)
                    append(" ")
                    append(localizeDamageType(it.damage_type))
                    it.versatile_dice?.let { v -> append(" ($v двумя руками)") }
                }
                addRow(getString(R.string.damage_label), damageText)
            }
            obj.armor_class?.let {
                val acText = buildString {
                    append(it.base)
                    if (it.dex_bonus) {
                        append(" + ")
                        if (it.max_dex != null) append("Ловкость (макс. +${it.max_dex})") else append("Ловкость")
                    }
                }
                addRow(getString(R.string.armor_class_label), acText)
                it.min_strength?.let { str -> addRow(getString(R.string.strength_requirement_label), str.toString()) }
                if (it.stealth_disadvantage) addRow(getString(R.string.stealth_disadvantage_label), getString(R.string.yes))
            }
            if (obj.properties.isNotEmpty()) {
                addRow(getString(R.string.properties_label), obj.properties.joinToString(", ") { localizeProperty(it) })
            }
            if (obj.effects.isNotEmpty()) {
                addRow(getString(R.string.effects_label), obj.effects.joinToString("\n") { "• ${it.get()}" })
            }
        }
        addSection(getString(R.string.reference_description)) { addLinkedItemDescription(obj.description.get(), obj.name.get()) }
        obj.table?.let { addSection("Таблица") { addView(createTableView(it)) } }
        obj.description2?.let { addSection("") { addView(buildRichLinkedTextView(it.get())) } }
        addSourceSection(obj.source)
    }

    private fun renderSpell() {
        val spell = viewModel.getSpell(objectId) ?: return showNotFound()
        binding.toolbar.title = spell.name.get()
        addSection(getString(R.string.reference_quick_info)) {
            val levelStr = if (spell.level == 0) getString(R.string.reference_spell_cantrip) else getString(R.string.reference_spell_level, spell.level)
            addRow(getString(R.string.reference_spell_level_label), levelStr)
            addRow(getString(R.string.reference_spell_school), localizeSpellSchool(spell.school))
            addRow(getString(R.string.reference_spell_casting_time), spell.casting_time)
            addRow(getString(R.string.reference_spell_duration), spell.duration)
            addRow(getString(R.string.reference_spell_range), spell.range?.text ?: spell.range?.type ?: "—")
            addRow(getString(R.string.reference_spell_components), spell.components.joinToString(", "))
            spell.material?.let { addRow(getString(R.string.reference_spell_material), it) }
            addRow(getString(R.string.reference_spell_concentration), if (spell.concentration) getString(R.string.yes) else getString(R.string.no))
            addRow(getString(R.string.reference_spell_ritual), if (spell.ritual) getString(R.string.yes) else getString(R.string.no))
            spell.saving_throw?.let { addRow(getString(R.string.reference_spell_saving_throw), localizeAbility(it)) }
            spell.attack_type?.let { addRow(getString(R.string.reference_spell_attack_type), it.replaceFirstChar { c -> c.uppercase() }) }
            spell.area_of_effect?.let { addRow(getString(R.string.reference_spell_area), "${it.size} фт ${it.type.replaceFirstChar { c -> c.uppercase() }}") }
        }
        addSection(getString(R.string.reference_description)) {
            addView(buildLinkedTextView(spell.description.get()))
        }
        spell.table?.let { addSection(getString(R.string.reference_spell_table)) { addView(createTableView(it)) } }
        spell.description2?.let { addSection("") { addView(buildRichLinkedTextView(it.get())) } }
        spell.higher_levels?.let {
            addSection(getString(R.string.reference_spell_higher_levels)) { addText(it.get()) }
        }
        spell.damage?.let { dmg ->
            addSection(getString(R.string.reference_spell_damage)) {
                addRow(getString(R.string.damage_label), dmg.damage_type.replaceFirstChar { it.uppercase() })
                if (dmg.damage_at_slot_level.isNotEmpty()) {
                    addText(getString(R.string.reference_spell_damage_per_slot))
                    for ((slot, dice) in dmg.damage_at_slot_level) {
                        addText("  $slot: $dice")
                    }
                }
                dmg.damage_at_character_level?.takeIf { it.isNotEmpty() }?.let { charLevels ->
                    addText(getString(R.string.reference_spell_damage_per_character_level))
                    for ((lvl, dice) in charLevels) {
                        addText("  $lvl: $dice")
                    }
                }
            }
        }
        spell.classes.takeIf { it.isNotEmpty() }?.let { ids ->
            addSection(getString(R.string.reference_spell_classes)) {
                addText(ids.mapNotNull { id -> viewModel.resolveName(id) ?: id }.joinToString(", "))
            }
        }
        addSourceSection(spell.source)
    }

    private fun localizeSpellSchool(school: String): String = UiLocalizer.school(school)

    private fun renderFeat() {
        val obj = viewModel.getFeat(objectId) ?: return showNotFound()
        binding.toolbar.title = obj.name.get()
        addSection(getString(R.string.reference_quick_info)) {
            addRow(getString(R.string.category_label), localizeCategory(obj.category))
            val prereq = obj.prerequisite
            if (prereq != null) addRow("Требование", prereq.get())
            if (obj.ability_score_increase.isNotEmpty()) {
                addRow("Характеристики", obj.ability_score_increase.joinToString(", ") { "${localizeAbility(it.ability)} +${it.increase}" })
            }
        }
        addSection(getString(R.string.reference_description)) { addText(obj.description.get()) }
        if (obj.benefits.isNotEmpty()) {
            addSection(getString(R.string.reference_benefits)) {
                for (b in obj.benefits) {
                    addFeatureRow(b.name?.get() ?: "Преимущество", b.description.get())
                }
            }
        }
        addSourceSection(obj.source)
    }

    private fun renderCondition() {
        val obj = viewModel.getCondition(objectId) ?: return showNotFound()
        binding.toolbar.title = obj.name.get()
        val excludedIds = setOf(objectId)
        addSection(getString(R.string.reference_description)) {
            addView(buildLinkedTextView(obj.description.get(), excludedIds))
        }
        if (obj.effects.isNotEmpty()) {
            addSection(getString(R.string.reference_effects)) {
                for (e in obj.effects) {
                    addView(buildLinkedTextView("• ${e.get()}", excludedIds))
                }
            }
        }
        addSourceSection(obj.source)
    }

    private fun buildLinkedTextView(text: String, excludedIds: Set<String> = emptySet()): TextView {
        val marker = ItemLinkifier.stripMarkers(text)
        val spannable = SpannableStringBuilder(marker.text)
        val matches = ItemLinkifier.findRanges(marker.text, viewModel.getConditionBucketsCache(), marker.excludedRanges)
        for ((start, end, fullId) in matches) {
            if (fullId in excludedIds) continue
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val type = viewModel.getEntryType(fullId)
                    val key = type?.let { categoryKeyForType(it) } ?: "conditions"
                    val bundle = Bundle().apply {
                        putString("objectId", fullId)
                        putString("categoryKey", key)
                    }
                    findNavController().navigate(R.id.referenceDetail, bundle)
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(android.text.style.ForegroundColorSpan(linkColor()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (link in marker.explicitLinks) {
            val range = link.range
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val type = viewModel.getEntryType(link.fullId)
                    val key = type?.let { categoryKeyForType(it) } ?: "conditions"
                    val bundle = Bundle().apply {
                        putString("objectId", link.fullId)
                        putString("categoryKey", key)
                    }
                    findNavController().navigate(R.id.referenceDetail, bundle)
                }
            }, range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(android.text.style.ForegroundColorSpan(linkColor()), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return TextView(requireContext()).apply {
            setText(spannable)
            movementMethod = LinkMovementMethod.getInstance()
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 0, 0, 8)
        }
    }

    private fun renderMechanic() {
        val obj = viewModel.getMechanic(objectId) ?: return showNotFound()
        binding.toolbar.title = obj.name.get()
        addSection(getString(R.string.reference_description)) { addText(obj.description.get()) }
        obj.table?.let { addSection("Таблица") { addView(createTableView(it)) } }
        obj.description2?.let { addSection("") { addView(buildRichLinkedTextView(it.get())) } }
    }

    private fun renderMonster() {
        val obj = viewModel.getMonster(objectId) ?: return showNotFound()
        binding.toolbar.title = obj.name.get()

        // ── Ключевые характеристики ───────────────────────────────────
        addSection(getString(R.string.reference_quick_stats)) {
            val subtypeStr = obj.subtype?.let { " ($it)" } ?: ""
            val sizeLabel = if (obj.name.get().startsWith("Рой")) {
                UiLocalizer.monsterSizeDetail(obj.size)
            } else {
                UiLocalizer.size(obj.size)
            }
            addRow(getString(R.string.size_label), "$sizeLabel ${UiLocalizer.type(obj.creature_type)}$subtypeStr")
            obj.alignment.takeIf { it.isNotBlank() }?.let { addRow("Мировоззрение", UiLocalizer.alignment(it)) }
            addRow("Класс Защиты", obj.armor_class.toString())
            obj.initiative?.takeIf { it.isNotBlank() }?.let { addRow("Инициатива", it) }
            obj.hit_points.takeIf { it.isNotBlank() }?.let { addRow("Хиты", it) }
            obj.speed?.let { addRow("Скорость", buildSpeedString(it)) }
            addRow("Опасность", "${formatChallengeRating(obj.challenge_rating)} (${formatNumber(obj.xp)} опыта; БВ +${obj.proficiency_bonus})")
        }

        // ── Характеристики ───────────────────────────────────────────
        if (obj.ability_scores.isNotEmpty()) {
            addSection(getString(R.string.reference_ability_scores)) {
                val order = listOf("strength", "dexterity", "constitution", "intelligence", "wisdom", "charisma")
                for (a in order) {
                    val score = obj.ability_scores[a] ?: continue
                    val mod = (score - 10) / 2
                    val modStr = if (mod >= 0) "+$mod" else "$mod"
                    val save = obj.saving_throws?.get(a)?.let { "+$it" } ?: modStr
                    addRow(localizeAbility(a), "$score (мод $modStr; спас $save)")
                }
            }
        }

        // ── Описание ────────────────────────────────────────────────
        if (obj.description.get().isNotBlank()) {
            addSection(getString(R.string.reference_description)) {
                addText(obj.description.get())
            }
        }

        // ── Навыки ───────────────────────────────────────────────────
        val skills = obj.skills
        if (skills != null && skills.isNotEmpty()) {
            addSection(getString(R.string.reference_skills)) {
                val parts = skills.map { (skill, bonus) -> "${UiLocalizer.skill(skill)} +$bonus" }
                addText(parts.joinToString(", "))
            }
        }

        // ── Чувства ──────────────────────────────────────────────────
        val senses = obj.senses
        if (senses != null && senses.isNotEmpty()) {
            addSection("Чувства") {
                addText(senses.entries.joinToString(", ") { (k, v) -> "${UiLocalizer.sense(k)} $v" })
            }
        }

        // ── Сопротивления и иммунитеты ───────────────────────────────
        val resistances = obj.damage_resistances
        if (resistances != null && resistances.isNotEmpty()) {
            addSection("Сопротивление урону") {
                addText(resistances.joinToString(", ") { localizeDamageType(it) })
            }
        }
        val vulnerabilities = obj.damage_vulnerabilities
        if (vulnerabilities != null && vulnerabilities.isNotEmpty()) {
            addSection("Уязвимость к урону") {
                addText(vulnerabilities.joinToString(", ") { localizeDamageType(it) })
            }
        }
        val dmgImmunities = obj.damage_immunities
        if (dmgImmunities != null && dmgImmunities.isNotEmpty()) {
            addSection("Иммунитет к урону") {
                addText(dmgImmunities.joinToString(", ") { localizeDamageType(it) })
            }
        }
        val condImmunities = obj.condition_immunities
        if (condImmunities != null && condImmunities.isNotEmpty()) {
            addSection("Иммунитет к состояниям") {
                addText(condImmunities.joinToString(", ") { UiLocalizer.condition(it) })
            }
        }

        // ── Языки, среда, сокровища ─────────────────────────────────
        obj.languages.takeIf { it.isNotBlank() }?.let { lang ->
            addSection("Языки") { addText(lang) }
        }
        if (obj.environment.isNotEmpty()) {
            addSection("Среда обитания") {
                addText(obj.environment.joinToString(", ") { UiLocalizer.environment(it) })
            }
        }
        obj.treasure?.takeIf { it.isNotBlank() }?.let { tr ->
            addSection("Сокровища") { addText(tr) }
        }
        obj.equipment?.takeIf { it.isNotBlank() }?.let { eq ->
            addSection("Снаряжение") { addView(buildRichLinkedTextView(eq)) }
        }

        // ── Разделы действий ────────────────────────────────────────
        if (obj.traits.isNotEmpty()) {
            addSection(getString(R.string.reference_traits)) {
                for (t in obj.traits) addLinkedFeatureRow(t.name.get(), t.description.get())
            }
        }
        if (obj.actions.isNotEmpty()) {
            addSection(getString(R.string.reference_actions)) {
                for (a in obj.actions) addLinkedFeatureRow(a.name.get(), a.description.get())
            }
        }
        addAbilityBlockSection(getString(R.string.reference_bonus_actions), obj.bonus_actions)
        addAbilityBlockSection(getString(R.string.reference_reactions), obj.reactions)
        addAbilityBlockSection(getString(R.string.reference_legendary_actions), obj.legendary_actions)
        addAbilityBlockSection(getString(R.string.reference_mythic_actions), obj.mythic_actions)
        addAbilityBlockSection(getString(R.string.reference_lair_actions), obj.lair_actions)

        obj.table?.let { addSection("Таблица") { addView(createTableView(it)) } }
        obj.description2?.let { addSection("") { addView(buildRichLinkedTextView(it.get())) } }

        addSourceSection(obj.source)
    }

    private fun addAbilityBlockSection(title: String, abilities: List<MonsterAbility>?) {
        if (abilities.isNullOrEmpty()) return
        addSection(title) {
            for (a in abilities) addLinkedFeatureRow(a.name.get(), a.description.get())
        }
    }

    private fun formatNumber(value: Int): String = java.text.NumberFormat.getInstance().format(value)


    // ─── Helpers ──────────────────────────────────────────────────────

    private fun addSection(title: String, block: LinearLayout.() -> Unit) {
        val renderTarget = activeRenderContainer ?: binding.detailContent
        CardBuilder.addSection(requireContext(), renderTarget, title, block)
    }

    private fun LinearLayout.addRow(label: String, value: String) {
        CardBuilder.addRow(this, label, value)
    }

    private fun LinearLayout.addText(text: String) {
        CardBuilder.addText(this, text)
    }

    private fun LinearLayout.addLinkedItemDescription(description: String, currentName: String) {
        val marker = ItemLinkifier.stripMarkers(description)
        val clean = marker.text
        val spannable = SpannableString.valueOf(clean)

        val combinedMap = HashMap<String, String>(viewModel.getCombinedNameMap())
        combinedMap.keys.removeIf { it == currentName.lowercase() }
        val matches = ItemLinkifier.findRanges(clean, combinedMap, marker.excludedRanges)
        for ((start, end, fullId) in matches) {
            spannable.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        val type = viewModel.getEntryType(fullId)
                        val key = when (type) {
                            "spell" -> "spells"
                            "condition" -> "conditions"
                            else -> "items"
                        }
                        navigateToReference(key, fullId)
                    }
                },
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        for (link in marker.explicitLinks) {
            val range = link.range
            spannable.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        val type = viewModel.getEntryType(link.fullId)
                        val key = when (type) {
                            "spell" -> "spells"
                            "condition" -> "conditions"
                            else -> "items"
                        }
                        navigateToReference(key, link.fullId)
                    }
                },
                range.first,
                range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val tv = TextView(requireContext()).apply {
            this.text = spannable
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 4, 0, 4)
            movementMethod = LinkMovementMethod.getInstance()
        }
        addView(tv)
    }

    private fun buildRichLinkedSpannable(text: String, currentName: String = ""): Spannable {
        val marker = ItemLinkifier.stripMarkers(text)
        val clean = marker.text
        val spannable = SpannableString.valueOf(clean)

        val cache = viewModel.getCombinedBucketsCache()
        val matches = ItemLinkifier.findRanges(clean, cache, marker.excludedRanges)
        for ((start, end, fullId) in matches) {
            spannable.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        val type = viewModel.getEntryType(fullId)
                        val key = when (type) {
                            "spell" -> "spells"
                            "condition" -> "conditions"
                            else -> "items"
                        }
                        navigateToReference(key, fullId)
                    }
                },
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        for (link in marker.explicitLinks) {
            val range = link.range
            spannable.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        val type = viewModel.getEntryType(link.fullId)
                        val key = when (type) {
                            "spell" -> "spells"
                            "condition" -> "conditions"
                            else -> "items"
                        }
                        navigateToReference(key, link.fullId)
                    }
                },
                range.first,
                range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return spannable
    }

    private fun buildRichLinkedTextView(text: String, currentName: String = ""): TextView {
        return TextView(requireContext()).apply {
            this.text = buildRichLinkedSpannable(text, currentName)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 4, 0, 4)
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun LinearLayout.addLinkedIds(ids: List<String>) {
        val spannable = SpannableStringBuilder()
        var first = true
        for (itemId in ids) {
            if (!first) spannable.append(", ") else first = false
            val name = viewModel.resolveName(itemId) ?: itemId
            val start = spannable.length
            spannable.append(name)
            spannable.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) { navigateToItem(itemId) }
                },
                start,
                spannable.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        val tv = TextView(requireContext()).apply {
            text = spannable
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 4, 0, 4)
            movementMethod = LinkMovementMethod.getInstance()
        }
        addView(tv)
    }

    private fun addFeatureRow(name: String, description: String) {
        val renderTarget = activeRenderContainer ?: binding.detailContent
        val nameView = TextView(requireContext()).apply {
            text = name
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setPadding(0, 12, 0, 4)
            setTypeface(null, Typeface.BOLD)
        }
        renderTarget.addView(nameView)
        val descView = TextView(requireContext()).apply {
            text = description
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 0, 0, 8)
        }
        renderTarget.addView(descView)
    }

    /** Adds a titled feature row whose description contains clickable links to items/spells/conditions. */
    private fun addLinkedFeatureRow(name: String, description: String) {
        val renderTarget = activeRenderContainer ?: binding.detailContent
        val nameView = TextView(requireContext()).apply {
            text = name
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setPadding(0, 12, 0, 4)
            setTypeface(null, Typeface.BOLD)
        }
        renderTarget.addView(nameView)
        renderTarget.addView(buildRichLinkedTextView(description))
    }

    private fun addSourceSection(source: SourceInfo) {
        val renderTarget = activeRenderContainer ?: binding.detailContent
        CardBuilder.addSourceSection(requireContext(), renderTarget, getString(R.string.reference_source), source)
    }

    private fun addReferencesSection(references: List<Reference>) {
        if (references.isEmpty()) return
        addSection(getString(R.string.reference_related)) {
            for (ref in references) {
                val name = viewModel.resolveName(ref.id) ?: "[Unknown]"
                val chip = Chip(requireContext()).apply {
                    text = name
                    isClickable = true
                    isCheckable = false
                    setOnClickListener {
                        val type = viewModel.getEntryType(ref.id)
                        if (type != null) {
                            val key = categoryKeyForType(type) ?: return@setOnClickListener
                            val bundle = Bundle().apply {
                                putString("objectId", ref.id)
                                putString("categoryKey", key)
                            }
                            findNavController().navigate(R.id.referenceDetail, bundle)
                        }
                    }
                }
                addView(chip)
            }
        }
    }

    private fun buildBackgroundEquipmentSpannable(option: EquipmentOption): SpannableStringBuilder {
        val sb = SpannableStringBuilder()

        val subItems = option.items
        if (subItems.isNotEmpty()) {
            for ((index, item) in subItems.withIndex()) {
                if (index > 0) sb.append(", ")
                val itemId = item.item_id
                if (itemId != null) {
                    val name = viewModel.resolveName(itemId) ?: itemId
                    val start = sb.length
                    sb.append(name)
                }
            }
        } else if (option.item_id != null) {
            val itemId = option.item_id!!
            val name = viewModel.resolveName(itemId) ?: itemId
            sb.append(name)
        }

        if (option.gold != null) {
            if (sb.isNotEmpty()) sb.append(", ")
            sb.append("${option.gold} ЗМ")
        }

        option.description?.get()?.let { desc ->
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append(desc)
        }
        
        // Apply item links to the entire text using ItemLinkifier.
        val text = sb.toString()
        val itemMap = viewModel.getItemNameMap()
        val matches = ItemLinkifier.findRanges(text, itemMap)
        
        // Remove any existing ClickableSpans to avoid duplicates.
        val existingSpans = sb.getSpans(0, sb.length, ClickableSpan::class.java)
        for (span in existingSpans) {
            sb.removeSpan(span)
        }
        
        // Add new links from ItemLinkifier.
        for ((start, end, fullId) in matches) {
            sb.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        navigateToItem(fullId)
                    }
                },
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return sb
    }

    private fun buildSpeedString(speed: MonsterSpeed?): String {
        if (speed == null) return "—"
        val parts = mutableListOf("${speed.walk} фт")
        speed.fly?.let { parts.add("полёт $it фт${if (speed.hover == true) " (парение)" else ""}") }
        speed.swim?.let { parts.add("плавание $it фт") }
        speed.climb?.let { parts.add("лазание $it фт") }
        speed.burrow?.let { parts.add("копание $it фт") }
        return parts.joinToString(", ")
    }

    private fun categoryKeyForType(type: String): String? = when (type) {
        "class" -> "classes"
        "species" -> "species"
        "background" -> "backgrounds"
        "feat" -> "feats"
        "item" -> "items"
        "condition" -> "conditions"
        "mechanic" -> "mechanics"
        "monster" -> "monsters"
        else -> null
    }

    private fun localizeAbility(ability: String): String = when (ability.lowercase()) {
        "strength" -> getString(R.string.ability_strength)
        "dexterity" -> getString(R.string.ability_dexterity)
        "constitution" -> getString(R.string.ability_constitution)
        "intelligence" -> getString(R.string.ability_intelligence)
        "wisdom" -> getString(R.string.ability_wisdom)
        "charisma" -> getString(R.string.ability_charisma)
        else -> ability.replaceFirstChar { it.uppercase() }
    }

    private fun navigateToItem(itemId: String) {
        val bundle = Bundle().apply {
            putString("objectId", itemId)
            putString("categoryKey", "items")
        }
        findNavController().navigate(R.id.referenceDetail, bundle)
    }

    private fun navigateToReference(categoryKey: String, objectId: String, featureId: String? = null) {
        val bundle = Bundle().apply {
            putString("objectId", objectId)
            putString("categoryKey", categoryKey)
            if (featureId != null) putString("featureId", featureId)
        }
        findNavController().navigate(R.id.referenceDetail, bundle)
    }

    private fun showNotFound() {
        binding.toolbar.title = getString(R.string.reference_not_found)
        CardBuilder.showNotFound(requireContext(), binding.detailContent, getString(R.string.reference_not_found))
    }

    override fun onStop() {
        super.onStop()
        // Save class tab scroll positions
        if (categoryKey == "classes") {
            val currentScrollView = getCurrentScrollView()
            if (currentScrollView != null) {
                val currentPos = binding.classViewPager.currentItem
                classTabScrollPositions[currentPos] = currentScrollView.scrollY
            }
        }
        // Save non-class content scroll position
        contentScrollPosition = binding.contentScroll.scrollY
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save scroll positions for all class tabs as a flat int array: [pos1, scroll1, pos2, scroll2, ...]
        val array = IntArray(classTabScrollPositions.size * 2)
        var i = 0
        for ((pos, scrollY) in classTabScrollPositions) {
            array[i++] = pos
            array[i++] = scrollY
        }
        outState.putIntArray("classTabScrollPositions", array)
        // Save ids of expanded feature cards so they can be restored on back navigation.
        outState.putStringArrayList("openFeatureIds", ArrayList(openFeatureIds))
        outState.putStringArrayList("openInvocationIds", ArrayList(openInvocationIds))
        outState.putStringArrayList("openSchemsIds", ArrayList(openSchemsIds))
        outState.putStringArrayList("openMetamagicIds", ArrayList(openMetamagicIds))
        outState.putStringArrayList("openManeuversIds", ArrayList(openManeuversIds))
        outState.putStringArrayList("openSpeciesTraitIds", ArrayList(openSpeciesTraitIds))
        outState.putStringArrayList("openClassSubclassIds", ArrayList(openClassSubclassIds))
        outState.putInt("contentScrollPosition", binding.contentScroll.scrollY)
    }

    override fun onDestroyView() {
        // Re-enable swipe navigation when leaving class card detail screen
        if (categoryKey == "classes") {
            (requireActivity() as com.herocraft24.core.ui.util.SwipeToggle).setSwipeEnabled(true)
        }
        super.onDestroyView()
        _binding = null
    }
}