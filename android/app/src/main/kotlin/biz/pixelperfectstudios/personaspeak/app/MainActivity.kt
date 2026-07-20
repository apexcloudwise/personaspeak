package biz.pixelperfectstudios.personaspeak.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Walking-skeleton onboarding: enable the keyboard, switch to it, try it.
 * The real onboarding (provider picker, key entry, persona browser) is
 * GTM Day 7.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Onboarding()
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Onboarding() {
        var tryIt by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("PersonaSpeak", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Three steps to a better class of text message:",
                style = MaterialTheme.typography.bodyLarge,
            )

            Button(onClick = {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }) { Text("1. Enable PersonaBoard") }

            Button(onClick = {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }) { Text("2. Switch keyboards") }

            OutlinedTextField(
                value = tryIt,
                onValueChange = { tryIt = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("3. Try it here — summon the butler") },
            )
        }
    }
}
