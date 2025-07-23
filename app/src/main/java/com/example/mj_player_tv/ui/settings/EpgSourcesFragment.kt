package com.example.mj_player_tv.ui.settings

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.MainActivity
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.database.entity.Settings
import com.example.mj_player_tv.databinding.FragmentAddplaylistBinding
import com.example.mj_player_tv.databinding.FragmentEpgsourcesBinding
import com.example.mj_player_tv.ui.adapter.EpgSourcesAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.rubensousa.dpadrecyclerview.spacing.DpadLinearSpacingDecoration
import kotlinx.coroutines.launch

@UnstableApi
class EpgSourcesFragment : Fragment(R.layout.fragment_epgsources), View.OnFocusChangeListener {

    private var _binding: FragmentEpgsourcesBinding? = null

    private val binding get() = _binding!!

    val epgSourceBox = ObjectBox.store.boxFor(EpgSource::class.java)
    val settingsBox = ObjectBox.store.boxFor(Settings::class.java)

    private var epgSourcesAdapter: EpgSourcesAdapter? = null

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
        _binding = FragmentEpgsourcesBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Gehe zurück zum vorherigen Fragment im Back Stack
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prepareRecyclerview()
        // Hier kannst du deinen Adapter mit Daten füllen oder aktualisieren

        binding.relLayoutAddepgBtn.onFocusChangeListener = this
        binding.rvLayoutEpgSources.onFocusChangeListener = this

        val settings = helpViewModel.settings
        val epgSources = epgSourceBox.all


        if (settings != null) {

            binding.relLayoutSortBtn.text = if (settings?.epgSourceSorting == 0) {
                "Sorted by: Name"
            } else {
                "Sorted by: Date added"
            }

            if (helpViewModel.lastSelectedEpgSettingId == binding.rvLayoutEpgSources.id) {
                binding.rvLayoutEpgSources.requestFocus()
            } else {
                binding.relLayoutAddepgBtn.requestFocus()
            }


                if (epgSources.isNullOrEmpty()) {
                    binding.rvLayoutEpgSources.isFocusable = false
                    binding.rvLayoutEpgSources.isFocusableInTouchMode = false
                    binding.relLayoutAddepgBtn.nextFocusDownId = R.id.relLayout_addpl_btn
                    binding.relLayoutAddepgBtn.nextFocusUpId = R.id.relLayout_addpl_btn
                } else {
                    val sortedSource =
                        if (settings.epgSourceSorting == 0) {
                            epgSources.sortedBy { it.name }
                                .sortedByDescending { it.isExternalEpg }
                        } else {
                            epgSources.sortedBy { it.id }
                                .sortedByDescending { it.isExternalEpg }
                        }
                    epgSourcesAdapter?.submitList(sortedSource)
                    binding.rvLayoutEpgSources.isFocusable = true
                    binding.rvLayoutEpgSources.isFocusableInTouchMode = true
                }

            binding.relLayoutSortBtn.setOnClickListener {
                if (settings.epgSourceSorting == 0) {
                    settings.epgSourceSorting = 1
                    binding.relLayoutSortBtn.text = "Sorted by: Date added"
                    settingsBox.put(settings)
                    helpViewModel.getSettings()
                    epgSourcesAdapter?.submitList(epgSources.sortedBy { it.id }
                        .sortedByDescending { it.isExternalEpg })
                } else {
                    settings.epgSourceSorting = 0
                    binding.relLayoutSortBtn.text = "Sorted by: Name"
                    settingsBox.put(settings)
                    helpViewModel.getSettings()
                    epgSourcesAdapter?.submitList(epgSources.sortedBy { it.name }
                        .sortedByDescending { it.isExternalEpg })
                }
            }

            binding.relLayoutAddepgBtn.setOnClickListener {
                // Hier wird aufgerufen, wenn die OK-Taste auf der Fernbedienung gedrückt wird
                helpViewModel.lastSelectedEpgSettingId = it.id
                changeAddFragment(AddEpgSourceFragment())
            }
        }
    }

    private val onClickListener = EpgSourcesAdapter.OnClickListener { epgSource, position ->
            helpViewModel.lastSelectedEpgSettingId = binding.rvLayoutEpgSources.id
            helpViewModel.clickedEpgSourceOptions = epgSource
            changeFragment(EpgSourceOptionsFragment())
    }

    override fun onFocusChange(view: View?, hasFocus: Boolean) {
        // Hier wird aufgerufen, wenn sich der Fokus auf einem Menüpunkt ändert
        if (hasFocus) {
            if (view != null) {
            }
        }
    }

    private fun changeAddFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.addPlaylistFragment, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
        (requireActivity() as? MainActivity)?.makeAddPlaylistContainerVisible()
    }

    private fun prepareRecyclerview() {
        epgSourcesAdapter = EpgSourcesAdapter(onClickListener, helpViewModel)
        binding.rvLayoutEpgSources.apply {
            adapter = epgSourcesAdapter
            addItemDecoration(
                DpadLinearSpacingDecoration.create(
                    itemSpacing = 3,
                    edgeSpacing = 3,
                    perpendicularEdgeSpacing = 3
                )
            )
            setFocusOutAllowed(true, false)
            setFocusOutSideAllowed(false, false)
            nextFocusUpId = R.id.relLayout_addpl_btn
        }
    }

    private fun changeFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.settings_container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    fun setFocusToFragment() {
        binding.relLayoutAddepgBtn.requestFocus()
        viewLifecycleOwner.lifecycleScope.launch {
            val epgSources = epgSourceBox.all
            if (epgSources.isNullOrEmpty()) {
                binding.rvLayoutEpgSources.isFocusable = false
                binding.rvLayoutEpgSources.isFocusableInTouchMode = false
                binding.relLayoutAddepgBtn.nextFocusDownId = R.id.relLayout_addpl_btn
                binding.relLayoutAddepgBtn.nextFocusUpId = R.id.relLayout_addpl_btn
            } else {
                val settings = helpViewModel.settings
                if (settings != null) {
                    val sortedSource =
                        if (settings.epgSourceSorting == 0) {
                            epgSources.sortedBy { it.name }.sortedByDescending { it.isExternalEpg }
                        } else {
                            epgSources.sortedBy { it.id }.sortedByDescending { it.isExternalEpg }
                        }
                    epgSourcesAdapter?.submitList(sortedSource)
                    binding.rvLayoutEpgSources.isFocusable = true
                    binding.rvLayoutEpgSources.isFocusableInTouchMode = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvLayoutEpgSources.adapter = null
        epgSourcesAdapter = null
        _binding = null
    }
}