package com.tileshell.feature.applist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tileshell.core.data.AppCatalogRepository
import com.tileshell.core.data.AppEntry
import com.tileshell.core.data.LayoutRepository
import com.tileshell.core.data.PinResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A completed pin, surfaced once for the UI to toast / navigate on. */
data class PinOutcome(val result: PinResult, val label: String)

/**
 * Exposes the live app catalogue plus a search query, surfacing the filtered
 * list for the UI. Backed by [AppCatalogRepository], so install/uninstall flow
 * through automatically. Also handles pinning an app to Start (FR-5) via
 * [LayoutRepository], emitting a [PinOutcome] for the toast.
 */
class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppCatalogRepository(application)
    private val layout = LayoutRepository.create(application)

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

    private val _pinned = MutableSharedFlow<PinOutcome>(extraBufferCapacity = 4)
    val pinned: SharedFlow<PinOutcome> = _pinned.asSharedFlow()

    fun setQuery(value: String) {
        _query.value = value
    }

    /** Pin [app] to Start, then emit the outcome for the UI to toast on. */
    fun pin(app: AppEntry) {
        viewModelScope.launch {
            val result = layout.pinApp(app)
            _pinned.emit(PinOutcome(result, app.label))
        }
    }
}
