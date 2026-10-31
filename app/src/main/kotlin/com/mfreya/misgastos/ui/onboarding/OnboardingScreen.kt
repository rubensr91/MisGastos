package com.mfreya.misgastos.ui.onboarding

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mfreya.misgastos.R
import com.mfreya.misgastos.ui.LocalAppContainer
import com.mfreya.misgastos.ui.components.BrandCard
import com.mfreya.misgastos.ui.components.SurfaceCard
import com.mfreya.misgastos.ui.support.NotificationAccess
import com.mfreya.misgastos.ui.theme.LocalBrandColors
import com.mfreya.misgastos.ui.theme.SectionOverline
import kotlinx.coroutines.launch

private const val STEP_COUNT = 2

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val settings = container.appSettings
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { STEP_COUNT })

    var accessGranted by remember { mutableStateOf(NotificationAccess.isGranted(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessGranted = NotificationAccess.isGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val blockedMessage = stringResource(R.string.onboarding_test_button_disabled)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            postTestNotification(context, snackbarHostState, scope)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(blockedMessage)
            }
        }
    }

    fun finish() {
        settings.setOnboardingCompleted(true)
        onFinished()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage == 0) {
                    TextButton(onClick = { finish() }) {
                        Text(text = stringResource(R.string.onboarding_skip))
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    else -> AccessPage(
                        accessGranted = accessGranted,
                        onOpenSettings = {
                            val intent = NotificationAccess.directSettingsIntent(context)
                                ?: NotificationAccess.settingsIntent(context)
                                ?: NotificationAccess.appUsageIntent(context)
                            if (intent != null) {
                                runCatching { context.startActivity(intent) }
                            }
                        },
                        onSendTest = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                postTestNotification(context, snackbarHostState, scope)
                            }
                        },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StepIndicator(current = pagerState.currentPage, total = STEP_COUNT)
                Button(
                    onClick = {
                        if (pagerState.currentPage == STEP_COUNT - 1) {
                            finish()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 28.dp,
                        vertical = 14.dp,
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = if (pagerState.currentPage == STEP_COUNT - 1) {
                            stringResource(R.string.common_start)
                        } else {
                            stringResource(R.string.common_continue)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

private fun postTestNotification(
    context: Context,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val posted = runCatching { TestNotification.post(context) }.isSuccess
    scope.launch {
        val message = if (posted) {
            context.getString(R.string.onboarding_test_sent)
        } else {
            context.getString(R.string.onboarding_test_button_disabled)
        }
        snackbarHostState.showSnackbar(message)
    }
}

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        BrandCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp)) {
            BrandHeroIcon(resId = R.drawable.ic_nav_onboarding)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_eyebrow),
                style = SectionOverline,
                color = LocalBrandColors.current.onBrand.copy(alpha = 0.82f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                color = LocalBrandColors.current.onBrand,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        SurfaceCard {
            WelcomePoint(R.drawable.ic_check, R.string.onboarding_welcome_point_1)
            Spacer(modifier = Modifier.height(14.dp))
            WelcomePoint(R.drawable.ic_nav_summary, R.string.onboarding_welcome_point_2)
            Spacer(modifier = Modifier.height(14.dp))
            WelcomePoint(R.drawable.ic_cloud_off, R.string.onboarding_welcome_point_3)
            Spacer(modifier = Modifier.height(14.dp))
            WelcomePoint(R.drawable.ic_info, R.string.onboarding_welcome_point_4)
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun WelcomePoint(iconRes: Int, textRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = stringResource(id = textRes),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AccessPage(
    accessGranted: Boolean,
    onOpenSettings: () -> Unit,
    onSendTest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        BrandCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp)) {
            BrandHeroIcon(resId = R.drawable.ic_stat_expense)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.onboarding_step_format, STEP_COUNT, STEP_COUNT),
                style = SectionOverline,
                color = LocalBrandColors.current.onBrand.copy(alpha = 0.82f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_access_title),
                style = MaterialTheme.typography.headlineMedium,
                color = LocalBrandColors.current.onBrand,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.onboarding_access_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (accessGranted) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(
                                id = if (accessGranted) R.drawable.ic_check else R.drawable.ic_warning,
                            ),
                            contentDescription = null,
                            tint = if (accessGranted) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = stringResource(
                        id = if (accessGranted) {
                            R.string.onboarding_access_granted
                        } else {
                            R.string.settings_access_missing
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (!accessGranted) {
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_open_settings),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onSendTest,
            enabled = accessGranted,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
        ) {
            Text(
                text = if (accessGranted) {
                    stringResource(R.string.onboarding_send_test)
                } else {
                    stringResource(R.string.onboarding_test_button_disabled)
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.onboarding_notification_permission),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

/** Icono grande dentro de la tarjeta de marca: círculo translúcido con el glifo del sistema. */
@Composable
private fun BrandHeroIcon(resId: Int) {
    val brand = LocalBrandColors.current
    Surface(
        shape = CircleShape,
        color = brand.onBrand.copy(alpha = 0.16f),
        modifier = Modifier.size(76.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = resId),
                contentDescription = null,
                tint = brand.onBrand,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun StepIndicator(current: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.onboarding_step_format, current + 1, total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        repeat(total) { index ->
            val active = index == current
            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(if (active) 10.dp else 8.dp)
                    .background(
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}
