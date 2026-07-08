package com.nathanb.lock.ui.screens.settings

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.nathanb.lock.R
import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.data.model.Schedule
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.viewmodel.LockViewModel

/** Draft edited in the add/edit sheet. id == null means "new". */
private data class ScheduleDraft(
    val id: Long?,
    val start: Int,
    val end: Int,
    val daysMask: Int,
    val profileId: Long?,
    val enabled: Boolean,
)

private fun formatMinutes(minuteOfDay: Int): String =
    "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

@Composable
private fun dayLabels(): List<String> = listOf(
    stringResource(R.string.schedule_day_mon),
    stringResource(R.string.schedule_day_tue),
    stringResource(R.string.schedule_day_wed),
    stringResource(R.string.schedule_day_thu),
    stringResource(R.string.schedule_day_fri),
    stringResource(R.string.schedule_day_sat),
    stringResource(R.string.schedule_day_sun),
)

@Composable
private fun daysSummary(daysMask: Int): String {
    val labels = dayLabels()
    return when (daysMask and Schedule.ALL_DAYS) {
        Schedule.ALL_DAYS -> stringResource(R.string.schedules_every_day)
        0b0011111 -> stringResource(R.string.schedules_weekdays)
        0b1100000 -> stringResource(R.string.schedules_weekends)
        0 -> stringResource(R.string.schedule_no_days)
        else -> (0..6).filter { (daysMask shr it) and 1 == 1 }.joinToString(", ") { labels[it] }
    }
}

