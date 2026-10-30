package ru.zhurnal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ru.zhurnal.app.ui.AppViewModel
import ru.zhurnal.app.ui.ZhurnalApp
import ru.zhurnal.app.ui.theme.ZhurnalTheme
import ru.zhurnal.app.widget.WidgetUpdater

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZhurnalTheme {
                ZhurnalApp(appViewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Обновляем виджеты при открытии приложения: после входа данные уже актуальны.
        WidgetUpdater.updateAll(this)
    }
}
