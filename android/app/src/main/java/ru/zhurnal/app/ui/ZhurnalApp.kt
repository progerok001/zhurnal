package ru.zhurnal.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.zhurnal.app.BuildConfig
import ru.zhurnal.app.data.UserDto
import ru.zhurnal.app.data.ZhurnalRepository
import ru.zhurnal.app.ui.components.BrandMark
import ru.zhurnal.app.ui.components.ScreenBackground
import ru.zhurnal.app.ui.screens.AdminScreen
import ru.zhurnal.app.ui.screens.AuditScreen
import ru.zhurnal.app.ui.screens.AuthScreen
import ru.zhurnal.app.ui.screens.GroupsScreen
import ru.zhurnal.app.ui.screens.JournalScreen
import ru.zhurnal.app.ui.screens.HomeworkScreen
import ru.zhurnal.app.ui.screens.HomeworkTopScreen
import ru.zhurnal.app.ui.screens.ProfileScreen
import ru.zhurnal.app.ui.screens.ScheduleScreen
import ru.zhurnal.app.ui.screens.ServerSetupScreen
import ru.zhurnal.app.ui.screens.SubstitutionsScreen
import ru.zhurnal.app.ui.screens.UpdateScreen

@Composable
fun ZhurnalApp(appVm: AppViewModel) {
    val sessionState by appVm.session.collectAsStateWithLifecycle()
    val authState by appVm.auth.collectAsStateWithLifecycle()
    val updateState by appVm.update.collectAsStateWithLifecycle()

    when (val state = sessionState) {
        SessionState.Loading -> SplashScreen()
        is SessionState.Ready -> {
            val session = state.session
            val baseUrl = session.baseUrl
            val user = session.user
            when {
                baseUrl.isNullOrBlank() -> ServerSetupScreen(
                    appVm = appVm,
                    initialUrl = "",
                    onBack = null,
                )
                updateState.checking -> SplashScreen()
                updateState.update != null -> UpdateScreen(
                    state = updateState,
                    currentVersionName = BuildConfig.VERSION_NAME.removeSuffix("-debug"),
                    onInstall = appVm::installUpdate,
                    onRetryCheck = appVm::checkForUpdate,
                    onOpenInstallSettings = appVm::openInstallSettings,
                )
                session.token.isNullOrBlank() || user == null -> AuthScreen(
                    appVm = appVm,
                    baseUrl = baseUrl,
                    authState = authState,
                )
                else -> MainNav(appVm = appVm, user = user)
            }
        }
    }
}

@Composable
private fun MainNav(appVm: AppViewModel, user: UserDto) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "groups",
    ) {
        composable("groups") {
            GroupsScreen(
                user = user,
                repository = appVm.repository,
                onOpenGroup = { groupId -> navController.navigate("journal/$groupId") },
                onAdmin = { navController.navigate("admin") },
                onProfile = { navController.navigate("profile") },
                onSubstitutions = { navController.navigate("substitutions") },
                onSchedule = { navController.navigate("schedule") },
            )
        }
        composable("substitutions") {
            SubstitutionsScreen(
                repository = appVm.repository,
                onBack = { navController.popBackStack() },
            )
        }
        composable("schedule") {
            ScheduleScreen(
                repository = appVm.repository,
                ownGroupId = user.groupId,
                onBack = { navController.popBackStack() },
                onOpenHomework = { groupId, subjectId, lessonDate ->
                    navController.navigate("homework/$groupId/$subjectId?date=$lessonDate")
                },
                onOpenTop = { groupId ->
                    navController.navigate("homework-top/$groupId")
                },
            )
        }
        composable(
            route = "homework/{groupId}/{subjectId}?date={date}",
            arguments = listOf(
                navArgument("groupId") { type = NavType.IntType },
                navArgument("subjectId") { type = NavType.IntType },
                navArgument("date") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val groupId = entry.arguments?.getInt("groupId")
            val subjectId = entry.arguments?.getInt("subjectId")
            val date = entry.arguments?.getString("date")?.takeIf { it.isNotBlank() }
            if (groupId != null && subjectId != null) {
                HomeworkScreen(
                    repository = appVm.repository,
                    groupId = groupId,
                    subjectId = subjectId,
                    lessonDate = date,
                    onBack = { navController.popBackStack() },
                    onOpenTop = { id -> navController.navigate("homework-top/$id") },
                )
            }
        }
        composable(
            route = "homework-top/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.IntType }),
        ) { entry ->
            val groupId = entry.arguments?.getInt("groupId")
            if (groupId != null) {
                HomeworkTopScreen(
                    repository = appVm.repository,
                    groupId = groupId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            route = "journal/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.IntType }),
        ) { entry ->
            val groupId = entry.arguments?.getInt("groupId")
            if (groupId != null) {
                JournalScreen(
                    groupId = groupId,
                    user = user,
                    repository = appVm.repository,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable("admin") {
            AdminScreen(
                repository = appVm.repository,
                onBack = { navController.popBackStack() },
                onAudit = { navController.navigate("audit") },
            )
        }
        composable("audit") {
            AuditScreen(
                repository = appVm.repository,
                onBack = { navController.popBackStack() },
            )
        }
        composable("profile") {
            ProfileScreen(
                user = user,
                appVm = appVm,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun SplashScreen() {
    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandMark(size = 88.dp)
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Журнал",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "успеваемость без лишней бумаги",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                strokeWidth = 2.5.dp,
            )
        }
    }
}
