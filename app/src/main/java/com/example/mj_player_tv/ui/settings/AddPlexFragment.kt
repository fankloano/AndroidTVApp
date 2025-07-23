package com.example.mj_player_tv.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.MainActivity
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.Accounts_
import com.example.mj_player_tv.databinding.FragmentAddplexBinding
import com.example.mj_player_tv.databinding.FragmentAddxtreamBinding
import com.example.mj_player_tv.repository.PlaylistLoadProcessState
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.PlexViewModel
import com.example.mj_player_tv.viewmodel.PlexViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import com.rubensousa.dpadrecyclerview.DpadRecyclerView
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@UnstableApi
class AddPlexFragment : Fragment(R.layout.fragment_addplex), View.OnFocusChangeListener {

    private var _binding: FragmentAddplexBinding? = null

    private val binding get() = _binding!!

    private var progressBar: ProgressBar? = null

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val helpViewModel: HelpViewModel by activityViewModels {
        HelpViewModelFactory(
            requireActivity().application
        )
    }

    private val plexViewModel: PlexViewModel by activityViewModels {
        PlexViewModelFactory(
            requireActivity().application
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddplexBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            (requireActivity() as? MainActivity)?.makeAddPlaylistContainerInvisible()
            val containerFragment = parentFragmentManager.findFragmentById(R.id.settings_container)
            if (containerFragment is AddPlaylistFragment) {
                containerFragment.setFocusToLastSelectedMenu()
            }
            plexViewModel.serverList.clear()
            plexViewModel.plexAccountAdded = 0
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (plexViewModel.plexAccountAdded == plexViewModel.serverList.size && plexViewModel.addedAccount) {
            plexViewModel.addedAccount = false
            plexViewModel.serverList.clear()
            plexViewModel.plexAccountAdded = 0
            parentFragmentManager.popBackStack()
        }

        val buttonSave = binding.btnSavePlex
        val password = binding.etPassword
        val plexMail = binding.etPlexmail

        buttonSave.onFocusChangeListener = this


        if (helpViewModel.playlistSuccessFullyAdded) {
            helpViewModel.playlistSuccessFullyAdded = false
            (requireActivity() as? MainActivity)?.makeAddPlaylistContainerInvisible()
            val containerFragment = parentFragmentManager.findFragmentById(R.id.settings_container)
            if (containerFragment is AddPlaylistFragment) {
                containerFragment.setFocusToLastSelectedMenu()
            }
            parentFragmentManager.popBackStack()
        } else {
            binding.etPlexmail.requestFocus()
        }

        buttonSave.setOnClickListener {
            checkUserData(plexMail, password)
        }
    }

    private fun checkUserData(
        plexMailEditText: EditText,
        passwordEditText: EditText,
    ) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val plexMail = plexMailEditText.text.toString().trim()
            val password = passwordEditText.text.toString()


            val emailPattern = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
            if (!plexMail.matches(emailPattern.toRegex())) {
                withContext(Dispatchers.Main) {
                    plexMailEditText.setError("Invalid Mail!")
                }
                return@launch
            } else {
                plexViewModel.getPlexData(plexMail, password)
                changeFragment(PlexLoadProcessFragment())
            }
        }
    }

    private fun changeFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.addPlaylistFragment, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    override fun onFocusChange(p0: View?, p1: Boolean) {
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}