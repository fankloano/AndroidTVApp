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
import com.example.mj_player_tv.repository.PlaylistLoadProcessState
import com.example.mj_player_tv.ui.LoadPlaylistProcessFragment
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@UnstableApi
class AddStalkerFragment : Fragment(R.layout.fragment_addstalker), View.OnFocusChangeListener {

    private var _binding: FragmentAddstalkerBinding? = null

    private val binding get() = _binding!!

    private var progressBar: ProgressBar? = null

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

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
        _binding = FragmentAddstalkerBinding.inflate(inflater, container, false)
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

        val buttonSave = binding.btnSaveStalker
        val buttonMore = binding.btnMoreOptions
        val buttonLess = binding.btnLessOptions
        val addUserAgent = binding.etUserAgent
        val titleUserAgent = binding.tvUseragent
        val macAddress = binding.etMacAddress
        val stalkerUrl = binding.etServerUrl
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
                macAddress.nextFocusDownId = R.id.et_userAgent
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
                macAddress.nextFocusDownId = R.id.btn_saveStalker
            }
        }

        buttonSave.setOnClickListener {
            checkUserData(stalkerUrl, macAddress, playlistName, addUserAgent)
        }
    }

    private fun checkUserData(
        stalkerUrlEditText: EditText,
        macAdressEditText: EditText,
        nameEditText: EditText,
        userAgentEditText: EditText
    ) {
        helpViewModel.addAccount = 1
        var stalkerUrl = stalkerUrlEditText.text.toString().trim()
        val macAddress = macAdressEditText.text.toString().trim().uppercase()
        val name = nameEditText.text.toString().trim()
        var userAgent = userAgentEditText.text.toString().trim()
        val isEmptyPlaylistName =
            stalkerUrl.removePrefix("http://").removeSuffix("/c/").removeSuffix("/")

        // Überprüfung 1: Prüfe, ob die URL mit http:// oder https:// beginnt
        if (!stalkerUrl.startsWith("http://") && !stalkerUrl.startsWith("https://")) {
            stalkerUrlEditText.setError("Invalid Url!")
            return
        } else {
            // Überprüfung 2: Falls die URL mit einem Slash und einem Buchstaben endet, entferne sie
            if (stalkerUrl.endsWith("/[a-zA-Z]/")) {
                stalkerUrl = stalkerUrl.replace("/[a-zA-Z]/", "/")
                if (!stalkerUrl.endsWith("/")) {
                    stalkerUrl += "/"
                    Log.d("LOGIN", "URL :80")
                }
            } else if (!stalkerUrl.endsWith("/")) {
                stalkerUrl += "/"
                Log.d("LOGIN", "URL :80")
            }

            val macPattern = Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")
            if (!macAddress.matches(macPattern)) {
                macAdressEditText.setError("Invalid Mac-Address!")
                Log.d("LOGIN", "MAC INVALID")
                return
            } else {
                if (name.isNotEmpty()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val existingName = async(Dispatchers.IO) {
                            accountBox.query(Accounts_.name.equal(name)).build().findFirst()
                        }.await()
                        if (existingName != null) {
                            nameEditText.setError("Playlist-Name already exists!")
                            return@launch
                        } else {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val existingAccount = async(Dispatchers.IO) {
                                    accountBox.query(Accounts_.totalAccountData.equal("$stalkerUrl$macAddress")).build().findFirst()
                                }.await()
                                if (existingAccount != null) {
                                    Toast.makeText(
                                        this@AddStalkerFragment.requireActivity(),
                                        "Account already existing!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@launch
                                } else {
                                    if (userAgent.isEmpty()) {
                                        userAgent =
                                            "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
                                        stalkerViewModel.getStalkerData(
                                            stalkerUrl,
                                            macAddress,
                                            name,
                                            userAgent
                                        )
                                        changeFragment(LoadPlaylistProcessFragment())
                                    } else {
                                            stalkerViewModel.getStalkerData(
                                                stalkerUrl,
                                                macAddress,
                                                name,
                                                userAgent
                                            )
                                        changeFragment(LoadPlaylistProcessFragment())
                                    }
                                }
                            }
                        }
                    }
                } else {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val existsName = async(Dispatchers.IO) {
                            accountBox.query(Accounts_.name.equal(isEmptyPlaylistName)).build().findFirst()
                        }.await()
                        if (existsName != null) {
                            val newPlaylistName = generateNumberedPlaylistName(isEmptyPlaylistName)
                            val existsAccount = async(Dispatchers.IO) {
                                accountBox.query(Accounts_.totalAccountData.equal("$stalkerUrl$macAddress")).build().findFirst()
                            }.await()
                            if (existsAccount != null) {
                                Toast.makeText(
                                    this@AddStalkerFragment.requireActivity(),
                                    "Account already existing!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                if (userAgent.isEmpty()) {
                                    userAgent =
                                        "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
                                    stalkerViewModel.getStalkerData(
                                        stalkerUrl,
                                        macAddress,
                                        newPlaylistName,
                                        userAgent
                                    )
                                    changeFragment(LoadPlaylistProcessFragment())
                                } else {
                                        stalkerViewModel.getStalkerData(
                                            stalkerUrl,
                                            macAddress,
                                            newPlaylistName,
                                            userAgent
                                        )
                                    changeFragment(LoadPlaylistProcessFragment())
                                }
                            }
                        } else {
                            Log.d("LOGIN", "NAME NOT EXISTING")
                            val existsAccount = async(Dispatchers.IO) {
                                accountBox.query(Accounts_.totalAccountData.equal("$stalkerUrl$macAddress")).build().findFirst()
                            }.await()
                            if (existsAccount != null) {
                                Toast.makeText(
                                    this@AddStalkerFragment.requireActivity(),
                                    "Account already existing!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val newPlaylistName =
                                    generateNumberedPlaylistName(isEmptyPlaylistName)
                                Log.d("LOGIN", "NAME NOT EXISTING NEW: $newPlaylistName")
                                if (userAgent.isEmpty()) {
                                    userAgent =
                                        "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
                                    stalkerViewModel.getStalkerData(
                                        stalkerUrl,
                                        macAddress,
                                        newPlaylistName,
                                        userAgent
                                    )
                                    changeFragment(LoadPlaylistProcessFragment())
                                } else {
                                        stalkerViewModel.getStalkerData(
                                            stalkerUrl,
                                            macAddress,
                                            newPlaylistName,
                                            userAgent
                                        )
                                    changeFragment(LoadPlaylistProcessFragment())
                                }
                            }
                        }
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