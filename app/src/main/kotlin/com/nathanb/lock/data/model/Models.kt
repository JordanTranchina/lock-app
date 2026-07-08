package com.nathanb.lock.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ProfileType(val value: String) {
    STANDARD("standard"),
    NO_ESCAPE("no_escape");

    companion object {
        fun fromValue(value: String?): ProfileType =
            entries.firstOrNull { it.value == value } ?: STANDARD
    }
}

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val blockedPackages: List<String>,
    @ColumnInfo(defaultValue = "standard") val type: String = ProfileType.STANDARD.value,
    @ColumnInfo(defaultValue = "0") val isDefault: Boolean = false,
    val durationMs: Long? = null,
)

@Entity(tableName = "sessions", indices = [Index("startTime")])
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val startTime: Long,
    val endTime: Long? = null,
    val endReason: String? = null,
)

@Entity(tableName = "nfc_tags")
data class NfcTag(
    @PrimaryKey val uid: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val profileId: Long? = null,
)

/**
 * A recurring time window that auto-engages the lock at [startMinuteOfDay] and auto-releases it
 * at [endMinuteOfDay], on the weekdays selected in [daysMask].
 *
 * Start and end are independent time-of-day events, each governed by [daysMask]. For a same-day
 * window (e.g. 09:00→17:00 on weekdays) both fire on the selected days. For an overnight window
 * (e.g. 22:00→07:00) the release fires the following morning, so include the mornings you want
 * released in the mask too.
 */
@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    /** Profile to lock with. null → the current default profile at trigger time. */
    val profileId: Long? = null,
    /** Minutes since midnight (0..1439) at which the lock engages. */
    val startMinuteOfDay: Int,
    /** Minutes since midnight (0..1439) at which the lock releases. */
    val endMinuteOfDay: Int,
    /** Weekday bitmask: bit 0 = Monday … bit 6 = Sunday. */
    @ColumnInfo(defaultValue = "127") val daysMask: Int = ALL_DAYS,
) {
    companion object {
        const val ALL_DAYS = 0b1111111 // 127 — every day
    }
}

data class LockState(
    val isLocked: Boolean = false,
    val sessionStartTime: Long? = null,
    val activeProfileId: Long? = null,
    val timeoutDurationMs: Long = 5 * 60 * 60 * 1000L,
    val emergencyUnlocksRemaining: Int = 2,
    val isManualMode: Boolean = false,
    val lockDurationMs: Long? = null,
    val isNoEscape: Boolean = false,
    /** True when the active session was started by a schedule (auto-released by the schedule). */
    val isScheduled: Boolean = false,
)

data class SetupStatus(
    val permissionsOk: Boolean,
    val hasApps: Boolean,
    val hasNfcTag: Boolean,
) {
    val isComplete: Boolean get() = permissionsOk && hasApps && hasNfcTag
    /** Visible steps only — permissions hidden when granted */
    val visibleSteps: List<Boolean> get() =
        if (permissionsOk) listOf(hasApps, hasNfcTag)
        else listOf(hasApps, hasNfcTag, permissionsOk)
    val completedCount: Int get() = visibleSteps.count { it }
    val totalCount: Int get() = visibleSteps.size
}

enum class EndReason(val value: String) {
    NFC("nfc"),
    MANUAL("manual"),
    EMERGENCY("emergency"),
    TIMEOUT("timeout"),
    DURATION("duration"),
    CANCELLED("cancelled"),
    UNINSTALL("uninstall"),
    SCHEDULE("schedule"),
}
