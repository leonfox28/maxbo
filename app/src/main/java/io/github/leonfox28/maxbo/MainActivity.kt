package io.github.leonfox28.maxbo

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        // The UI is Chinese only, so the system's own strings (Compose's
        // accessibility labels, the date picker and the like) are Chinese
        // too, whatever the system language. Only the locale is overridden:
        // the rest of the configuration still follows the system as it
        // changes. (Configuration() sets the font scale to 1 before Android 8,
        // so it's unset again here.)
        applyOverrideConfiguration(
            Configuration().apply {
                fontScale = 0f
                setLocales(LocaleList(Locale.SIMPLIFIED_CHINESE))
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as MaxboApplication).graph
        setContent { MaxboApp(graph) }
    }
}
