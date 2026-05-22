package com.basauri.ftmowidget.widget

import com.basauri.ftmowidget.data.WidgetSnapshot

sealed interface WidgetState {
    data object Unconfigured : WidgetState
    data object Loading : WidgetState
    data class Error(val message: String, val cached: WidgetSnapshot?) : WidgetState
    data class Ready(val snapshot: WidgetSnapshot) : WidgetState
}
