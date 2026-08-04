package com.basauri.ftmowidget.widget

import com.basauri.ftmowidget.data.AccountSnapshot

sealed interface WidgetState {
    data object Unconfigured : WidgetState
    data object Loading : WidgetState
    data class Error(val message: String, val cached: AccountSnapshot?, val refreshing: Boolean = false) : WidgetState
    data class Ready(val snapshot: AccountSnapshot, val refreshing: Boolean = false) : WidgetState
}
