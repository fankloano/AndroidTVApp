package com.example.mj_player_tv.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvCategoryOB_
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.FragmentAddplaylistBinding
import com.example.mj_player_tv.databinding.FragmentPlaylistChannelSortBinding
import com.example.mj_player_tv.databinding.FragmentPlaylistLogosBinding
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class CategoryChannelSortFragment : Fragment(R.layout.fragment_playlist_channel_sort) {

    private var _binding: FragmentPlaylistChannelSortBinding? = null

    private val binding get() = _binding!!

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

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
        _binding = FragmentPlaylistChannelSortBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Gehe zurück zum vorherigen Fragment im Back Stack
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentCategory = helpViewModel.currentFocusedTvCategory
        if (currentCategory != null) {
            if (currentCategory.orderBy == 0) {
                binding.linLayoutUseSortByPlaylist.requestFocus()
                binding.cbUseSortByPlaylist.isChecked = true
                binding.cbUseSortByPlaylist.isActivated = true
                binding.cbUseSortByName.isChecked = false
                binding.cbUseSortByName.isActivated = false
                binding.cbUseSortByManually.isChecked = false
                binding.cbUseSortByManually.isActivated = false
            } else if (currentCategory.orderBy == 1) {
                binding.linLayoutUseSortByName.requestFocus()
                binding.cbUseSortByPlaylist.isChecked = false
                binding.cbUseSortByPlaylist.isActivated = false
                binding.cbUseSortByName.isChecked = true
                binding.cbUseSortByName.isActivated = true
                binding.cbUseSortByManually.isChecked = false
                binding.cbUseSortByManually.isActivated = false
            } else {
                binding.linLayoutUseSortByManually.requestFocus()
                binding.cbUseSortByPlaylist.isChecked = false
                binding.cbUseSortByPlaylist.isActivated = false
                binding.cbUseSortByName.isChecked = false
                binding.cbUseSortByName.isActivated = false
                binding.cbUseSortByManually.isChecked = true
                binding.cbUseSortByManually.isActivated = true
            }

            // Aktualisiere den Status der Checkboxen basierend auf currentAccount

            binding.linLayoutUseSortByPlaylist.setOnClickListener {
                // Aktualisiere currentAccount und Checkboxen nur, wenn die Checkbox nicht bereits ausgewählt ist
                if (!binding.cbUseSortByPlaylist.isChecked) {
                    currentCategory.orderBy = 0
                    binding.cbUseSortByPlaylist.isChecked = true
                    binding.cbUseSortByPlaylist.isActivated = true
                    binding.cbUseSortByName.isChecked = false
                    binding.cbUseSortByName.isActivated = false
                    binding.cbUseSortByManually.isChecked = false
                    binding.cbUseSortByManually.isActivated = false
                    tvCatBox.put(currentCategory)
                    updateChannelList()
                }
            }

            binding.linLayoutUseSortByName.setOnClickListener {
                // Aktualisiere currentAccount und Checkboxen nur, wenn die Checkbox nicht bereits ausgewählt ist
                if (!binding.cbUseSortByName.isChecked) {
                    currentCategory.orderBy = 1
                    binding.cbUseSortByPlaylist.isChecked = false
                    binding.cbUseSortByPlaylist.isActivated = false
                    binding.cbUseSortByName.isChecked = true
                    binding.cbUseSortByName.isActivated = true
                    binding.cbUseSortByManually.isChecked = false
                    binding.cbUseSortByManually.isActivated = false
                    tvCatBox.put(currentCategory)
                    updateChannelList()
                }
            }

            binding.linLayoutUseSortByManually.setOnClickListener {
                // Aktualisiere currentAccount und Checkboxen nur, wenn die Checkbox nicht bereits ausgewählt ist
                if (!binding.cbUseSortByManually.isChecked) {
                    currentCategory.orderBy = 2
                    binding.cbUseSortByPlaylist.isChecked = true
                    binding.cbUseSortByPlaylist.isActivated = true
                    binding.cbUseSortByName.isChecked = false
                    binding.cbUseSortByName.isActivated = false
                    binding.cbUseSortByManually.isChecked = false
                    binding.cbUseSortByManually.isActivated = false
                    tvCatBox.put(currentCategory)
                    updateChannelList()
                }
            }
        }
    }

    private fun updateChannelList() {
        val channelFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
        if (channelFragment is TvChannelsFragment) {
            channelFragment.updateChannelList()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}