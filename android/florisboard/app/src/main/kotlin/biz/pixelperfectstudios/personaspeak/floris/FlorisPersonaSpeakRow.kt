/*
 * PersonaSpeak — the rewrite row as hosted by the FlorisBoard fork.
 *
 * Licensed under the Apache License, Version 2.0; this file follows the
 * PersonaSpeak module law (first-party code in clearly separated packages).
 */
package biz.pixelperfectstudios.personaspeak.floris

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import biz.pixelperfectstudios.personaspeak.ui.rewrite.RewritePanel

/**
 * The PersonaSpeak row, mounted in FlorisBoard's TextInputLayout between the
 * Smartbar and the keys — the dedicated-row contract from ADR-0007: the
 * host's suggestion row and key rows stay visible and usable in every
 * PersonaSpeak state.
 *
 * Panel state is scoped per input session via the host's session generation:
 * a new field session composes from a fresh ViewModel.
 */
@Composable
fun FlorisPersonaSpeakRow(host: FlorisPersonaSpeakHost) {
    val viewModel = remember(host.sessionGeneration) {
        host.graph.createRewritePanelViewModel()
    }
    val state by viewModel.state.collectAsState()
    val imeView = LocalView.current
    RewritePanel(
        state = state,
        onRewrite = viewModel::request,
        onApply = viewModel::apply,
        onDismiss = viewModel::dismiss,
        onSettings = { host.launchSettings() },
        // Geometry only. The panel freezes this before Review expands the
        // row; the closest host-equivalent of the ASK container height is
        // the full input view height.
        preExpansionImeHeightPx = { imeView.height },
        onOpenPersonaPicker = viewModel::openPersonaPicker,
        onSelectPersona = viewModel::selectPersona,
        onOpenMoodPicker = viewModel::openMoodPicker,
        onSelectMood = viewModel::selectMood,
        onOpenPersonaBrowser = {
            host.launchSettings(FlorisPersonaSpeakSettingsActivity.DESTINATION_PERSONAS)
        },
    )
}
