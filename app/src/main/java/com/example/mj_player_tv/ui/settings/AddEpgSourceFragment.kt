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
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgDataOB_
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.database.entity.EpgSource_
import com.example.mj_player_tv.databinding.FragmentAddEpgSourceBinding
import com.example.mj_player_tv.databinding.FragmentAddstalkerBinding
import com.example.mj_player_tv.databinding.FragmentHomeBinding
import com.example.mj_player_tv.repository.ExternEpgProcessState
import com.example.mj_player_tv.repository.PlaylistLoadProcessState
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import io.objectbox.Box
import io.objectbox.kotlin.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@UnstableApi
class AddEpgSourceFragment : Fragment(R.layout.fragment_add_epg_source), View.OnFocusChangeListener {

    private var _binding: FragmentAddEpgSourceBinding? = null

    private val binding get() = _binding!!

    private val epgSourceBox: Box<EpgSource> = ObjectBox.store.boxFor(EpgSource::class.java)
    private val epgDataBox: Box<EpgDataOB> = ObjectBox.store.boxFor(EpgDataOB::class.java)

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
        _binding = FragmentAddEpgSourceBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            parentFragmentManager.popBackStack()
            (requireActivity() as? MainActivity)?.makeAddPlaylistContainerInvisible()
            val containerFragment = parentFragmentManager.findFragmentById(R.id.settings_container)
            if (containerFragment is EpgSourcesFragment) {
                containerFragment.setFocusToFragment()
            }
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val buttonSave = binding.btnSaveEpg
        val epgUrl = binding.etEpgUrl
        val epgName = binding.etEpgsourceName

        buttonSave.onFocusChangeListener = this
        epgUrl.onFocusChangeListener = this
        epgName.onFocusChangeListener = this

        binding.etEpgsourceName.requestFocus()


        buttonSave.setOnClickListener {
            buttonSave.visibility = View.INVISIBLE
            checkUserData(epgUrl, epgName)
            view.requestFocus()
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            helpViewModel.epgProcessState.collect { epgProcessState ->
               withContext(Dispatchers.IO) {
                when (epgProcessState) {
                    is ExternEpgProcessState.Loading -> {
                        withContext(Dispatchers.Main) {
                            binding.tvEpgDownload.text = "Download Epg-Source"
                            binding.tvEpgDownload.visibility = View.VISIBLE
                        }
                    }
                    is ExternEpgProcessState.Parsing -> {
                        withContext(Dispatchers.Main) {
                            binding.tvEpgDownload.text = "STARTE PARSEN"
                        }
                    }
                    is ExternEpgProcessState.ParsingFinished -> {
                        withContext(Dispatchers.Main) {
                            binding.tvEpgDownload.text =
                                "PARSEN FERTIG IN: ${epgProcessState.message} Sekunden"
                        }
                    }
                    is ExternEpgProcessState.EpgDataToDatabaseFinished -> {
                        withContext(Dispatchers.Main) {
                            binding.tvEpgchannels.text =
                                "DATENBANK EINFÜGEN FERTIG IN: ${epgProcessState.message} Sekunden"
                        }
                    }
                    is ExternEpgProcessState.Success -> {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@AddEpgSourceFragment.requireActivity(), "EPG-source added!", Toast.LENGTH_SHORT).show()
                        }
                        updateActualEpgData()
                        helpViewModel.resetEpgProcessState()
                        withContext(Dispatchers.Main) {
                            delay(3000)
                            parentFragmentManager.popBackStack()
                            val containerFragment = parentFragmentManager.findFragmentById(R.id.settings_container)
                            if (containerFragment is EpgSourcesFragment) {
                                containerFragment.setFocusToFragment()
                            }
                        }
                    }

                    is ExternEpgProcessState.Error -> {
                        withContext(Dispatchers.Main) {
                                binding.tvEpgDownload.text = epgProcessState.message
                        }
                        helpViewModel.resetEpgProcessState()
                    }

                    else -> {}
                }
                }
            }
        }
    }

    private fun updateActualEpgData() {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val currentDate = Calendar.getInstance()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                val todayDateString = dateFormat.format(currentDate.time)
                val lastEpg = helpViewModel.actualEpgData
                Log.d("ACTUALEPGSIZE", "FIRST: ${lastEpg.size}")
                currentDate.add(Calendar.DAY_OF_MONTH, 1)
                val tomorrowDateString = dateFormat.format(currentDate.time)
                val actualEpg = epgDataBox.query(
                    EpgDataOB_.datum.equal(todayDateString).or(
                        EpgDataOB_.datum.equal(tomorrowDateString))).build().find()
                helpViewModel.actualEpgData = actualEpg
                Log.d("ACTUALEPGSIZE", "THEN: ${helpViewModel.actualEpgData.size}")
            }
        }
    }

    private fun checkUserData(et_epgUrl: EditText, et_epgName: EditText,
    ) {
        lifecycleScope.launch {
            val allExternalEpgSources = epgSourceBox.query(EpgSource_.isExternalEpg.equal(true)).build().find()
            val epgUrl = et_epgUrl.text.toString().trim()
            var epgName = et_epgName.text.toString().trim()
            if (allExternalEpgSources.any { it.url == epgUrl }) {
                binding.btnSaveEpg.visibility = View.VISIBLE
                et_epgUrl.error = "Epg-source already in use!"
            } else if (!epgUrl.startsWith("http://") && !epgUrl.startsWith("https://")) {
                binding.btnSaveEpg.visibility = View.VISIBLE
                et_epgUrl.error = "Invalid Url!"
            } else {
                if (epgName.isEmpty()) {
                    if (epgUrl.startsWith("https://")) {
                        epgName = epgUrl.removePrefix("https://")
                        setupObservers()
                        helpViewModel.loadEpgDataFromExternalSource(epgUrl, epgName)
                    } else {
                        if (epgUrl.startsWith("http://")) {
                            epgName = epgUrl.removePrefix("http://")
                            setupObservers()
                            helpViewModel.loadEpgDataFromExternalSource(epgUrl, epgName)
                        }
                    }
                } else {
                    Log.d("ADDEPGSOURCE", "EPG NAME: $epgName")
                    val epgNameExistsQuery = epgSourceBox.query(
                        EpgSource_.name.equal(epgName)
                    ).build()
                    val epgNameExists = epgNameExistsQuery.findFirst()
                    epgNameExistsQuery.close()
                    if (epgNameExists == null) {
                        Log.d("ADDEPGSOURCE", "NO NAME: $epgName")
                        setupObservers()
                        helpViewModel.loadEpgDataFromExternalSource(epgUrl, epgName)
                    } else {
                        Log.d("ADDEPGSOURCE", "ALREADY: ${epgNameExists.name}")
                        binding.btnSaveEpg.visibility = View.VISIBLE
                        binding.etEpgsourceName.setError("Name already exists!")
                    }
                }
            }
        }
    }

    override fun onFocusChange(p0: View?, p1: Boolean) {
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.btnSaveEpg.setOnClickListener(null)
        _binding = null
    }
}