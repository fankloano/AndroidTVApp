package com.example.mj_player_tv.utils

import android.R.attr.gravity
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.example.mj_player_tv.R

class LoadingDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val progressBar = ProgressBar(requireContext()).apply {
            isIndeterminate = true
        }

        val textView = TextView(requireContext()).apply {
            text = "Please wait, loading…"
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20) // Abstand nach unten
        }

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 20)
            addView(textView)
            addView(progressBar)
        }

        return AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(layout)
            .setCancelable(false)
            .create()
    }
}
