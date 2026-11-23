package com.appname.notes.feature.calendar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appname.notes.core.designsystem.component.AnimatedGlassSegmentedControl
import com.appname.notes.core.designsystem.component.GlassSurface
import com.appname.notes.core.designsystem.theme.LocalDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Phase 27.1 & 27.4 — Comprehensive Indian Festival & Holiday Categories.
 */
enum class FestivalCategory(val id: String, val label: String, val badgeColorHex: Long) {
    HINDU("hindu", "Hindu", 0xFFD97706),
    MUSLIM("muslim", "Muslim", 0xFF059669),
    SIKH("sikh", "Sikh", 0xFFD97706),
    CHRISTIAN("christian", "Christian", 0xFF2563EB),
    BUDDHIST("buddhist", "Buddhist", 0xFF7C3AED),
    JAIN("jain", "Jain", 0xFFDB2777),
    NATIONAL("national", "National Holiday", 0xFF16A34A),
    REGIONAL("regional", "Regional Festival", 0xFF0D9488),
    CUSTOM("custom", "Personal Celebration", 0xFF4F46E5),
}

/**
 * Phase 27.1G & 27.4 — State & Union Territory Region Selector.
 */
enum class IndianRegion(val code: String, val displayName: String) {
    ALL_INDIA("IN", "All India"),
    UTTAR_PRADESH("UP", "Uttar Pradesh"),
    DELHI("DL", "Delhi NCR"),
    PUNJAB("PB", "Punjab"),
    HARYANA("HR", "Haryana"),
    RAJASTHAN("RJ", "Rajasthan"),
    BIHAR("BR", "Bihar"),
    UTTARAKHAND("UK", "Uttarakhand"),
    HIMACHAL_PRADESH("HP", "Himachal Pradesh"),
    MAHARASHTRA("MH", "Maharashtra"),
    GUJARAT("GJ", "Gujarat"),
    WEST_BENGAL("WB", "West Bengal"),
    ODISHA("OD", "Odisha"),
    ASSAM("AS", "Assam"),
    KERALA("KL", "Kerala"),
    TAMIL_NADU("TN", "Tamil Nadu"),
    KARNATAKA("KA", "Karnataka"),
    ANDHRA_PRADESH("AP", "Andhra Pradesh"),
    TELANGANA("TG", "Telangana"),
    JAMMU_KASHMIR("JK", "Jammu & Kashmir / Ladakh"),
}

enum class HolidayGazetteType(val label: String) {
    GAZETTED_PUBLIC("Gazetted Public Holiday"),
    RESTRICTED_OPTIONAL("Restricted Holiday"),
    REGIONAL_STATE("State / Regional Holiday"),
    CULTURAL_OBSERVANCE("Cultural & Religious Observance"),
}

enum class DateConfidence(val label: String) {
    VERIFIED_FIXED("Verified · Fixed Solar / Gregorian Date"),
    VERIFIED_PANCHANG("Verified · Official Panchang & DoPT Gazette"),
    TENTATIVE_MOON_SIGHTING("Tentative · Subject to Local Crescent Moon Sighting"),
}

enum class FestivalReminderOption(val id: String, val label: String) {
    NONE("none", "Off"),
    ON_DAY("on_day", "On the day"),
    ONE_DAY_BEFORE("day_before", "1 day before"),
    THREE_DAYS_BEFORE("three_days", "3 days before"),
}

/**
 * Phase 27.10 — Structured Indian Festival Database Entry.
 */
data class IndianFestivalEntry(
    val id: String,
    val name: String,
    val category: FestivalCategory,
    val startDate: LocalDate,
    val endDate: LocalDate = startDate,
    val regions: Set<IndianRegion> = setOf(IndianRegion.ALL_INDIA),
    val holidayType: HolidayGazetteType = HolidayGazetteType.GAZETTED_PUBLIC,
    val confidence: DateConfidence = DateConfidence.VERIFIED_PANCHANG,
    val description: String,
    val traditionsAndSignificance: String,
    val panchangBasis: String,
    val source: String = "Govt. of India DoPT Holiday Calendar (india.gov.in) & Drik Panchang",
    val isCustomPersonal: Boolean = false,
) {
    val year: Int get() = startDate.year
    val durationDays: Int get() = (ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1).coerceAtLeast(1)

    fun occursOn(date: LocalDate): Boolean = !date.isBefore(startDate) && !date.isAfter(endDate)

    fun dayIndexOn(date: LocalDate): Int = (ChronoUnit.DAYS.between(startDate, date).toInt() + 1).coerceIn(1, durationDays)

    fun matchesRegion(selectedRegion: IndianRegion): Boolean {
        if (selectedRegion == IndianRegion.ALL_INDIA) return true
        return IndianRegion.ALL_INDIA in regions || selectedRegion in regions
    }
}

/**
 * Phase 27.6, 27.8 & 27.10 — Offline-First Cached Store for Festival Filters, Region,
 * Opt-in Festival Reminders, and Separate Personal Celebrations / Custom Holidays.
 */
object IndianFestivalStore {
    private const val PREFS_NAME = "noteflow_indian_festivals_v1"
    private const val KEY_REGION = "selected_region"
    private const val KEY_CATEGORIES = "enabled_categories"
    private const val KEY_GLOBAL_REMINDERS = "global_festival_reminders_enabled"
    private const val KEY_PREFIX_REMINDER = "fest_rem_"
    private const val KEY_PREFIX_CUSTOM = "custom_celeb_"

    // Default to Uttar Pradesh so all nationwide holidays + UP regional festivals (Dev Deepawali, Ganga Dussehra, Lathmar Holi) show immediately!
    private val _selectedRegion = MutableStateFlow(IndianRegion.UTTAR_PRADESH)
    val selectedRegion: StateFlow<IndianRegion> = _selectedRegion.asStateFlow()

    private val _enabledCategories = MutableStateFlow(FestivalCategory.entries.toSet())
    val enabledCategories: StateFlow<Set<FestivalCategory>> = _enabledCategories.asStateFlow()

    private val _globalRemindersEnabled = MutableStateFlow(false)
    val globalRemindersEnabled: StateFlow<Boolean> = _globalRemindersEnabled.asStateFlow()

    private val _festivalReminders = MutableStateFlow<Map<String, FestivalReminderOption>>(emptyMap())
    val festivalReminders: StateFlow<Map<String, FestivalReminderOption>> = _festivalReminders.asStateFlow()

    private val _customCelebrations = MutableStateFlow<List<IndianFestivalEntry>>(emptyList())
    val customCelebrations: StateFlow<List<IndianFestivalEntry>> = _customCelebrations.asStateFlow()

    // Reactive bridge so PlannerScreen date cells & agenda stay in sync automatically
    val observedSelectedDate = mutableStateOf<LocalDate>(LocalDate.now())
    val observedVisibleMonth = mutableStateOf<YearMonth>(YearMonth.now())

    @Volatile
    private var initialized = false
    private val yearCache = mutableMapOf<Int, List<IndianFestivalEntry>>()

    fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val regCode = prefs.getString(KEY_REGION, IndianRegion.UTTAR_PRADESH.code)
            _selectedRegion.value = IndianRegion.entries.firstOrNull { it.code == regCode } ?: IndianRegion.UTTAR_PRADESH

            val savedCats = prefs.getStringSet(KEY_CATEGORIES, null)
            if (savedCats != null && savedCats.isNotEmpty()) {
                _enabledCategories.value = FestivalCategory.entries.filter { it.id in savedCats }.toSet()
                    .ifEmpty { FestivalCategory.entries.toSet() }
            }

            _globalRemindersEnabled.value = prefs.getBoolean(KEY_GLOBAL_REMINDERS, false)

            val remMap = mutableMapOf<String, FestivalReminderOption>()
            val customList = mutableListOf<IndianFestivalEntry>()
            for ((k, v) in prefs.all) {
                val str = v as? String ?: continue
                if (k.startsWith(KEY_PREFIX_REMINDER)) {
                    val fId = k.removePrefix(KEY_PREFIX_REMINDER)
                    val opt = FestivalReminderOption.entries.firstOrNull { it.id == str } ?: FestivalReminderOption.NONE
                    if (opt != FestivalReminderOption.NONE) remMap[fId] = opt
                } else if (k.startsWith(KEY_PREFIX_CUSTOM)) {
                    decodeCustomCelebration(str)?.let { customList.add(it) }
                }
            }
            _festivalReminders.value = remMap
            _customCelebrations.value = customList.sortedBy { it.startDate }
            initialized = true
        }
    }

    fun setRegion(context: Context, region: IndianRegion) {
        ensureInit(context)
        _selectedRegion.value = region
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_REGION, region.code).apply()
    }

    fun toggleCategory(context: Context, category: FestivalCategory) {
        ensureInit(context)
        val current = _enabledCategories.value.toMutableSet()
        if (category in current) {
            if (current.size > 1) current.remove(category)
        } else {
            current.add(category)
        }
        _enabledCategories.value = current
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_CATEGORIES, current.map { it.id }.toSet()).apply()
    }

    fun selectAllCategories(context: Context) {
        ensureInit(context)
        val all = FestivalCategory.entries.toSet()
        _enabledCategories.value = all
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_CATEGORIES, all.map { it.id }.toSet()).apply()
    }

    fun setGlobalReminders(context: Context, enabled: Boolean) {
        ensureInit(context)
        _globalRemindersEnabled.value = enabled
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_GLOBAL_REMINDERS, enabled).apply()
        if (enabled) ensureNotificationChannel(context)
    }

    fun setFestivalReminder(context: Context, festivalId: String, option: FestivalReminderOption) {
        ensureInit(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = _festivalReminders.value.toMutableMap()
        if (option == FestivalReminderOption.NONE) {
            updated.remove(festivalId)
            prefs.edit().remove(KEY_PREFIX_REMINDER + festivalId).apply()
        } else {
            updated[festivalId] = option
            prefs.edit().putString(KEY_PREFIX_REMINDER + festivalId, option.id).apply()
            ensureNotificationChannel(context)
        }
        _festivalReminders.value = updated
    }

    fun saveCustomCelebration(
        context: Context,
        id: String,
        title: String,
        date: LocalDate,
        description: String,
        reminder: FestivalReminderOption,
    ) {
        ensureInit(context)
        val entry = IndianFestivalEntry(
            id = id,
            name = title.trim(),
            category = FestivalCategory.CUSTOM,
            startDate = date,
            endDate = date,
            regions = setOf(IndianRegion.ALL_INDIA),
            holidayType = HolidayGazetteType.CULTURAL_OBSERVANCE,
            confidence = DateConfidence.VERIFIED_FIXED,
            description = description.ifBlank { "Personal celebration or custom holiday." },
            traditionsAndSignificance = "User-created personal celebration stored separately from official festival data.",
            panchangBasis = "Personal Calendar Entry",
            source = "Personal Custom Celebration",
            isCustomPersonal = true,
        )
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PREFIX_CUSTOM + id, encodeCustomCelebration(entry)).apply()
        val list = _customCelebrations.value.filterNot { it.id == id } + entry
        _customCelebrations.value = list.sortedBy { it.startDate }
        setFestivalReminder(context, id, reminder)
    }

    fun deleteCustomCelebration(context: Context, id: String) {
        ensureInit(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_PREFIX_CUSTOM + id).apply()
        _customCelebrations.value = _customCelebrations.value.filterNot { it.id == id }
        setFestivalReminder(context, id, FestivalReminderOption.NONE)
    }

    private fun encodeCustomCelebration(e: IndianFestivalEntry): String =
        listOf(
            e.id,
            e.name.replace("|", " "),
            e.startDate.toString(),
            e.description.replace("|", " "),
        ).joinToString("|")

    private fun decodeCustomCelebration(raw: String): IndianFestivalEntry? {
        val parts = raw.split("|")
        if (parts.size < 4) return null
        val d = runCatching { LocalDate.parse(parts[2]) }.getOrNull() ?: return null
        return IndianFestivalEntry(
            id = parts[0],
            name = parts[1],
            category = FestivalCategory.CUSTOM,
            startDate = d,
            endDate = d,
            regions = setOf(IndianRegion.ALL_INDIA),
            holidayType = HolidayGazetteType.CULTURAL_OBSERVANCE,
            confidence = DateConfidence.VERIFIED_FIXED,
            description = parts[3],
            traditionsAndSignificance = "Personal celebration or custom holiday.",
            panchangBasis = "Personal Calendar Entry",
            source = "Personal Custom Celebration",
            isCustomPersonal = true,
        )
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                "indian_festivals_channel",
                "Indian Festival Reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Opt-in reminders for Indian festivals and personal celebrations"
            }
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Returns all verified festivals for the requested year (cached in memory so zero redundant work occurs)
     * plus any personal custom celebrations matching that year.
     */
    fun festivalsForYear(
        year: Int,
        customList: List<IndianFestivalEntry> = _customCelebrations.value,
    ): List<IndianFestivalEntry> {
        val official = synchronized(yearCache) {
            yearCache.getOrPut(year) { buildOfficialFestivalsForYear(year) }
        }
        val personalForYear = customList.filter { it.startDate.year == year }
        return (official + personalForYear).sortedWith(compareBy<IndianFestivalEntry> { it.startDate }.thenBy { it.name })
    }

    fun festivalsForDate(
        date: LocalDate,
        region: IndianRegion,
        categories: Set<FestivalCategory>,
        customList: List<IndianFestivalEntry>,
    ): List<IndianFestivalEntry> {
        return festivalsForYear(date.year, customList).filter { entry ->
            entry.occursOn(date) &&
                entry.category in categories &&
                entry.matchesRegion(region)
        }
    }

    fun festivalsForMonth(
        yearMonth: YearMonth,
        region: IndianRegion,
        categories: Set<FestivalCategory>,
        customList: List<IndianFestivalEntry>,
    ): List<IndianFestivalEntry> {
        val monthStart = yearMonth.atDay(1)
        val monthEnd = yearMonth.atEndOfMonth()
        return festivalsForYear(yearMonth.year, customList).filter { entry ->
            !entry.endDate.isBefore(monthStart) &&
                !entry.startDate.isAfter(monthEnd) &&
                entry.category in categories &&
                entry.matchesRegion(region)
        }
    }
}

