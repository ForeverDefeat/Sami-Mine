package dev.studios.samimine.ui.components

import androidx.compose.runtime.compositionLocalOf

/** The authenticated shell owns delivery state; demo-only regression fixtures remain local. */
val LocalSyncEnabled = compositionLocalOf { false }
fun syncHint(enabled: Boolean) = if (enabled) "Consulta el estado de envío en la parte superior" else "Sincronización no habilitada"
