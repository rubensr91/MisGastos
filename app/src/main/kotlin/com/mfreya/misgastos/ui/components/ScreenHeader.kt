package com.mfreya.misgastos.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Altura de la cabecera. Antes ocupaba los 64 dp que Material pone por defecto. */
val ScreenHeaderHeight: Dp = 56.dp

/**
 * Comportamiento de las cabeceras: al bajar la lista la cabecera se retira y el contenido
 * gana esos pixeles; al subir vuelve a su sitio. El limite de desplazamiento se fija al
 * alto de la barra para que se retire del todo en vez de quedar un borde asomando.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberScreenHeaderScrollBehavior(): TopAppBarScrollBehavior {
    val limite = with(LocalDensity.current) { ScreenHeaderHeight.toPx() }
    return TopAppBarDefaults.enterAlwaysScrollBehavior(
        state = rememberTopAppBarState(
            // Solo el limite: la cabecera arranca visible (desplazamiento 0) y se retira
            // hasta -limite al bajar.
            initialHeightOffsetLimit = -limite,
        ),
    )
}

/**
 * Cabecera de las cuatro vistas principales.
 *
 * No se le fija la altura a mano: TopAppBar ya reserva dentro de ella el hueco del
 * status bar, y recortarla por fuera dejaba el titulo sin sitio.
 *
 * Es deliberadamente sorda: sin color propio (se apoya en el color de la
 * pagina, asi que no parece una franja pegada encima), titulo compacto y solo el nombre de
 * la vista. Al hacer scroll hacia abajo se retira entera y al subir vuelve a aparecer, en
 * lugar de quedarse quitando sitio para siempre.
 *
 * El fondo si se pinta cuando la cabecera esta retraida, porque en ese momento el contenido
 * pasa por debajo y sin el fondo se leeria del reves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenHeader(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            // El color de fondo exacto de la pagina: la cabecera se funde con ella en vez
            // de dejar un rectangulo mas claro flotando sobre el titulo.
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
    )
}