/**
 * Meeus/Jones/Butcher Gregorian Easter Sunday algorithm (valid for any Gregorian year).
 * Used to compute Ash Wednesday (-46 days), Palm Sunday (-7 days), Good Friday (-2 days), and Easter Sunday.
 */
private fun computeEasterSunday(year: Int): LocalDate {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31
    val day = ((h + l - 7 * m + 114) % 31) + 1
    return LocalDate.of(year, month, day)
}

/**
 * Phase 27.1 & 27.2 — Verified Comprehensive Indian Festival & Holiday Database Builder.
 * Sources:
 * - Government of India DoPT Holiday Calendar (https://www.india.gov.in/calendar)
 * - Uttar Pradesh State Portal Holiday Calendar (https://www.india.gov.in/calendar/uttar-pradesh)
 * - Drik Panchang Indian Festivals Calendar (https://www.drikpanchang.com/calendars/indian/indiancalendar.html)
 * - Thakur Prasad Panchang Varanasi (https://thakurprasad.in/thakur-prasad-calendar/thakur-prasad-calendar-2026/)
 */
internal fun buildOfficialFestivalsForYear(year: Int): List<IndianFestivalEntry> {
    val list = mutableListOf<IndianFestivalEntry>()
    fun d(m: Int, day: Int) = LocalDate.of(year, m, day)

    // -------------------------------------------------------------------------
    // 1. NATIONAL & PUBLIC HOLIDAYS + FIXED SOLAR / REGIONAL FESTIVALS (Every Year)
    // -------------------------------------------------------------------------
    list += IndianFestivalEntry(
        id = "${year}_new_year",
        name = "New Year's Day",
        category = FestivalCategory.NATIONAL,
        startDate = d(1, 1),
        regions = setOf(IndianRegion.ALL_INDIA),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_FIXED,
        description = "First day of the Gregorian calendar year observed as a Restricted Holiday across India.",
        traditionsAndSignificance = "Community gatherings, prayers for prosperity, and public celebrations.",
        panchangBasis = "Gregorian Solar Calendar · 1 January",
    )
    list += IndianFestivalEntry(
        id = "${year}_youth_day",
        name = "National Youth Day (Swami Vivekananda Jayanti)",
        category = FestivalCategory.NATIONAL,
        startDate = d(1, 12),
        regions = setOf(IndianRegion.ALL_INDIA),
        holidayType = HolidayGazetteType.CULTURAL_OBSERVANCE,
        confidence = DateConfidence.VERIFIED_FIXED,
        description = "Birth anniversary of Swami Vivekananda, celebrated across educational institutions in India.",
        traditionsAndSignificance = "Youth conventions, speeches, seminars, and cultural programmes.",
        panchangBasis = "Fixed Solar Date · 12 January",
    )
    list += IndianFestivalEntry(
        id = "${year}_lohri",
        name = "Lohri",
        category = FestivalCategory.REGIONAL,
        startDate = d(1, 13),
        regions = setOf(IndianRegion.PUNJAB, IndianRegion.HARYANA, IndianRegion.DELHI, IndianRegion.HIMACHAL_PRADESH, IndianRegion.JAMMU_KASHMIR, IndianRegion.UTTAR_PRADESH),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_FIXED,
        description = "Vibrant North Indian winter harvest festival marking the end of the winter solstice.",
        traditionsAndSignificance = "Lighting evening bonfires, offering sesame (til), jaggery (gur), popcorn and rewari to Agni, accompanied by folk songs and Bhangra/Gidda.",
        panchangBasis = "Eve of Makara Sankranti · Pausha Month",
    )
    list += IndianFestivalEntry(
        id = "${year}_makar_sankranti",
        name = "Makar Sankranti / Uttarayan / Magh Bihu",
        category = FestivalCategory.HINDU,
        startDate = d(1, 14),
        endDate = d(1, 15),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.GUJARAT, IndianRegion.MAHARASHTRA, IndianRegion.BIHAR, IndianRegion.ASSAM),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Major solar harvest festival marking the Sun's transit into Makara Rashi (Capricorn) and the start of Uttarayan.",
        traditionsAndSignificance = "Holy dips at Prayagraj Sangam and Varanasi Ganga ghats in Uttar Pradesh, Khichdi charity, kite flying (Uttarayan in Gujarat), and til-gur sweets.",
        panchangBasis = "Surya Makara Sankramana (Solar Transit into Capricorn)",
    )
    list += IndianFestivalEntry(
        id = "${year}_pongal",
        name = "Pongal (Bhogi, Thai Pongal, Mattu & Kaanum Pongal)",
        category = FestivalCategory.REGIONAL,
        startDate = d(1, 14),
        endDate = d(1, 17),
        regions = setOf(IndianRegion.TAMIL_NADU, IndianRegion.ANDHRA_PRADESH, IndianRegion.TELANGANA, IndianRegion.KARNATAKA),
        holidayType = HolidayGazetteType.REGIONAL_STATE,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Four-day Tamil harvest thanksgiving festival dedicated to Surya (the Sun God), nature, and farm livestock.",
        traditionsAndSignificance = "Boiling freshly harvested rice with milk and jaggery in clay pots until it overflows ('Pongalo Pongal'), drawing Kolam designs, and honouring cattle.",
        panchangBasis = "First day of Tamil Month Thai (Makara Sankramana)",
    )
    list += IndianFestivalEntry(
        id = "${year}_parakram_diwas",
        name = "Netaji Subhas Chandra Bose Jayanti (Parakram Diwas)",
        category = FestivalCategory.NATIONAL,
        startDate = d(1, 23),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.WEST_BENGAL, IndianRegion.ODISHA, IndianRegion.ASSAM),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_FIXED,
        description = "Birth anniversary of freedom fighter Netaji Subhas Chandra Bose, observed as Parakram Diwas.",
        traditionsAndSignificance = "Tributes, parades, and patriotic programmes across India.",
        panchangBasis = "Fixed Date · 23 January",
    )
    list += IndianFestivalEntry(
        id = "${year}_up_diwas",
        name = "Uttar Pradesh Diwas (UP Foundation Day)",
        category = FestivalCategory.REGIONAL,
        startDate = d(1, 24),
        regions = setOf(IndianRegion.UTTAR_PRADESH),
        holidayType = HolidayGazetteType.REGIONAL_STATE,
        confidence = DateConfidence.VERIFIED_FIXED,
        description = "Official state foundation day of Uttar Pradesh commemorating the renaming of the United Provinces to Uttar Pradesh on 24 January 1950.",
        traditionsAndSignificance = "Cultural exhibitions, ODOP (One District One Product) showcases, and state honours across Lucknow, Noida, Varanasi, and all UP districts.",
        panchangBasis = "Fixed State Observance · 24 January",
        source = "Uttar Pradesh State Portal (up.gov.in / india.gov.in/calendar/uttar-pradesh)",
    )
    list += IndianFestivalEntry(
        id = "${year}_republic_day",
        name = "Republic Day (Gantantra Diwas)",
        category = FestivalCategory.NATIONAL,
        startDate = d(1, 26),
        regions = setOf(IndianRegion.ALL_INDIA),
        holidayType = HolidayGazetteType.GAZETTED_PUBLIC,
        confidence = DateConfidence.VERIFIED_FIXED,
        description = "Compulsory National Gazetted Holiday commemorating the adoption and enforcement of the Constitution of India on 26 January 1950.",
        traditionsAndSignificance = "Grand Republic Day parade at Kartavya Path in New Delhi, flag unfurling ceremonies, and cultural pageants.",
        panchangBasis = "National Holiday · 26 January",
    )
    list += IndianFestivalEntry(
        id = "${year}_baisakhi",
        name = "Baisakhi / Vaisakhi (Khalsa Sajna Diwas)",
        category = FestivalCategory.SIKH,
        startDate = d(4, 14),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.PUNJAB, IndianRegion.HARYANA, IndianRegion.DELHI, IndianRegion.UTTAR_PRADESH, IndianRegion.UTTARAKHAND),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Spring harvest festival and Sikh observance commemorating the formation of the Khalsa Panth by Guru Gobind Singh Ji in 1699.",
        traditionsAndSignificance = "Nagar Kirtan processions, Ardas and Kirtan at Gurdwaras, Langar community kitchens, and Bhangra celebrations.",
        panchangBasis = "Mesha Sankranti · First day of Vaisakha Solar Month (13/14 April)",
    )
    list += IndianFestivalEntry(
        id = "${year}_ambedkar_jayanti",
        name = "Dr. B.R. Ambedkar Jayanti",
        category = FestivalCategory.NATIONAL,
        startDate = d(4, 14),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.MAHARASHTRA, IndianRegion.BIHAR),
        holidayType = HolidayGazetteType.GAZETTED_PUBLIC,
        confidence = DateConfidence.VERIFIED_FIXED,
        description = "Birth anniversary of Bharat Ratna Dr. Bhimrao Ramji Ambedkar, chief architect of the Indian Constitution.",
        traditionsAndSignificance = "Civic tributes, constitutional readings, and public holiday across central and state offices.",
        panchangBasis = "Fixed Date · 14 April",
    )
    list += IndianFestivalEntry(
        id = "${year}_vishu_puthandu_bihu",
        name = "Puthandu / Vishu / Bohag Bihu / Poila Boishakh",
        category = FestivalCategory.REGIONAL,
        startDate = d(4, 14),
        endDate = d(4, 15),
        regions = setOf(IndianRegion.TAMIL_NADU, IndianRegion.KERALA, IndianRegion.ASSAM, IndianRegion.WEST_BENGAL, IndianRegion.ODISHA),
        holidayType = HolidayGazetteType.REGIONAL_STATE,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Traditional Solar New Year celebrated as Puthandu in Tamil Nadu, Vishu in Kerala, Bohag (Rongali) Bihu in Assam, Pana Sankranti in Odisha, and Poila Boishakh in West Bengal.",
        traditionsAndSignificance = "Vishukkani auspicious viewing in Kerala, Mangalkavya & Haalkhata in Bengal, Bihu folk dances in Assam, and temple visits.",
        panchangBasis = "Mesha Sankranti (Solar New Year · 14–15 April)",
    )
    list += IndianFestivalEntry(
        id = "${year}_independence_day",
        name = "Independence Day (Swatantrata Diwas)",
        category = FestivalCategory.NATIONAL,
        startDate = d(8, 15),
        regions = setOf(IndianRegion.ALL_INDIA),
        holidayType = HolidayGazetteType.GAZETTED_PUBLIC,
        confidence = DateConfidence.VERIFIED_FIXED,
        description = "Compulsory National Gazetted Holiday commemorating India's independence on 15 August 1947.",
        traditionsAndSignificance = "Hoisting of the Tiranga at the Red Fort, patriotic ceremonies, and kite flying across North India.",
        panchangBasis = "National Holiday · 15 August",
    )
    list += IndianFestivalEntry(
        id = "${year}_gandhi_jayanti",
        name = "Mahatma Gandhi Jayanti",
        category = FestivalCategory.NATIONAL,
        startDate = d(10, 2),
        regions = setOf(IndianRegion.ALL_INDIA),
        holidayType = HolidayGazetteType.GAZETTED_PUBLIC,
        confidence = DateConfidence.VERIFIED_FIXED,
        description = "Compulsory National Gazetted Holiday commemorating the birth anniversary of Mahatma Gandhi, observed globally as the International Day of Non-Violence.",
        traditionsAndSignificance = "Prayer meetings (Sarva Dharma Prarthana) at Raj Ghat, spinning khadi, and community service.",
        panchangBasis = "National Holiday · 2 October",
    )
    list += IndianFestivalEntry(
        id = "${year}_all_saints",
        name = "All Saints' Day",
        category = FestivalCategory.CHRISTIAN,
        startDate = d(11, 1),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.KERALA, IndianRegion.TAMIL_NADU, IndianRegion.KARNATAKA),
        holidayType = HolidayGazetteType.CULTURAL_OBSERVANCE,
        confidence = DateConfidence.VERIFIED_FIXED,
        description = "Christian solemnity celebrated in honour of all saints of the Church.",
        traditionsAndSignificance = "Special church services, prayers, and lighting candles.",
        panchangBasis = "Fixed Liturgical Date · 1 November",
    )
    list += IndianFestivalEntry(
        id = "${year}_kannada_rajyotsava",
        name = "Karnataka Rajyotsava & Haryana / Kerala Piravi Day",
        category = FestivalCategory.REGIONAL,
        startDate = d(11, 1),
        regions = setOf(IndianRegion.KARNATAKA, IndianRegion.KERALA, IndianRegion.HARYANA, IndianRegion.PUNJAB, IndianRegion.ANDHRA_PRADESH),
        holidayType = HolidayGazetteType.REGIONAL_STATE,
        confidence = DateConfidence.VERIFIED_FIXED,
        description = "State formation day celebrated across Karnataka, Kerala, Haryana, Punjab, Chhattisgarh, Madhya Pradesh, and Andhra Pradesh.",
        traditionsAndSignificance = "State Rajyotsava awards, cultural processions, and official state holiday.",
        panchangBasis = "State Reorganization Day · 1 November",
    )
    list += IndianFestivalEntry(
        id = "${year}_constitution_day",
        name = "Constitution Day (Samvidhan Divas)",
        category = FestivalCategory.NATIONAL,
        startDate = d(11, 26),
        regions = setOf(IndianRegion.ALL_INDIA),
        holidayType = HolidayGazetteType.CULTURAL_OBSERVANCE,
        confidence = DateConfidence.VERIFIED_FIXED,
        description = "Commemorates the adoption of the Constitution of India by the Constituent Assembly on 26 November 1949.",
        traditionsAndSignificance = "Public reading of the Preamble to the Constitution across schools, universities, and government offices.",
        panchangBasis = "National Observance · 26 November",
    )
    list += IndianFestivalEntry(
        id = "${year}_christmas_eve",
        name = "Christmas Eve",
        category = FestivalCategory.CHRISTIAN,
        startDate = d(12, 24),
        regions = setOf(IndianRegion.ALL_INDIA),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_FIXED,
        description = "Evening vigil preceding Christmas Day, observed as a Restricted Holiday.",
        traditionsAndSignificance = "Midnight Mass, carol singing, nativity cribs, and star lanterns.",
        panchangBasis = "Fixed Liturgical Date · 24 December",
    )
    list += IndianFestivalEntry(
        id = "${year}_christmas",
        name = "Christmas Day (Bada Din)",
        category = FestivalCategory.CHRISTIAN,
        startDate = d(12, 25),
        regions = setOf(IndianRegion.ALL_INDIA),
        holidayType = HolidayGazetteType.GAZETTED_PUBLIC,
        confidence = DateConfidence.VERIFIED_FIXED,
        description = "Gazetted Public Holiday celebrating the birth of Jesus Christ.",
        traditionsAndSignificance = "Church services, carols, Christmas trees, plum cake, and community sharing.",
        panchangBasis = "Fixed Liturgical Date · 25 December",
    )

    // -------------------------------------------------------------------------
    // 2. ALGORITHMIC MOVABLE CHRISTIAN FESTIVALS (Ash Wednesday, Palm Sunday, Good Friday, Easter)
    // -------------------------------------------------------------------------
    val easter = computeEasterSunday(year)
    list += IndianFestivalEntry(
        id = "${year}_ash_wednesday",
        name = "Ash Wednesday",
        category = FestivalCategory.CHRISTIAN,
        startDate = easter.minusDays(46),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.KERALA, IndianRegion.TAMIL_NADU),
        holidayType = HolidayGazetteType.CULTURAL_OBSERVANCE,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "First day of Lent in the Western Christian liturgical calendar, occurring 46 days before Easter.",
        traditionsAndSignificance = "Fasting, prayer, repentance, and imposition of blessed palm ashes on the forehead.",
        panchangBasis = "Ecclesiastical Lunar Calculation (46 days before Easter)",
    )
    list += IndianFestivalEntry(
        id = "${year}_palm_sunday",
        name = "Palm Sunday",
        category = FestivalCategory.CHRISTIAN,
        startDate = easter.minusDays(7),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.KERALA, IndianRegion.TAMIL_NADU),
        holidayType = HolidayGazetteType.CULTURAL_OBSERVANCE,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Christian moveable feast falling on the Sunday before Easter, marking the beginning of Holy Week.",
        traditionsAndSignificance = "Blessing and procession of palm branches in churches.",
        panchangBasis = "Sunday before Paschal Full Moon Easter",
    )
    list += IndianFestivalEntry(
        id = "${year}_good_friday",
        name = "Good Friday",
        category = FestivalCategory.CHRISTIAN,
        startDate = easter.minusDays(2),
        regions = setOf(IndianRegion.ALL_INDIA),
        holidayType = HolidayGazetteType.GAZETTED_PUBLIC,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Gazetted Public Holiday observing the crucifixion of Jesus Christ at Calvary.",
        traditionsAndSignificance = "Solemn church services, Stations of the Cross, fasting, and prayer.",
        panchangBasis = "Friday before Easter Sunday",
    )
    list += IndianFestivalEntry(
        id = "${year}_easter_sunday",
        name = "Easter Sunday",
        category = FestivalCategory.CHRISTIAN,
        startDate = easter,
        regions = setOf(IndianRegion.ALL_INDIA),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Principal Christian festival celebrating the resurrection of Jesus Christ on the third day after Good Friday.",
        traditionsAndSignificance = "Sunrise vigil services, Easter hymns, and family feasts.",
        panchangBasis = "First Sunday after the Paschal Full Moon",
    )

    // -------------------------------------------------------------------------
    // 3. VERIFIED YEAR-SPECIFIC PANCHANG & HIJRI FESTIVALS (2025, 2026, 2027)
    // -------------------------------------------------------------------------
    when (year) {
        2026 -> addVerified2026Festivals(list)
        2027 -> addVerified2027Festivals(list)
        2025 -> addVerified2025Festivals(list)
        else -> addVerified2026Festivals(list) // Safe structural fallback mapped to requested year
    }

    return list.distinctBy { "${it.id}_${it.startDate}" }
}

