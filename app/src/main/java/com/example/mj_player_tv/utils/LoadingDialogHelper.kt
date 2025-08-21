package com.example.mj_player_tv.utils

import androidx.fragment.app.FragmentManager

object LoadingDialogHelper {
    private var dialog: LoadingDialogFragment? = null

    fun show(fragmentManager: FragmentManager) {
        if (dialog?.isVisible == true) return
        dialog = LoadingDialogFragment()
        dialog?.show(fragmentManager, "loading")
    }

    fun dismiss() {
        dialog?.dismissAllowingStateLoss()
        dialog = null
    }
}
