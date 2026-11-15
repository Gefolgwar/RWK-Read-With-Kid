package com.gefolgwar.readwithkid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Language names are shown in their own language so a parent finds theirs whatever the current one is. */
private val UiLanguage.label: Int
    get() = when (this) {
        UiLanguage.SYSTEM -> R.string.ui_language_system
        UiLanguage.UK -> R.string.ui_language_uk
        UiLanguage.DE -> R.string.ui_language_de
        UiLanguage.EN -> R.string.ui_language_en
    }

@Composable
fun SettingsScreen(
    language: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit,
    syllableColors: Boolean,
    onSyllableColorsChange: (Boolean) -> Unit,
    onPrivacy: () -> Unit,
    onLicenses: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    Column(modifier = modifier) {
        ScreenHeader(R.string.settings_title, onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                stringResource(R.string.ui_language_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            Column(Modifier.selectableGroup()) {
                UiLanguage.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(option == language, role = Role.RadioButton) { onLanguageChange(option) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = option == language, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(option.label), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(syllableColors, role = Role.Switch, onValueChange = onSyllableColorsChange)
                    .padding(vertical = 4.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.syllable_colors_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.syllable_colors_hint), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = syllableColors, onCheckedChange = null)
            }
            Spacer(Modifier.height(16.dp))
            SettingsItem(Icons.Default.Lock, R.string.privacy_title, onPrivacy)
            SettingsItem(Icons.Default.Description, R.string.licenses_title, onLicenses)
        }
    }
}

@Composable
private fun SettingsItem(icon: ImageVector, title: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ScreenHeader(title: Int, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    }
    Spacer(Modifier.height(8.dp))
}

/** The privacy policy, shown as text in the app. */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)

    Column(modifier = modifier) {
        ScreenHeader(R.string.privacy_title, onBack)
        Text(
            stringResource(R.string.privacy_policy),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        )
    }
}

/** Licences of all bundled libraries and speech models, with the full licence texts. */
@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val licenses = remember(resources) {
        resources.openRawResource(R.raw.open_source_licenses).bufferedReader().use { it.readText() }
    }
    BackHandler(onBack = onBack)

    Column(modifier = modifier) {
        ScreenHeader(R.string.licenses_title, onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.licenses_intro), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Text(licenses, style = MaterialTheme.typography.bodySmall)
        }
    }
}
