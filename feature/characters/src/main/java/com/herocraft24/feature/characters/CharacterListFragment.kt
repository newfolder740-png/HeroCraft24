package com.herocraft24.feature.characters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.herocraft24.core.ui.widget.StateViewBinder
import com.herocraft24.feature.characters.databinding.FragmentCharacterListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CharacterListFragment : Fragment() {

    private var _binding: FragmentCharacterListBinding? = null
    private val binding get() = _binding!!
    private val vm: CharactersViewModel by viewModels({ requireActivity() })
    private lateinit var stateViewBinder: StateViewBinder

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentCharacterListBinding.inflate(i, c, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        stateViewBinder = StateViewBinder(binding.contentContainer)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val adapter = CharListAdapter(
            onItemClick = { char ->
                findNavController().navigate(R.id.characterSheet, Bundle().apply { putString("characterId", char.id) })
            },
            onDelete = { char -> vm.deleteCharacter(char.id) },
            onDuplicate = { char -> vm.duplicateCharacter(char.id) }
        )
        binding.recyclerView.adapter = adapter
        binding.fabAdd.setOnClickListener { findNavController().navigate(R.id.characterCreate) }

        lifecycleScope.launch {
            vm.characters.collectLatest { chars ->
                adapter.submitList(chars)
                if (chars.isEmpty()) stateViewBinder.showEmpty(
                    title = "Нет персонажей", subtitle = "Создайте первого персонажа",
                    actionLabel = "Создать", onAction = { findNavController().navigate(R.id.characterCreate) }
                ) else stateViewBinder.hideAll()
            }
        }
    }

    override fun onDestroyView() { stateViewBinder.release(); super.onDestroyView(); _binding = null }
}

class CharListAdapter(
    val onItemClick: (CharacterData) -> Unit,
    val onDelete: (CharacterData) -> Unit,
    val onDuplicate: (CharacterData) -> Unit
) : RecyclerView.Adapter<CharListAdapter.VH>() {
    private var items = listOf<CharacterData>()
    fun submitList(l: List<CharacterData>) { items = l; notifyDataSetChanged() }
    class VH(v: View) : RecyclerView.ViewHolder(v)

    override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH {
        val tv = TextView(p.context).apply {
            setPadding(48, 32, 48, 32); setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        }
        return VH(tv)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val c = items[pos]
        (h.itemView as TextView).text = "${c.name} — Level ${c.level}"
        h.itemView.setOnClickListener { onItemClick(c) }
        h.itemView.setOnLongClickListener {
            androidx.appcompat.app.AlertDialog.Builder(h.itemView.context)
                .setTitle(c.name)
                .setItems(arrayOf("Duplicate", "Delete")) { _, w ->
                    if (w == 0) onDuplicate(c) else onDelete(c)
                }.show()
            true
        }
    }

    override fun getItemCount() = items.size
}