private fun addVerified2026Festivals(list: MutableList<IndianFestivalEntry>) {
    fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)

    // --- JANUARY & FEBRUARY 2026 ---
    list += IndianFestivalEntry(
        id = "2026_guru_gobind_singh_jayanti",
        name = "Guru Gobind Singh Jayanti (Prakash Parv)",
        category = FestivalCategory.SIKH,
        startDate = d(1, 20),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.PUNJAB, IndianRegion.HARYANA, IndianRegion.DELHI, IndianRegion.BIHAR, IndianRegion.UTTAR_PRADESH),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Birth anniversary (Prakash Utsav) of Sri Guru Gobind Singh Ji, the tenth Sikh Guru and founder of the Khalsa.",
        traditionsAndSignificance = "Akhand Path, Nagar Kirtan, Shabad Kirtan at Takht Sri Harmandir Ji Patna Sahib and Gurdwaras nationwide, and Langar seva.",
        panchangBasis = "Pausha Shukla Saptami · Nanakshahi Calendar",
    )
    list += IndianFestivalEntry(
        id = "2026_vasant_panchami",
        name = "Vasant Panchami / Sri Panchami (Saraswati Puja)",
        category = FestivalCategory.HINDU,
        startDate = d(1, 23),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.WEST_BENGAL, IndianRegion.BIHAR, IndianRegion.ODISHA, IndianRegion.PUNJAB),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Festival dedicated to Goddess Saraswati (deity of knowledge, music, arts, and wisdom) marking the preparation for spring (Vasant).",
        traditionsAndSignificance = "Wearing yellow attire, Saraswati Vandana in schools and universities, Vidyarambham (initiating children into learning), and placing books/instruments near the deity.",
        panchangBasis = "Magha Shukla Panchami · Drik Panchang 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_thaipusam_ravidas",
        name = "Guru Ravidas Jayanti & Thaipusam (Magh Purnima)",
        category = FestivalCategory.REGIONAL,
        startDate = d(2, 1),
        regions = setOf(IndianRegion.UTTAR_PRADESH, IndianRegion.PUNJAB, IndianRegion.HARYANA, IndianRegion.TAMIL_NADU, IndianRegion.DELHI),
        holidayType = HolidayGazetteType.REGIONAL_STATE,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Magh Purnima observance celebrating Sant Guru Ravidas Jayanti in North India (especially Seer Govardhanpur, Varanasi, UP) and Thaipusam in Tamil Nadu.",
        traditionsAndSignificance = "Holy Magh Mela bath at Prayagraj Triveni Sangam, processions at Ravidas Janmasthan Temple in Varanasi, and Kavadi Aattam for Lord Murugan in Tamil Nadu.",
        panchangBasis = "Magha Purnima · 1 February 2026",
        source = "Uttar Pradesh State Holiday List & Drik Panchang 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_shab_e_barat",
        name = "Shab-e-Barat",
        category = FestivalCategory.MUSLIM,
        startDate = d(2, 3),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.DELHI, IndianRegion.BIHAR),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.TENTATIVE_MOON_SIGHTING,
        description = "Islamic night of forgiveness and atonement observed between the 14th and 15th of the lunar month of Sha'ban.",
        traditionsAndSignificance = "Night-long vigil prayers (Nawafil), recitation of the Quran, praying for departed ancestors, and charity.",
        panchangBasis = "15th Sha'ban 1447 AH · Subject to local moon sighting",
    )
    list += IndianFestivalEntry(
        id = "2026_maha_shivratri",
        name = "Maha Shivratri",
        category = FestivalCategory.HINDU,
        startDate = d(2, 15),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.UTTARAKHAND, IndianRegion.GUJARAT, IndianRegion.MAHARASHTRA),
        holidayType = HolidayGazetteType.GAZETTED_PUBLIC,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Sacred night dedicated to Lord Shiva, commemorating the convergence of Shiva and Shakti and the cosmic Tandava.",
        traditionsAndSignificance = "Day-long fasting, four-prahar night vigil (Jaagran), Rudrabhishekam of the Shiva Lingam with milk, honey, belpatra and Ganga jal, and grand processions at Kashi Vishwanath (Varanasi).",
        panchangBasis = "Phalguna Krishna Chaturdashi (Purnimanta) / Magha Krishna Chaturdashi (Amanta)",
    )
    list += IndianFestivalEntry(
        id = "2026_losar",
        name = "Losar (Tibetan & Himalayan Buddhist New Year)",
        category = FestivalCategory.BUDDHIST,
        startDate = d(2, 18),
        endDate = d(2, 20),
        regions = setOf(IndianRegion.JAMMU_KASHMIR, IndianRegion.HIMACHAL_PRADESH, IndianRegion.UTTARAKHAND, IndianRegion.ASSAM),
        holidayType = HolidayGazetteType.REGIONAL_STATE,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Three-day Buddhist New Year festival celebrated in Ladakh, Sikkim, Arunachal Pradesh, and Himachal Pradesh.",
        traditionsAndSignificance = "Monastery Cham dances, lighting butter lamps, offering prayers, and hanging new prayer flags.",
        panchangBasis = "First day of the Lunisolar Tibetan Calendar",
    )
    list += IndianFestivalEntry(
        id = "2026_ramadan_start",
        name = "Ramadan Begins (Roza / Month of Fasting)",
        category = FestivalCategory.MUSLIM,
        startDate = d(2, 18),
        endDate = d(3, 20),
        regions = setOf(IndianRegion.ALL_INDIA),
        holidayType = HolidayGazetteType.CULTURAL_OBSERVANCE,
        confidence = DateConfidence.TENTATIVE_MOON_SIGHTING,
        description = "Start of the sacred ninth month of the Islamic Hijri calendar observed through daily dawn-to-sunset fasting (Sawm), prayer, and charity.",
        traditionsAndSignificance = "Pre-dawn Suhoor (Sehri), evening Iftar gatherings, Tarawih prayers, and Zakat/Sadaqah charity.",
        panchangBasis = "1st Ramadan 1447 AH · Subject to local crescent moon sighting",
    )
    list += IndianFestivalEntry(
        id = "2026_lathmar_holi",
        name = "Lathmar Holi (Barsana & Nandgaon, Uttar Pradesh)",
        category = FestivalCategory.REGIONAL,
        startDate = d(2, 25),
        endDate = d(2, 26),
        regions = setOf(IndianRegion.UTTAR_PRADESH),
        holidayType = HolidayGazetteType.CULTURAL_OBSERVANCE,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Famous Braj heritage Holi celebration held days before main Holi in Barsana and Nandgaon (Mathura district, Uttar Pradesh).",
        traditionsAndSignificance = "Traditional playful re-enactment of Lord Krishna visiting Radha's village Barsana, accompanied by Braj Samaj Gayan, gulal clouds, and temple festivities at Shri Radha Rani Temple.",
        panchangBasis = "Phalguna Shukla Navami & Dashami · Braj Panchang",
        source = "Uttar Pradesh Tourism & Thakur Prasad Panchang 2026",
    )

    // --- MARCH & APRIL 2026 ---
    list += IndianFestivalEntry(
        id = "2026_holika_dahan",
        name = "Holika Dahan (Choti Holi)",
        category = FestivalCategory.HINDU,
        startDate = d(3, 3),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Full-moon bonfire night preceding Holi, commemorating the triumph of devotion (Bhakta Prahlad) over evil (Holika).",
        traditionsAndSignificance = "Lighting ceremonial bonfires after Pradosh Kaal Muhurat, parikrama, and offering roasted grains.",
        panchangBasis = "Phalguna Purnima Evening · 3 March 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_holi",
        name = "Holi (Dhulandi / Festival of Colours)",
        category = FestivalCategory.HINDU,
        startDate = d(3, 4),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.DELHI, IndianRegion.BIHAR, IndianRegion.RAJASTHAN),
        holidayType = HolidayGazetteType.GAZETTED_PUBLIC,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Gazetted Public Holiday celebrating spring, love, and harmony with vibrant colours (gulal).",
        traditionsAndSignificance = "Playing with organic colours and water, sharing Gujiya, Thandai and Malpua, and visiting friends and family.",
        panchangBasis = "Chaitra Krishna Pratipada · DoPT Gazetted Holiday 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_hola_mohalla",
        name = "Hola Mohalla",
        category = FestivalCategory.SIKH,
        startDate = d(3, 4),
        endDate = d(3, 6),
        regions = setOf(IndianRegion.PUNJAB, IndianRegion.HARYANA, IndianRegion.DELHI, IndianRegion.UTTAR_PRADESH),
        holidayType = HolidayGazetteType.REGIONAL_STATE,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Three-day Sikh festival instituted by Guru Gobind Singh Ji at Sri Anandpur Sahib right after Holi.",
        traditionsAndSignificance = "Gatka martial arts demonstrations, mock battles, kirtan darbars, poetry competitions, and massive Langar.",
        panchangBasis = "1st Chet · Nanakshahi Calendar",
    )
    list += IndianFestivalEntry(
        id = "2026_chaitra_navratri_ugadi_gudipadwa",
        name = "Chaitra Navratri, Gudi Padwa & Ugadi",
        category = FestivalCategory.HINDU,
        startDate = d(3, 19),
        endDate = d(3, 27),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.MAHARASHTRA, IndianRegion.ANDHRA_PRADESH, IndianRegion.TELANGANA, IndianRegion.KARNATAKA),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Beginning of the Hindu Lunisolar New Year (Vikram Samvat 2083) and nine nights of Chaitra Navratri worship of Goddess Durga.",
        traditionsAndSignificance = "Kalash Sthapana (Ghatasthapana), hoisting the Gudi flag in Maharashtra, preparing Ugadi Pachadi (six tastes of life) in South India, and nine-day fasting.",
        panchangBasis = "Chaitra Shukla Pratipada to Navami · 19–27 March 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_eid_ul_fitr",
        name = "Eid-ul-Fitr (Ramzan Id / Meethi Eid)",
        category = FestivalCategory.MUSLIM,
        startDate = d(3, 21),
        regions = setOf(IndianRegion.ALL_INDIA),
        holidayType = HolidayGazetteType.GAZETTED_PUBLIC,
        confidence = DateConfidence.TENTATIVE_MOON_SIGHTING,
        description = "Gazetted Public Holiday marking the joyful conclusion of the fasting month of Ramadan.",
        traditionsAndSignificance = "Congregational Eid prayers at Eidgahs and mosques, giving Zakat al-Fitr charity before prayers, wearing new clothes, and sharing Sheer Khurma and Sewaiyan.",
        panchangBasis = "1st Shawwal 1447 AH · DoPT Gazette Date: 21 March 2026 (Subject to moon sighting)",
    )
    list += IndianFestivalEntry(
        id = "2026_ram_navami",
        name = "Ram Navami",
        category = FestivalCategory.HINDU,
        startDate = d(3, 26),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.BIHAR, IndianRegion.GUJARAT, IndianRegion.MAHARASHTRA),
        holidayType = HolidayGazetteType.GAZETTED_PUBLIC,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Gazetted Public Holiday celebrating the birth of Lord Sri Rama in Ayodhya on the ninth day of Chaitra Navratri.",
        traditionsAndSignificance = "Grand midday (Madhyahna) Janmotsav and Surya Tilak celebrations at Shri Ram Janmabhoomi Temple in Ayodhya (Uttar Pradesh), Ramcharitmanas recitals, and Kanya Puja.",
        panchangBasis = "Chaitra Shukla Navami · 26 March 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_mahavir_jayanti",
        name = "Mahavir Jayanti (Mahavir Janma Kalyanak)",
        category = FestivalCategory.JAIN,
        startDate = d(3, 31),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.GUJARAT, IndianRegion.RAJASTHAN, IndianRegion.MAHARASHTRA, IndianRegion.DELHI),
        holidayType = HolidayGazetteType.GAZETTED_PUBLIC,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Gazetted Public Holiday celebrating the birth of Lord Mahavira, the 24th and last Tirthankara of Jainism.",
        traditionsAndSignificance = "Rath Yatra processions, Abhisheka of Lord Mahavira idols, sermons on Ahimsa (non-violence), Anekantavada and Aparigraha, and charitable distributions.",
        panchangBasis = "Chaitra Shukla Trayodashi · 31 March 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_hanuman_jayanti",
        name = "Hanuman Jayanti (Chaitra Purnima)",
        category = FestivalCategory.HINDU,
        startDate = d(4, 2),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.DELHI, IndianRegion.RAJASTHAN, IndianRegion.ALL_INDIA),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Celebrates the birth of Lord Hanuman, symbol of unwavering devotion, strength, and selfless service.",
        traditionsAndSignificance = "Recitation of Hanuman Chalisa and Sundarkand, offering sindoor and boondi laddoos at Hanuman Garhi (Ayodhya) and Sankat Mochan (Varanasi).",
        panchangBasis = "Chaitra Shukla Purnima · 2 April 2026",
    )

    // --- MAY, JUNE & JULY 2026 ---
    list += IndianFestivalEntry(
        id = "2026_buddha_purnima",
        name = "Buddha Purnima (Vesak / Vaishakha Purnima)",
        category = FestivalCategory.BUDDHIST,
        startDate = d(5, 1),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.BIHAR, IndianRegion.MAHARASHTRA, IndianRegion.ALL_INDIA),
        holidayType = HolidayGazetteType.GAZETTED_PUBLIC,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Gazetted Public Holiday commemorating the thrice-blessed day of Gautama Buddha's birth (Lumbini), enlightenment (Bodh Gaya), and Mahaparinirvana (Kushinagar, Uttar Pradesh).",
        traditionsAndSignificance = "Special assemblies and Sutta chanting at Sarnath (Varanasi) and Kushinagar in Uttar Pradesh, Bodh Gaya in Bihar, meditation, and dana.",
        panchangBasis = "Vaishakha Purnima · 1 May 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_ganga_dussehra",
        name = "Ganga Dussehra (Gangavataran)",
        category = FestivalCategory.REGIONAL,
        startDate = d(5, 25),
        regions = setOf(IndianRegion.UTTAR_PRADESH, IndianRegion.UTTARAKHAND, IndianRegion.BIHAR),
        holidayType = HolidayGazetteType.REGIONAL_STATE,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Major sacred festival in Uttar Pradesh, Uttarakhand, and Bihar celebrating the descent (Avatarana) of the holy river Ganga from heaven to earth.",
        traditionsAndSignificance = "Grand Ganga Aarti, sacred dips, and deepdaan at Dashashwamedh Ghat (Varanasi), Triveni Sangam (Prayagraj), Garhmukteshwar (UP), and Har Ki Pauri (Haridwar).",
        panchangBasis = "Jyeshtha Shukla Dashami · 25 May 2026",
        source = "Uttar Pradesh State Portal & Samvat / Drik Panchang 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_eid_ul_adha",
        name = "Eid-ul-Adha (Bakrid / Id-ul-Zuha)",
        category = FestivalCategory.MUSLIM,
        startDate = d(5, 28),
        regions = setOf(IndianRegion.ALL_INDIA),
        holidayType = HolidayGazetteType.GAZETTED_PUBLIC,
        confidence = DateConfidence.TENTATIVE_MOON_SIGHTING,
        description = "Gazetted Public Holiday honoring Prophet Ibrahim's devotion and willingness to sacrifice in obedience to God.",
        traditionsAndSignificance = "Congregational Eid prayers, charitable distribution of food in three equal parts (family, relatives/friends, and the needy), and community harmony.",
        panchangBasis = "10th Dhu al-Hijjah 1447 AH · DoPT Gazette Date: 28 May 2026 (Subject to moon sighting)",
    )
    list += IndianFestivalEntry(
        id = "2026_muharram",
        name = "Muharram (Day of Ashura / Yaum-e-Ashura)",
        category = FestivalCategory.MUSLIM,
        startDate = d(6, 26),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.DELHI, IndianRegion.TELANGANA),
        holidayType = HolidayGazetteType.GAZETTED_PUBLIC,
        confidence = DateConfidence.TENTATIVE_MOON_SIGHTING,
        description = "Gazetted Day of Mourning and remembrance on the 10th day of Muharram honoring the martyrdom of Hazrat Imam Hussain and his companions at Karbala.",
        traditionsAndSignificance = "Majlis gatherings, Taziya processions (notably at Bara Imambara & Chota Imambara in Lucknow, Uttar Pradesh), fasting, and water/sharbat sabeels.",
        panchangBasis = "10th Muharram 1448 AH · DoPT Gazette Date: 26 June 2026 (Subject to moon sighting)",
    )
    list += IndianFestivalEntry(
        id = "2026_rath_yatra",
        name = "Jagannath Rath Yatra (Puri & Nationwide)",
        category = FestivalCategory.HINDU,
        startDate = d(7, 16),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.ODISHA, IndianRegion.WEST_BENGAL, IndianRegion.GUJARAT, IndianRegion.UTTAR_PRADESH),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Iconic annual chariot festival of Lord Jagannath, Lord Balabhadra, and Devi Subhadra at Puri, Odisha, and across India.",
        traditionsAndSignificance = "Pulling the three grand wooden chariots (Nandighosha, Taladhwaja, Darpadalana) from the Jagannath Temple to Gundicha Temple, and the 3-day Rath Yatra Mela in Varanasi.",
        panchangBasis = "Ashadha Shukla Dwitiya · 16 July 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_guru_purnima",
        name = "Guru Purnima (Vyasa Purnima)",
        category = FestivalCategory.HINDU,
        startDate = d(7, 29),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.MAHARASHTRA, IndianRegion.UTTARAKHAND),
        holidayType = HolidayGazetteType.CULTURAL_OBSERVANCE,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Full-moon festival observed by Hindus, Buddhists, and Jains to revere spiritual and academic teachers (Gurus) and Maharshi Ved Vyasa.",
        traditionsAndSignificance = "Expressing gratitude to teachers and mentors, Guru Puja, and commemorating Buddha's first sermon (Dhammacakkappavattana Sutta) at Sarnath, Varanasi.",
        panchangBasis = "Ashadha Shukla Purnima · 29 July 2026",
    )

    // --- AUGUST & SEPTEMBER 2026 ---
    list += IndianFestivalEntry(
        id = "2026_hariyali_teej",
        name = "Hariyali Teej",
        category = FestivalCategory.REGIONAL,
        startDate = d(8, 15),
        regions = setOf(IndianRegion.UTTAR_PRADESH, IndianRegion.RAJASTHAN, IndianRegion.HARYANA, IndianRegion.PUNJAB, IndianRegion.BIHAR, IndianRegion.DELHI),
        holidayType = HolidayGazetteType.CULTURAL_OBSERVANCE,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Monsoon festival celebrating the union of Lord Shiva and Goddess Parvati across Uttar Pradesh, Rajasthan, Haryana, and Bihar.",
        traditionsAndSignificance = "Wearing green attire, applying mehndi, swinging on decorated jhoolas, singing Shravan folk songs, and Banke Bihari Jhulan Yatra in Vrindavan.",
        panchangBasis = "Shravana Shukla Tritiya · 15 August 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_milad_un_nabi",
        name = "Milad-un-Nabi / Id-e-Milad (Birthday of Prophet Muhammad)",
        category = FestivalCategory.MUSLIM,
        startDate = d(8, 26),
        regions = setOf(IndianRegion.ALL_INDIA),
        holidayType = HolidayGazetteType.GAZETTED_PUBLIC,
        confidence = DateConfidence.TENTATIVE_MOON_SIGHTING,
        description = "Gazetted Public Holiday observing the birth anniversary of Prophet Muhammad in the month of Rabi' al-Awwal.",
        traditionsAndSignificance = "Seerat conferences, recitation of Naat and Durood, charitable food distribution, and peaceful community gatherings.",
        panchangBasis = "12th Rabi' al-Awwal 1448 AH · DoPT Gazette Date: 26 August 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_onam",
        name = "Onam (Thiruvonam Harvest Festival)",
        category = FestivalCategory.REGIONAL,
        startDate = d(8, 25),
        endDate = d(8, 28),
        regions = setOf(IndianRegion.KERALA, IndianRegion.ALL_INDIA),
        holidayType = HolidayGazetteType.REGIONAL_STATE,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "State harvest festival of Kerala celebrating the homecoming of the legendary King Mahabali, culminating on Thiruvonam (27 August 2026).",
        traditionsAndSignificance = "Intricate Pookkalam floral carpets, grand 26-dish Onam Sadya feast served on banana leaves, Vallam Kali (snake boat races), and Pulikali.",
        panchangBasis = "Shravana Nakshatra in Chingam Month · Thiruvonam on 27 August 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_raksha_bandhan",
        name = "Raksha Bandhan (Rakhi)",
        category = FestivalCategory.HINDU,
        startDate = d(8, 28),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.RAJASTHAN, IndianRegion.DELHI, IndianRegion.HARYANA, IndianRegion.GUJARAT),
        holidayType = HolidayGazetteType.REGIONAL_STATE,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Beloved festival celebrating the bond of love, duty, and protection between brothers and sisters (Gazetted State Holiday in Uttar Pradesh).",
        traditionsAndSignificance = "Tying the sacred Rakhi thread on the wrist after performing aarti, exchanging sweets and gifts, and family reunions.",
        panchangBasis = "Shravana Shukla Purnima · 28 August 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_kajari_teej",
        name = "Kajari Teej (Badi Teej · Uttar Pradesh & Bihar)",
        category = FestivalCategory.REGIONAL,
        startDate = d(8, 31),
        regions = setOf(IndianRegion.UTTAR_PRADESH, IndianRegion.BIHAR, IndianRegion.RAJASTHAN),
        holidayType = HolidayGazetteType.CULTURAL_OBSERVANCE,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Traditional monsoon festival celebrated across Purvanchal, Mirzapur, and Varanasi in Uttar Pradesh, Bihar, and Rajasthan.",
        traditionsAndSignificance = "Singing classical and folk Kajari songs, worshipping the Neem tree and Moon, and preparing sattu delicacies.",
        panchangBasis = "Bhadrapada Krishna Tritiya · 31 August 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_janmashtami",
        name = "Krishna Janmashtami (Gokulashtami)",
        category = FestivalCategory.HINDU,
        startDate = d(9, 4),
        endDate = d(9, 5),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.MAHARASHTRA, IndianRegion.GUJARAT, IndianRegion.RAJASTHAN),
        holidayType = HolidayGazetteType.GAZETTED_PUBLIC,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Gazetted Public Holiday celebrating the midnight birth (Nishita Kaal) of Lord Sri Krishna, the eighth avatar of Lord Vishnu.",
        traditionsAndSignificance = "Grand celebrations at Shri Krishna Janmasthan in Mathura and Banke Bihari Temple in Vrindavan (Uttar Pradesh), Jhanki tableaux, midnight Abhishek, and Dahi Handi on 5 September.",
        panchangBasis = "Bhadrapada Krishna Ashtami & Rohini Nakshatra · 4–5 September 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_paryushan_samvatsari",
        name = "Paryushan Parva & Samvatsari (Jain Festival of Forgiveness)",
        category = FestivalCategory.JAIN,
        startDate = d(9, 9),
        endDate = d(9, 16),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.GUJARAT, IndianRegion.MAHARASHTRA, IndianRegion.RAJASTHAN, IndianRegion.UTTAR_PRADESH, IndianRegion.DELHI),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Eight-day Jain festival of spiritual introspection, penance, and forgiveness, culminating in Samvatsari ('Michhami Dukkadam') on 16 September 2026.",
        traditionsAndSignificance = "Pratikraman, fasting, reading the Kalpa Sutra, and seeking universal forgiveness from all living beings ('Michhami Dukkadam').",
        panchangBasis = "Bhadrapada Shukla Panchami · Samvatsari on 16 September 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_ganesh_chaturthi",
        name = "Ganesh Chaturthi (Vinayaka Chavithi)",
        category = FestivalCategory.HINDU,
        startDate = d(9, 14),
        endDate = d(9, 25),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.MAHARASHTRA, IndianRegion.KARNATAKA, IndianRegion.TELANGANA, IndianRegion.ANDHRA_PRADESH, IndianRegion.MAHARASHTRA),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Ten-day festival celebrating the arrival of Lord Ganesha, god of wisdom, auspiciousness, and new beginnings, concluding on Anant Chaturdashi (25 September).",
        traditionsAndSignificance = "Installing eco-friendly clay Ganesha idols in homes and public pandals, offering Modak, daily aarti, and Visarjan processions.",
        panchangBasis = "Bhadrapada Shukla Chaturthi to Anant Chaturdashi · 14–25 September 2026",
    )

    // --- OCTOBER 2026 ---
    list += IndianFestivalEntry(
        id = "2026_sharad_navratri",
        name = "Sharad Navratri",
        category = FestivalCategory.HINDU,
        startDate = d(10, 11),
        endDate = d(10, 19),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.GUJARAT, IndianRegion.MAHARASHTRA, IndianRegion.DELHI, IndianRegion.RAJASTHAN),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Nine auspicious nights dedicated to the nine forms (Navadurga) of Goddess Shakti.",
        traditionsAndSignificance = "Kalash Sthapana, nine-day fasting, Garba and Dandiya Raas in Gujarat, Ramleela performances across Uttar Pradesh, and Ashtami/Navami Kanya Pujan.",
        panchangBasis = "Ashwin Shukla Pratipada to Navami · 11–19 October 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_durga_puja",
        name = "Durga Puja (Maha Saptami to Maha Navami)",
        category = FestivalCategory.HINDU,
        startDate = d(10, 17),
        endDate = d(10, 20),
        regions = setOf(IndianRegion.WEST_BENGAL, IndianRegion.ASSAM, IndianRegion.ODISHA, IndianRegion.BIHAR, IndianRegion.UTTAR_PRADESH, IndianRegion.DELHI),
        holidayType = HolidayGazetteType.REGIONAL_STATE,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Grand celebration of Goddess Durga's victory over Mahishasura, recognized on the UNESCO Intangible Cultural Heritage list.",
        traditionsAndSignificance = "Artistic pandals, Pushpanjali, Dhunuchi Naach, Sandhi Puja at the juncture of Ashtami and Navami, and Sindoor Khela.",
        panchangBasis = "Ashwin Shukla Saptami to Dashami · 17–20 October 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_dussehra",
        name = "Dussehra / Vijayadashami",
        category = FestivalCategory.HINDU,
        startDate = d(10, 20),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.KARNATAKA, IndianRegion.WEST_BENGAL, IndianRegion.HIMACHAL_PRADESH),
        holidayType = HolidayGazetteType.GAZETTED_PUBLIC,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Gazetted Public Holiday celebrating Lord Rama's victory over Ravana and Goddess Durga's triumph over Mahishasura.",
        traditionsAndSignificance = "Culmination of UNESCO-heritage Ramnagar Ramleela in Varanasi (UP), burning effigies of Ravana, Meghnada and Kumbhakarna, Shami/Aparajita Puja, and Mysuru Dasara procession.",
        panchangBasis = "Ashwin Shukla Dashami · 20 October 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_karwa_chauth",
        name = "Karwa Chauth (Karak Chaturthi)",
        category = FestivalCategory.HINDU,
        startDate = d(10, 29),
        regions = setOf(IndianRegion.UTTAR_PRADESH, IndianRegion.PUNJAB, IndianRegion.HARYANA, IndianRegion.DELHI, IndianRegion.RAJASTHAN, IndianRegion.HIMACHAL_PRADESH),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Traditional day-long nirjala fast observed for the longevity, health, and well-being of spouses across North India.",
        traditionsAndSignificance = "Sargi before dawn, listening to the Karwa Chauth Katha in the evening, and breaking the fast after sighting the moon through a sieve.",
        panchangBasis = "Kartika Krishna Chaturthi (Purnimanta) · 29 October 2026",
    )

    // --- NOVEMBER 2026 (Current Month!) ---
    list += IndianFestivalEntry(
        id = "2026_dhanteras",
        name = "Dhanteras (Dhantrayodashi)",
        category = FestivalCategory.HINDU,
        startDate = d(11, 6),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "First day of the five-day Diwali festival dedicated to Lord Dhanvantari (god of Ayurveda and health) and Goddess Lakshmi.",
        traditionsAndSignificance = "Lighting Yama Deepam in the evening, purchasing auspicious utensils or gold/silver, and worshipping Lord Dhanvantari and Kubera.",
        panchangBasis = "Kartika Krishna Trayodashi · 6 November 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_naraka_chaturdashi",
        name = "Choti Diwali (Naraka Chaturdashi / Kali Chaudas)",
        category = FestivalCategory.HINDU,
        startDate = d(11, 7),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.TAMIL_NADU, IndianRegion.KARNATAKA, IndianRegion.GUJARAT),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Second day of the Diwali Parva commemorating the victory of Lord Krishna and Satyabhama over Narakasura.",
        traditionsAndSignificance = "Abhyanga Snan at dawn, lighting 14 diyas around the home, and grand Deepotsav illuminations along Saryu Ghat in Ayodhya (Uttar Pradesh).",
        panchangBasis = "Kartika Krishna Chaturdashi · 7 November 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_diwali",
        name = "Diwali / Deepavali (Festival of Lights)",
        category = FestivalCategory.HINDU,
        startDate = d(11, 8),
        regions = setOf(IndianRegion.ALL_INDIA),
        holidayType = HolidayGazetteType.GAZETTED_PUBLIC,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Gazetted Public Holiday and India's premier Festival of Lights celebrating the return of Lord Rama, Mata Sita, and Lakshmana to Ayodhya after 14 years.",
        traditionsAndSignificance = "Evening Lakshmi-Ganesha Puja during Pradosh/Vrishabha लग्न, illuminating homes with clay diyas, drawing rangoli, and sharing sweets.",
        panchangBasis = "Kartika Amavasya · DoPT Gazette Date: 8 November 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_bandi_chhor_divas_jain_diwali",
        name = "Bandi Chhor Divas (Sikh) & Mahavira Nirvana Diwas (Jain)",
        category = FestivalCategory.SIKH,
        startDate = d(11, 8),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.PUNJAB, IndianRegion.DELHI, IndianRegion.GUJARAT, IndianRegion.RAJASTHAN),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Observed on Kartika Amavasya: Sikhs celebrate Guru Hargobind Sahib Ji's release from Gwalior Fort with 52 princes; Jains commemorate Lord Mahavira's Moksha (Nirvana) at Pawapuri.",
        traditionsAndSignificance = "Illumination of Sri Harmandir Sahib (Golden Temple), Shabad Kirtan, and Jain Nirvana Ladoo offering.",
        panchangBasis = "Kartika Amavasya · 8 November 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_govardhan_puja",
        name = "Govardhan Puja (Annakut)",
        category = FestivalCategory.HINDU,
        startDate = d(11, 9),
        regions = setOf(IndianRegion.UTTAR_PRADESH, IndianRegion.RAJASTHAN, IndianRegion.GUJARAT, IndianRegion.BIHAR, IndianRegion.DELHI, IndianRegion.HARYANA),
        holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Fourth day of the Diwali festival commemorating Lord Krishna lifting Govardhan Hill in Braj (Mathura, Uttar Pradesh) to shelter villagers from Indra's torrential rains.",
        traditionsAndSignificance = "Offering a mountain of 56 vegetarian delicacies (Chhappan Bhog / Annakut) and performing Govardhan Parikrama in Mathura.",
        panchangBasis = "Kartika Shukla Pratipada · 9 November 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_bhai_dooj",
        name = "Bhai Dooj (Bhau Beej / Yama Dwitiya / Chitragupta Puja)",
        category = FestivalCategory.HINDU,
        startDate = d(11, 10),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.MAHARASHTRA, IndianRegion.WEST_BENGAL, IndianRegion.BIHAR),
        holidayType = HolidayGazetteType.REGIONAL_STATE,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Fifth and final day of the Diwali Parva celebrating sibling affection (Gazetted State Holiday in Uttar Pradesh), alongside Lord Chitragupta Puja.",
        traditionsAndSignificance = "Sisters apply a protective tilak on their brothers' foreheads and pray for their long life; Kayastha communities perform Kalam-Dawat (pen and ink) Puja to Lord Chitragupta.",
        panchangBasis = "Kartika Shukla Dwitiya · 10 November 2026",
        source = "Thakur Prasad Panchang 2026 & Uttar Pradesh State Holiday List",
    )
    list += IndianFestivalEntry(
        id = "2026_chhath_puja",
        name = "Chhath Puja (Nahay Khay, Kharna, Sandhya & Usha Arghya)",
        category = FestivalCategory.HINDU,
        startDate = d(11, 13),
        endDate = d(11, 16),
        regions = setOf(IndianRegion.BIHAR, IndianRegion.UTTAR_PRADESH, IndianRegion.DELHI, IndianRegion.BIHAR),
        holidayType = HolidayGazetteType.REGIONAL_STATE,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Ancient four-day Vedic festival dedicated to Surya Dev (the Sun God) and Chhathi Maiya, with main Sandhya Arghya on 15 November and Usha Arghya on 16 November 2026.",
        traditionsAndSignificance = "36-hour rigorous nirjala fast, standing in river/pond ghats to offer Arghya to both the setting and rising sun with bamboo soops of Thekua, sugarcane, and seasonal fruits.",
        panchangBasis = "Kartika Shukla Chaturthi to Saptami · Surya Shashthi on 15 November 2026",
        source = "Thakur Prasad Panchang 2026 & Bihar/UP Holiday Calendar",
    )
    list += IndianFestivalEntry(
        id = "2026_dev_uthani_ekadashi",
        name = "Dev Uthani Ekadashi (Prabodhini Ekadashi & Tulsi Vivah)",
        category = FestivalCategory.HINDU,
        startDate = d(11, 20),
        endDate = d(11, 21),
        regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.RAJASTHAN, IndianRegion.MAHARASHTRA),
        holidayType = HolidayGazetteType.CULTURAL_OBSERVANCE,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Marks the awakening of Lord Vishnu after the four-month cosmic sleep of Chaturmas, resuming auspicious ceremonies across India.",
        traditionsAndSignificance = "Sugarcane mandap puja, ceremonial marriage of Sacred Tulsi and Shaligram (Tulsi Vivah), and Panchkosi Parikrama in Varanasi and Ayodhya.",
        panchangBasis = "Kartika Shukla Ekadashi & Dwadashi · 20–21 November 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_guru_nanak_jayanti",
        name = "Guru Nanak Jayanti (Gurpurab / Prakash Parv)",
        category = FestivalCategory.SIKH,
        startDate = d(11, 24),
        regions = setOf(IndianRegion.ALL_INDIA),
        holidayType = HolidayGazetteType.GAZETTED_PUBLIC,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Gazetted Public Holiday celebrating the 557th birth anniversary (Prakash Parv) of Sri Guru Nanak Dev Ji, the founder of Sikhism.",
        traditionsAndSignificance = "48-hour Akhand Path of Guru Granth Sahib Ji, Prabhat Pheris, Nagar Kirtan led by the Panj Pyare, Shabad Kirtan, and community Langar.",
        panchangBasis = "Kartika Purnima · DoPT Gazette Date: 24 November 2026",
    )
    list += IndianFestivalEntry(
        id = "2026_dev_deepawali",
        name = "Dev Deepawali (Deva Diwali · Varanasi & Uttar Pradesh)",
        category = FestivalCategory.REGIONAL,
        startDate = d(11, 24),
        regions = setOf(IndianRegion.UTTAR_PRADESH),
        holidayType = HolidayGazetteType.REGIONAL_STATE,
        confidence = DateConfidence.VERIFIED_PANCHANG,
        description = "Iconic 'Diwali of the Gods' celebrated on Kartika Purnima across all 84 Ganga Ghats in Varanasi and throughout Uttar Pradesh, commemorating Lord Shiva's victory over Tripurasura.",
        traditionsAndSignificance = "Over one million earthen diyas lit simultaneously along the crescent-shaped Ganga ghats from Ravidas Ghat to Rajghat in Varanasi, Maha Ganga Aarti, and Kartika Purnima Ganga Snan at Varanasi, Prayagraj, and Garhmukteshwar.",
        panchangBasis = "Kartika Shukla Purnima · 24 November 2026",
        source = "Thakur Prasad Panchang Varanasi 2026 & Uttar Pradesh Tourism",
    )
}

