package com.herocraft24.app

import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.herocraft24.app.databinding.ActivityMainBinding
import com.herocraft24.core.model.AppLocale
import com.herocraft24.core.ui.util.SwipeToggle
import com.herocraft24.feature.characters.CharactersFragment
import com.herocraft24.feature.equipment.EquipmentFragment
import com.herocraft24.feature.reference.ReferenceFragment
import com.herocraft24.feature.settings.SettingsFragment
import com.herocraft24.feature.spells.SpellsFragment

class MainActivity : AppCompatActivity(), SwipeToggle {

    private lateinit var binding: ActivityMainBinding
    private var lastBackPressTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppLocale.current = getSharedPreferences("settings", MODE_PRIVATE)
            .getString("language", "en") ?: "en"

        setTheme(R.style.Theme_HeroCraft24)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up ViewPager2 with adapter
        binding.viewPager.adapter = ViewPagerAdapter(this)

        // Connect TabLayout with ViewPager2 using icons and small text
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.setIcon(R.drawable.ic_person)
                    tab.text = getString(R.string.tab_characters)
                }
                1 -> {
                    tab.setIcon(R.drawable.ic_spells)
                    tab.text = getString(R.string.tab_spells)
                }
                2 -> {
                    tab.setIcon(R.drawable.ic_backpack)
                    tab.text = getString(R.string.tab_equipment)
                }
                3 -> {
                    tab.setIcon(R.drawable.ic_menu_book)
                    tab.text = getString(R.string.tab_reference)
                }
                4 -> {
                    tab.setIcon(R.drawable.ic_settings)
                    tab.text = getString(R.string.tab_settings)
                }
            }
        }.attach()

        // Reset current tab to home when reselecting the same tab
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {}
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                tab?.position?.let { resetCurrentTab(it) }
            }
        })

        // Keep all tabs in memory to avoid lag when switching
        binding.viewPager.offscreenPageLimit = 4

        // Preload all tabs at startup so they're ready instantly
        for (i in 1 until 5) {
            binding.viewPager.setCurrentItem(i, false)
        }
        binding.viewPager.setCurrentItem(0, false)

        // Back press handling with nested navigation support for ViewPager2
        onBackPressedDispatcher.addCallback(this) {
            val currentPosition = binding.viewPager.currentItem
            val fragmentInManager = supportFragmentManager.findFragmentByTag("f$currentPosition")

            val handled = if (fragmentInManager != null) {
                val childFm = fragmentInManager.childFragmentManager
                val nestedHost = childFm.fragments.firstOrNull() as? NavHostFragment
                val navController = nestedHost?.navController

                // If the current destination is a class detail on a non-first tab,
                // let it handle the back press (switch to the first tab).
                val currentFragment = nestedHost?.childFragmentManager?.fragments?.lastOrNull()
                val consumedByClass = (currentFragment as? com.herocraft24.feature.reference.ReferenceDetailFragment)
                    ?.onClassBackPressed() == true

                if (consumedByClass) {
                    true
                } else {
                    navController?.previousBackStackEntry != null && navController.popBackStack()
                }
            } else {
                false
            }

            if (!handled) {
                // We are on the main screen of the current tab — require double-press to exit
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000) {
                    finish()
                } else {
                    lastBackPressTime = now
                    showExitToast()
                }
            }
        }
    }

    private fun showExitToast() {
        android.widget.Toast.makeText(
            this,
            R.string.press_back_again_to_exit,
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    override fun setSwipeEnabled(enabled: Boolean) {
        binding.viewPager.isUserInputEnabled = enabled
    }

    private fun resetCurrentTab(position: Int) {
        val fragmentInManager = supportFragmentManager.findFragmentByTag("f$position") ?: return
        val childFm = fragmentInManager.childFragmentManager
        val nestedHost = childFm.fragments.firstOrNull() as? NavHostFragment ?: return
        val navController = nestedHost.navController

        val startDestId = navController.graph.startDestinationId
        navController.popBackStack(startDestId, true)
        navController.navigate(startDestId)
    }

    private class ViewPagerAdapter(fragmentActivity: AppCompatActivity) :
        FragmentStateAdapter(fragmentActivity) {

        override fun getItemCount(): Int = 5

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> CharactersFragment()
                1 -> SpellsFragment()
                2 -> EquipmentFragment()
                3 -> ReferenceFragment()
                4 -> SettingsFragment()
                else -> throw IllegalArgumentException("Invalid fragment position")
            }
        }
    }
}