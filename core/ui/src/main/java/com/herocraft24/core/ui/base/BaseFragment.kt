package com.herocraft24.core.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

/**
 * Base fragment for all screens in the application.
 *
 * Provides:
 * - ViewBinding support via [setupBinding]
 * - Common toolbar configuration
 * - Consistent lifecycle hooks
 *
 * Subclasses must implement [setupBinding] and [onViewCreated].
 */
abstract class BaseFragment<VB : ViewBinding> : Fragment() {

    private var _binding: VB? = null
    protected val binding: VB
        get() = _binding!!

    protected abstract fun setupBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): VB

    @CallSuper
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = setupBinding(inflater, container)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}