package com.mfreya.misgastos.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mfreya.misgastos.R
import com.mfreya.misgastos.domain.BudgetLimit
import com.mfreya.misgastos.domain.model.Category
import com.mfreya.misgastos.domain.model.Expense
import com.mfreya.misgastos.domain.model.ThemeMode
import com.mfreya.misgastos.domain.parse.AmountParser
import com.mfreya.misgastos.ui.LocalAppContainer
import com.mfreya.misgastos.ui.components.ScreenHeader
import com.mfreya.misgastos.ui.components.SectionHeader
import com.mfreya.misgastos.ui.components.SurfaceCard
import com.mfreya.misgastos.ui.components.rememberScreenHeaderScrollBehavior
import com.mfreya.misgastos.ui.containerViewModel
import com.mfreya.misgastos.ui.export.CsvExporter
import com.mfreya.misgastos.ui.format.Money
import com.mfreya.misgastos.ui.format.categoryLabel
import com.mfreya.misgastos.ui.support.NotificationAccess
import com.mfreya.misgastos.ui.support.appLabelOrPackage
import com.mfreya.misgastos.ui.theme.SectionOverline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = containerViewModel("settings") { container ->
        SettingsViewModel(
            appContext = container.appSettingsContext,
            settings = container.appSettings,
            repository = container.expenseRepository,
            classifier = container.classifier,
            budgets = container.budgetRepository,
        )
    },
) {
    val headerScroll = rememberScreenHeaderScrollBehavior()
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    // Se resuelve aqui y no dentro del onClick para dejar claro que es texto plano.
    val privacyPolicyUrl = stringResource(R.string.settings_privacy_policy_url)

    val allowAll by viewModel.allowAllPackages.collectAsStateWithLifecycle()
    val allowed by viewModel.allowedPackages.collectAsStateWithLifecycle()
    val storedCount by viewModel.storedCount.collectAsStateWithLifecycle()
    val needsReview by viewModel.needsReview.collectAsStateWithLifecycle()
    val sourcePackages by viewModel.sourcePackages.collectAsStateWithLifecycle()
    val sourcePackageCounts by viewModel.sourcePackageCounts.collectAsStateWithLifecycle()
    val learned by viewModel.learned.collectAsStateWithLifecycle()
    val learnedCount by viewModel.learnedCount.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val budgetLimits by viewModel.budgetLimits.collectAsStateWithLifecycle()
    val expenseCategories by viewModel.expenseCategories.collectAsStateWithLifecycle()

    var showDeletePrompt by remember { mutableStateOf(false) }
    var showDeleteFinal by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    var toForget by remember { mutableStateOf<String?>(null) }
    val counts = remember(sourcePackageCounts) {
        sourcePackageCounts.associate { it.sourcePackage to it.total }
    }
    val forgetTitle = stringResource(R.string.settings_sources_forget_title)
    val forgetAction = stringResource(R.string.settings_sources_forget)

    /** Modo cerrado: solo se leen las apps marcadas. */
    fun narrowToSelectedOnly() = viewModel.setAllowAllPackages(false)
    var budgetDialogOpen by remember { mutableStateOf(false) }
    var budgetToRemove by remember { mutableStateOf<BudgetLimit?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val deletedAllMessage = stringResource(R.string.settings_deleted_all)
    val exportEmptyMessage = stringResource(R.string.export_csv_empty)
    val exportDoneMessage = stringResource(R.string.export_csv_done)
    val exportFailedMessage = stringResource(R.string.export_csv_failed)
    val budgetSavedMessage = stringResource(R.string.settings_budget_saved)
    val budgetRemovedMessage = stringResource(R.string.settings_budget_removed)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(CsvExporter.MIME_TYPE),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val (rows, csv) = withContext(Dispatchers.IO) {
                    val loaded = viewModel.allExpenses()
                    loaded to CsvExporter.build(loaded)
                }
                val message = when {
                    rows.isEmpty() -> exportEmptyMessage
                    CsvExporter.write(context, uri, csv) -> exportDoneMessage
                    else -> exportFailedMessage
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val packages = remember(sourcePackages, allowed) {
        (sourcePackages + container.appSettings.knownPackages() + allowed)
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            ScreenHeader(
                title = stringResource(R.string.settings_title),
                scrollBehavior = headerScroll,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(headerScroll.nestedScrollConnection),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "appearance") {
                AppearanceCard(
                    themeMode = themeMode,
                    onThemeModeChange = viewModel::setThemeMode,
                )
            }

            item(key = "access") {
                SectionCard(
                    title = stringResource(R.string.settings_section_access),
                    ok = viewModel.accessGranted,
                    okText = stringResource(R.string.settings_access_granted),
                    failText = stringResource(R.string.settings_access_missing),
                    iconOk = Icons.Filled.Check,
                    iconFail = Icons.Filled.Warning,
                    action = if (viewModel.accessGranted) {
                        null
                    } else {
                        {
                            context.openIntentOrFallback(
                                NotificationAccess.directSettingsIntent(context),
                                NotificationAccess.settingsIntent(context),
                            )
                        } to stringResource(R.string.settings_open_access)
                    },
                )
            }

            item(key = "sources") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(title = stringResource(R.string.settings_sources_title))
                    SurfaceCard {
                        Text(
                            text = stringResource(R.string.settings_sources_privacy),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // Aviso mientras se lean todas las apps: es el estado que mas invade,
                        // asi que se dice en alto y se ofrece el camino corto para dejarlo.
                        if (allowAll) {
                            Spacer(modifier = Modifier.height(12.dp))
                            SurfaceCard(contentPadding = PaddingValues(12.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(
                                            imageVector = Icons.Filled.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.settings_sources_all_risk),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            narrowToSelectedOnly()
                                        },
                                        contentPadding = PaddingValues(horizontal = 0.dp),
                                    ) {
                                        Text(text = stringResource(R.string.settings_sources_narrow))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        ChoiceRow(
                            label = stringResource(R.string.settings_sources_all),
                            description = stringResource(R.string.settings_sources_all_desc),
                            selected = allowAll,
                            onSelect = { viewModel.setAllowAllPackages(true) },
                        )
                        ChoiceRow(
                            label = stringResource(R.string.settings_sources_selected),
                            description = stringResource(R.string.settings_sources_only_selected_risk),
                            selected = !allowAll,
                            onSelect = { narrowToSelectedOnly() },
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        if (packages.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_sources_empty_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            packages.forEach { packageName ->
                                PackageRow(
                                    packageName = packageName,
                                    count = counts[packageName] ?: 0,
                                    enabled = !allowAll,
                                    checked = packageName in allowed,
                                    onCheckedChange = { viewModel.setPackageAllowed(packageName, it) },
                                    onForget = { toForget = packageName },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "diagnostics") {
                DiagnosticsCard(
                    accessGranted = viewModel.accessGranted,
                    listenerConnected = viewModel.listenerConnected,
                    batteryUnrestricted = viewModel.batteryUnrestricted,
                )
            }

            item(key = "data") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(title = stringResource(R.string.settings_section_data))
                    SurfaceCard {
                        Text(
                            text = pluralStringResource(
                                R.plurals.settings_stored_count,
                                storedCount,
                                storedCount,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { exportLauncher.launch(CsvExporter.suggestedFileName()) },
                                enabled = storedCount > 0,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_export),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = stringResource(R.string.export_csv))
                            }
                            TextButton(
                                onClick = { showDeletePrompt = true },
                                enabled = storedCount > 0,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = stringResource(R.string.settings_delete_all))
                            }
                        }
                    }
                }
            }

            item(key = "review") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(title = stringResource(R.string.settings_section_review))
                    SurfaceCard {
                        Text(
                            text = stringResource(R.string.settings_review_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (needsReview.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_review_empty),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            needsReview.forEachIndexed { index, expense ->
                                if (index > 0) HorizontalDivider()
                                NeedsReviewRow(
                                    expense = expense,
                                    onClick = { editing = expense },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "auto") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(title = stringResource(R.string.settings_section_auto))
                    SurfaceCard {
                        Text(
                            text = stringResource(R.string.settings_auto_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "learned") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.settings_section_learned),
                        action = if (learned.isEmpty()) {
                            null
                        } else {
                            {
                                TextButton(onClick = { viewModel.forgetAllLearned() }) {
                                    Text(
                                        text = stringResource(R.string.settings_learned_forget),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                    )
                    SurfaceCard {
                        Text(
                            text = pluralStringResource(
                                R.plurals.settings_learned_count,
                                learnedCount,
                                learnedCount,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_learned_forget_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (learned.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_learned_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            learned.forEachIndexed { index, rule ->
                                if (index > 0) HorizontalDivider()
                                LearnedRow(
                                    merchantKey = rule.merchantKey,
                                    category = rule.category,
                                    onForget = { viewModel.forgetLearned(rule.merchantKey) },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "budgets") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.settings_section_budgets),
                        action = {
                            TextButton(onClick = { budgetDialogOpen = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = stringResource(R.string.settings_budget_add))
                            }
                        },
                    )
                    SurfaceCard {
                        Text(
                            text = stringResource(R.string.settings_budgets_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (budgetLimits.isEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.settings_budgets_empty),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            budgetLimits.forEachIndexed { index, limit ->
                                if (index > 0) HorizontalDivider()
                                BudgetLimitRow(
                                    limit = limit,
                                    onRemove = { budgetToRemove = limit },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "about") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(title = stringResource(R.string.settings_section_about))
                    SurfaceCard {
                        Text(
                            text = stringResource(R.string.settings_version, appVersion(context)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_privacy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Google Play exige la politica de privacidad enlazada DENTRO de la
                        // app, no solo en la ficha de la tienda. LocalUriHandler la abre en
                        // el navegador del sistema sin necesitar ningun permiso.
                        Text(
                            text = stringResource(R.string.settings_privacy_policy),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.settings_privacy_policy_url),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { uriHandler.openUri(privacyPolicyUrl) },
                        )
                    }
                }
            }
        }
    }

    toForget?.let { packageName ->
        val label = context.appLabelOrPackage(packageName)
        val count = counts[packageName] ?: 0
        // Los mensajes se resuelven aqui, no dentro del launch: ahi ya no se puede.
        val bodyMessage = pluralStringResource(
            R.plurals.settings_sources_forget_body,
            count,
            count,
            label,
        )
        val doneMessage = stringResource(R.string.settings_sources_forget_done, label)
        AlertDialog(
            onDismissRequest = { toForget = null },
            title = { Text(text = forgetTitle) },
            text = { Text(text = bodyMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        toForget = null
                        viewModel.forgetPackage(packageName) {
                            scope.launch { snackbarHostState.showSnackbar(doneMessage) }
                        }
                    },
                ) {
                    Text(text = forgetAction, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { toForget = null }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showDeletePrompt) {
        AlertDialog(
            onDismissRequest = { showDeletePrompt = false },
            title = { Text(text = stringResource(R.string.settings_delete_all_confirm_title)) },
            text = {
                Text(
                    text = pluralStringResource(
                        R.plurals.settings_delete_all_confirm_body,
                        storedCount,
                        storedCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeletePrompt = false
                    showDeleteFinal = true
                }) {
                    Text(text = stringResource(R.string.common_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePrompt = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showDeleteFinal) {
        AlertDialog(
            onDismissRequest = { showDeleteFinal = false },
            title = { Text(text = stringResource(R.string.settings_delete_all_confirm_title)) },
            text = { Text(text = stringResource(R.string.settings_delete_all_confirm_action)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteFinal = false
                        viewModel.deleteAll()
                        scope.launch {
                            snackbarHostState.showSnackbar(deletedAllMessage)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(text = stringResource(R.string.settings_delete_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFinal = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (budgetDialogOpen) {
        BudgetDialog(
            existing = budgetLimits,
            availableCategories = expenseCategories,
            onDismiss = { budgetDialogOpen = false },
            onSave = { category, cents ->
                viewModel.setBudget(category, cents)
                budgetDialogOpen = false
                scope.launch { snackbarHostState.showSnackbar(budgetSavedMessage) }
            },
        )
    }

    val removal = budgetToRemove
    if (removal != null) {
        AlertDialog(
            onDismissRequest = { budgetToRemove = null },
            title = {
                Text(
                    text = stringResource(
                        R.string.settings_budget_remove_title,
                        categoryLabel(removal.category),
                    ),
                )
            },
            text = { Text(text = stringResource(R.string.settings_budget_remove_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        budgetToRemove = null
                        viewModel.clearBudget(removal.category)
                        scope.launch { snackbarHostState.showSnackbar(budgetRemovedMessage) }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(text = stringResource(R.string.settings_budget_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { budgetToRemove = null }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }

    val target = editing
    if (target != null) {
        EditExpenseDialog(
            expense = target,
            onDismiss = { editing = null },
            onSave = { merchant, category ->
                viewModel.updateMerchant(target, merchant)
                viewModel.updateCategory(target, category)
                editing = null
            },
        )
    }
}

@Composable
private fun AppearanceCard(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val options = remember {
        listOf(
            ThemeMode.SYSTEM to R.string.settings_theme_system,
            ThemeMode.LIGHT to R.string.settings_theme_light,
            ThemeMode.DARK to R.string.settings_theme_dark,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = stringResource(R.string.settings_section_appearance))
        SurfaceCard {
            Text(
                text = stringResource(R.string.settings_theme_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            options.forEach { (mode, labelRes) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onThemeModeChange(mode) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                    )
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (themeMode == mode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    ok: Boolean,
    okText: String,
    failText: String,
    iconOk: androidx.compose.ui.graphics.vector.ImageVector,
    iconFail: androidx.compose.ui.graphics.vector.ImageVector,
    action: Pair<() -> Unit, String>?,
) {
    SurfaceCard {
        Text(
            text = title,
            style = SectionOverline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (ok) iconOk else iconFail,
                contentDescription = null,
                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (ok) okText else failText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
        }
        if (action != null) {
            TextButton(onClick = action.first) {
                Text(text = action.second)
            }
        }
    }
}

@Composable
private fun PackageRow(
    packageName: String,
    count: Int,
    enabled: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onForget: () -> Unit,
) {
    val context = LocalContext.current
    val label = remember(packageName) { context.appLabelOrPackage(packageName) }
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                )
                // El identificador tecnico queda como detalle secundario solo cuando hay
                // nombre publico; si no lo hay, el titulo ya es el paquete y no se repite.
                Text(
                    text = if (label == packageName) {
                        stringResource(R.string.settings_sources_app_unknown)
                    } else {
                        packageName
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (count > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.settings_sources_count,
                            count,
                            count,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            TextButton(onClick = onForget, enabled = count > 0) {
                Text(
                    text = stringResource(R.string.settings_sources_forget),
                    color = if (count > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

/** Fila de eleccion con su explicacion debajo, para las dos formas de tratar las apps. */
@Composable
private fun ChoiceRow(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NeedsReviewRow(
    expense: Expense,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = expense.merchant,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Money.format(expense.amountCents),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            text = stringResource(R.string.settings_review_confidence, expense.parseConfidence),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        if (expense.rawText.isNotBlank()) {
            Text(
                text = expense.rawText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun LearnedRow(
    merchantKey: String,
    category: String,
    onForget: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = merchantKey,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = categoryLabel(category),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        TextButton(onClick = onForget) {
            Text(
                text = stringResource(R.string.settings_learned_forget),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetLimitRow(limit: BudgetLimit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryLabel(limit.category),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.summary_budget_limit_line, Money.format(limit.limitCents)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRemove) {
            Text(
                text = stringResource(R.string.settings_budget_remove),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Alta o edicion de un limite mensual. El importe se valida con el mismo parser que lee los
 * avisos del banco, asi que se admiten "150", "150,50" o "1.200,00".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetDialog(
    existing: List<BudgetLimit>,
    availableCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (category: String, cents: Long) -> Unit,
) {
    val alreadyBudgeted = existing.map { it.category }.toSet()
    // El catalogo primero y despues las categorias que de verdad usan los gastos, para que
    // tambien se pueda presupuestar una categoria creada a mano ("Freya", por ejemplo).
    val options = remember(availableCategories, alreadyBudgeted) {
        (Category.DEFAULT_CATEGORIES + availableCategories)
            .distinct()
            .filter { it !in alreadyBudgeted }
    }
    var category by remember(options) { mutableStateOf(options.firstOrNull().orEmpty()) }
    var amount by remember { mutableStateOf("") }
    val cents = AmountParser.toCents(amount)
    val valid = cents != null && cents > 0L
    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_budget_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(text = stringResource(R.string.settings_budget_limit_label)) },
                    prefix = { Text(text = Money.SYMBOL) },
                    singleLine = true,
                    isError = amount.isNotBlank() && !valid,
                    supportingText = if (amount.isNotBlank() && !valid) {
                        { Text(text = stringResource(R.string.settings_budget_amount_invalid)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.expense_category_label),
                        style = SectionOverline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { option ->
                            FilterChip(
                                selected = category == option,
                                onClick = { category = option },
                                label = { Text(text = categoryLabel(option)) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && category.isNotBlank(),
                onClick = { onSave(category, cents ?: 0L) },
            ) {
                Text(text = stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun EditExpenseDialog(
    expense: Expense,
    onDismiss: () -> Unit,
    onSave: (merchant: String, category: String) -> Unit,
) {
    var merchant by remember(expense.id) { mutableStateOf(expense.merchant) }
    var category by remember(expense.id) { mutableStateOf(expense.category) }
    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.expense_category_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = Money.format(expense.amountCents),
                    style = MaterialTheme.typography.titleLarge,
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text(text = stringResource(R.string.expense_merchant_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(text = stringResource(R.string.expense_category_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Category.DEFAULT_CATEGORIES.filter { it != category }.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { suggestion ->
                                FilterChip(
                                    selected = category == suggestion,
                                    onClick = { category = suggestion },
                                    label = { Text(text = categoryLabel(suggestion)) },
                                )
                            }
                        }
                    }
                }
                if (expense.rawText.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.expense_raw_text),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = expense.rawText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(merchant, category) }) {
                Text(text = stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
    )
}

private fun appVersion(context: android.content.Context): String =
    runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"
