/*
 * PersonaSpeak — host glue for the FlorisBoard fork.
 *
 * Licensed under the Apache License, Version 2.0; this file follows the
 * PersonaSpeak module law (first-party code in clearly separated packages).
 */
package biz.pixelperfectstudios.personaspeak.floris

import android.content.Context
import android.content.Intent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import biz.pixelperfectstudios.personaspeak.ime.PersonaSpeakImeGraph

/**
 * One instance per [dev.patrickgold.florisboard.FlorisImeService]. Forwards
 * the framework input lifecycle into the host-neutral PersonaSpeak IME graph
 * and exposes a session-generation counter the row composable keys its
 * ViewModel on, so panel state never survives an input-session boundary.
 *
 * Differs from the ASK host deliberately: FlorisBoard's IME window already
 * installs real Compose view-tree owners (LifecycleInputMethodService), so
 * session isolation comes from re-keying the ViewModel per session rather
 * than from per-session owners.
 */
class FlorisPersonaSpeakHost(
    context: Context,
    inputConnectionSupplier: () -> InputConnection?,
    editorInfoSupplier: () -> EditorInfo?,
) {
    val graph = PersonaSpeakImeGraph(context, inputConnectionSupplier, editorInfoSupplier)

    var sessionGeneration: Int by mutableIntStateOf(0)
        private set

    fun onStartInput() {
        graph.onStartInput()
    }

    fun onStartInputView() {
        graph.resolvingProvider.invalidate()
        sessionGeneration++
    }

    fun onUpdateSelection(newSelStart: Int, newSelEnd: Int) {
        graph.sessionState.selectionChanged(newSelStart, newSelEnd)
    }

    fun onFinishInput() {
        // Bump even without a following onStartInputView so a resumed session
        // always composes from a fresh ViewModel.
        sessionGeneration++
    }

    fun onDestroy() {
        // The graph's ImeViewTreeOwners are never driven in this host —
        // FlorisBoard's own LifecycleInputMethodService supplies Compose
        // owners — so they must not be destroyed here either; tearing down a
        // registry that was never started would throw.
        graph.sessionState.finish()
    }

    fun launchSettings(destination: String = FlorisPersonaSpeakSettingsActivity.DESTINATION_HOME) {
        val intent = FlorisPersonaSpeakSettingsActivity.createIntent(graphContext, destination).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        graphContext.startActivity(intent)
    }

    private val graphContext: Context = context.applicationContext
}
