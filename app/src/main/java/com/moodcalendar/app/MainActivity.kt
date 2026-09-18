package com.moodcalendar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moodcalendar.app.ui.auth.AuthScreen
import com.moodcalendar.app.ui.auth.AuthViewModel
import com.moodcalendar.app.ui.calendar.CalendarScreen
import com.moodcalendar.app.ui.calendar.CalendarViewModel
import com.moodcalendar.app.ui.components.LoginRequiredDialog
import com.moodcalendar.app.ui.events.EventEditScreen
import com.moodcalendar.app.ui.events.EventEditViewModel
import com.moodcalendar.app.ui.events.EventListScreen
import com.moodcalendar.app.ui.events.EventListViewModel
import com.moodcalendar.app.ui.mood.MoodEditScreen
import com.moodcalendar.app.ui.mood.MoodEditViewModel
import com.moodcalendar.app.ui.mood.MoodJournalScreen
import com.moodcalendar.app.ui.mood.MoodJournalViewModel
import com.moodcalendar.app.ui.navigation.Routes
import com.moodcalendar.app.ui.settings.ReminderSettingsScreen
import com.moodcalendar.app.ui.settings.SettingsScreen
import com.moodcalendar.app.ui.settings.SettingsViewModel
import com.moodcalendar.app.ui.settings.ThemeSettingsScreen
import com.moodcalendar.app.ui.social.FriendsFeedScreen
import com.moodcalendar.app.ui.social.SocialViewModel
import com.moodcalendar.app.ui.theme.MoodCalendarTheme
import com.moodcalendar.app.ui.theme.ThemeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MoodCalendarApp
        setContent {
            val themeVm: ThemeViewModel = viewModel(
                factory = ThemeViewModel.factory(app.container.settingsRepository)
            )
            val themeStyle by themeVm.themeStyle.collectAsStateWithLifecycle()
            MoodCalendarTheme(themeStyle = themeStyle) {
                MoodCalendarAppRoot(app = app)
            }
        }
    }
}

@Composable
private fun MoodCalendarAppRoot(app: MoodCalendarApp) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in setOf(
        Routes.CALENDAR,
        Routes.EVENTS,
        Routes.MOODS,
        Routes.SETTINGS
    )
    val session by app.container.authRepository.session.collectAsStateWithLifecycle()
    var showLoginGate by remember { mutableStateOf(false) }

    fun openSocialOrGate() {
        if (session == null) {
            showLoginGate = true
        } else {
            navController.navigate(Routes.SOCIAL)
        }
    }

    LoginRequiredDialog(
        visible = showLoginGate,
        onDismiss = { showLoginGate = false },
        onGoLogin = {
            showLoginGate = false
            navController.navigate(Routes.SETTINGS_AUTH)
        }
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.CALENDAR,
                        onClick = {
                            navController.navigate(Routes.CALENDAR) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                        label = { Text("日历") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.EVENTS,
                        onClick = {
                            navController.navigate(Routes.EVENTS) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Outlined.EventNote, contentDescription = null) },
                        label = { Text("事件") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.MOODS,
                        onClick = {
                            navController.navigate(Routes.MOODS) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) },
                        label = { Text("感情") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = {
                            navController.navigate(Routes.SETTINGS) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                        label = { Text("我的") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CALENDAR,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.CALENDAR) {
                val vm: CalendarViewModel = viewModel(
                    factory = CalendarViewModel.factory(app.container)
                )
                CalendarScreen(
                    viewModel = vm,
                    onAddEvent = { date -> navController.navigate(Routes.eventEdit(date = date)) },
                    onEditEvent = { id -> navController.navigate(Routes.eventEdit(id)) },
                    onAddMood = { date -> navController.navigate(Routes.moodEdit(date = date)) },
                    onEditMood = { id, date ->
                        navController.navigate(Routes.moodEdit(moodId = id, date = date))
                    }
                )
            }
            composable(Routes.EVENTS) {
                val vm: EventListViewModel = viewModel(
                    factory = EventListViewModel.factory(app.container.eventRepository)
                )
                EventListScreen(
                    viewModel = vm,
                    onAdd = { navController.navigate(Routes.eventEdit()) },
                    onEdit = { id -> navController.navigate(Routes.eventEdit(id)) }
                )
            }
            composable(Routes.MOODS) {
                val vm: MoodJournalViewModel = viewModel(
                    factory = MoodJournalViewModel.factory(
                        app.container.moodRepository,
                        app.container.settingsRepository
                    )
                )
                MoodJournalScreen(
                    viewModel = vm,
                    onOpenMood = { id, date ->
                        navController.navigate(Routes.moodEdit(moodId = id, date = date))
                    },
                    onOpenSocial = { openSocialOrGate() }
                )
            }
            composable(Routes.SETTINGS) {
                val accountSubtitle = session?.let {
                    "${it.displayName.ifBlank { it.email }} · 已登录"
                } ?: "未登录，本地功能仍可用"
                SettingsScreen(
                    accountSubtitle = accountSubtitle,
                    onOpenAccount = { navController.navigate(Routes.SETTINGS_AUTH) },
                    onOpenSocial = { openSocialOrGate() },
                    onOpenTheme = { navController.navigate(Routes.SETTINGS_THEME) },
                    onOpenReminder = { navController.navigate(Routes.SETTINGS_REMINDER) }
                )
            }
            composable(Routes.SETTINGS_AUTH) {
                val vm: AuthViewModel = viewModel(
                    factory = AuthViewModel.factory(
                        app,
                        app.container.authRepository,
                        app.container.syncEngine
                    )
                )
                AuthScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SOCIAL) {
                val vm: SocialViewModel = viewModel(
                    factory = SocialViewModel.factory(
                        app.container.authRepository,
                        app.container.socialRepository
                    )
                )
                FriendsFeedScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onNeedLogin = {
                        navController.popBackStack()
                        showLoginGate = true
                    }
                )
            }
            composable(Routes.SETTINGS_THEME) {
                val vm: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.factory(app.container.settingsRepository)
                )
                ThemeSettingsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS_REMINDER) {
                ReminderSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.EVENT_EDIT,
                arguments = listOf(
                    navArgument("eventId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("date") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->
                val eventId = entry.arguments?.getLong("eventId") ?: -1L
                val date = entry.arguments?.getString("date").orEmpty()
                val vm: EventEditViewModel = viewModel(
                    factory = EventEditViewModel.factory(
                        app,
                        app.container.eventRepository,
                        eventId.takeIf { it > 0 },
                        date.ifBlank { null }
                    )
                )
                EventEditScreen(
                    viewModel = vm,
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.MOOD_EDIT,
                arguments = listOf(
                    navArgument("moodId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("date") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->
                val moodId = entry.arguments?.getLong("moodId") ?: -1L
                val date = entry.arguments?.getString("date").orEmpty()
                val vm: MoodEditViewModel = viewModel(
                    factory = MoodEditViewModel.factory(
                        app,
                        app.container.moodRepository,
                        app.container.settingsRepository,
                        moodId.takeIf { it > 0 },
                        date
                    )
                )
                MoodEditScreen(
                    viewModel = vm,
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
