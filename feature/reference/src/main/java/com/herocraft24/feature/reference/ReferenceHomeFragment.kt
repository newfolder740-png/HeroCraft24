package com.herocraft24.feature.reference

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.herocraft24.feature.reference.databinding.CardCategoryBinding
import com.herocraft24.feature.reference.databinding.FragmentReferenceHomeBinding

class ReferenceHomeFragment : Fragment() {

    private var _binding: FragmentReferenceHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReferenceViewModel by viewModels()

    private val categoryIcons = mapOf(
        "classes" to "\uD83E\uDDD9",
        "species" to "\uD83E\uDDDD",
        "backgrounds" to "\uD83D\uDCDC",
        "feats" to "\u2B50",
        "items" to "\uD83D\uDD2A",
        "conditions" to "\uD83E\uDD15",
        "mechanics" to "\u2694\uFE0F",
        "monsters" to "\uD83D\uDC09",
        "spells" to "\uD83E\uDDD0"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReferenceHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildCategoryCards()
    }

    private fun buildCategoryCards() {
        val container = binding.categoriesContainer
        container.removeAllViews()

        // Exclude items and spells as they have their own bottom navigation tabs
        val excludedCategories = setOf("items", "spells")
        
        for ((key, label) in viewModel.categoryKeys) {
            if (key in excludedCategories) continue
            
            val ids = viewModel.getCategoryIds(key)
            val card = createCategoryCard(key, label, ids.size)
            container.addView(card)
        }
    }

    private fun createCategoryCard(key: String, label: String, count: Int): View {
        val cardView = CardCategoryBinding.inflate(
            LayoutInflater.from(requireContext()), binding.categoriesContainer, false
        )
        cardView.categoryIcon.text = categoryIcons[key] ?: "\uD83D\uDCD6"
        cardView.categoryName.text = label
        cardView.categoryCount.text = "$count объектов"
        cardView.root.setOnClickListener {
            val bundle = Bundle().apply {
                putString("categoryKey", key)
                putString("categoryLabel", label)
            }
            findNavController().navigate(R.id.referenceList, bundle)
        }
        return cardView.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}