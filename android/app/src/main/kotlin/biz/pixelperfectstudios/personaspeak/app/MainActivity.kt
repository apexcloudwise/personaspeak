package biz.pixelperfectstudios.personaspeak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import biz.pixelperfectstudios.personaspeak.app.ui.nav.PersonaSpeakNavHost
import biz.pixelperfectstudios.personaspeak.app.ui.theme.PersonaSpeakTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PersonaSpeakTheme {
                PersonaSpeakNavHost(rememberNavController())
            }
        }
    }
}
