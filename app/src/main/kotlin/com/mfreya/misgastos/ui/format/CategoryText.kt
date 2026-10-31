package com.mfreya.misgastos.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mfreya.misgastos.domain.model.Category

/**
 * Nombre legible de una categoria. Las 21 del catalogo se muestran traducidas; las que el
 * usuario creo a mano se muestran tal cual, porque son suyas y no forman parte del catalogo.
 */
@Composable
fun categoryLabel(category: String): String =
    Category.nameRes(category)?.let { stringResource(it) } ?: category