package com.example.mj_player_tv.ui

import android.os.Bundle
import android.util.Log
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
import com.example.mj_player_tv.databinding.FragmentCopychannelTocategoryBinding
import com.example.mj_player_tv.ui.adapter.CopySingleChannelToCategoryAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.rubensousa.dpadrecyclerview.spacing.DpadLinearSpacingDecoration
import io.objectbox.Box

@UnstableApi
class CopyChannelToCategoryFragment : Fragment(R.layout.fragment_copychannel_tocategory) {

    private var _binding: FragmentCopychannelTocategoryBinding? = null

    private var copyChannelCategoriesAdapter: CopySingleChannelToCategoryAdapter? = null

    private val tvCatBox: Box<TvCategoryOB> = ObjectBox.store.boxFor(TvCategoryOB::class.java)

    private val chanPosBox: Box<ChannelPositions> =
        ObjectBox.store.boxFor(ChannelPositions::class.java)

    private val binding get() = _binding!!

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
        _binding = FragmentCopychannelTocategoryBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prepareTvCategoriesRecyclerView()

        if (helpViewModel.currentFocusedChannel != null) {
            binding.titleSetting.text =
                "Copy ${helpViewModel.currentFocusedChannel!!.showingName} to following categories:"
            val tvCategoriesQuery = tvCatBox.query(
                TvCategoryOB_.userCategory.equal(true)
            ).build()
            val tvCategories = tvCategoriesQuery.find()
            tvCategoriesQuery.close()

            val filteredCategories =
                tvCategories.filter { it.tvChannelLink.none { it.channel == helpViewModel.currentFocusedChannel!!.idByAccountData } }
            if (filteredCategories.isNotEmpty()) {
                copyChannelCategoriesAdapter?.submitList(filteredCategories)
                binding.rvLayoutTvCategories.requestFocus()
            } else {
                if (tvCategories.isEmpty()) {
                    Toast.makeText(
                        this@CopyChannelToCategoryFragment.requireActivity(),
                        "No user-defined categories available.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@CopyChannelToCategoryFragment.requireActivity(),
                        "TV channel is already in all user-defined categories.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                parentFragmentManager.popBackStack()
            }
        }
    }

        private val onClickListener =
            CopySingleChannelToCategoryAdapter.OnClickListener { tvCategory, isChecked ->
                if (helpViewModel.currentFocusedChannel != null) {
                    if (isChecked) {
                        val totalChannPosForCat = tvCategory.tvChannelLink.count()
                        val channelPosition = ChannelPositions(
                            id = 0,
                            helpViewModel.currentFocusedChannel!!.idByAccountData,
                            tvCategory.playlistId,
                            tvCategory.idByAccountData,
                            totalChannPosForCat,
                            totalChannPosForCat,
                            "${tvCategory.idByAccountData}_${helpViewModel.currentFocusedChannel!!.idByAccountData}",
                            false,
                            true
                        )
                        channelPosition.tvchannel.target = helpViewModel.currentFocusedChannel
                        channelPosition.tvcategory.target = tvCategory
                        chanPosBox.put(channelPosition)
                        val position =
                            copyChannelCategoriesAdapter?.currentList?.indexOf(tvCategory)
                        if (position != null) {
                            copyChannelCategoriesAdapter?.notifyItemChanged(position)
                        }
                        Toast.makeText(
                            this@CopyChannelToCategoryFragment.requireActivity(),
                            "${helpViewModel.currentFocusedChannel!!.showingName} added to: ${tvCategory.showingName}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        tvCategory.tvChannelLink.reset()
                        val channPosToRemove =
                            tvCategory.tvChannelLink.firstOrNull { it.catAndChannelAccount == "${tvCategory.idByAccountData}_${helpViewModel.currentFocusedChannel!!.idByAccountData}" }
                        if (channPosToRemove != null) {
                            chanPosBox.remove(channPosToRemove)
                        }
                        val position =
                            copyChannelCategoriesAdapter?.currentList?.indexOf(tvCategory)
                        if (position != null) {
                            copyChannelCategoriesAdapter?.notifyItemChanged(position)
                        }
                        Toast.makeText(
                            this@CopyChannelToCategoryFragment.requireActivity(),
                            "${helpViewModel.currentFocusedChannel!!.showingName} removed from ${tvCategory.showingName}",
                            Toast.LENGTH_SHORT
                        ).show()

                    }
                }
            }

        private fun prepareTvCategoriesRecyclerView() {
            copyChannelCategoriesAdapter =
                CopySingleChannelToCategoryAdapter(onClickListener, helpViewModel, this)
            binding.rvLayoutTvCategories.apply {
                adapter = copyChannelCategoriesAdapter
                addItemDecoration(
                    DpadLinearSpacingDecoration.create(
                        itemSpacing = 3,
                        edgeSpacing = 3,
                        perpendicularEdgeSpacing = 3
                    )
                )
                setFocusOutAllowed(false, false)
                setFocusOutSideAllowed(false, false)
            }
        }

        fun closeFragment() {
            parentFragmentManager.popBackStack()
        }

        override fun onDestroy() {
            super.onDestroy()
            _binding = null
    }
}