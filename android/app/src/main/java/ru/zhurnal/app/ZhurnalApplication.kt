package ru.zhurnal.app

import android.app.Application
import ru.zhurnal.app.data.AppContainer

class ZhurnalApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
