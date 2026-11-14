package com.gefolgwar.readwithkid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RecordVoiceOver
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Asks the parental-gate question; calls [onPassed] once it is answered correctly. */
@Composable
fun ParentalGateDialog(onPassed: () -> Unit, onDismiss: () -> Unit) {
    var question by remember { mutableStateOf(GateQuestion.next()) }
    var answer by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val check = {
        if (question.accepts(answer)) {
            onPassed()
        } else {
            wrong = true
            answer = ""
            question = GateQuestion.next()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.parental_gate_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.parental_gate_question, question.a, question.b))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.parental_gate_answer)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (wrong) ErrorText(stringResource(R.string.parental_gate_wrong))
            }
        },
        confirmButton = {
            TextButton(onClick = check, enabled = answer.isNotEmpty()) { Text(stringResource(R.string.parental_gate_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** First launch: what the app does and that audio never leaves the phone. */
@Composable
fun OnboardingScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )
            OnboardingPoint(R.string.onboarding_what_title, R.string.onboarding_what)
            OnboardingPoint(R.string.onboarding_privacy_title, R.string.onboarding_privacy)
            OnboardingPoint(R.string.onboarding_parents_title, R.string.onboarding_parents)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_start))
        }
    }
}

@Composable
private fun OnboardingPoint(title: Int, text: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    }
}

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
    onModels: () -> Unit,
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
            SettingsItem(Icons.Default.RecordVoiceOver, R.string.models_title, onModels)
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

/**
 * The privacy policy; [onOpenPublicPage] opens its public web page and is offered only
 * once that page's address is set in `privacy_policy_url`.
 */
@Composable
fun PrivacyPolicyScreen(onOpenPublicPage: (String) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val url = stringResource(R.string.privacy_policy_url)
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
        if (url.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onOpenPublicPage(url) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.privacy_open_public_page))
            }
        }
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
