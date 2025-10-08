package com.example.mj_player_tv.ui.tvguidetest

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.contains
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R

// Custom RecyclerView für die Program Guide EPG
class EpgRecyclerview(
    context: Context,
    attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    private var lastFocusedShowView: View? = null
    private var nextFocusByUpDown: View? = null
    private val focusRange = Rect()
    private var internalKeepCurrentProgramFocused = true

    init {
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

        viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus != nextFocusByUpDown) {
                nextFocusByUpDown = null
            }
            if (EpgUtil.isDescendant(this, newFocus)) {
                lastFocusedShowView = newFocus
            }
        }
    }

    /**
     * Überschreibe Fokus-Suche, um Up/Down gezielt zu kontrollieren
     */
    override fun focusSearch(focused: View?, direction: Int): View? {
        if (focused == null || focused !in this) return super.focusSearch(focused, direction)

        return when (direction) {
            View.FOCUS_UP, View.FOCUS_DOWN -> {
                val next = findNextFocus(focused, direction)
                next ?: super.focusSearch(focused, direction)
            }
            else -> super.focusSearch(focused, direction)
        }
    }

    /**
     * Finde das nächste fokussierbare Programm basierend auf Fokusbereich
     */
    private fun findNextFocus(focused: View, direction: Int): View? {
        val index = indexOfChild(focused)
        if (index == -1) return null
        val nextIndex = if (direction == View.FOCUS_UP) index - 1 else index + 1
        if (nextIndex !in 0 until childCount) return null

        val candidate = getChildAt(nextIndex)?.let {
            EpgUtil.findNextFocusable(it as ViewGroup, focusRange, internalKeepCurrentProgramFocused)
        }

        nextFocusByUpDown = candidate
        return candidate
    }

    /**
     * Fokus auf ein bestimmtes ShowView setzen (nach Layout)
     */
    fun focusShowAfterLayout(showView: View) {
        nextFocusByUpDown = showView
        showView.post {
            if (showView.isShown) {
                showView.requestFocus()
                lastFocusedShowView = showView
            }
        }
    }

    /**
     * Überschreibe requestChildFocus, um Fokus auf GridView-Level zu kontrollieren
     */
    override fun requestChildFocus(child: View, focused: View) {
        super.requestChildFocus(child, focused)
        lastFocusedShowView = focused

        // Nur ausführen, wenn RecyclerView ein LayoutManager hat
        val layoutManager = layoutManager ?: return

        // Position des aktuell fokussierten Channels
        val position = getChildAdapterPosition(child)
        if (position == RecyclerView.NO_POSITION) return

        // Sichtbare Grenzen berechnen
        val topVisible = computeVerticalScrollOffset()
        val visibleHeight = height
        val totalHeight = computeVerticalScrollRange()
        val bottomVisible = topVisible + visibleHeight

        // Grenzen: nicht zentrieren, wenn man ganz oben oder ganz unten ist
        if (position == 0 || bottomVisible >= totalHeight) return

        // Jetzt zentrieren, wenn möglich
        val childCenter = child.top + child.height / 2
        val recyclerCenter = height / 2
        val scrollBy = childCenter - recyclerCenter

        // Nur scrollen, wenn das Kind wirklich außerhalb des Zentrums liegt
        if (scrollBy != 0) {
            smoothScrollBy(0, scrollBy)
        }
    }


    /**
     * Wiederherstellen des letzten Fokus beim erneuten Betreten
     */
    override fun onRequestFocusInDescendants(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        lastFocusedShowView?.let {
            if (it.isShown) return it.requestFocus()
        }
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect)
    }
}
