package com.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.musicplayer.service.BmeDecryptDataSource
import com.musicplayer.service.BmeProfile
import com.musicplayer.service.BmeProfileManager
import com.musicplayer.ui.theme.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BmeSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var userProfiles by remember { mutableStateOf(BmeProfileManager.getUserProfiles(context)) }
    var showAdd      by remember { mutableStateOf(false) }
    var editing      by remember { mutableStateOf<BmeProfile?>(null) }
    var deleting     by remember { mutableStateOf<BmeProfile?>(null) }

    if (showAdd || editing != null) {
        BmeProfileDialog(
            initial   = editing,
            onDismiss = { showAdd = false; editing = null },
            onSave    = { p ->
                if (editing != null) BmeProfileManager.update(context, p)
                else                 BmeProfileManager.add(context, p)
                userProfiles = BmeProfileManager.getUserProfiles(context)
                showAdd = false; editing = null
            }
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor   = SurfaceDeep,
            title = { Text("מחיקת פרופיל BME", color = TextPrimary) },
            text  = { Text("למחוק את \"${target.name}\"?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    BmeProfileManager.remove(context, target.id)
                    userProfiles = BmeProfileManager.getUserProfiles(context)
                    deleting = null
                }) { Text("מחק", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text("ביטול", color = TextSecondary)
                }
            }
        )
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("הגדרות BME", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Filled.Add, "הוסף פרופיל", tint = AccentViolet)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDeep)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // מובנים
            item { SectionTitle("פרופילים מובנים") }
            items(BmeProfileManager.BUILT_IN) { profile ->
                BmeProfileCard(profile, onEdit = null, onDelete = null)
            }

            // משתמש
            item { SectionTitle("הפרופילים שלי") }
            if (userProfiles.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center) {
                        Text("אין פרופילים עדיין. לחץ + כדי להוסיף.",
                            color = TextTertiary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                items(userProfiles, key = { it.id }) { profile ->
                    BmeProfileCard(
                        profile  = profile,
                        onEdit   = { editing  = profile },
                        onDelete = { deleting = profile }
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item { BmeInfoBox() }
        }
    }
}

// ─── Profile Card ─────────────────────────────────────────────────────────────

@Composable
private fun BmeProfileCard(
    profile : BmeProfile,
    onEdit  : (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = SurfaceDeep),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (profile.isBuiltIn) Icons.Filled.Lock else Icons.Filled.AudioFile,
                null,
                tint     = if (profile.isBuiltIn) TextTertiary else AccentViolet,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.name, color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge)
                val offsetLabel = when (profile.startOffset) {
                    BmeDecryptDataSource.AUTO_DETECT -> "זיהוי אוטומטי"
                    BmeDecryptDataSource.FROM_START  -> "מתחילת הקובץ"
                    BmeDecryptDataSource.WAV_OFFSET  -> "WAV (44 bytes)"
                    else -> "offset: ${profile.startOffset}"
                }
                Text(offsetLabel, color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall)
                val patternsLabel = if (profile.filePatterns.isEmpty()) "כל הקבצים"
                                    else profile.filePatterns.joinToString(", ")
                Text(patternsLabel, color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, null, tint = TextSecondary,
                        modifier = Modifier.size(18.dp))
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ─── Add / Edit Dialog ────────────────────────────────────────────────────────

@Composable
private fun BmeProfileDialog(
    initial  : BmeProfile?,
    onDismiss: () -> Unit,
    onSave   : (BmeProfile) -> Unit
) {
    var name         by remember { mutableStateOf(initial?.name ?: "") }
    var keyText      by remember { mutableStateOf(initial?.key?.toString() ?: "") }
    var offsetMode   by remember {
        mutableStateOf(when (initial?.startOffset) {
            null, BmeDecryptDataSource.AUTO_DETECT -> 0
            BmeDecryptDataSource.FROM_START        -> 1
            BmeDecryptDataSource.WAV_OFFSET        -> 2
            else                                   -> 3
        })
    }
    var customOffset by remember {
        mutableStateOf(if ((initial?.startOffset ?: -1L) > 44L) initial!!.startOffset.toString() else "")
    }
    var patterns     by remember { mutableStateOf(initial?.filePatterns?.joinToString(", ") ?: "") }

    val keyInt  = keyText.toIntOrNull()
    val isValid = name.isNotBlank() && keyInt != null && keyInt in 1..255

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors   = CardDefaults.cardColors(containerColor = SurfaceDeep),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                Modifier.padding(20.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(if (initial == null) "הוסף פרופיל BME" else "ערוך פרופיל BME",
                    style = MaterialTheme.typography.titleMedium, color = TextPrimary)

                BmeField(value = name, label = "שם הפרופיל",
                    onChange = { name = it })

                BmeField(
                    value   = keyText,
                    label   = "מזהה BME (1–255)",
                    isError = keyText.isNotEmpty() && (keyInt == null || keyInt !in 1..255),
                    keyboard = KeyboardType.Number,
                    onChange = { keyText = it.filter(Char::isDigit) }
                )

                Text("נקודת התחלה:", color = TextSecondary,
                    style = MaterialTheme.typography.labelMedium)

                listOf(
                    "אוטומטי (MP3 / WAV)",
                    "מתחילת הקובץ",
                    "WAV standard (44 bytes)",
                    "מותאם אישית…"
                ).forEachIndexed { idx, label ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = offsetMode == idx,
                            onClick  = { offsetMode = idx },
                            colors   = RadioButtonDefaults.colors(selectedColor = AccentViolet)
                        )
                        Text(label, color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp))
                    }
                }

                if (offsetMode == 3) {
                    BmeField(
                        value    = customOffset,
                        label    = "offset (bytes)",
                        keyboard = KeyboardType.Number,
                        onChange = { customOffset = it.filter(Char::isDigit) }
                    )
                }

                BmeField(
                    value    = patterns,
                    label    = "תבניות קבצים (ריק = הכל)",
                    onChange = { patterns = it }
                )
                Text("הפרד בפסיקים: _bme, .enc, שם_תבנית",
                    color = TextTertiary, style = MaterialTheme.typography.labelSmall)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("ביטול", color = TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val resolvedOffset = when (offsetMode) {
                                0    -> BmeDecryptDataSource.AUTO_DETECT
                                1    -> BmeDecryptDataSource.FROM_START
                                2    -> BmeDecryptDataSource.WAV_OFFSET
                                else -> customOffset.toLongOrNull() ?: BmeDecryptDataSource.AUTO_DETECT
                            }
                            onSave(BmeProfile(
                                id           = initial?.id ?: UUID.randomUUID().toString(),
                                name         = name.trim(),
                                key          = keyInt!!,
                                startOffset  = resolvedOffset,
                                filePatterns = patterns.split(",").map(String::trim).filter(String::isNotEmpty),
                                isBuiltIn    = false
                            ))
                        },
                        enabled = isValid,
                        colors  = ButtonDefaults.buttonColors(containerColor = AccentViolet)
                    ) { Text("שמור") }
                }
            }
        }
    }
}

