package io.github.herfar.barodial.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.herfar.barodial.BuildConfig
import io.github.herfar.barodial.R
import io.github.herfar.barodial.data.calllog.CallHistoryAvailability
import io.github.herfar.barodial.data.contacts.ContactsState
import io.github.herfar.barodial.data.recordings.RecordingsAvailability
import io.github.herfar.barodial.data.settings.AppSettings
import io.github.herfar.barodial.data.settings.StartTab
import io.github.herfar.barodial.data.settings.ThemeMode
import io.github.herfar.barodial.dial.DefaultDialerInfo
import io.github.herfar.barodial.ui.components.AppTopBar
import io.github.herfar.barodial.ui.components.SectionHeader
import io.github.herfar.barodial.ui.icons.AppIcons
import io.github.herfar.barodial.ui.theme.LocalCallColors

class SettingsActions(
    val back: () -> Unit,
    val update: ((AppSettings) -> AppSettings) -> Unit,
    val setDirectCall: (Boolean) -> Unit,
    val clearHistory: () -> Unit,
    val requestContacts: () -> Unit,
    val openAppSettings: () -> Unit,
    val openDefaultApps: () -> Unit,
    val requestRecordings: () -> Unit,
    val openDiagnostics: () -> Unit,
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    contactsState: ContactsState,
    callPermissionGranted: Boolean,
    callHistory: CallHistoryAvailability,
    recordings: RecordingsAvailability,
    recordingCount: Int,
    dialerInfo: DefaultDialerInfo?,
    navigationIcon: ImageVector,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    applyStatusBarInsets: Boolean = true,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Surface(modifier.fillMaxSize().testTag("settingsScreen"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = stringResource(R.string.settings_title),
                navigationIcon = navigationIcon,
                navigationDescription = stringResource(R.string.action_back),
                onNavigate = actions.back,
                applyStatusBarInsets = applyStatusBarInsets,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("settingsList"),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item("integration") { IntegrationCard(dialerInfo) }

                item("displayHeader") { Header(R.string.settings_section_display) }
                item("theme") {
                    ChoiceRow(
                        title = stringResource(R.string.settings_theme),
                        options = listOf(
                            ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                            ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                            ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                        ),
                        selected = settings.themeMode,
                        onSelect = { mode -> actions.update { it.copy(themeMode = mode) } },
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    item("dynamic") {
                        SwitchRow(
                            title = stringResource(R.string.settings_dynamic_color),
                            subtitle = stringResource(R.string.settings_dynamic_color_desc),
                            checked = settings.dynamicColor,
                            onChange = { v -> actions.update { it.copy(dynamicColor = v) } },
                        )
                    }
                }
                item("startTab") {
                    ChoiceRow(
                        title = stringResource(R.string.settings_start_tab),
                        options = listOf(
                            StartTab.HOME to stringResource(R.string.tab_home),
                            StartTab.CONTACTS to stringResource(R.string.tab_contacts),
                            StartTab.FAVORITES to stringResource(R.string.tab_favorites),
                        ),
                        selected = settings.startTab,
                        onSelect = { tab -> actions.update { it.copy(startTab = tab) } },
                    )
                }

                item("searchHeader") { Header(R.string.settings_section_search) }
                item("nearBar") {
                    SwitchRow(
                        title = stringResource(R.string.settings_results_near_bar),
                        subtitle = stringResource(R.string.settings_results_near_bar_desc),
                        checked = settings.resultsNearSearchBar,
                        onChange = { v -> actions.update { it.copy(resultsNearSearchBar = v) } },
                    )
                }
                item("clearAfterCall") {
                    SwitchRow(
                        title = stringResource(R.string.settings_clear_after_call),
                        subtitle = stringResource(R.string.settings_clear_after_call_desc),
                        checked = settings.clearSearchAfterCall,
                        onChange = { v -> actions.update { it.copy(clearSearchAfterCall = v) } },
                    )
                }
                item("saveHistory") {
                    SwitchRow(
                        title = stringResource(R.string.settings_save_history),
                        subtitle = stringResource(R.string.settings_save_history_desc),
                        checked = settings.saveSearchHistory,
                        onChange = { v -> actions.update { it.copy(saveSearchHistory = v) } },
                    )
                }
                item("clearHistory") {
                    ActionRow(
                        title = stringResource(R.string.settings_clear_history),
                        subtitle = stringResource(R.string.settings_clear_history_desc),
                        onClick = { confirmClear = true },
                        modifier = Modifier.testTag("settingsClearHistory"),
                    )
                }

                item("callHeader") { Header(R.string.settings_section_calling) }
                item("directCall") {
                    SwitchRow(
                        title = stringResource(R.string.settings_direct_call),
                        subtitle = stringResource(R.string.settings_direct_call_desc),
                        checked = settings.directCall,
                        onChange = actions.setDirectCall,
                        modifier = Modifier.testTag("settingsDirectCall"),
                    )
                }
                item("haptics") {
                    SwitchRow(
                        title = stringResource(R.string.settings_keypad_haptics),
                        subtitle = null,
                        checked = settings.keypadHaptics,
                        onChange = { v -> actions.update { it.copy(keypadHaptics = v) } },
                    )
                }

                item("favHeader") { Header(R.string.settings_section_favorites) }
                item("systemFav") {
                    SwitchRow(
                        title = stringResource(R.string.settings_show_system_favorites),
                        subtitle = stringResource(R.string.settings_show_system_favorites_desc),
                        checked = settings.showSystemFavorites,
                        onChange = { v -> actions.update { it.copy(showSystemFavorites = v) } },
                    )
                }

                item("permHeader") { Header(R.string.settings_section_permissions) }
                item("permContacts") {
                    val granted = contactsState !is ContactsState.PermissionRequired
                    ActionRow(
                        title = stringResource(R.string.settings_perm_contacts),
                        subtitle = stringResource(if (granted) R.string.settings_perm_granted else R.string.settings_perm_denied_tap),
                        onClick = if (granted) actions.openAppSettings else actions.requestContacts,
                    )
                }
                item("permCall") {
                    ActionRow(
                        title = stringResource(R.string.settings_perm_call_phone),
                        subtitle = stringResource(if (callPermissionGranted) R.string.settings_perm_granted else R.string.settings_perm_call_phone_off),
                        onClick = actions.openAppSettings,
                    )
                }
                item("permCallLog") {
                    ActionRow(
                        title = stringResource(R.string.settings_perm_call_log),
                        subtitle = stringResource(
                            when (callHistory) {
                                CallHistoryAvailability.NotIncludedInBuild -> R.string.settings_perm_call_log_not_included
                                CallHistoryAvailability.Available -> R.string.settings_perm_granted
                                is CallHistoryAvailability.Denied -> R.string.settings_perm_call_log_denied
                                is CallHistoryAvailability.PermissionRequired -> R.string.settings_perm_call_log_required
                            },
                        ),
                        onClick = actions.openAppSettings,
                    )
                }

                item("permRecordings") {
                    ActionRow(
                        title = stringResource(R.string.settings_perm_recordings),
                        subtitle = when (recordings) {
                            RecordingsAvailability.Available -> stringResource(R.string.settings_perm_recordings_found, recordingCount)
                            is RecordingsAvailability.Denied -> stringResource(R.string.settings_perm_denied_tap)
                            is RecordingsAvailability.PermissionRequired -> stringResource(R.string.settings_perm_recordings_required)
                        },
                        onClick = when (recordings) {
                            is RecordingsAvailability.PermissionRequired -> actions.requestRecordings
                            else -> actions.openAppSettings
                        },
                        modifier = Modifier.testTag("settingsRecordings"),
                    )
                }

                item("recDiagnostics") {
                    ActionRow(
                        title = stringResource(R.string.diag_title),
                        subtitle = stringResource(R.string.settings_diagnostics_desc),
                        onClick = actions.openDiagnostics,
                        modifier = Modifier.testTag("settingsDiagnostics"),
                    )
                }

                item("aboutHeader") { Header(R.string.settings_section_about) }
                item("privacy") {
                    InfoRow(stringResource(R.string.settings_privacy_title), stringResource(R.string.settings_privacy_body))
                }
                item("version") {
                    InfoRow(
                        stringResource(R.string.settings_version),
                        "${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR}, ${BuildConfig.BUILD_TYPE})",
                    )
                }
                item("open") {
                    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = actions.openDefaultApps, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.settings_open_default_apps))
                        }
                        OutlinedButton(onClick = actions.openAppSettings, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_open_app_settings))
                        }
                    }
                }
                item("inset") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_history)) },
            text = { Text(stringResource(R.string.settings_clear_history_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    actions.clearHistory()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun IntegrationCard(info: DefaultDialerInfo?) {
    val ok = info != null && info.packageName != null && !info.thisAppHoldsDialerRole
    val callColors = LocalCallColors.current
    Card(
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("integrationCard"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (ok) AppIcons.Check else AppIcons.Info, contentDescription = null, tint = if (ok) callColors.call else MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_integration_title), style = MaterialTheme.typography.titleMedium)
            }
            val dialer = when {
                info == null -> stringResource(R.string.settings_checking)
                info.packageName == null -> stringResource(R.string.settings_default_dialer_unknown)
                else -> "${info.label ?: info.packageName} (${info.packageName})"
            }
            Text(stringResource(R.string.settings_default_dialer, dialer), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(if (info?.thisAppHoldsDialerRole == true) R.string.settings_role_warning else R.string.settings_role_ok),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(stringResource(R.string.settings_integration_body), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Header(res: Int) {
    SectionHeader(stringResource(res), modifier = Modifier.widthIn(max = 640.dp))
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .heightIn(min = 64.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(title: String, body: String) {
    Column(
        Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (value, label) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                ) { Text(label, maxLines = 1) }
            }
        }
    }
}
