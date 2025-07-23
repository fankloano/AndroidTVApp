package com.example.mj_player_tv.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.FragmentEditCategoryNameBinding
import com.example.mj_player_tv.databinding.FragmentEditChannelNameBinding
import com.example.mj_player_tv.databinding.FragmentPlaylistsBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@UnstableApi
class EditCategoryNameFragment : Fragment(R.layout.fragment_edit_category_name) {

    private var _binding: FragmentEditCategoryNameBinding? = null

    private val binding get() = _binding!!

    private val tvCatBox: Box<TvCategoryOB> = ObjectBox.store.boxFor(TvCategoryOB::class.java)

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
        _binding = FragmentEditCategoryNameBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val category = tvCatBox.get(helpViewModel.currentFocusedTvCategory!!.id)

        if (category != null) {
            binding.tvOriginalName.text = category.title
            binding.etEditedCategoryName.setText(category.showingName)
            binding.etEditedCategoryName.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etEditedCategoryName, InputMethodManager.SHOW_IMPLICIT)

            binding.etEditedCategoryName.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    // Speichern Sie den neuen Namen hier
                    saveEditedCategoryName(category)
                    val inputMethodManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.hideSoftInputFromWindow(binding.etEditedCategoryName.windowToken, 0)
                    // true zurückgeben, um zu signalisieren, dass das Ereignis verarbeitet wurde
                    true
                } else {
                    // Wenn der Benutzer die Zurücktaste drückt, ohne auf "Done" zu tippen,
                    // setzen Sie den Namen zurück
                    binding.etEditedCategoryName.setText(category.showingName)
                    // false zurückgeben, um zu signalisieren, dass das Ereignis nicht verarbeitet wurde
                    false
                }
            }


            binding.btnResetCategoryName.setOnClickListener {
                val alertDialogBuilder = AlertDialog.Builder(requireContext())

                alertDialogBuilder.setMessage("Reset current category name to original name?")

                alertDialogBuilder.setPositiveButton("Yes") { _, _ ->
                    resetChannelName(category)
                }

                alertDialogBuilder.setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss()
                }

                val alertDialog = alertDialogBuilder.create()
                alertDialog.show()
            }
        }
    }

    private fun saveEditedCategoryName(categoryOB: TvCategoryOB) {
        categoryOB.showingName = binding.etEditedCategoryName.text.toString()
        tvCatBox.put(categoryOB)
    }

    private fun resetChannelName(categoryOB: TvCategoryOB) {
        categoryOB.showingName = categoryOB.title
        tvCatBox.put(categoryOB)
        binding.etEditedCategoryName.setText(categoryOB.showingName)
        binding.etEditedCategoryName.requestFocus()
        binding.etEditedCategoryName.setSelection(binding.etEditedCategoryName.text.length)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}