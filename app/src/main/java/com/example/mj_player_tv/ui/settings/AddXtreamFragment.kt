package com.example.mj_player_tv.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.util.Log
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
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.MainActivity
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.Accounts_
import com.example.mj_player_tv.databinding.FragmentAddplaylistBinding
import com.example.mj_player_tv.databinding.FragmentAddstalkerBinding
import com.example.mj_player_tv.databinding.FragmentAddxtreamBinding
import com.example.mj_player_tv.repository.PlaylistLoadProcessState
import com.example.mj_player_tv.ui.LoadPlaylistProcessFragment
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@UnstableApi
class AddXtreamFragment : Fragment(R.layout.fragment_addxtream), View.OnFocusChangeListener {

    private var _binding: FragmentAddxtreamBinding? = null

    private val binding get() = _binding!!

    private var progressBar: ProgressBar? = null

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val helpViewModel: HelpViewModel by activityViewModels {
        HelpViewModelFactory(
            requireActivity().application
        )
    }

    private val xtreamViewModel: XtreamViewModel by activityViewModels {
        XtreamViewModelFactory(
            requireActivity().application
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddxtreamBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            (requireActivity() as? MainActivity)?.makeAddPlaylistContainerInvisible()
            val containerFragment = parentFragmentManager.findFragmentById(R.id.settings_container)
            if (containerFragment is AddPlaylistFragment) {
                containerFragment.setFocusToLastSelectedMenu()
            }
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val buttonSave = binding.btnSaveXtream
        val buttonMore = binding.btnMoreOptions
        val buttonLess = binding.btnLessOptions
        val addUserAgent = binding.etUserAgent
        val titleUserAgent = binding.tvUseragent
        val password = binding.etPassword
        val username = binding.etUsername
        val serverurl = binding.etServerUrl
        val playlistName = binding.etPlaylistName

        buttonSave.onFocusChangeListener = this
        buttonMore.onFocusChangeListener = this
        buttonLess.onFocusChangeListener = this

        if (helpViewModel.playlistSuccessFullyAdded) {
            helpViewModel.playlistSuccessFullyAdded = false
            (requireActivity() as? MainActivity)?.makeAddPlaylistContainerInvisible()
            val containerFragment = parentFragmentManager.findFragmentById(R.id.settings_container)
            if (containerFragment is AddPlaylistFragment) {
                containerFragment.setFocusToLastSelectedMenu()
            }
            parentFragmentManager.popBackStack()
        } else {
            binding.etPlaylistName.requestFocus()
        }


        buttonMore.setOnClickListener {
            lifecycleScope.launch {
                delay(500L)
                addUserAgent.visibility = View.VISIBLE
                titleUserAgent.visibility = View.VISIBLE
                buttonLess.visibility = View.VISIBLE
                buttonMore.visibility = View.INVISIBLE
                addUserAgent.requestFocus()
                password.nextFocusDownId = R.id.et_userAgent
            }
        }

        buttonLess.setOnClickListener {
            lifecycleScope.launch {
                delay(500L)
                addUserAgent.visibility = View.GONE
                titleUserAgent.visibility = View.GONE
                buttonLess.visibility = View.INVISIBLE
                buttonMore.visibility = View.VISIBLE
                buttonMore.requestFocus()
                password.nextFocusDownId = R.id.btn_saveStalker
            }
        }

        buttonSave.setOnClickListener {
            checkUserData(serverurl, username, password, playlistName, addUserAgent)
        }
    }


    private fun checkUserData(
        serverUrlEditText: EditText,
        usernameEditText: EditText,
        passwordEditText: EditText,
        nameEditText: EditText,
        userAgentEditText: EditText
    ) {
        helpViewModel.addAccount = 0
        var serverUrl = serverUrlEditText.text.toString().trim()
        val userName = usernameEditText.text.toString()
        val password = passwordEditText.text.toString()
        val name = nameEditText.text.toString().trim()
        var userAgent = userAgentEditText.text.toString().trim()

        // Überprüfung 1: Prüfe, ob die URL mit http:// oder https:// beginnt
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            serverUrlEditText.setError("Invalid Url!")
            return
        } else {
            // Überprüfung 2: Falls die URL mit einem Slash und einem Buchstaben endet, entferne sie
            serverUrl = if (serverUrl.endsWith("/")) {
                serverUrl.removeSuffix("/")
            } else {
                serverUrl
            }
            if (name.isNotEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val existingName = async(Dispatchers.IO) {
                        accountBox.query(Accounts_.name.equal(name)).build().findFirst()
                    }.await()
                    if (existingName != null) {
                        nameEditText.setError("Playlist-Name already exists!")
                        return@launch
                    } else {
                        val isAlreadyAddedQuery = accountBox.query(
                            Accounts_.stalkerUrl.equal(serverUrl)
                                .and(
                                    Accounts_.username.equal(userName)
                                        .and(Accounts_.macAddress.equal(password))
                                )
                        ).build()
                        val isAlreadyAdded = isAlreadyAddedQuery.findFirst()
                        if (isAlreadyAdded != null) {
                            Toast.makeText(
                                this@AddXtreamFragment.requireActivity(),
                                "Account already existing!",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        } else {
                            userAgent = if (userAgent.isEmpty()) {
                                "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
                            } else {
                                userAgent
                            }
                                xtreamViewModel.getXtreamData(
                                    serverUrl,
                                    userName,
                                    password,
                                    name,
                                    userAgent
                                )
                            changeFragment(LoadPlaylistProcessFragment())
                        }
                    }
                }
            } else {
                viewLifecycleOwner.lifecycleScope.launch {
                    val emptyPlaylistName = serverUrl.removePrefix("http://").trim()
                    val existingName = async(Dispatchers.IO) {
                        accountBox.query(Accounts_.name.equal(emptyPlaylistName)).build()
                            .findFirst()
                    }.await()
                    if (existingName != null) {
                        val newPlaylistName = generateNumberedPlaylistName(emptyPlaylistName)
                        userAgent = if (userAgent.isEmpty()) {
                            "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
                        } else {
                            userAgent
                        }
                            xtreamViewModel.getXtreamData(
                                serverUrl,
                                userName,
                                password,
                                newPlaylistName,
                                userAgent
                            )
                        changeFragment(LoadPlaylistProcessFragment())
                    } else {
                        userAgent = if (userAgent.isEmpty()) {
                            "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
                        } else {
                            userAgent
                        }
                            xtreamViewModel.getXtreamData(
                                serverUrl,
                                userName,
                                password,
                                emptyPlaylistName,
                                userAgent
                            )
                        changeFragment(LoadPlaylistProcessFragment())
                    }
                }
            }
        }
    }

    private suspend fun generateNumberedPlaylistName(name: String): String =
        withContext(Dispatchers.IO) {
            var numberedName = name
            var counter = 2
            while (accountBox.query(Accounts_.name.equal(numberedName)).build().findFirst() != null) {
                numberedName = "${name}_$counter"
                counter++
            }
            numberedName
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