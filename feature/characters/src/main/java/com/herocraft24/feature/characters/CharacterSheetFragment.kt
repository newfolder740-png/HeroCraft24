package com.herocraft24.feature.characters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.herocraft24.feature.characters.databinding.FragmentCharacterSheetBinding

class CharacterSheetFragment : Fragment() {

    private var _binding: FragmentCharacterSheetBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by activityViewModels()
    private var charId: String? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentCharacterSheetBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        charId = arguments?.getString("characterId")
        if (charId == null) {
            binding.toolbar.title = "Ошибка"
            return
        }

        val char = vm.getCharacter(charId!!)
        binding.toolbar.findViewById<android.widget.TextView>(R.id.toolbar_title)?.text = char?.name ?: "Персонаж"
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val adapter = SheetPagerAdapter(this, charId!!)
        binding.viewPager.adapter = adapter

        val tabTitles = listOf("Основное", "Умения", "Заклинания", "Инвентарь")
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class SheetPagerAdapter(fragment: Fragment, private val charId: String) : FragmentStateAdapter(fragment) {
        override fun getItemCount() = 4
        override fun createFragment(position: Int): Fragment {
            val args = Bundle().apply { putString("characterId", charId) }
            return when (position) {
                0 -> SheetMainFragment().apply { arguments = args }
                1 -> SheetAbilitiesFragment().apply { arguments = args }
                2 -> SheetSpellsFragment().apply { arguments = args }
                3 -> SheetInventoryFragment().apply { arguments = args }
                else -> SheetMainFragment().apply { arguments = args }
            }
        }
    }
}
