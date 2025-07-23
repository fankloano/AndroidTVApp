package com.example.mj_player_tv.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.Accounts_
import com.example.mj_player_tv.database.entity.Settings
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvCategoryOB_
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.FragmentCreateCategoryBinding
import com.example.mj_player_tv.databinding.FragmentEditCategoryNameBinding
import com.example.mj_player_tv.databinding.FragmentEditChannelNameBinding
import com.example.mj_player_tv.databinding.FragmentPlaylistsBinding
import com.example.mj_player_tv.ui.settings.AddChannelsToUserCategoryAccountFragment
import com.example.mj_player_tv.ui.settings.AddChannelsToUserCategoryCategoriesFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import io.objectbox.Box
import io.objectbox.kotlin.query
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@UnstableApi
class CreateCategoryFragment : Fragment(R.layout.fragment_create_category) {

    private var _binding: FragmentCreateCategoryBinding? = null

    private val binding get() = _binding!!

    private val tvCatBox: Box<TvCategoryOB> = ObjectBox.store.boxFor(TvCategoryOB::class.java)

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val settingsBox: Box<Settings> = ObjectBox.store.boxFor(Settings::class.java)

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
        _binding = FragmentCreateCategoryBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

            if (helpViewModel.currentFocusedTvAccount!!.isUserCategories) {
                binding.switchAllPlaylists.isChecked = true
            } else {
                binding.switchAllPlaylists.isChecked = false
            }

            binding.etEditedCategoryName.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etEditedCategoryName, InputMethodManager.SHOW_IMPLICIT)

            binding.etEditedCategoryName.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    // Speichern Sie den neuen Namen hier
                    val inputMethodManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.hideSoftInputFromWindow(binding.etEditedCategoryName.windowToken, 0)
                    // true zurückgeben, um zu signalisieren, dass das Ereignis verarbeitet wurde
                    true
                } else {
                    // Wenn der Benutzer die Zurücktaste drückt, ohne auf "Done" zu tippen,
                    // setzen Sie den Namen zurück
                    // false zurückgeben, um zu signalisieren, dass das Ereignis nicht verarbeitet wurde
                    false
                }
            }

            binding.btnSaveCategory.setOnClickListener {
                val alertDialogBuilder = AlertDialog.Builder(requireContext())

                alertDialogBuilder.setMessage("Do you want to add channels to the new category?")

                alertDialogBuilder.setPositiveButton("Yes") { _, _ ->
                    saveNewCategory()
                    if (!binding.switchAllPlaylists.isChecked) {
                        if (helpViewModel.currentFocusedTvAccount!!.isUserCategories) {
                            changeFragment(AddChannelsToUserCategoryAccountFragment())
                        } else {
                            helpViewModel.addChannelsToUserCategoryFromAccount = helpViewModel.currentFocusedTvAccount
                            changeFragment(
                                AddChannelsToUserCategoryCategoriesFragment()
                            )
                        }
                    } else {
                        changeFragment(AddChannelsToUserCategoryAccountFragment())
                    }
                    helpViewModel.addChannelsToUserCategory = true
                }

                alertDialogBuilder.setNegativeButton("No") { dialog, _ ->
                    saveNewCategory()
                    saveAndClose(dialog as AlertDialog)
                }

                val alertDialog = alertDialogBuilder.create()
                alertDialog.show()
            }

            binding.relLayoutAllPlaylists.setOnClickListener {
                binding.switchAllPlaylists.isChecked = !binding.switchAllPlaylists.isChecked
            }
        }

    private fun saveAndClose(dialog: AlertDialog)  {
        dialog.dismiss()
        viewLifecycleOwner.lifecycleScope.launch {
            delay(1000)
            parentFragmentManager.popBackStack()
        }
    }

    private fun saveNewCategory() {
        if (helpViewModel.currentFocusedTvCategory != null) {
            if (binding.switchAllPlaylists.isChecked) {
                val userAccountQuery = accountBox.query(Accounts_.isUserCategories.equal(true)).build()
                val userAccount = userAccountQuery.findFirst()
                userAccountQuery.close()
                if (userAccount != null) {
                    val query = tvCatBox.query {
                        equal(
                            TvCategoryOB_.playlistId,
                            userAccount.id
                        )
                    }
                    val existingUserCategories = query.find().count()
                    query.close()
                    if (existingUserCategories == 0) {
                        userAccount.isSelected = true
                        accountBox.put(userAccount)
                    }
                    val categoryNumber = existingUserCategories + 1
                    val newTvCategory = TvCategoryOB(
                        id = 0,
                        playlistId = userAccount.id,
                        tvCatId = "USER_${categoryNumber}_${userAccount.id}",
                        number = categoryNumber,
                        censored = 0,
                        title = binding.etEditedCategoryName.text.toString(),
                        editedName = binding.etEditedCategoryName.text.toString(),
                        showingName = binding.etEditedCategoryName.text.toString(),
                        accountData = "",
                        favorite = true,
                        orderBy = userAccount.orderBy,
                        idByAccountData = "USER_${categoryNumber}_${userAccount.id}",
                        newCategory = false,
                        userCategory = true,
                        null,
                        null,
                        false,
                        false
                    )
                    newTvCategory.tvaccount.target = userAccount
                    tvCatBox.put(newTvCategory)
                    helpViewModel.categoryToAddChannelsInto = newTvCategory
                }
            } else {
                val currentAccount = accountBox.get(helpViewModel.currentFocusedTvCategory!!.playlistId!!)
                val query = tvCatBox.query {
                    equal(
                        TvCategoryOB_.playlistId,
                        helpViewModel.currentFocusedTvCategory!!.playlistId ?: 0L
                    )
                    equal(TvCategoryOB_.userCategory, true)
                }
                val existingUserCategories = query.find().count()
                query.close()
                val categoryNumber = existingUserCategories + 1
                val newTvCategory = TvCategoryOB(
                    id = 0,
                    playlistId = helpViewModel.currentFocusedTvCategory!!.playlistId,
                    tvCatId = "USER_${categoryNumber}_${helpViewModel.currentFocusedTvCategory!!.playlistId}",
                    number = categoryNumber,
                    censored = 0,
                    title = binding.etEditedCategoryName.text.toString(),
                    editedName = binding.etEditedCategoryName.text.toString(),
                    showingName = binding.etEditedCategoryName.text.toString(),
                    accountData = "",
                    favorite = true,
                    orderBy = currentAccount.orderBy,
                    idByAccountData = "USER_${categoryNumber}_${helpViewModel.currentFocusedTvCategory!!.playlistId}",
                    newCategory = false,
                    userCategory = true,
                    null,
                    null,
                    false,
                    false
                )
                newTvCategory.tvaccount.target = currentAccount
                tvCatBox.put(newTvCategory)
                helpViewModel.categoryToAddChannelsInto = newTvCategory
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun changeFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.container_AssignChannelToEpg, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }
}