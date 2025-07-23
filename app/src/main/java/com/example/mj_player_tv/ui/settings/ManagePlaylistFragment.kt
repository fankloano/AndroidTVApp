package com.example.mj_player_tv.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.databinding.FragmentAddplaylistBinding
import com.example.mj_player_tv.databinding.FragmentManagePlaylistBinding
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.google.android.material.tabs.TabLayout

@UnstableApi
class ManagePlaylistFragment: Fragment(R.layout.fragment_manage_playlist) {

    private var _binding: FragmentManagePlaylistBinding? = null

    private val binding get() = _binding!!
    lateinit var tabLayout: TabLayout

    val TAG_MANAGE_PLAYLIST_FRAGMENT = "MANAGE_PLAYLIST_TAG"

    private val stalkerViewModel: StalkerViewModel by activityViewModels {
        StalkerViewModelFactory(
            requireActivity().application
        )
    }

    val NUM_TABS = 3

    val managelist = arrayListOf("TV", "MOVIES", "SERIES")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManagePlaylistBinding.inflate(inflater, container, false)
        val view = binding.root

        requireActivity().onBackPressedDispatcher.addCallback(this) {
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tabLayout = binding.tabLayout

        for (string in managelist) {
            val tab = tabLayout.newTab()
            tab.text = string
            tabLayout.addTab(tab)
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val position = tab?.position ?: 0
                val fragment: Fragment = when (position) {
                    0 -> ManageTvCategoryFragment()
                    1 -> ManageMovieCategoryFragment()
                    2 -> ManageSeriesCategoryFragment()
                    else -> ManageTvCategoryFragment() // Standard-Fragment, falls Position ungültig ist
                }

                // Öffne das ausgewählte Fragment im FragmentContainerView
                parentFragmentManager.beginTransaction()
                    .replace(R.id.container_manage_playlist, fragment)
                    .addToBackStack(TAG_MANAGE_PLAYLIST_FRAGMENT)
                    .commit()
                // Markiere das ausgewählte Tab manuell
                tabLayout.getTabAt(position)?.select()
            }


            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // Handle tab unselection
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // Handle tab reselection
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}