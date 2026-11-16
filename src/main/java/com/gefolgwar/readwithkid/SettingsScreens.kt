package com.gefolgwar.readwithkid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            SectionTitle(R.string.ui_language_title)
            Card(colors = cardColors(), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.selectableGroup().padding(vertical = 8.dp)) {
                    UiLanguage.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(option == language, role = Role.RadioButton) { onLanguageChange(option) }
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 16.dp)
                        ) {
                            RadioButton(selected = option == language, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(stringResource(option.label), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Card(colors = cardColors(), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(syllableColors, role = Role.Switch, onValueChange = onSyllableColorsChange)
                        .padding(16.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.syllable_colors_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.syllable_colors_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        SyllablePreview(Modifier.padding(top = 12.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(checked = syllableColors, onCheckedChange = null)
                }
            }
            Spacer(Modifier.height(24.dp))
            Card(colors = cardColors(), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                SettingsItem(Icons.Default.Lock, R.string.privacy_title, onPrivacy)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 56.dp))
                SettingsItem(Icons.Default.Description, R.string.licenses_title, onLicenses)
            }
        }
    }
}

/** A word in the reading font with its syllables coloured, to show what the option does. */
@Composable
private fun SyllablePreview(modifier: Modifier = Modifier) {
    val word = stringResource(R.string.syllable_preview_word)
    val syllables = Syllables.split(word, TextPreparation.detectLanguage(word) ?: TextLanguage.UK)
    val palette = SyllableColors
    Text(
        buildAnnotatedString {
            syllables.forEachIndexed { i, syllable ->
                withStyle(SpanStyle(color = palette[i % palette.size])) { append(syllable) }
            }
        },
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 26.sp),
        modifier = modifier
    )
}

@Composable
private fun SectionTitle(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(start = 4.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

@Composable
private fun SettingsItem(icon: ImageVector, title: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Back arrow and the screen's title; [action] at the end of the row. */
@Composable
fun ScreenHeader(title: Int, onBack: () -> Unit, action: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        action()
    }
    Spacer(Modifier.height(16.dp))
}

/** The privacy policy, shown as text in the app. */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)

    Column(modifier = modifier) {
        ScreenHeader(R.string.privacy_title, onBack)
        Text(
            stringResource(R.string.privacy_policy),
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp)
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
                .padding(horizontal = 4.dp)
        ) {
            Text(stringResource(R.string.licenses_intro), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Text(licenses, style = MaterialTheme.typography.bodySmall)
        }
    }
}
