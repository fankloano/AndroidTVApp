package com.example.mj_player_tv.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Settings
import com.example.mj_player_tv.databinding.FragmentModifyVodcategoryPreSuffixesBinding
import com.example.mj_player_tv.databinding.FragmentPlaylistsBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModifyVodCategoriesNamesFragment : Fragment(R.layout.fragment_modify_vodcategory_pre_suffixes) {

    private var _binding: FragmentModifyVodcategoryPreSuffixesBinding? = null

    private val binding get() = _binding!!

    private val settingsBox = ObjectBox.store.boxFor(Settings::class.java)

    private val stalkerViewModel: StalkerViewModel by activityViewModels {
        StalkerViewModelFactory(
            requireActivity().application
        )
    }

    private val helpViewModel: HelpViewModel by activityViewModels {
        HelpViewModelFactory(
            requireActivity().application
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentModifyVodcategoryPreSuffixesBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            val settingsAll = withContext(Dispatchers.IO) { settingsBox.all }
            val settings = settingsAll.firstOrNull()
            if (settings != null) {
                binding.editTextPrefixes.setText(settings.moviecategoryPrefixes.joinToString(","))
                binding.editTextSuffixes.setText(settings.moviecategorySuffixes.joinToString(","))
                binding.editTextPrefixes.requestFocus()
                binding.editTextPrefixes.setSelection(binding.editTextPrefixes.text.length)
            }
        }


        binding.btnSavePrefixesSuffixes.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                binding.editTextPrefixes.requestFocus()
                binding.editTextPrefixes.setSelection(binding.editTextPrefixes.text.length)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                binding.editTextSuffixes.requestFocus()
                binding.editTextSuffixes.setSelection(binding.editTextSuffixes.text.length)
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.editTextPrefixes.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                binding.editTextSuffixes.requestFocus()
                binding.editTextSuffixes.setSelection(binding.editTextSuffixes.text.length)
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.editTextSuffixes.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                binding.editTextPrefixes.requestFocus()
                binding.editTextPrefixes.setSelection(binding.editTextPrefixes.text.length)
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.btnSavePrefixesSuffixes.setOnClickListener {
            saveNewPreAndSuffixes()
        }
    }

    private fun changeFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.settings_container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun saveNewPreAndSuffixes() {
        viewLifecycleOwner.lifecycleScope.launch {
            val settingsAll = withContext(Dispatchers.IO) { settingsBox.all }
            val settings = settingsAll.firstOrNull()
            if (settings != null) {
                val newPrefixes =
                    binding.editTextPrefixes.text.toString().split(",").filter { it.isNotBlank() }
                val newSuffixes =
                    binding.editTextSuffixes.text.toString().split(",").filter { it.isNotBlank() }

                settings.moviecategoryPrefixes.clear()
                settings.moviecategoryPrefixes = newPrefixes.toMutableList()
                settings.moviecategorySuffixes.clear()
                settings.moviecategorySuffixes = newSuffixes.toMutableList()
                withContext(Dispatchers.IO) { settingsBox.put(settings) }

                helpViewModel.updatePrefixesAndSuffixesMovieCategories(settings.moviecategoryPrefixes, settings.moviecategorySuffixes)
                parentFragmentManager.popBackStack()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}