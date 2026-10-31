package com.mfreya.misgastos.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mfreya.misgastos.ui.list.ExpenseListScreen
import com.mfreya.misgastos.ui.onboarding.OnboardingScreen
import com.mfreya.misgastos.ui.settings.SettingsScreen
import com.mfreya.misgastos.ui.summary.SummaryScreen

private const val TransitionMillis = 220

@Composable
fun AppNavHost(
    startWithOnboarding: Boolean,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute != null && currentRoute != Routes.ONBOARDING

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // Este Scaffold solo es dueño de la barra inferior. Si se dejaran los insets del
        // sistema, descontaria otra vez el hueco del status bar que ya descuenta la cabecera
        // de cada pantalla, y las cuatro vistas empezarian con una franja en blanco.
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(destination.iconRes),
                                    contentDescription = null,
                                )
                            },
                            label = { Text(text = stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (startWithOnboarding) {
                Routes.ONBOARDING
            } else {
                TopLevelDestination.Summary.route
            },
            modifier = Modifier.padding(padding),
            enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = TransitionMillis)) +
                    slideInHorizontally(
                        animationSpec = tween(durationMillis = TransitionMillis),
                        initialOffsetX = { fullWidth -> fullWidth / 8 },
                    )
            },
            exitTransition = { fadeOut(animationSpec = tween(durationMillis = TransitionMillis / 2)) },
            popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = TransitionMillis)) +
                    slideInHorizontally(
                        animationSpec = tween(durationMillis = TransitionMillis),
                        initialOffsetX = { fullWidth -> -fullWidth / 8 },
                    )
            },
            popExitTransition = { fadeOut(animationSpec = tween(durationMillis = TransitionMillis / 2)) },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(TopLevelDestination.Summary.route) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(TopLevelDestination.Summary.route) {
                SummaryScreen()
            }
            composable(TopLevelDestination.Expenses.route) {
                ExpenseListScreen()
            }
            composable(TopLevelDestination.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
