package biz.pixelperfectstudios.personaspeak.app.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import biz.pixelperfectstudios.personaspeak.app.ui.onboarding.AiSelectionScreen
import biz.pixelperfectstudios.personaspeak.app.ui.onboarding.ApiKeyScreen
import biz.pixelperfectstudios.personaspeak.app.ui.onboarding.DemoScreen
import biz.pixelperfectstudios.personaspeak.app.ui.onboarding.SetupScreen
import biz.pixelperfectstudios.personaspeak.app.ui.onboarding.WelcomeScreen

/**
 * The app-module nav graph. One composable destination per [Screen], each
 * rendering a trivial placeholder so the graph compiles and every route is
 * reachable today. Real onboarding/settings screens land in their own worker
 * PRs against the [Screen] contract — they replace the [Placeholder] calls.
 */
@Composable
fun PersonaSpeakNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.startRoute) {
        composable(Screen.OnboardingWelcome.route) {
            WelcomeScreen(
                onGetStarted = { navController.navigate(Screen.OnboardingSetup.route) },
                onSkipSetup = { navController.navigate(Screen.SettingsHome.route) },
            )
        }
        composable(Screen.OnboardingSetup.route) {
            SetupScreen(onContinue = { navController.navigate(Screen.OnboardingAiSelection.route) })
        }
        composable(Screen.OnboardingAiSelection.route) {
            AiSelectionScreen(onContinue = { navController.navigate(Screen.OnboardingApiKey.route) })
        }
        composable(Screen.OnboardingApiKey.route) {
            ApiKeyScreen(
                onContinue = { navController.navigate(Screen.OnboardingDemo.route) },
                onSkip = { navController.navigate(Screen.OnboardingDemo.route) },
            )
        }
        composable(Screen.OnboardingDemo.route) {
            // Completing the demo finishes onboarding: pop the back stack down
            // to the start destination so Back never returns the user into the
            // onboarding flow once they're out.
            DemoScreen(
                onComplete = {
                    navController.navigate(Screen.SettingsHome.route) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Screen.SettingsHome.route) { Placeholder(Screen.SettingsHome) }
        composable(Screen.PersonaBrowser.route) { Placeholder(Screen.PersonaBrowser) }
        composable(
            route = Screen.PersonaDetail.route,
            arguments = listOf(navArgument(Screen.PERSONA_ID_ARG) { type = NavType.StringType }),
        ) { entry ->
            Placeholder(Screen.PersonaDetail, entry.arguments?.getString(Screen.PERSONA_ID_ARG))
        }
        composable(Screen.AiProviders.route) { Placeholder(Screen.AiProviders) }
        composable(Screen.RewriteBehaviour.route) { Placeholder(Screen.RewriteBehaviour) }
        composable(Screen.Privacy.route) { Placeholder(Screen.Privacy) }
    }
}

@Composable
private fun Placeholder(screen: Screen, arg: String? = null) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val label = if (arg != null) "${screen.title} · $arg" else screen.title
            Text(text = "$label\nnot yet built", color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
