package biz.pixelperfectstudios.personaspeak.app.ui.nav

/**
 * Every destination the app-module nav graph knows about. This is the contract
 * onboarding/settings screen workers build against: [route] is the nav edge,
 * [title] is the placeholder label.
 *
 * Only Activity-hosted screens appear here. IME-window UI (persona strip,
 * persona/mood pickers, result card, error overlays, first-transform
 * celebration) lives in :keyboard and is intentionally absent — those are
 * overlay states inside the InputMethodService, not navigable destinations.
 *
 * Source: docs/superpowers/specs/2026-07-21-stitch-mockups-implementation-spec.md
 * (Sets 1 and 4; Set 6 edge cases that are IME overlays are excluded).
 */
sealed class Screen(val route: String, val title: String) {

    // Onboarding (Set 1)
    data object OnboardingWelcome : Screen("onboarding/welcome", "Welcome")
    data object OnboardingSetup : Screen("onboarding/setup", "Make it your keyboard")
    data object OnboardingAiSelection : Screen("onboarding/ai-selection", "Pick a brain")
    data object OnboardingApiKey : Screen("onboarding/api-key", "Your key, your business")
    data object OnboardingDemo : Screen("onboarding/demo", "Try it")

    // Settings (Set 4)
    data object SettingsHome : Screen("settings/home", "PersonaSpeak")
    data object PersonaBrowser : Screen("settings/persona-browser", "Personas")
    data object PersonaDetail : Screen("settings/persona-detail/{$PERSONA_ID_ARG}", "Persona")
    data object AiProviders : Screen("settings/ai-providers", "AI provider")
    data object RewriteBehaviour : Screen("settings/rewrite-behaviour", "Rewrite behaviour")
    data object Privacy : Screen("settings/privacy", "Privacy")

    companion object {
        const val PERSONA_ID_ARG = "personaId"

        /** Build the persona-detail route for a given persona id. */
        fun personaDetail(personaId: String): String = "settings/persona-detail/$personaId"

        /** First destination a fresh install lands on. */
        val startRoute: String = OnboardingWelcome.route

        /** Every leaf destination, so NavHost registration can't drift from this file. */
        val all: List<Screen> = listOf(
            OnboardingWelcome,
            OnboardingSetup,
            OnboardingAiSelection,
            OnboardingApiKey,
            OnboardingDemo,
            SettingsHome,
            PersonaBrowser,
            PersonaDetail,
            AiProviders,
            RewriteBehaviour,
            Privacy,
        )
    }
}
