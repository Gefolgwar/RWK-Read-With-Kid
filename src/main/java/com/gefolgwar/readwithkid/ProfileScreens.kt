package com.gefolgwar.readwithkid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Avatars are Material Symbols icons (Apache 2.0) on a coloured circle — no photos.
private val Avatar.icon: ImageVector
    get() = when (this) {
        Avatar.CAT -> Icons.Default.Pets
        Avatar.RABBIT -> Icons.Default.CrueltyFree
        Avatar.BEE -> Icons.Default.EmojiNature
        Avatar.BIRD -> Icons.Default.FlutterDash
        Avatar.ROCKET -> Icons.Default.RocketLaunch
        Avatar.STAR -> Icons.Default.Star
        Avatar.SUN -> Icons.Default.WbSunny
        Avatar.TREE -> Icons.Default.Park
        Avatar.BALL -> Icons.Default.SportsSoccer
        Avatar.CAR -> Icons.Default.DirectionsCar
        Avatar.MUSIC -> Icons.Default.MusicNote
        Avatar.CASTLE -> Icons.Default.Castle
    }

private val Avatar.color: Color
    get() = when (this) {
        Avatar.CAT -> Color(0xFFFFB74D)
        Avatar.RABBIT -> Color(0xFFF48FB1)
        Avatar.BEE -> Color(0xFFFFD54F)
        Avatar.BIRD -> Color(0xFF4FC3F7)
        Avatar.ROCKET -> Color(0xFF9575CD)
        Avatar.STAR -> Color(0xFFFFF176)
        Avatar.SUN -> Color(0xFFFFA726)
        Avatar.TREE -> Color(0xFF81C784)
        Avatar.BALL -> Color(0xFFAED581)
        Avatar.CAR -> Color(0xFFE57373)
        Avatar.MUSIC -> Color(0xFF4DB6AC)
        Avatar.CASTLE -> Color(0xFF90A4AE)
    }

@Composable
fun AvatarImage(avatar: Avatar, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(avatar.color)
    ) {
        Icon(avatar.icon, contentDescription = contentDescription, tint = Color(0xFF263238), modifier = Modifier.size(size * 0.6f))
    }
}

/** Parent side: the list of child profiles. */
@Composable
fun ProfilesScreen(
    children: List<ChildProfile>,
    onAdd: () -> Unit,
    onEdit: (ChildProfile) -> Unit,
    onDelete: (ChildProfile) -> Unit,
    onProgress: (ChildProfile) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<ChildProfile?>(null) }

    BackHandler(onBack = onBack)

    pendingDelete?.let { child ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_profile_title)) },
            text = { Text(stringResource(R.string.delete_profile_message, child.name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(child)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.profiles_title), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))
        if (children.isEmpty()) {
            Text(
                stringResource(R.string.profiles_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(children, key = { it.id }) { child ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                            AvatarImage(child.avatar, 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(child.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onProgress(child) }) {
                                Icon(Icons.Default.Insights, contentDescription = stringResource(R.string.progress))
                            }
                            IconButton(onClick = { onEdit(child) }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                            }
                            IconButton(onClick = { pendingDelete = child }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_profile))
        }
    }
}

@Composable
fun ProfileEditorScreen(
    editor: ProfileEditorState,
    onNameChange: (String) -> Unit,
    onAvatarChange: (Avatar) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onCancel)

    Column(modifier = modifier) {
        Text(
            stringResource(if (editor.id == 0L) R.string.new_profile_title else R.string.edit_profile_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = editor.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.profile_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.profile_avatar_label), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(72.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(Avatar.entries) { avatar ->
                val selected = avatar == editor.avatar
                Box(contentAlignment = Alignment.Center) {
                    AvatarImage(
                        avatar,
                        64.dp,
                        contentDescription = stringResource(R.string.avatar_option, avatar.ordinal + 1),
                        modifier = Modifier
                            .border(
                                width = 4.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onAvatarChange(avatar) }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cancel))
            }
            Button(onClick = onSave, enabled = editor.canSave, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

/** Child side: avatars to pick from, then the chosen child's texts as big cards. */
@Composable
fun ChildHomeScreen(
    state: ChildHomeUiState,
    onSelect: (ChildProfile) -> Unit,
    onBack: () -> Unit,
    onRead: (SavedText) -> Unit,
    onParents: () -> Unit,
    modifier: Modifier = Modifier
) {
    val child = state.child
    if (child == null) {
        AvatarPicker(state.children, onSelect, onParents, modifier)
    } else {
        ChildTexts(child, state.texts, onRead, onBack, modifier)
    }
}

@Composable
private fun AvatarPicker(
    children: List<ChildProfile>,
    onSelect: (ChildProfile) -> Unit,
    onParents: () -> Unit,
    modifier: Modifier
) {
    Column(modifier = modifier) {
        Text(stringResource(R.string.child_home_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        if (children.isEmpty()) {
            Text(
                stringResource(R.string.child_home_no_profiles),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(children, key = { it.id }) { child ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onSelect(child) }
                            .padding(8.dp)
                    ) {
                        AvatarImage(child.avatar, 112.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            child.name,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onParents, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Default.ManageAccounts, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.parents))
        }
    }
}

@Composable
private fun ChildTexts(
    child: ChildProfile,
    texts: List<SavedText>,
    onRead: (SavedText) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier
) {
    BackHandler(onBack = onBack)

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            AvatarImage(child.avatar, 48.dp)
            Spacer(Modifier.width(12.dp))
            Text(child.name, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(16.dp))
        if (texts.isEmpty()) {
            Text(stringResource(R.string.child_texts_empty), style = MaterialTheme.typography.bodyLarge)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(texts, key = { it.id }) { text ->
                    Card(
                        onClick = { onRead(text) },
                        colors = CardDefaults.cardColors(containerColor = child.avatar.color.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(20.dp)) {
                            Text(
                                text.title,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.read), modifier = Modifier.size(40.dp))
                        }
                    }
                }
            }
        }
    }
}
