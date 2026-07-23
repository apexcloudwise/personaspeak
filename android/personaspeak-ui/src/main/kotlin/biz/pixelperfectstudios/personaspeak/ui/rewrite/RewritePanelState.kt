package biz.pixelperfectstudios.personaspeak.ui.rewrite

import biz.pixelperfectstudios.personaspeak.ui.editor.StaleReason

sealed interface RewritePanelState {
    data object Idle : RewritePanelState
    data object Loading : RewritePanelState
    data class Review(
        val candidate: RewriteCandidate,
        val outcome: RewriteOutcome? = null,
    ) : RewritePanelState
    data class Message(val kind: RewriteMessage) : RewritePanelState
}

sealed interface RewriteOutcome {
    data object Applied : RewriteOutcome
    data class Stale(val reason: StaleReason) : RewriteOutcome
    data object Rejected : RewriteOutcome
    data object Unconfirmed : RewriteOutcome
}

sealed interface RewriteMessage {
    data object NoPersona : RewriteMessage {
        override fun toString() = "No persona selected."
    }
    data object EmptyInput : RewriteMessage {
        override fun toString() = "Nothing to rewrite."
    }
    data object SensitiveEditor : RewriteMessage {
        override fun toString() = "Can't rewrite password or private fields."
    }
    data object UnsupportedEditor : RewriteMessage {
        override fun toString() = "This field doesn't support rewriting."
    }
    data object IncompleteRead : RewriteMessage {
        override fun toString() = "Couldn't read the current text."
    }
    data object OversizedInput : RewriteMessage {
        override fun toString() = "Text is too long to rewrite."
    }
    data object ProviderFailure : RewriteMessage {
        override fun toString() = "Rewriting service is unavailable."
    }
    data object MalformedResponse : RewriteMessage {
        override fun toString() = "The rewrite came back empty."
    }
    data object StaleResult : RewriteMessage {
        override fun toString() = "Text changed before the rewrite could be applied."
    }
    data object WriteRejected : RewriteMessage {
        override fun toString() = "The editor rejected the rewrite."
    }
    data object WriteUnconfirmed : RewriteMessage {
        override fun toString() = "Rewrite applied but couldn't be confirmed."
    }
}
