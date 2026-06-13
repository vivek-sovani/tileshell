package com.tileshell.feature.applist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tileshell.core.data.AppCatalogRepository
import com.tileshell.core.data.AppEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes the live app catalogue plus a search query, surfacing the filtered
 * list for the UI. Backed by [AppCatalogRepository], so install/uninstall flow
 * through automatically.
 */
class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppCatalogRepository(application)

    private val apps: StateFlow<List<AppEntry>> = repository.apps.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** The catalogue filtered by the current query (case-insensitive substring). */
    val filteredApps: StateFlow<List<AppEntry>> =
        combine(apps, _query) { list, q -> AppListFilter.filter(list, q) }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun setQuery(value: String) {
        _query.value = value
    }
}
