package com.example.mj_player_tv.ui

import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvCategoryOB_
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.FragmentManageTvcategoriesEditorBinding
import com.example.mj_player_tv.databinding.FragmentManageTvchannelsBinding
import com.example.mj_player_tv.ui.adapter.ManageTvCategoriesEditorAdapter
import com.example.mj_player_tv.ui.adapter.ManageTvChannelsAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class ManageTvCategoriesFragment: Fragment(R.layout.fragment_manage_tvcategories_editor) {

    private var _binding: FragmentManageTvcategoriesEditorBinding? = null

    private val binding get() = _binding!!

    private lateinit var manageTvCategoriesAdapter: ManageTvCategoriesEditorAdapter

    private val tvCatBox: Box<TvCategoryOB> = ObjectBox.store.boxFor(TvCategoryOB::class.java)

    private val helpViewModel: HelpViewModel by activityViewModels {
        HelpViewModelFactory(
            requireActivity().application
        )
    }

    private val stalkerViewModel: StalkerViewModel by activityViewModels {
        StalkerViewModelFactory(
            requireActivity().application
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageTvcategoriesEditorBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Gehe zurück zum vorherigen Fragment im Back Stack

            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        prepareRecyclerView()
        binding.rvLayoutTvCategories.visibility = View.INVISIBLE
        if (helpViewModel.currentFocusedTvAccount != null) {
            helpViewModel.currentFocusedTvAccount?.let {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val categoriesQuery = tvCatBox.query(
                        TvCategoryOB_.playlistId.equal(it.id)
                    ).order(TvCategoryOB_.number).build()
                    val tvcategories = categoriesQuery.find()
                    categoriesQuery.close()
                    if (tvcategories.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            manageTvCategoriesAdapter.submitList(tvcategories)
                            binding.rvLayoutTvCategories.post {
                                val thisCat = manageTvCategoriesAdapter.currentList.firstOrNull { it.id == helpViewModel.currentFocusedTvCategory?.id }
                                val position = manageTvCategoriesAdapter.currentList.indexOf(thisCat)
                                binding.rvLayoutTvCategories.setSelectedPosition(position)
                                binding.rvLayoutTvCategories.visibility = View.VISIBLE
                                binding.rvLayoutTvCategories.requestFocus()
                            }
                        }
                    } else {
                        Toast.makeText(this@ManageTvCategoriesFragment.requireActivity(), "No TV Categories found for ${it.name}", Toast.LENGTH_SHORT).show()
                        delay(500)
                        parentFragmentManager.popBackStack()
                    }
                }
            }
        }

        view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                // Hier fügst du die Logik für die Zurück-Navigation im settings_container hinzu
                // Zum Beispiel:
                val fragmentManager = parentFragmentManager
                if (fragmentManager.backStackEntryCount > 0) {
                    fragmentManager.popBackStack()
                } else {
                    // Wenn es keine vorherigen Einträge gibt, kannst du das Fragment schließen
                    // oder andere Aktionen durchführen.
                }
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }
    }

    private fun prepareRecyclerView() {
        manageTvCategoriesAdapter = ManageTvCategoriesEditorAdapter(listener, this)
        binding.rvLayoutTvCategories.apply {
            adapter = manageTvCategoriesAdapter
            setFocusOutSideAllowed(false, false)
            setFocusOutAllowed(false, false)
        }
    }

    val listener = ManageTvCategoriesEditorAdapter.OnClickListener { tvcategory ->
        if (tvcategory.id == helpViewModel.currentFocusedTvCategory?.id) {

        }
        tvCatBox.put(tvcategory)
        binding.rvLayoutTvCategories.requestFocus()
    }

    fun closeFragment() {
        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun updateChannelList() {
        val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
        if (containerFragment is TvChannelsFragment) {
            containerFragment.updateChannelList()
        }
    }
}