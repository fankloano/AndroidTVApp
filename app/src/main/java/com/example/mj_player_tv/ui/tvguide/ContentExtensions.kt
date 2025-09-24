package com.example.mj_player_tv.ui.tvguide

import android.os.SystemClock
import android.view.KeyEvent
import com.example.mj_player_tv.database.entity.EpgDataOB

fun KeyEvent.onLeftPressed() =
    this.action == KeyEvent.ACTION_DOWN && (this.keyCode == KeyEvent.KEYCODE_DPAD_LEFT)

fun KeyEvent.onRightPressed() =
    this.action == KeyEvent.ACTION_DOWN && (this.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)

fun KeyEvent.onUpPressed() =
    this.action == KeyEvent.ACTION_DOWN && this.keyCode == KeyEvent.KEYCODE_DPAD_UP

fun KeyEvent.onDownPressed() =
    this.action == KeyEvent.ACTION_DOWN && this.keyCode == KeyEvent.KEYCODE_DPAD_DOWN

fun KeyEvent.onEnterPressed() =
    this.action == KeyEvent.ACTION_UP && (SystemClock.uptimeMillis() - this.downTime) < 350 && (this.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || this.keyCode == KeyEvent.KEYCODE_ENTER)

fun KeyEvent.onBackPressed() =
    this.action == KeyEvent.ACTION_UP && (SystemClock.uptimeMillis() - this.downTime) < 350 && (this.keyCode == KeyEvent.KEYCODE_BACK)
