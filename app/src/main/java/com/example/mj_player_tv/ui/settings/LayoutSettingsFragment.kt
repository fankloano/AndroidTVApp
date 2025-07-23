package com.example.mj_player_tv.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.Accounts_
import com.example.mj_player_tv.database.entity.Settings
import com.example.mj_player_tv.databinding.FragmentAddplaylistBinding
import com.example.mj_player_tv.databinding.FragmentSettingsLayoutBinding
import com.example.mj_player_tv.ui.AddTmdbApiKeyFragment
import com.example.mj_player_tv.ui.ModifyChannelNamesFragment
import com.example.mj_player_tv.ui.ModifyVodCategoriesNamesFragment
import com.example.mj_player_tv.ui.ModifyTvCategoriesNamesFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import io.objectbox.Box

@UnstableApi
class LayoutSettingsFragment : Fragment(R.layout.fragment_settings_layout) {

    private var _binding: FragmentSettingsLayoutBinding? = null

    private val binding get() = _binding!!

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

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
        _binding = FragmentSettingsLayoutBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Gehe zurück zum vorherigen Fragment im Back Stack
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        helpViewModel.lastSelectedLayoutSettingId?.let {
            view.findViewById<TextView>(it).requestFocus()
        }
            ?: binding.btnLanguage.requestFocus()

        val userAccount =
            accountBox.query(Accounts_.isUserCategories.equal(true)).build().findFirst()

        if (userAccount != null) {
            binding.switchAllPlaylists.isChecked = userAccount.isSelected

            binding.relLayoutAllPlaylists.setOnClickListener {
                if (binding.switchAllPlaylists.isChecked) {
                    binding.switchAllPlaylists.isChecked = false
                    userAccount.isSelected = false
                } else {
                    binding.switchAllPlaylists.isChecked = true
                    userAccount.isSelected = true
                }
                accountBox.put(userAccount)
            }


            binding.btnModifyChannelNames.setOnClickListener {
                helpViewModel.lastSelectedLayoutSettingId = it.id
                changeFragment(ModifyChannelNamesFragment())
            }

            binding.btnModifyTvCategories.setOnClickListener {
                helpViewModel.lastSelectedLayoutSettingId = it.id
                changeFragment(ModifyTvCategoriesNamesFragment())
            }

            binding.btnModifyVodCategories.setOnClickListener {
                helpViewModel.lastSelectedLayoutSettingId = it.id
                changeFragment(ModifyVodCategoriesNamesFragment())
            }

            binding.btnTmdb.setOnClickListener {
                changeFragment(AddTmdbApiKeyFragment())
            }

            binding.btnVodplayer.setOnClickListener {
                changeFragment(VodPlayerSelectionFragment())
            }
        }
    }

    private fun changeFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.settings_container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}