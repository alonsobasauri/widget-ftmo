package com.basauri.ftmowidget.widget

import com.basauri.ftmowidget.data.AccountView

sealed interface WidgetState {
    data object Unconfigured : WidgetState
    data object Loading : WidgetState

    /** This widget is bound to one account. */
    data class Single(val account: AccountView, val refreshing: Boolean = false) : WidgetState

    /** This widget stacks every configured account. */
    data class Multi(val accounts: List<AccountView>, val refreshing: Boolean = false) : WidgetState
}