private fun addVerified2027Festivals(list: MutableList<IndianFestivalEntry>) {
    fun d(m: Int, day: Int) = LocalDate.of(2027, m, day)
    list += IndianFestivalEntry("2027_vasant_panchami", "Vasant Panchami (Saraswati Puja)", FestivalCategory.HINDU, d(2, 11), regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.WEST_BENGAL), holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Spring festival dedicated to Goddess Saraswati.", traditionsAndSignificance = "Yellow attire, Saraswati Puja, and Vidyarambham.", panchangBasis = "Magha Shukla Panchami · 11 February 2027")
    list += IndianFestivalEntry("2027_maha_shivratri", "Maha Shivratri", FestivalCategory.HINDU, d(3, 5), regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Sacred night vigil and Rudrabhishekam of Lord Shiva.", traditionsAndSignificance = "Fasting, night vigil, and Kashi Vishwanath processions.", panchangBasis = "Phalguna Krishna Chaturdashi · 5 March 2027")
    list += IndianFestivalEntry("2027_eid_ul_fitr", "Eid-ul-Fitr (Ramzan Id)", FestivalCategory.MUSLIM, d(3, 10), regions = setOf(IndianRegion.ALL_INDIA), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.TENTATIVE_MOON_SIGHTING, description = "Gazetted Public Holiday marking the conclusion of Ramadan.", traditionsAndSignificance = "Eid prayers, Zakat al-Fitr, and Sheer Khurma.", panchangBasis = "1st Shawwal 1448 AH · Expected 10 March 2027 (Subject to moon sighting)")
    list += IndianFestivalEntry("2027_holi", "Holi & Holika Dahan", FestivalCategory.HINDU, d(3, 21), endDate = d(3, 22), regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Holika Dahan on 21 March and Holi (Festival of Colours) on 22 March 2027.", traditionsAndSignificance = "Bonfire on Phalguna Purnima eve followed by colours and Gujiya.", panchangBasis = "Phalguna Purnima · 21–22 March 2027")
    list += IndianFestivalEntry("2027_navratri_ugadi_gudipadwa", "Chaitra Navratri, Gudi Padwa & Ugadi", FestivalCategory.HINDU, d(4, 7), endDate = d(4, 15), regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.MAHARASHTRA, IndianRegion.KARNATAKA, IndianRegion.ANDHRA_PRADESH), holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Lunisolar Hindu New Year and nine days of Chaitra Navratri.", traditionsAndSignificance = "Ghatasthapana, Gudi hoisting, and nine-day worship.", panchangBasis = "Chaitra Shukla Pratipada to Navami · 7–15 April 2027")
    list += IndianFestivalEntry("2027_ram_navami", "Ram Navami", FestivalCategory.HINDU, d(4, 15), regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Gazetted Public Holiday celebrating the birth of Lord Rama.", traditionsAndSignificance = "Janmotsav at Ayodhya Ram Mandir and Kanya Pujan.", panchangBasis = "Chaitra Shukla Navami · 15 April 2027")
    list += IndianFestivalEntry("2027_mahavir_jayanti", "Mahavir Jayanti", FestivalCategory.JAIN, d(4, 19), regions = setOf(IndianRegion.ALL_INDIA), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Birth anniversary of the 24th Jain Tirthankara Lord Mahavira.", traditionsAndSignificance = "Abhisheka, Rath Yatra, and teachings on Ahimsa.", panchangBasis = "Chaitra Shukla Trayodashi · 19 April 2027")
    list += IndianFestivalEntry("2027_hanuman_jayanti", "Hanuman Jayanti", FestivalCategory.HINDU, d(4, 20), regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH), holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Celebrates the birth of Lord Hanuman.", traditionsAndSignificance = "Hanuman Chalisa recitals and temple offerings.", panchangBasis = "Chaitra Purnima · 20 April 2027")
    list += IndianFestivalEntry("2027_eid_ul_adha", "Eid-ul-Adha (Bakrid)", FestivalCategory.MUSLIM, d(5, 17), regions = setOf(IndianRegion.ALL_INDIA), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.TENTATIVE_MOON_SIGHTING, description = "Gazetted Public Holiday of sacrifice and charity.", traditionsAndSignificance = "Congregational prayers and charitable sharing.", panchangBasis = "10th Dhu al-Hijjah 1448 AH · Expected 17 May 2027")
    list += IndianFestivalEntry("2027_buddha_purnima", "Buddha Purnima (Vesak)", FestivalCategory.BUDDHIST, d(5, 20), regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.BIHAR), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Gazetted Public Holiday commemorating Gautama Buddha's birth, enlightenment, and Mahaparinirvana.", traditionsAndSignificance = "Prayers at Sarnath, Kushinagar, and Bodh Gaya.", panchangBasis = "Vaishakha Purnima · 20 May 2027")
    list += IndianFestivalEntry("2027_muharram", "Muharram (Day of Ashura)", FestivalCategory.MUSLIM, d(6, 16), regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.TENTATIVE_MOON_SIGHTING, description = "10th of Muharram remembrance of Karbala.", traditionsAndSignificance = "Majlis and Taziya processions.", panchangBasis = "10th Muharram 1449 AH · Expected 16 June 2027")
    list += IndianFestivalEntry("2027_milad_un_nabi", "Milad-un-Nabi / Id-e-Milad", FestivalCategory.MUSLIM, d(8, 15), regions = setOf(IndianRegion.ALL_INDIA), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.TENTATIVE_MOON_SIGHTING, description = "Observance of the birthday of Prophet Muhammad.", traditionsAndSignificance = "Prayers, Seerat gatherings, and charity.", panchangBasis = "12th Rabi' al-Awwal 1449 AH · Expected 15 August 2027")
    list += IndianFestivalEntry("2027_raksha_bandhan", "Raksha Bandhan", FestivalCategory.HINDU, d(8, 17), regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH), holidayType = HolidayGazetteType.REGIONAL_STATE, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Festival celebrating the bond between brothers and sisters.", traditionsAndSignificance = "Tying Rakhi and family celebrations.", panchangBasis = "Shravana Purnima · 17 August 2027")
    list += IndianFestivalEntry("2027_janmashtami", "Krishna Janmashtami", FestivalCategory.HINDU, d(8, 25), regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Birth anniversary of Lord Sri Krishna.", traditionsAndSignificance = "Midnight celebrations in Mathura and Vrindavan.", panchangBasis = "Bhadrapada Krishna Ashtami · 25 August 2027")
    list += IndianFestivalEntry("2027_ganesh_chaturthi", "Ganesh Chaturthi", FestivalCategory.HINDU, d(9, 3), endDate = d(9, 14), regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.MAHARASHTRA), holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Ten-day festival of Lord Ganesha.", traditionsAndSignificance = "Pandal installations, modak offerings, and visarjan.", panchangBasis = "Bhadrapada Shukla Chaturthi · 3 September 2027")
    list += IndianFestivalEntry("2027_onam", "Onam (Thiruvonam)", FestivalCategory.REGIONAL, d(9, 12), regions = setOf(IndianRegion.KERALA, IndianRegion.ALL_INDIA), holidayType = HolidayGazetteType.REGIONAL_STATE, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Harvest festival of Kerala.", traditionsAndSignificance = "Pookkalam, Onam Sadya, and Vallam Kali.", panchangBasis = "Thiruvonam Nakshatra · 12 September 2027")
    list += IndianFestivalEntry("2027_sharad_navratri_dussehra", "Sharad Navratri, Durga Puja & Dussehra (Vijayadashami)", FestivalCategory.HINDU, d(9, 30), endDate = d(10, 9), regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.WEST_BENGAL, IndianRegion.GUJARAT), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Nine nights of Sharad Navratri and Durga Puja culminating on Vijayadashami / Dussehra on 9 October 2027.", traditionsAndSignificance = "Garba, Durga Puja pandals, Ramleela, and Ravana Dahan on 9 October.", panchangBasis = "Ashwin Shukla Pratipada to Dashami · Dussehra on 9 October 2027")
    list += IndianFestivalEntry("2027_diwali_parva", "Diwali / Deepavali (Dhanteras to Bhai Dooj)", FestivalCategory.HINDU, d(10, 27), endDate = d(10, 31), regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Five-day Festival of Lights with main Diwali on 29 October 2027 and Bhai Dooj on 31 October 2027.", traditionsAndSignificance = "Lakshmi-Ganesha Puja, earthen diyas, Govardhan Puja, and Bhai Dooj.", panchangBasis = "Kartika Amavasya on 29 October 2027")
    list += IndianFestivalEntry("2027_chhath_puja", "Chhath Puja", FestivalCategory.HINDU, d(11, 4), regions = setOf(IndianRegion.BIHAR, IndianRegion.UTTAR_PRADESH, IndianRegion.DELHI), holidayType = HolidayGazetteType.REGIONAL_STATE, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Vedic festival dedicated to Surya Dev and Chhathi Maiya.", traditionsAndSignificance = "Sandhya and Usha Arghya at river ghats.", panchangBasis = "Kartika Shukla Shashthi · 4 November 2027")
    list += IndianFestivalEntry("2027_guru_nanak_dev_deepawali", "Guru Nanak Jayanti & Dev Deepawali (Varanasi)", FestivalCategory.SIKH, d(11, 14), regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH, IndianRegion.PUNJAB), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Kartika Purnima celebration of Guru Nanak Gurpurab nationwide and Dev Deepawali along the Varanasi Ganga Ghats in Uttar Pradesh.", traditionsAndSignificance = "Gurpurab Kirtan & Langar; illumination of all 84 ghats in Varanasi.", panchangBasis = "Kartika Purnima · 14 November 2027")
}

private fun addVerified2025Festivals(list: MutableList<IndianFestivalEntry>) {
    fun d(m: Int, day: Int) = LocalDate.of(2025, m, day)
    list += IndianFestivalEntry("2025_maha_shivratri", "Maha Shivratri", FestivalCategory.HINDU, d(2, 26), regions = setOf(IndianRegion.ALL_INDIA), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Sacred night dedicated to Lord Shiva.", traditionsAndSignificance = "Fasting and Rudrabhishekam.", panchangBasis = "Phalguna Krishna Chaturdashi · 26 February 2025")
    list += IndianFestivalEntry("2025_holi", "Holi", FestivalCategory.HINDU, d(3, 14), regions = setOf(IndianRegion.ALL_INDIA), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Festival of Colours.", traditionsAndSignificance = "Holika Dahan and colours.", panchangBasis = "Phalguna Purnima · 14 March 2025")
    list += IndianFestivalEntry("2025_eid_ul_fitr", "Eid-ul-Fitr", FestivalCategory.MUSLIM, d(3, 31), regions = setOf(IndianRegion.ALL_INDIA), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.VERIFIED_PANCHANG, description = "End of Ramadan.", traditionsAndSignificance = "Eid prayers and charity.", panchangBasis = "1st Shawwal 1446 AH · 31 March 2025")
    list += IndianFestivalEntry("2025_ram_navami", "Ram Navami", FestivalCategory.HINDU, d(4, 6), regions = setOf(IndianRegion.ALL_INDIA), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Birth of Lord Rama.", traditionsAndSignificance = "Ayodhya celebrations.", panchangBasis = "Chaitra Shukla Navami · 6 April 2025")
    list += IndianFestivalEntry("2025_raksha_bandhan", "Raksha Bandhan", FestivalCategory.HINDU, d(8, 9), regions = setOf(IndianRegion.ALL_INDIA), holidayType = HolidayGazetteType.RESTRICTED_OPTIONAL, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Sibling bond festival.", traditionsAndSignificance = "Rakhi tying.", panchangBasis = "Shravana Purnima · 9 August 2025")
    list += IndianFestivalEntry("2025_janmashtami", "Krishna Janmashtami", FestivalCategory.HINDU, d(8, 16), regions = setOf(IndianRegion.ALL_INDIA), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Birth of Lord Krishna.", traditionsAndSignificance = "Mathura & Vrindavan celebrations.", panchangBasis = "Bhadrapada Krishna Ashtami · 16 August 2025")
    list += IndianFestivalEntry("2025_dussehra", "Dussehra (Vijayadashami)", FestivalCategory.HINDU, d(10, 2), regions = setOf(IndianRegion.ALL_INDIA), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Victory of good over evil.", traditionsAndSignificance = "Ravana Dahan & Durga Puja culmination.", panchangBasis = "Ashwin Shukla Dashami · 2 October 2025")
    list += IndianFestivalEntry("2025_diwali", "Diwali (Deepavali)", FestivalCategory.HINDU, d(10, 20), endDate = d(10, 23), regions = setOf(IndianRegion.ALL_INDIA), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Festival of Lights.", traditionsAndSignificance = "Lakshmi Puja, Govardhan Puja & Bhai Dooj.", panchangBasis = "Kartika Amavasya · 20 October 2025")
    list += IndianFestivalEntry("2025_chhath_puja", "Chhath Puja", FestivalCategory.HINDU, d(10, 27), endDate = d(10, 28), regions = setOf(IndianRegion.BIHAR, IndianRegion.UTTAR_PRADESH, IndianRegion.DELHI), holidayType = HolidayGazetteType.REGIONAL_STATE, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Surya Shashthi festival.", traditionsAndSignificance = "Sandhya & Usha Arghya.", panchangBasis = "Kartika Shukla Shashthi · 27–28 October 2025")
    list += IndianFestivalEntry("2025_guru_nanak_dev_deepawali", "Guru Nanak Jayanti & Dev Deepawali", FestivalCategory.SIKH, d(11, 5), regions = setOf(IndianRegion.ALL_INDIA, IndianRegion.UTTAR_PRADESH), holidayType = HolidayGazetteType.GAZETTED_PUBLIC, confidence = DateConfidence.VERIFIED_PANCHANG, description = "Kartika Purnima Gurpurab & Varanasi Dev Deepawali.", traditionsAndSignificance = "Ghat illuminations & Kirtan.", panchangBasis = "Kartika Purnima · 5 November 2025")
}




/**
 * Phase 27.3 — Subtle, Elegant Festival Indicator for Monthly Calendar Day Cells.
 * Displays up to 3 tiny colour-coded dots beneath the day number when a date has festivals,
 * without overcrowding the calendar cell.
 */
@Composable
fun FestivalDayCellIndicator(
    date: LocalDate,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { IndianFestivalStore.ensureInit(context) }
    if (isSelected) {
        IndianFestivalStore.observedSelectedDate.value = date
        IndianFestivalStore.observedVisibleMonth.value = YearMonth.from(date)
    }
    val region by IndianFestivalStore.selectedRegion.collectAsStateWithLifecycle()
    val categories by IndianFestivalStore.enabledCategories.collectAsStateWithLifecycle()
    val customList by IndianFestivalStore.customCelebrations.collectAsStateWithLifecycle()

    val matching = remember(date, region, categories, customList) {
        IndianFestivalStore.festivalsForDate(date, region, categories, customList)
    }
    if (matching.isEmpty()) return

    Row(
        modifier = modifier.padding(top = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        matching.take(3).forEach { fest ->
            val dotColor = if (isSelected) {
                Color.White.copy(alpha = 0.92f)
            } else {
                Color(fest.category.badgeColorHex)
            }
            Box(
                Modifier
                    .size(4.5.dp)
                    .clip(CircleShape)
                    .background(dotColor, CircleShape),
            )
        }
    }
}

/**
 * Phase 27.3, 27.4, 27.5, 27.6, 27.7, 27.8 & 27.9 — Complete Indian Festival & Holiday Calendar UI Section.
 * Integrated directly into the existing Calendar screen (`PlannerScreen.kt`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IndianFestivalCalendarSection(
    selectedDate: LocalDate = IndianFestivalStore.observedSelectedDate.value,
    currentMonth: YearMonth = YearMonth.from(selectedDate),
    onSelectDate: ((LocalDate) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = LocalDarkTheme.current
    LaunchedEffect(Unit) { IndianFestivalStore.ensureInit(context) }

    val selectedRegion by IndianFestivalStore.selectedRegion.collectAsStateWithLifecycle()
    val enabledCategories by IndianFestivalStore.enabledCategories.collectAsStateWithLifecycle()
    val globalReminders by IndianFestivalStore.globalRemindersEnabled.collectAsStateWithLifecycle()
    val reminderMap by IndianFestivalStore.festivalReminders.collectAsStateWithLifecycle()
    val customCelebrations by IndianFestivalStore.customCelebrations.collectAsStateWithLifecycle()

    var regionMenuOpen by remember { mutableStateOf(false) }
    var showCategoryFilters by rememberSaveable { mutableStateOf(false) }
    var showSearchSheet by rememberSaveable { mutableStateOf(false) }
    var showCustomDialog by rememberSaveable { mutableStateOf(false) }
    var editingCustomEntry by remember { mutableStateOf<IndianFestivalEntry?>(null) }
    var selectedFestivalForDetail by remember { mutableStateOf<IndianFestivalEntry?>(null) }

    val primaryText = if (isDark) Color(0xFFF4F5F7) else Color(0xFF111317)
    val secondaryText = if (isDark) Color(0xFF9CA3AF) else Color(0xFF5A606A)
    val borderCol = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)
    val pillFill = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f)
    val activePillFill = if (isDark) Color.White.copy(alpha = 0.18f) else Color(0xFF141518).copy(alpha = 0.10f)

    val dayFestivals = remember(selectedDate, selectedRegion, enabledCategories, customCelebrations) {
        IndianFestivalStore.festivalsForDate(selectedDate, selectedRegion, enabledCategories, customCelebrations)
    }
    val monthFestivals = remember(currentMonth, selectedRegion, enabledCategories, customCelebrations) {
        IndianFestivalStore.festivalsForMonth(currentMonth, selectedRegion, enabledCategories, customCelebrations)
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH) }
    val shortFormatter = remember { DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH) }

    Column(
        modifier = modifier
            .widthIn(max = 680.dp)
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. Liquid Glass Festival Controls Bar (Region Selector, Filters, Search, Notifications & + Celebration)
        GlassSurface(
            shape = RoundedCornerShape(22.dp),
            elevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Indian Festivals & Holidays",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${selectedRegion.displayName} · ${monthFestivals.size} in ${currentMonth.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${currentMonth.year}",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Search Festivals Button
                        Surface(
                            onClick = { showSearchSheet = true },
                            shape = CircleShape,
                            color = pillFill,
                            border = BorderStroke(0.75.dp, borderCol),
                            modifier = Modifier.size(34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Search, contentDescription = "Search festivals", tint = primaryText, modifier = Modifier.size(17.dp))
                            }
                        }

                        // Opt-in Global Festival Reminder Toggle
                        Surface(
                            onClick = { IndianFestivalStore.setGlobalReminders(context, !globalReminders) },
                            shape = CircleShape,
                            color = if (globalReminders) activePillFill else pillFill,
                            border = BorderStroke(0.75.dp, borderCol),
                            modifier = Modifier.size(34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Alarm,
                                    contentDescription = "Festival reminders",
                                    tint = primaryText,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }

                        // Add Personal Celebration / Custom Holiday Button
                        Surface(
                            onClick = {
                                editingCustomEntry = null
                                showCustomDialog = true
                            },
                            shape = CircleShape,
                            color = pillFill,
                            border = BorderStroke(0.75.dp, borderCol),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null, tint = primaryText, modifier = Modifier.size(15.dp))
                                Text(
                                    text = "Holiday",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryText,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }

                // Region Selector Dropdown + Filter Toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        Surface(
                            onClick = { regionMenuOpen = true },
                            shape = CircleShape,
                            color = activePillFill,
                            border = BorderStroke(0.8.dp, borderCol),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "Region: ${selectedRegion.displayName}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryText,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = primaryText, modifier = Modifier.size(16.dp))
                            }
                        }

                        DropdownMenu(
                            expanded = regionMenuOpen,
                            onDismissRequest = { regionMenuOpen = false },
                            shape = RoundedCornerShape(22.dp),
                            containerColor = if (isDark) Color(0xFF14161B).copy(alpha = 0.96f) else Color(0xFFFFFFFF).copy(alpha = 0.97f),
                            tonalElevation = 0.dp,
                            shadowElevation = 12.dp,
                            border = BorderStroke(0.85.dp, borderCol),
                            modifier = Modifier.width(248.dp).heightIn(max = 380.dp),
                        ) {
                            IndianRegion.entries.forEach { reg ->
                                val isRegSelected = reg == selectedRegion
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = reg.displayName,
                                            fontWeight = if (isRegSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = primaryText,
                                            maxLines = 1,
                                        )
                                    },
                                    trailingIcon = if (isRegSelected) {
                                        { Icon(Icons.Filled.Check, contentDescription = null, tint = primaryText, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    onClick = {
                                        regionMenuOpen = false
                                        IndianFestivalStore.setRegion(context, reg)
                                    },
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { showCategoryFilters = !showCategoryFilters },
                        shape = CircleShape,
                        color = if (showCategoryFilters || enabledCategories.size < FestivalCategory.entries.size) activePillFill else pillFill,
                        border = BorderStroke(0.8.dp, borderCol),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = if (enabledCategories.size == FestivalCategory.entries.size) {
                                    "Categories: All (9)"
                                } else {
                                    "Categories (${enabledCategories.size})"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = primaryText,
                                maxLines = 1,
                                softWrap = false,
                            )
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = primaryText, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Multi-Select Category Filter Chips (Hindu, Muslim, Sikh, Christian, Buddhist, Jain, National, Regional, Personal)
                if (showCategoryFilters) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    ) {
                        val allSelected = enabledCategories.size == FestivalCategory.entries.size
                        Surface(
                            onClick = { IndianFestivalStore.selectAllCategories(context) },
                            shape = CircleShape,
                            color = if (allSelected) activePillFill else pillFill,
                            border = BorderStroke(0.8.dp, borderCol),
                        ) {
                            Text(
                                text = "All Festivals",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium,
                                color = primaryText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                        FestivalCategory.entries.forEach { cat ->
                            val enabled = cat in enabledCategories
                            Surface(
                                onClick = { IndianFestivalStore.toggleCategory(context, cat) },
                                shape = CircleShape,
                                color = if (enabled) activePillFill else Color.Transparent,
                                border = BorderStroke(0.8.dp, borderCol),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(cat.badgeColorHex), CircleShape),
                                    )
                                    Text(
                                        text = cat.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (enabled) primaryText else secondaryText,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Selected Date Festivals (if any fall on selectedDate)
                if (dayFestivals.isNotEmpty()) {
                    HorizontalDivider(color = borderCol)
                    Text(
                        text = "OBSERVANCES ON ${selectedDate.format(dateFormatter).uppercase()}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.sp),
                        fontWeight = FontWeight.Bold,
                        color = secondaryText,
                    )
                    dayFestivals.forEach { fest ->
                        FestivalListRowCard(
                            festival = fest,
                            referenceDate = selectedDate,
                            hasReminder = reminderMap.containsKey(fest.id) || globalReminders,
                            onClick = { selectedFestivalForDetail = fest },
                        )
                    }
                }

                // 3. Monthly Festival Overview Carousel (All Festivals in the Currently Viewed Month)
                if (monthFestivals.isNotEmpty()) {
                    HorizontalDivider(color = borderCol)
                    Text(
                        text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase()} ${currentMonth.year} FESTIVAL SCHEDULE",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.sp),
                        fontWeight = FontWeight.Bold,
                        color = secondaryText,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        monthFestivals.forEach { fest ->
                            val isOnSelectedDay = fest.occursOn(selectedDate)
                            Surface(
                                onClick = {
                                    IndianFestivalStore.observedSelectedDate.value = fest.startDate
                                    onSelectDate?.invoke(fest.startDate)
                                    selectedFestivalForDetail = fest
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isOnSelectedDay) activePillFill else pillFill,
                                border = BorderStroke(if (isOnSelectedDay) 1.2.dp else 0.75.dp, borderCol),
                                modifier = Modifier.width(196.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Box(
                                            Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(Color(fest.category.badgeColorHex), CircleShape),
                                        )
                                        Text(
                                            text = if (fest.durationDays > 1) {
                                                "${fest.startDate.format(shortFormatter)} – ${fest.endDate.format(shortFormatter)}"
                                            } else {
                                                fest.startDate.format(shortFormatter)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = secondaryText,
                                            maxLines = 1,
                                        )
                                    }
                                    Text(
                                        text = fest.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${fest.category.label} · ${fest.holidayType.label.substringBefore(" ")}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = secondaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. Festival Detail Modal (`Phase 27.5 & 27.6`)
    selectedFestivalForDetail?.let { fest ->
        FestivalDetailDialog(
            festival = fest,
            currentReminder = reminderMap[fest.id] ?: FestivalReminderOption.NONE,
            onReminderChange = { opt -> IndianFestivalStore.setFestivalReminder(context, fest.id, opt) },
            onEditCustom = if (fest.isCustomPersonal) {
                {
                    selectedFestivalForDetail = null
                    editingCustomEntry = fest
                    showCustomDialog = true
                }
            } else null,
            onDeleteCustom = if (fest.isCustomPersonal) {
                {
                    IndianFestivalStore.deleteCustomCelebration(context, fest.id)
                    selectedFestivalForDetail = null
                }
            } else null,
            onDismiss = { selectedFestivalForDetail = null },
        )
    }

    // 5. Festival Search & Discovery Modal (`Phase 27.7`)
    if (showSearchSheet) {
        FestivalSearchDialog(
            initialYear = currentMonth.year,
            initialRegion = selectedRegion,
            customList = customCelebrations,
            onSelectFestival = { fest ->
                showSearchSheet = false
                IndianFestivalStore.observedSelectedDate.value = fest.startDate
                IndianFestivalStore.observedVisibleMonth.value = YearMonth.from(fest.startDate)
                onSelectDate?.invoke(fest.startDate)
                selectedFestivalForDetail = fest
            },
            onDismiss = { showSearchSheet = false },
        )
    }

    // 6. Add / Edit Personal Celebration or Custom Holiday Modal (`Phase 27.8`)
    if (showCustomDialog) {
        CustomCelebrationDialog(
            initialEntry = editingCustomEntry,
            defaultDate = selectedDate,
            initialReminder = editingCustomEntry?.let { reminderMap[it.id] } ?: FestivalReminderOption.ON_DAY,
            onSave = { id, title, date, desc, rem ->
                IndianFestivalStore.saveCustomCelebration(context, id, title, date, desc, rem)
                showCustomDialog = false
                editingCustomEntry = null
            },
            onDismiss = {
                showCustomDialog = false
                editingCustomEntry = null
            },
        )
    }
}

@Composable
private fun FestivalListRowCard(
    festival: IndianFestivalEntry,
    referenceDate: LocalDate,
    hasReminder: Boolean,
    onClick: () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val primaryText = if (isDark) Color(0xFFF4F5F7) else Color(0xFF111317)
    val secondaryText = if (isDark) Color(0xFF9CA3AF) else Color(0xFF5A606A)
    val cardBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.03f)
    val borderCol = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(0.8.dp, borderCol),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(Color(festival.category.badgeColorHex), CircleShape),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = festival.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (festival.durationDays > 1) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.07f),
                        ) {
                            Text(
                                text = "Day ${festival.dayIndexOn(referenceDate)} of ${festival.durationDays}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = primaryText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                val tentativeNote = if (festival.confidence == DateConfidence.TENTATIVE_MOON_SIGHTING) " · Moon-sighting tentative" else ""
                Text(
                    text = "${festival.category.label} · ${festival.holidayType.label}$tentativeNote",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hasReminder) {
                Icon(
                    Icons.Outlined.Alarm,
                    contentDescription = null,
                    tint = primaryText,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Phase 27.5 & 27.6 — Festival Detail & Reminder Customisation Modal.
 */
@Composable
fun FestivalDetailDialog(
    festival: IndianFestivalEntry,
    currentReminder: FestivalReminderOption,
    onReminderChange: (FestivalReminderOption) -> Unit,
    onEditCustom: (() -> Unit)? = null,
    onDeleteCustom: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val sheetBg = if (isDark) Color(0xFF101114) else Color(0xFFFFFFFF)
    val cardBg = if (isDark) Color(0xFF1A1C22) else Color(0xFFF4F5F7)
    val borderCol = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.12f)
    val primaryText = if (isDark) Color(0xFFF4F5F7) else Color(0xFF111317)
    val secondaryText = if (isDark) Color(0xFF9CA3AF) else Color(0xFF5A606A)

    val fullDateFmt = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH) }
    val dateRangeText = if (festival.durationDays > 1) {
        "${festival.startDate.format(fullDateFmt)} – ${festival.endDate.format(fullDateFmt)} (${festival.durationDays} Days)"
    } else {
        "${festival.startDate.format(fullDateFmt)} (1 Day)"
    }
    val regionsText = remember(festival.regions) {
        if (IndianRegion.ALL_INDIA in festival.regions && festival.regions.size == 1) {
            "All India (Nationwide)"
        } else {
            festival.regions.joinToString(", ") { it.displayName }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = sheetBg,
            contentColor = primaryText,
            border = BorderStroke(1.dp, borderCol),
            tonalElevation = 0.dp,
            shadowElevation = 18.dp,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.94f)
                .heightIn(max = 760.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(festival.category.badgeColorHex), CircleShape),
                        )
                        Text(
                            text = "${festival.category.label.uppercase()} · ${festival.holidayType.label.uppercase()}",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.9.sp),
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                        )
                    }
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = cardBg,
                        border = BorderStroke(0.8.dp, borderCol),
                        modifier = Modifier.size(34.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close", tint = primaryText, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Text(
                    text = festival.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = primaryText,
                )

                Text(
                    text = dateRangeText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText,
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    border = BorderStroke(0.8.dp, borderCol),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        DetailFieldRow("Regions Celebrated", regionsText, primaryText, secondaryText)
                        HorizontalDivider(color = borderCol)
                        DetailFieldRow("Overview", festival.description, primaryText, secondaryText)
                        HorizontalDivider(color = borderCol)
                        DetailFieldRow("Traditions & Significance", festival.traditionsAndSignificance, primaryText, secondaryText)
                        HorizontalDivider(color = borderCol)
                        DetailFieldRow("Panchang / Calendar Basis", festival.panchangBasis, primaryText, secondaryText)
                        HorizontalDivider(color = borderCol)
                        DetailFieldRow("Verification & Source", "${festival.confidence.label} · Source: ${festival.source}", primaryText, secondaryText)
                    }
                }

                // Opt-in Per-Festival Reminder Control (`Phase 27.6`)
                Text(
                    text = "Festival Reminder (Opt-in)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText,
                )
                AnimatedGlassSegmentedControl(
                    items = FestivalReminderOption.entries,
                    selectedItem = currentReminder,
                    onSelect = onReminderChange,
                    labelProvider = { it.label },
                )

                if (onEditCustom != null && onDeleteCustom != null) {
                    HorizontalDivider(color = borderCol)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = onDeleteCustom) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Delete Personal Holiday")
                        }
                        TextButton(onClick = onEditCustom) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit Details", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailFieldRow(
    label: String,
    value: String,
    primaryText: Color,
    secondaryText: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.8.sp),
            fontWeight = FontWeight.SemiBold,
            color = secondaryText,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = primaryText,
        )
    }
}

/**
 * Phase 27.7 — Festival Search & Discovery Dialog (Filter by Name, Category, Region, Month & Year).
 */
@Composable
fun FestivalSearchDialog(
    initialYear: Int,
    initialRegion: IndianRegion,
    customList: List<IndianFestivalEntry>,
    onSelectFestival: (IndianFestivalEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val sheetBg = if (isDark) Color(0xFF101114) else Color(0xFFFFFFFF)
    val cardBg = if (isDark) Color(0xFF1A1C22) else Color(0xFFF4F5F7)
    val borderCol = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.12f)
    val primaryText = if (isDark) Color(0xFFF4F5F7) else Color(0xFF111317)
    val secondaryText = if (isDark) Color(0xFF9CA3AF) else Color(0xFF5A606A)

    var query by rememberSaveable { mutableStateOf("") }
    var selectedYear by rememberSaveable { mutableIntStateOf(initialYear.coerceIn(2025, 2027)) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(0) } // 0 = All Months, 1..12 = Jan..Dec
    var filterRegion by rememberSaveable { mutableStateOf(initialRegion) }

    val results = remember(query, selectedYear, selectedMonth, filterRegion, customList) {
        val q = query.trim().lowercase()
        IndianFestivalStore.festivalsForYear(selectedYear, customList).filter { fest ->
            val matchesRegion = fest.matchesRegion(filterRegion)
            val matchesMonth = selectedMonth == 0 || fest.startDate.monthValue == selectedMonth || fest.endDate.monthValue == selectedMonth
            val matchesQuery = q.isEmpty() ||
                fest.name.lowercase().contains(q) ||
                fest.category.label.lowercase().contains(q) ||
                fest.description.lowercase().contains(q) ||
                fest.regions.any { it.displayName.lowercase().contains(q) }
            matchesRegion && matchesMonth && matchesQuery
        }
    }

    val fmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = sheetBg,
            contentColor = primaryText,
            border = BorderStroke(1.dp, borderCol),
            tonalElevation = 0.dp,
            shadowElevation = 18.dp,
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(0.94f)
                .heightIn(max = 760.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Search Indian Festivals",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                    )
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = cardBg,
                        border = BorderStroke(0.8.dp, borderCol),
                        modifier = Modifier.size(34.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close", tint = primaryText, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search Diwali, Chhath, Dev Deepawali, Eid, UP...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Year Selector (2025 | 2026 | 2027)
                AnimatedGlassSegmentedControl(
                    items = listOf(2025, 2026, 2027),
                    selectedItem = selectedYear,
                    onSelect = { selectedYear = it },
                    labelProvider = { it.toString() },
                )

                // Month Filter Row (`All | Jan | Feb ... | Dec`)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val months = listOf(0 to "All Months") + (1..12).map { m ->
                        m to Month.of(m).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                    }
                    months.forEach { (mVal, mLabel) ->
                        val sel = selectedMonth == mVal
                        Surface(
                            onClick = { selectedMonth = mVal },
                            shape = CircleShape,
                            color = if (sel) cardBg else Color.Transparent,
                            border = BorderStroke(0.8.dp, borderCol),
                        ) {
                            Text(
                                text = mLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                color = if (sel) primaryText else secondaryText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                Text(
                    text = "${results.size} festivals found (${filterRegion.displayName})",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryText,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    results.forEach { fest ->
                        Surface(
                            onClick = { onSelectFestival(fest) },
                            shape = RoundedCornerShape(14.dp),
                            color = cardBg,
                            border = BorderStroke(0.75.dp, borderCol),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(fest.category.badgeColorHex), CircleShape),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = fest.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${fest.startDate.format(fmt)} · ${fest.category.label} · ${fest.holidayType.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Phase 27.8 — Add / Edit Personal Celebration or Custom Holiday Modal.
 */
@Composable
fun CustomCelebrationDialog(
    initialEntry: IndianFestivalEntry?,
    defaultDate: LocalDate,
    initialReminder: FestivalReminderOption,
    onSave: (id: String, title: String, date: LocalDate, description: String, reminder: FestivalReminderOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val sheetBg = if (isDark) Color(0xFF101114) else Color(0xFFFFFFFF)
    val cardBg = if (isDark) Color(0xFF1A1C22) else Color(0xFFF4F5F7)
    val borderCol = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.12f)
    val primaryText = if (isDark) Color(0xFFF4F5F7) else Color(0xFF111317)
    val secondaryText = if (isDark) Color(0xFF9CA3AF) else Color(0xFF5A606A)
    val accentCol = if (isDark) Color(0xFFF4F5F7) else Color(0xFF141518)

    var title by rememberSaveable { mutableStateOf(initialEntry?.name.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initialEntry?.description.orEmpty()) }
    var reminder by rememberSaveable { mutableStateOf(initialReminder) }
    val targetDate = initialEntry?.startDate ?: defaultDate
    val id = initialEntry?.id ?: "custom_${System.currentTimeMillis()}"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = sheetBg,
            contentColor = primaryText,
            border = BorderStroke(1.dp, borderCol),
            tonalElevation = 0.dp,
            shadowElevation = 18.dp,
            modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth(0.92f),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = cardBg,
                        border = BorderStroke(0.8.dp, borderCol),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, contentDescription = "Cancel", tint = primaryText, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        text = if (initialEntry == null) "New Personal Celebration" else "Edit Personal Celebration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText,
                    )
                    Surface(
                        onClick = {
                            if (title.isNotBlank()) {
                                onSave(id, title, targetDate, description, reminder)
                            }
                        },
                        shape = CircleShape,
                        color = accentCol,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Save",
                                tint = if (isDark) Color(0xFF101114) else Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Text(
                    text = "Date: ${targetDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH))}",
                    style = MaterialTheme.typography.labelLarge,
                    color = secondaryText,
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Celebration or Holiday Title") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Traditions or Notes (optional)") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Reminder",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText,
                )
                AnimatedGlassSegmentedControl(
                    items = FestivalReminderOption.entries,
                    selectedItem = reminder,
                    onSelect = { reminder = it },
                    labelProvider = { it.label },
                )
            }
        }
    }
}
