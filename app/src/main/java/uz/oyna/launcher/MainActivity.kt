package uz.oyna.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import uz.oyna.launcher.system.SystemControls
import uz.oyna.launcher.system.WidgetHostManager
import uz.oyna.launcher.ui.LauncherRoot
import uz.oyna.launcher.ui.LauncherState
import uz.oyna.launcher.ui.OynaTheme

class MainActivity : ComponentActivity() {

    private val state = LauncherState()
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            OynaTheme { LauncherRoot(state) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home tugmasi bosilganda (launcher allaqachon ochiq bo'lsa)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME) && resumed) {
            state.onHomePressed()
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { WidgetHostManager.host.startListening() }
    }

    override fun onStop() {
        runCatching { WidgetHostManager.host.stopListening() }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        state.isDefaultLauncher = SystemControls.isDefaultLauncher(this)
    }

    override fun onPause() {
        resumed = false
        state.menu = null
        super.onPause()
    }

    @Deprecated("AppWidget bind/configure oynalari natijasi uchun")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!WidgetHostManager.onActivityResult(this, requestCode, resultCode)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}
