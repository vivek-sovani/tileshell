package com.tileshell.feature.applist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tileshell.core.data.AppCatalogRepository
import com.tileshell.core.data.AppEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes the live, alphabetically sorted app catalogue for the app list.
 * Backed by [AppCatalogRepository], so install/uninstall/update changes flow
 * through automatically.
 */
class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppCatalogRepository(application)

    val apps: StateFlow<List<AppEntry>> = repository.apps.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
}
