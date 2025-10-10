package com.example.mj_player_tv.ui.epg

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.MainActivity
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.help.AccountTvCategory
import com.example.mj_player_tv.databinding.FragmentAccountTvcategoriesBinding
import com.example.mj_player_tv.ui.adapter.AccountTvCategoriesAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.TvGuideViewModel
import com.example.mj_player_tv.viewmodel.TvGuideViewModelFactory
import io.objectbox.Box

@UnstableApi
class AccountTvCategoriesFragment : Fragment(R.layout.fragment_account_tvcategories) {

    private var _binding: FragmentAccountTvcategoriesBinding? = null

    private val binding get() = _binding!!

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val tvCatBox: Box<TvCategoryOB> = ObjectBox.store.boxFor(TvCategoryOB::class.java)

    private var accountsList = mutableListOf<AccountTvCategory>()

    private var displayList = mutableListOf<AccountTvCategory>()

    var isFirstOpenFragment = true
    private var expandedAccountId: Long? = null
    private lateinit var accountTvCategoriesAdapter: AccountTvCategoriesAdapter

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

    private val tvGuideViewModel: TvGuideViewModel by activityViewModels {
        TvGuideViewModelFactory(
            requireActivity().application
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountTvcategoriesBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prepareAccountCategoryRecyclerView()

        helpViewModel.tvAccountsWithCategoriesLiveData.observe(viewLifecycleOwner) { accounts ->
            if (accounts.isEmpty()) {
                // ... Logik für leere Liste (bleibt gleich)
                binding.tvNoTvAccounts.visibility = View.VISIBLE
                binding.rvLayoutTvAccountsMenu.visibility = View.INVISIBLE
                openMainMenu()

            } else {
                binding.tvNoTvAccounts.visibility = View.INVISIBLE
                binding.rvLayoutTvAccountsMenu.visibility = View.VISIBLE

                // 1. **Prüfen, ob der erweiterte Account noch existiert**
                if (expandedAccountId != null) {
                    val isExpandedAccountPresent = accounts.any {
                        it is AccountTvCategory.Account && it.id == expandedAccountId
                    }

                    if (!isExpandedAccountPresent) {
                        // Der Account wurde entfernt, Zustand zurücksetzen!
                        expandedAccountId = null
                        accountTvCategoriesAdapter.submitList(accounts)
                        displayList = accounts.toMutableList()
                    }
                }

                // 2. **Die interne Datenquelle des Fragments (accountsList) aktualisieren**
                // Hier ist es wichtig, die neue Liste zu übernehmen und die DisplayList neu zu generieren.
                if (accountsList != accounts) {
                    accountsList = accounts.toMutableList()
                    accountTvCategoriesAdapter.submitList(accountsList)
                    if (isFirstOpenFragment) {
                        binding.rvLayoutTvAccountsMenu.requestFocus()
                        displayList = accountsList
                        isFirstOpenFragment = false
                    }
                }
            }
        }
    }

    private fun prepareAccountCategoryRecyclerView() {
        accountTvCategoriesAdapter = AccountTvCategoriesAdapter(
            helpViewModel,
            this,
            { account, accountPosition ->
                toggleAccount(account, accountPosition)
            }
        )
        binding.rvLayoutTvAccountsMenu.apply {
            adapter = accountTvCategoriesAdapter
            setFocusOutAllowed(false, false)
            setFocusOutSideAllowed(true, true)
        }
    }


    private fun toggleAccount(account: AccountTvCategory.Account, position: Int) {
        expandedAccountId = if (account.id == expandedAccountId) null else {
            tvGuideViewModel.currentFocusedTvAccount?.let {
                tvGuideViewModel.getEpgForTime(it)
            }
            account.id
        }
        expandCategories()
    }

    private fun expandCategories() {
        val newDisplayList = mutableListOf<AccountTvCategory>()
        accountsList.forEach { item ->
            newDisplayList.add(item) // Account-Header hinzufügen

            // Prüfe, ob dieser Account der erweiterte ist
            if (item is AccountTvCategory.Account && item.id == expandedAccountId) {
                // FÜGE ALLE Kategorien DIREKT darunter in die NEUE Liste ein
                newDisplayList.addAll(item.categories)
            }
        }

        // Weise die NEU ERSTELLTE Liste zu und übermittle sie dem Adapter.
        displayList = newDisplayList
        accountTvCategoriesAdapter.submitList(displayList.toList())
    }


    fun loadChannelsForCategory(tvCategoryId: Long) {
        val tvCategory = tvCatBox.get(tvCategoryId)
        tvGuideViewModel.requestloadChannelsForCategory(tvCategory)
    }

    fun openMainMenu() {
        (requireActivity() as? MainActivity)?.openMenu()
        (requireActivity() as? MainActivity)?.toggleVisibilityOfMainContainer(false)
        (requireActivity() as? MainActivity)?.lastSelectFocus()
    }

    fun focusTvGuide() {
        tvGuideViewModel.requestFocusOnTvGuide()
    }

    fun updateFocusedTvAccount(accountId: Long) {
        val account = accountBox.get(accountId)
        tvGuideViewModel.currentFocusedTvAccount = account
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

}