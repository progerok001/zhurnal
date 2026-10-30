package ru.zhurnal.app.data

import android.content.Context

class AppContainer(context: Context) {
    val sessionStore: SessionStore = SessionStore(context.applicationContext)
    val repository: ZhurnalRepository = ZhurnalRepository(sessionStore)
}
