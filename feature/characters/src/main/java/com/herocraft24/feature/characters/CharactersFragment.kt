package com.herocraft24.feature.characters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.herocraft24.feature.characters.databinding.FragmentCharactersBinding

class CharactersFragment : Fragment() {
    private var _binding: FragmentCharactersBinding? = null
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentCharactersBinding.inflate(i, c, false); return _binding!!.root
    }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}