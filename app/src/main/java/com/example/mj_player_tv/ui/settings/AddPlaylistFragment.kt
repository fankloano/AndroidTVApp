package com.example.mj_player_tv.ui.settings


import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.MainActivity
import com.example.mj_player_tv.R
import com.example.mj_player_tv.databinding.FragmentAddplaylistBinding
import com.example.mj_player_tv.databinding.FragmentHomeBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory

@UnstableApi
class AddPlaylistFragment : Fragment(R.layout.fragment_addplaylist) {

    private var _binding: FragmentAddplaylistBinding? = null

    private val binding get() = _binding!!

    private var lastSelectedMenu: View? = null

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
        _binding = FragmentAddplaylistBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lastSelectedMenu?.requestFocus() ?: binding.btnAddXtream.requestFocus()

        binding.btnAddXtream.setOnClickListener {
            lastSelectedMenu = binding.btnAddXtream
            changeFragment(AddXtreamFragment())
        }

        binding.btnAddStalker.setOnClickListener {
            lastSelectedMenu = binding.btnAddStalker
            changeFragment(AddStalkerFragment())
        }

        binding.btnAddplex.setOnClickListener {
            lastSelectedMenu = binding.btnAddplex
            changeFragment(AddPlexFragment())
        }

        view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                // Hier fügst du die Logik für die Zurück-Navigation im settings_container hinzu
                // Zum Beispiel:
                val fragmentManager = parentFragmentManager
                if (fragmentManager.backStackEntryCount > 0) {
                    fragmentManager.popBackStack()
                } else {
                    // Wenn es keine vorherigen Einträge gibt, kannst du das Fragment schließen
                    // oder andere Aktionen durchführen.
                }
                return@setOnKeyListener true
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }
    }

    private fun changeFragment(fragment: Fragment) {
            val transaction = parentFragmentManager.beginTransaction()
            transaction.replace(R.id.addPlaylistFragment, fragment)
            transaction.addToBackStack(null)
            transaction.commit()
            (requireActivity() as? MainActivity)?.makeAddPlaylistContainerVisible()
    }

    fun setFocusToLastSelectedMenu() {
        lastSelectedMenu?.requestFocus() ?: binding.btnAddXtream.requestFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}