// ─── helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = AccentViolet, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
private fun BmeField(
    value   : String,
    label   : String,
    isError : Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onChange,
        label           = { Text(label) },
        singleLine      = true,
        isError         = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier        = Modifier.fillMaxWidth(),
        colors          = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = AccentViolet,
            focusedLabelColor    = AccentViolet,
            unfocusedBorderColor = TextTertiary,
            unfocusedLabelColor  = TextTertiary,
            focusedTextColor     = TextPrimary,
            unfocusedTextColor   = TextPrimary,
            cursorColor          = AccentViolet
        )
    )
}

@Composable
private fun BmeInfoBox() {
    Card(
        colors   = CardDefaults.cardColors(containerColor = AccentViolet.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("מה זה BME?", color = AccentViolet,
                style = MaterialTheme.typography.labelMedium)
            Text(
                "קבצי BME הם קבצי שמע מוגנים. הנגן מפענח אותם אוטומטית בזמן ניגון — " +
                "ללא שינוי הקובץ המקורי. קבצים רגילים ינוגנו כרגיל.",
                color = TextSecondary, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(2.dp))
            Text("פרופילי המשתמש מקבלים עדיפות על הפרופילים המובנים.",
                color = TextTertiary, style = MaterialTheme.typography.labelSmall)
        }
    }
}
