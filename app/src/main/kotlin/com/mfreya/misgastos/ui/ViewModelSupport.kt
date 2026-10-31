package com.mfreya.misgastos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mfreya.misgastos.di.AppContainer

class SimpleViewModelFactory<T : ViewModel>(
    private val builder: () -> T,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = builder() as VM
}

@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline builder: (AppContainer) -> VM,
): VM {
    val container = LocalAppContainer.current
    val factory = remember { SimpleViewModelFactory { builder(container) } }
    return viewModel(key = key, factory = factory)
}