@Composable
fun SchedulesScreen(
    viewModel: LockViewModel,
    onBack: () -> Unit,
) {
    val colors = LockTheme.colors
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val profiles by viewModel.profilesSorted.collectAsStateWithLifecycle()

    var draft by remember { mutableStateOf<ScheduleDraft?>(null) }

    // Exact-alarm permission can be toggled from system settings; re-check on resume.
    var canScheduleExact by remember { mutableStateOf(true) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val am = context.getSystemService(AlarmManager::class.java)
            canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                (am?.canScheduleExactAlarms() ?: true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(54.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = colors.primary,
                )
            }
            Text(
                text = stringResource(R.string.schedules_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = colors.onSurface,
                letterSpacing = (-0.5).sp,
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.schedules_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            lineHeight = 21.sp,
        )

        Spacer(Modifier.height(20.dp))

        if (!canScheduleExact) {
            ExactAlarmCard(
                onOpenSettings = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:${context.packageName}")),
                        )
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
        }

        if (schedules.isEmpty()) {
            EmptyState()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                schedules.forEach { schedule ->
                    ScheduleCard(
                        schedule = schedule,
                        profiles = profiles,
                        onToggle = { viewModel.setScheduleEnabled(schedule.id, it) },
                        onClick = {
                            draft = ScheduleDraft(
                                id = schedule.id,
                                start = schedule.startMinuteOfDay,
                                end = schedule.endMinuteOfDay,
                                daysMask = schedule.daysMask,
                                profileId = schedule.profileId,
                                enabled = schedule.enabled,
                            )
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                draft = ScheduleDraft(
                    id = null,
                    start = 22 * 60,
                    end = 7 * 60,
                    daysMask = Schedule.ALL_DAYS,
                    profileId = profiles.firstOrNull { it.isDefault }?.id,
                    enabled = true,
                )
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = Color.White,
            ),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.schedules_add), fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(100.dp))
    }

    draft?.let { current ->
        ScheduleEditorSheet(
            draft = current,
            profiles = profiles,
            onDismiss = { draft = null },
            onDelete = current.id?.let { existingId ->
                {
                    viewModel.deleteSchedule(
                        Schedule(
                            id = existingId,
                            startMinuteOfDay = current.start,
                            endMinuteOfDay = current.end,
                            daysMask = current.daysMask,
                            profileId = current.profileId,
                            enabled = current.enabled,
                        ),
                    )
                    draft = null
                }
            },
            onSave = { updated ->
                if (updated.id == null) {
                    viewModel.addSchedule(
                        startMinuteOfDay = updated.start,
                        endMinuteOfDay = updated.end,
                        daysMask = updated.daysMask,
                        profileId = updated.profileId,
                    )
                } else {
                    viewModel.updateSchedule(
                        Schedule(
                            id = updated.id,
                            startMinuteOfDay = updated.start,
                            endMinuteOfDay = updated.end,
                            daysMask = updated.daysMask,
                            profileId = updated.profileId,
                            enabled = updated.enabled,
                        ),
                    )
                }
                draft = null
            },
        )
    }
}

@Composable
private fun EmptyState() {
    val colors = LockTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = stringResource(R.string.schedules_empty),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = colors.onSurface,
        )
        Text(
            text = stringResource(R.string.schedules_empty_desc),
            fontSize = 13.sp,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExactAlarmCard(onOpenSettings: () -> Unit) {
    val colors = LockTheme.colors
    SettingsCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.schedule_exact_alarm_title),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = colors.onSurface,
            )
            Text(
                text = stringResource(R.string.schedule_exact_alarm_desc),
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
                lineHeight = 19.sp,
            )
            TextButton(onClick = onOpenSettings, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(stringResource(R.string.schedule_exact_alarm_action), color = colors.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: Schedule,
    profiles: List<Profile>,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val colors = LockTheme.colors
    val profileName = schedule.profileId
        ?.let { id -> profiles.firstOrNull { it.id == id }?.name }
        ?: stringResource(R.string.schedule_profile_default)
    val overnight = schedule.endMinuteOfDay <= schedule.startMinuteOfDay

    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = stringResource(
                        R.string.schedule_range,
                        formatMinutes(schedule.startMinuteOfDay),
                        formatMinutes(schedule.endMinuteOfDay),
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (schedule.enabled) colors.onSurface else colors.onSurfaceVariant,
                )
                Text(
                    text = buildString {
                        append(daysSummary(schedule.daysMask))
                        append(" · ")
                        append(profileName)
                        if (overnight) {
                            append(" · ")
                            append(stringResource(R.string.schedule_overnight_note))
                        }
                    },
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            Switch(
                checked = schedule.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.primary,
                    checkedThumbColor = colors.cardContainer,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditorSheet(
    draft: ScheduleDraft,
    profiles: List<Profile>,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (ScheduleDraft) -> Unit,
) {
    val colors = LockTheme.colors
    var start by remember { mutableIntStateOf(draft.start) }
    var end by remember { mutableIntStateOf(draft.end) }
    var daysMask by remember { mutableIntStateOf(draft.daysMask) }
    var profileId by remember { mutableStateOf(draft.profileId) }
    var picking by remember { mutableStateOf<String?>(null) } // "start" | "end" | null

    val labels = dayLabels()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 14.dp, bottom = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.onSurface.copy(alpha = 0.15f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(
                    if (draft.id == null) R.string.schedule_new_title else R.string.schedule_edit_title,
                ),
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = colors.onSurface,
            )

            Spacer(Modifier.height(16.dp))

            // Lock at / Unlock at
            TimeRow(
                label = stringResource(R.string.schedule_lock_at),
                value = formatMinutes(start),
                onClick = { picking = "start" },
            )
            Spacer(Modifier.height(8.dp))
            TimeRow(
                label = stringResource(R.string.schedule_unlock_at),
                value = formatMinutes(end),
                onClick = { picking = "end" },
            )

            Spacer(Modifier.height(20.dp))

            // Repeat days
            Text(
                text = stringResource(R.string.schedule_repeat),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.8.sp,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                (0..6).forEach { bit ->
                    val selected = (daysMask shr bit) and 1 == 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(if (selected) colors.primary else Color.Transparent)
                            .border(
                                width = if (selected) 0.dp else 1.5.dp,
                                color = if (selected) Color.Transparent else colors.onSurfaceVariant.copy(alpha = 0.3f),
                                shape = CircleShape,
                            )
                            .clickable { daysMask = daysMask xor (1 shl bit) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = labels[bit].take(2),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (selected) Color.White else colors.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickChip(stringResource(R.string.schedules_every_day)) { daysMask = Schedule.ALL_DAYS }
                QuickChip(stringResource(R.string.schedules_weekdays)) { daysMask = 0b0011111 }
                QuickChip(stringResource(R.string.schedules_weekends)) { daysMask = 0b1100000 }
            }

            // Profile selection (only when the user has more than the default)
            if (profiles.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.schedule_profile),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.8.sp,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    profiles.forEach { profile ->
                        ProfileRadioRow(
                            label = profile.name,
                            selected = profileId == profile.id,
                            onClick = { profileId = profile.id },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    onSave(draft.copy(start = start, end = end, daysMask = daysMask, profileId = profileId))
                },
                enabled = daysMask and Schedule.ALL_DAYS != 0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.White,
                    disabledContainerColor = colors.primary.copy(alpha = 0.4f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
            ) {
                Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
            }

            if (onDelete != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.schedule_delete), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    picking?.let { which ->
        TimePickerDialog(
            initialMinuteOfDay = if (which == "start") start else end,
            onDismiss = { picking = null },
            onConfirm = { minute ->
                if (which == "start") start = minute else end = minute
                picking = null
            },
        )
    }
}

@Composable
private fun TimeRow(label: String, value: String, onClick: () -> Unit) {
    val colors = LockTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.cardContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 15.sp, color = colors.onSurface, modifier = Modifier.weight(1f))
        Text(text = value, fontWeight = FontWeight.Black, fontSize = 20.sp, color = colors.primary)
    }
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    val colors = LockTheme.colors
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.primary.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
    }
}

@Composable
private fun ProfileRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LockTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.primary else colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Box(
                modifier = Modifier.size(22.dp).clip(CircleShape).background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White))
            }
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color(0xFFC0C0C0), CircleShape),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMinuteOfDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val colors = LockTheme.colors
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.action_save), color = colors.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = colors.onSurfaceVariant)
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        containerColor = colors.surfaceContainer,
    )
}
