package com.techgv.stash.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.techgv.stash.model.Lang

/** The strings the design localises (shelf chrome and group headers). Everything else is English. */
data class Strings(
    val unread: String,
    val search: String,
    val all: String,
    val unreadChip: String,
    val today: String,
    val yesterday: String,
    val thisWeek: String,
    val lastWeek: String,
    val earlier: String,
    val addLink: String,
    val archive: String,
    val settings: String,
    val share: String,
    /** Sunday first. */
    val weekdays: List<String>,
    val months: List<String>,
)

private val en = Strings(
    "unread", "Search your shelf", "All", "Unread", "Today", "Yesterday", "This week", "Last week", "Earlier",
    "Add link", "Archive", "Settings", "Share",
    listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"),
    listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"),
)

private val es = Strings(
    "sin leer", "Busca en tu estante", "Todo", "Sin leer", "Hoy", "Ayer", "Esta semana", "La semana pasada", "Antes",
    "Añadir enlace", "Archivo", "Ajustes", "Compartir",
    listOf("Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"),
    listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"),
)

private val hi = Strings(
    "अपठित", "अपनी शेल्फ़ में खोजें", "सभी", "अपठित", "आज", "कल", "इस हफ़्ते", "पिछले हफ़्ते", "पहले",
    "लिंक जोड़ें", "संग्रह", "सेटिंग्स", "शेयर",
    listOf("रविवार", "सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार"),
    listOf("जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्तूबर", "नवंबर", "दिसंबर"),
)

private val ta = Strings(
    "படிக்காதவை", "அலமாரியில் தேடுங்கள்", "அனைத்தும்", "படிக்காதவை", "இன்று", "நேற்று", "இந்த வாரம்", "கடந்த வாரம்", "முன்பு",
    "இணைப்பு சேர்", "காப்பகம்", "அமைப்புகள்", "பகிர்",
    listOf("ஞாயிறு", "திங்கள்", "செவ்வாய்", "புதன்", "வியாழன்", "வெள்ளி", "சனி"),
    listOf("ஜனவரி", "பிப்ரவரி", "மார்ச்", "ஏப்ரல்", "மே", "ஜூன்", "ஜூலை", "ஆகஸ்ட்", "செப்டம்பர்", "அக்டோபர்", "நவம்பர்", "டிசம்பர்"),
)

fun stringsFor(lang: Lang): Strings = when (lang) {
    Lang.Es -> es
    Lang.Hi -> hi
    Lang.Ta -> ta
    Lang.Auto, Lang.En -> en // the design treats "Phone" as English
}

val LocalStrings = staticCompositionLocalOf { en }
