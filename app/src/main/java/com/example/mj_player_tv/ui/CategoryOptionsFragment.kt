package com.example.mj_player_tv.ui

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.ChannelPositions_
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvCategoryOB_
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.database.entity.TvChannelOB_
import com.example.mj_player_tv.databinding.FragmentCategoryOptionsBinding
import com.example.mj_player_tv.databinding.FragmentChannelOptionsBinding
import com.example.mj_player_tv.databinding.FragmentPlaylistsBinding
import com.example.mj_player_tv.ui.settings.AddChannelsToUserCategoryAccountFragment
import com.example.mj_player_tv.ui.settings.AddChannelsToUserCategoryCategoriesFragment
import com.example.mj_player_tv.ui.settings.CategoryChannelSortFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class CategoryOptionsFragment : Fragment(R.layout.fragment_category_options) {

    private var _binding: FragmentCategoryOptionsBinding? = null

    private val binding get() = _binding!!

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val tvChannBox: Box<TvChannelOB> = ObjectBox.store.boxFor(TvChannelOB::class.java)

    private val tvCatBox: Box<TvCategoryOB> = ObjectBox.store.boxFor(TvCategoryOB::class.java)

    private val manualPositionsBox: Box<ChannelPositions> = ObjectBox.store.boxFor(ChannelPositions::class.java)

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
        _binding = FragmentCategoryOptionsBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
            if (containerFragment is TvChannelsFragment) {
                containerFragment.closeCategoryOptionsContainer()
            }
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (helpViewModel.addChannelsToUserCategory) {
            val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
            if (containerFragment is TvChannelsFragment) {
                containerFragment.setFirstFocusToAccounts()
            }
            helpViewModel.isChannelOptionsContainerOpened = false
            helpViewModel.modifiedChannelList = false
            helpViewModel.addChannelsToUserCategory = false
            parentFragmentManager.popBackStack()
        }

        helpViewModel.lastSelectedCategoryOptionsMenuId?.let {
            view.findViewById<TextView>(it).requestFocus()
        }
            ?: binding.relLayoutVisibility.requestFocus()


        if (helpViewModel.currentFocusedTvCategory != null) {
            binding.relLayoutAddChannels.visibility = if (helpViewModel.currentFocusedTvCategory!!.userCategory) {
                View.VISIBLE
            } else {
                View.GONE
            }
            if (helpViewModel.currentFocusedTvCategory!!.userCategory) {
                binding.tvVisibility.text = "Delete Category"
            } else {
                binding.tvVisibility.text = "Hide Category"
            }
            binding.tvCategoryName.text = helpViewModel.currentFocusedTvCategory!!.showingName
            binding.tvIsChannelOrder.text = if (helpViewModel.currentFocusedTvCategory?.orderBy == 2) {
                "Sort manually"
            } else if (helpViewModel.currentFocusedTvCategory?.orderBy == 1) {
                "Sort by Name"
            } else {
                "Sort by Playlist"
            }

        }

        binding.relLayoutVisibility.setOnFocusChangeListener { _, hasFocus ->
            binding.tvVisibility.isSelected = hasFocus
        }

        binding.relLayoutVisibility.setOnFocusChangeListener { _, hasFocus ->
            binding.tvVisibility.isSelected = hasFocus
        }

        binding.relLayoutVisibility.setOnClickListener {
                if (helpViewModel.currentFocusedTvCategory != null) {
                    if (!helpViewModel.currentFocusedTvCategory!!.userCategory) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            helpViewModel.currentFocusedTvCategory!!.favorite = false

                            tvCatBox.put(helpViewModel.currentFocusedTvCategory!!)
                        }
                        val containerFragment =
                            parentFragmentManager.findFragmentById(R.id.navHostFragment)
                        if (containerFragment is TvChannelsFragment) {
                            containerFragment.closeCategoryOptionsContainer()
                        }
                    } else {
                        manualPositionsBox.remove(helpViewModel.currentFocusedTvCategory!!.tvChannelLink)
                        tvCatBox.remove(helpViewModel.currentFocusedTvCategory!!)
                        val containerFragment =
                            parentFragmentManager.findFragmentById(R.id.navHostFragment)
                        if (containerFragment is TvChannelsFragment) {
                            containerFragment.closeCategoryOptionsContainer()
                        }
                    }
                }
            }

        binding.relLayoutEdit.setOnFocusChangeListener { _, hasFocus ->
            binding.tvEditCategory.isSelected = hasFocus
        }

        binding.relLayoutEdit.setOnClickListener {
            helpViewModel.lastSelectedCategoryOptionsMenuId = binding.relLayoutEdit.id
            changeFragment(EditCategoryNameFragment())
        }

        binding.relLayoutChannelOrder.setOnFocusChangeListener { _, hasFocus ->
            binding.tvOrder.isSelected = hasFocus
            binding.tvIsChannelOrder.isSelected = hasFocus
        }

        binding.relLayoutChannelOrder.setOnClickListener {
            helpViewModel.lastSelectedCategoryOptionsMenuId = binding.relLayoutChannelOrder.id
            changeFragment(CategoryChannelSortFragment())
        }

        binding.relLayoutAddChannels.setOnFocusChangeListener { _, hasFocus ->
            binding.tvAddChannelsToCategory.isSelected = hasFocus
        }

        binding.relLayoutAddChannels.setOnClickListener {
            helpViewModel.lastSelectedCategoryOptionsMenuId = binding.relLayoutAddChannels.id
            helpViewModel.categoryToAddChannelsInto = helpViewModel.currentFocusedTvCategory
            if (helpViewModel.currentFocusedTvAccount != null && helpViewModel.currentFocusedTvAccount!!.isUserCategories) {
                changeFragment(AddChannelsToUserCategoryAccountFragment())
            } else {
                changeFragment(AddChannelsToUserCategoryCategoriesFragment())
            }
        }

        binding.relLayoutOrderCategories.setOnFocusChangeListener { _, hasFocus ->
            binding.tvOrderCategories.isSelected = hasFocus
        }

        binding.relLayoutOrderCategories.setOnClickListener {
            helpViewModel.lastSelectedCategoryOptionsMenuId = binding.relLayoutOrderCategories.id
            return@setOnClickListener
        }

        binding.relLayoutEpgTimeOffset.setOnFocusChangeListener { _, hasFocus ->
            binding.tvTimeOffSet.isSelected = hasFocus
        }

        binding.relLayoutCategoryEditor.setOnFocusChangeListener { _, hasFocus ->
            binding.tvCategoryEditor.isSelected = hasFocus
        }

        binding.relLayoutCategoryEditor.setOnClickListener {
            helpViewModel.lastSelectedCategoryOptionsMenuId = binding.relLayoutCategoryEditor.id
            changeFragment(CreateCategoryFragment())
        }
    }

    private fun changeFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.container_AssignChannelToEpg, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    suspend fun checkWasOnlyCategory(categoryOB: TvCategoryOB) : Boolean {
        return withContext(Dispatchers.IO) {
            val account = accountBox.get(categoryOB.playlistId!!)
            account.tvcategories.isEmpty()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        helpViewModel.lastSelectedCategoryOptionsMenuId = null
        _binding = null
    }
}