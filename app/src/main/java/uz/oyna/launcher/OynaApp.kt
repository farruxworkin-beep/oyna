package uz.oyna.launcher

import android.app.Application
import uz.oyna.launcher.data.Apps
import uz.oyna.launcher.data.HomeStore
import uz.oyna.launcher.data.Wallpaper
import uz.oyna.launcher.system.SystemControls
import uz.oyna.launcher.system.WidgetHostManager

class OynaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        HomeStore.init(this)
        SystemControls.init(this)
        WidgetHostManager.init(this)
        Wallpaper.init(this)
        Apps.init(this) // oxirida: yuklangach HomeStore ga xabar beradi
    }
}
