# Mis Gastos

App de Android que **escucha las notificaciones de tu banco**, extrae el importe de cada
pago y lo guarda en una base de datos local para que puedas ver **gráficos y estadísticas**
de en qué gastas. No hay que introducir nada a mano: el flujo es 100 % automático.

- Sin cuentas, sin registro, sin servidor.
- **Sin permiso `INTERNET`**: la app no puede enviar nada a ningún sitio.
- Todo se queda en tu teléfono.

---

## 1. Requisitos

| | |
|---|---|
| Android mínimo | **8.0 Oreo (API 26)** |
| Compilado / probado contra | **API 36 (Android 16)** |
| JDK para compilar | 17 o superior (probado con Temurin 21) |
| Pantalla | cualquiera, incluido modo oscuro |

La app se instala como APK normal: no hay publicación en Play Store ni dependencias de
servicios de Google. Está pensada para instalarse a mano en tu propio teléfono.

---

## 2. Compilar e instalar

El proyecto usa el **Gradle wrapper**, así que no hace falta tener Gradle instalado:

```bash
cd ~/projects/misgatos

# Depuración (para uso diario)
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Versión limpia: unit tests + lint + APK de release
./gradlew build

# Solo los tests
./gradlew :app:testDebugUnitTest

# El paquete que se sube a Google Play (obligatorio: Play no acepta APK)
./gradlew bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

Para instalarlo en el teléfono por cable USB (con depuración USB activada):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

O directamente: copia el `.apk` al móvil y ábrelo (tendrás que permitir «instalar apps de
orígenes desconocidos» para el explorador de archivos).

El SDK de Android se detecta mediante `local.properties` (`sdk.dir`), que está en
`.gitignore` a propósito porque es específico de cada máquina.

---

## 3. Primeros pasos

1. Abre la app. Verás el **onboarding** de dos pantallas con las ideas básicas.
2. En la segunda pantalla pulsa **«Conceder acceso»**: se abre *Ajustes del sistema* →
   *Acceso a notificaciones*. Activa **Mis Gastos**.
3. Vuelve a la app (el estado se refresca solo al reanudar) y pulsa
   **«Enviar notificación de prueba»**. Deberías ver una notificación que dice
   *«Gastaste 1,23 € en Tienda Demo»*.
4. En unos segundos aparecerá en **Resumen** como un gasto de 1,23 €. Si no aparece, ve a
   **Ajustes → Diagnóstico**: ahí se ve si el servicio está conectado y si la app ha sido
   excluida de la optimización de batería.

En cuanto pagues con tu tarjeta, el banco mandará su notificación, la app la leerá y la
guardará. No hace falta abrir la app.

---

## 4. Qué hace y cómo

```
Notificación del banco
        │
        ▼
GastoNotificationListener      (NotificationListenerService)
        │                       extrae título y cuerpo
        ▼
NotificationIngestor          filtra por lista de apps permitidas
        │
        ▼
NotificationTextParser        (Kotlin puro, testeado en JVM)
        │                       importe en céntimos + comercio + confianza
        │                       + tipo: gasto o dinero recibido
        ▼
IncomeDetector                los ingresos se descartan aquí (no se guardan)
        │
        ▼
ExpenseRepository  ──►  Room: tabla `expenses`  (índice único anti-duplicados)
        │
        ▼
Flow  ──►  Compose  (Resumen con gráficos · Lista con buscador · Ajustes)
```

### Formato de notificación reconocido

El parser es heurístico y tolera muchos formatos reales. Algunos ejemplos que están
cubiertos por los tests:

| Notificación | Importe | Comercio |
|---|---|---|
| `Gastaste 12,50 € en Mercadona` | 12,50 € | Mercadona |
| `Gastaste 3.20€ en BAR EL PILON` | 3,20 € | BAR EL PILON |
| `Gastaste 1.234,56 € en Amazon` | 1 234,56 € | Amazon |
| `Pago realizado. Has gastado 45,90 € en GLOVO. Ya puedes revisarlo` | 45,90 € | GLOVO |
| `Compra con tarjeta 8,99 € en Amazon.es` | 8,99 € | Amazon.es |
| `Gastaste 12,50€ en Netflix..Disponible el 03/04` | 12,50 € | Netflix |
| `Importe: 7,40 € Comercio: REPSOL` | 7,40 € | REPSOL |
| `Has pagado 20 € en Uber Eats` | 20,00 € | Uber Eats |

El separador decimal se detecta solo: `12,50`, `12.50` y `1.234,56` / `1,234.56` se
interpretan correctamente, y los millares con `.` o con espacio no se confunden con
decimales. Si la notificación **no** parece un gasto (p. ej. «Tienes 5 notificaciones
nuevas») se descarta sin guardar nada.

Cada importe lleva un **nivel de confianza** (0-100). Los que el app no está segura de
ellos aparecen marcados en la lista y se pueden listar en **Ajustes → Revisión** para
corregir el comercio y la categoría a mano.

### Dinero recibido: no es un gasto

Si el aviso describe dinero que **entra** en la cuenta, no es un gasto y no se guarda.
`domain/parse/IncomeDetector.kt` lo detecta antes de insertar y el ingestor lo descarta
con el resultado `IngestResult.Income` (visible en logcat, etiqueta `MisGastosIngest`,
como `descartada: dinero recibido, no es un gasto`). Así no deforma el total del mes, los
presupuestos ni los gráficos.

| Aviso | Por qué se descarta |
|---|---|
| `Has recibido una transferencia de 250,00 € de Juan` | transferencia entrante |
| `Bizum recibido de Maria: 20,00 €` | bizum entrante |
| `Has recibido 20,00 € de Juan` | verbo de recepción sin sustantivo de cargo |
| `Te hemos ingresado 1.850,00 €. Nomina de octubre` | ingreso / nómina |
| `Abono de 45,00 € en tu cuenta` | abono |
| `Devolucion de Amazon: 24,99 €` | devolución / reembolso |

La detección busca fórmulas concretas, no palabras sueltas: `Has recibido un cargo de
5,00 € en Netflix` **sí** es un gasto, porque tras «recibido» aparece un sustantivo de
cargo. Los avisos de gasto normales (`Gastaste`, `Has pagado`, `Compra con tarjeta`…)
siguen guardándose igual.

> Esta regla no borra ingresos que se hubieran guardado antes de añadirla: solo evita
> que entren nuevos.

### Base de datos

Una sola tabla, `expenses`, en `mis_gastos.db`:

| Columna | Qué es |
|---|---|
| `amountCents` | importe en **céntimos** (`Long`) — nunca se usa `Double` para dinero |
| `currency` | `EUR` |
| `merchant` | comercio normalizado, usado para agrupar |
| `merchantRaw` | comercio tal y como apareció |
| `category` | «Sin categoría» por defecto, editable a mano |
| `postedAt` / `ingestedAt` | fecha del gasto / fecha en que la leímos (epoch millis) |
| `rawText` | **texto original crudo** de la notificación |
| `sourcePackage` / `sourceTitle` | app que emitió la notificación |
| `parseConfidence` | confianza del parseo, 0-100 |

Índices por fecha, comercio y categoría, más un **índice único**
`(sourcePackage, postedAt, amountCents)`: si el sistema reenvía la misma notificación, el
`INSERT` se ignora y **no se duplica el gasto**.

Ese índice solo cubre la reentrega con la **misma** hora. Para el resto de duplicados (ver
abajo) hay una comprobación aparte, antes de insertar.

### Un pago, un solo gasto: la fusión de avisos

Cuando pagas con Google Pay, avisan **dos apps**: la del banco y Google Wallet. Los dos avisos
describen el mismo pago y llegan con uno o dos segundos de diferencia, pero con datos
distintos: el del banco suele traer el verbo (`Gastaste 45,00 € en AMAZON PRIME`) y el
comercio limpio, y el de Google Wallet trae el nombre real del comercio en el título y el
número de la tarjeta en el cuerpo (`45,00 € con Trade Republic Visa Card ••4797`).

Antes se guardaban los dos y el total salía **duplicado**. Ahora, si llega un aviso con el
**mismo importe** que un gasto guardado en los últimos **30 segundos**
(`NotificationIngestor.MERGE_WINDOW_MILLIS`), se entiende que es el mismo pago y se **combinan
en una sola fila** en lugar de crear otra. Da igual el orden de llegada: el mejor dato gana
tanto si el aviso nuevo es el bueno como si es el que ya estaba guardado.

Cada campo se queda con el aviso que mejor lo trajo (`domain/payment/PaymentMerger.kt`):

| Dato | De dónde sale |
|---|---|
| comercio | del aviso que mejor lo leyó (primero importa que lo conozca) |
| texto original | del aviso con el texto **más largo**: es el que más información guarda |
| título | del aviso con el título **más largo**: suele traer el nombre real del comercio |
| origen (app) | del aviso ganador, para que en Ajustes se vea de dónde salió el dato |
| confianza | la mayor de las dos |
| categoría | se **recalcula** con el comercio ganador, así que también se aplica una regla nueva o una corrección tuya |

> **Consecuencia asumida:** dos compras **del mismo importe** en menos de 30 segundos se
> fusionan en una sola fila, aunque sean en sitios distintos. Con 30 segundos el margen es
> suficiente para los dos avisos de un mismo pago y muy corto para que te ocurra por error.
> Los descartes y combinaciones quedan en logcat con la etiqueta `MisGastosIngest`
> (`combinada con la id=…`).

### Categorización automática

Cada gasto nuevo recibe una categoría **sin que hagas nada**. El motor es 100 % local, sin
internet y sin IA: son reglas de palabras clave, y por encima de ellas está lo que **has
corregido tú**.

El orden de decisión es:

1. **Lo aprendido por ti.** Si en algún momento cambiaste de categoría un gasto de un comercio,
   la app memoriza el par `comercio → categoría` en la tabla `merchant_categories` y lo aplica a
   todos los gastos siguientes de ese comercio (también si el banco escribe el nombre con otra
   forma: `Mercadona`, `Mercadona, S.L.` o `MERCADONA` apuntan a la misma clave).
2. **Las reglas por palabras clave.** 20 categorías × ~25 palabras clave cada una
   (`domain/category/CategoryRules.kt`). Ejemplos:

   | Categoría | Ejemplos que la activan |
   |---|---|
   | Supermercado | Mercadona, Carrefour, Eroski, Lidl, Día, Alcampo |
   | Combustible | Repsol, Cepsa, BP, TotalEnergies, Galp, repostaje |
   | Transporte / Comida rápida | Uber, Cabify, Bolt · McDonald's, Burger King, Telepizza |
   | Restaurante | GLOVO, Uber Eats, Deliveroo, «bar», «café», «pizzería» |
   | Suscripciones | Netflix, Spotify, Amazon Prime |
   | Servicios | Movistar, Vodafone, Orange, factura, comunidad |
   | Viajes | Ryanair, Booking.com, Renfe |
   | Salud, Educación, Deportes, Ocio, Mascotas, Regalos, Banca, Impuestos, Efectivo, Otros | palabras clave propias |

Las palabras clave se comparan como **secuencias de tokens**, no como pedazos de texto: `bar`
no casa dentro de `barcelona`, y `uber eats` solo casa si esas dos palabras van seguidas.

**Puntuación.** Una coincidencia en el **comercio** vale el doble que en el **texto original** de
la notificación, porque el comercio es mucho más fiable que el texto entero del aviso. Se exigen
**2 puntos** para dar categoría, de modo que una mención suelta en el cuerpo del aviso («compra con
tarjeta terminada en 1234») nunca decide sola.

**Las 21 categorías** son: Sin categoría, Supermercado, Restaurante, Comida rápida, Transporte,
Combustible, Estacionamiento, Viajes, Compras online, Suscripciones, Salud, Ocio, Servicios,
Educación, Deportes, Mascotas, Regalos, Banca, Impuestos, Efectivo y Otros. En la base de datos se
guarda el nombre en español como identificador, y la app lo traduce al idioma del móvil; si creas
una categoría a mano con otro nombre, se muestra tal cual.

**Dónde tocar el comportamiento:**

- **Ajustes → Categorización automática** explica las reglas.
- **Ajustes → Aprendido de ti** lista los comercios que has corregido (con el número de veces que
  lo has confirmado) y permite **olvidar** uno o todos.
- **Gasto → tocar la categoría** abre el selector: al elegir otra, además de corregir ese gasto,
  la app **aprende** ese comercio para los siguientes. Elegir «Sin categoría» no se memoriza
  (significa «sin opinión»), así que el siguiente gasto de ese comercio se vuelve a clasificar por
  reglas.
- Solo se categorizan los **gastos nuevos**. Lo que ya tenías guardado conserva la categoría que
  tuviera.

La tabla `merchant_categories` se añadió en la **versión 2** de la base de datos y la de
`budgets` (los presupuestos por categoría) en la **versión 3**. Room migra sola
(`AppDatabase.MIGRATION_1_2` y `MIGRATION_2_3`) y no toca los gastos que ya tenías: ni al añadir
la tabla de reglas ni al añadir la de presupuestos.

### Eficiencia

La app **no está «todo el rato en segundo plano»**: `NotificationListenerService` es un servicio
**enlazado por el sistema**, que solo existe porque concediste acceso a las notificaciones. No
hay alarmas, ni `WorkManager`, ni wakelocks, ni servicio en primer plano: Android arranca el
proceso cuando llega un aviso, la app hace su trabajo y el sistema lo mata después. Cualquier
alternativa (un servicio con notificación permanente, o un `WorkManager` que vaya comprobando)
gastaría **más** batería, así que se ha optimizado el trabajo, no el Convencimiento de estar vivo.

Lo que sí se ha hecho:

- **Filtro previo antes de tocar la base de datos.** `domain/parse/ExpensePreFilter.kt` es un
  objeto puro con dos expresiones regulares que exigen lo mínimo que el parser necesita para dar
  un gasto (un verbo de gasto o el símbolo de la divisa, y al menos una cifra). Un «Mariana. Mi
  amor» o un tweet se descartan en nanosegundos sin abrir SQLite ni tocar nada.
- **Las notificaciones se procesan en ráfaga.** `ingest/IngestQueue.kt` acumula los avisos en un
  canal sin límite y espera **1,5 s** a ver si llega alguno más; todo lo que se juntó se procesa
  como **un único lote y en una sola transacción** (un `fsync` en lugar de N). Efecto colateral
  bueno: si banco y Google Wallet avisan del mismo pago, la fusión se aplica dentro del lote y
  solo queda una fila.
- **La búsqueda del pago gemelo también usa el índice.** La consulta que busca un gasto del
  mismo importe usa `postedAt BETWEEN :desde AND :hasta` en lugar de
  `ABS(postedAt - ?) <= ?`: con la segunda forma SQLite solo puede usar el índice por paquete y
  filtra fila a fila (medido: **1,24 ms** con 20.000 gastos); con la primera aprovecha el rango
  completo (**0,002 ms**, unas 600 veces
  más rápido). El resultado es idéntico.
- **El paquete se filtra en el listener**, antes de encolar nada, con la lista de emisores
  permitidos de Ajustes.
- **Ajustes ya no carga la tabla entera** solo para saber qué apps han enviado avisos: se usa
  `SELECT DISTINCT sourcePackage FROM expenses`, sin recorrer el `rawText` de cada gasto.
- **Blindaje del listener.** Si el sistema desenlaza el servicio en silencio, la app se queda sin
  capturar gastos. Al abrirla, si sigue habiendo permiso pero el servicio no está conectado, se
  llama a `NotificationListenerService.requestRebind(...)` (Android 13 o superior). Si la versión
  es anterior, *Ajustes → Diagnóstico* lo dice, en vez de fallar sin ruido. Esto no cuesta
  batería: solo actúa cuando hay permiso y el servicio está caído.
- Se borró código muerto (`count()` sin usages) y se corrigió la concordancia de «1 gastos» con
  recursos `<plurals>` en los dos idiomas.

### Exportar a CSV

En **Ajustes → Datos → Exportar CSV** se elige dónde guardarlo (el selector de archivos del
sistema, sin permisos de almacenamiento). Columnas:

```
fecha,hora,importe,moneda,comercio,categoria,app_origen,confianza,texto_original
```

El archivo lleva BOM UTF-8 para que Excel abra bien los acentos, y el texto original va
entre comillas con las comillas internas duplicadas.

---

## 5. Pantallas

Las cuatro vistas comparten la misma cabecera, definida una sola vez en
`ui/components/ScreenHeader.kt`: barra fina, **sin color propio** (se apoya en el fondo de la
pagina, asi que no parece una franja pegada encima), y el titulo en letra compacta
(`titleMedium` en vez de `titleLarge`) porque el unico contenido que aporta es el nombre de
la vista. Ademas **se retira al bajar y vuelve al subir**: al hacer scroll hacia abajo la
cabecera se va y el contenido gana esos pixeles; en cuanto subes un poco vuelve a su sitio,
con el fondo de la pagina puesto para que lo que pasa por debajo no se lea del reves. Asi el
titulo esta cuando lo necesitas y desaparece cuando estorba.

Ademas, el `Scaffold` de `AppNavHost` que sostiene la barra inferior de pestanas usa
`contentWindowInsets = WindowInsets(0)`: si se dejaran los insets del sistema, esa
capa descontaria **otra vez** el hueco del status bar que ya descuenta la cabecera de
cada pantalla, y las cuatro vistas arrancarian con una franja en blanco por arriba.
Cada pantalla se queda asi con su altura real.


- **Resumen** — tarjeta protagonista con degradado de marca y el total del mes contando
  hacia arriba, «gastado hoy» y número de movimientos, pastilla de tendencia frente al mes
  anterior, media diaria de 7 días y ticket medio, línea de evolución de los últimos 30 días
  con media móvil, top 5 de comercios y anillo de categorías con el total del mes en el
  centro. Todos los gráficos están dibujados con `Canvas` de Compose: no hay ninguna librería
  de gráficos de terceros.
- **Gastos** — lista agrupada por día (con el total de cada jornada en la cabecera, y
  «Hoy» / «Ayer» en lugar de la fecha), avatar de color por categoría en cada fila,
  búsqueda libre (comercio, categoría, texto original) y total de lo que hay en pantalla.
  El único criterio es el buscador: los filtros de rango de fechas, categoría y orden
  se quitaron de esta pantalla a petición del usuario, y el listado se muestra siempre
  del más reciente al más antiguo.
  - **Deslizar una fila hacia la izquierda revela dos acciones** —*Editar* y *Eliminar*— en
    lugar de borrar el gasto: el arrastre está limitado al ancho de los botones, así que
    nunca se desliza "fuera" por accidente. Solo una fila puede estar abierta a la vez, y
    volver a tocarla la cierra. **Borrar sigue exigiendo tocar la papelera y confirmar**,
    con opción de *Deshacer*.
  - **Editar un gasto** abre un diálogo con el **importe** (validado con el mismo parser que
    el resto de la app, admite "45,90" y "45.90"), el **nombre del establecimiento** y las
    **21 categorías** —más un campo para crear una nueva—. Al guardar la categoría corregida
    la app **aprende** esa asociación, igual que cuando editas desde Ajustes. Si el cambio
    del importe chocara con el índice único de la tabla, avisa de que no pudo guardar.
  - **Añadir un gasto a mano** con el botón flotante **+**: útil para lo que no avisa de nada,
    como una tarjeta física, un pago en efectivo o un particular. Pide **importe**,
    **comercio** (obligatorio: si no hay aviso del que copiarlo, el usuario es quien sabe a
    quién pagó), **fecha** (por defecto hoy, con calendario y sin dejar elegir días futuros)
    y **categoría**. Si se deja en *Sin categoría*, la app la deduce del comercio con las
    reglas de siempre; si se elige una, también queda aprendida. El gasto se guarda con un
    origen reservado (`manual`) y confianza 100, así que nunca sale en *Revisar* ni se
    fusiona con el aviso del banco si coinciden el importe y el minuto.
  - **Al hacer scroll el buscador desaparece**: no es una cabecera fija sino el primer
    elemento de la lista, así que se va de la vista al bajar (como las filas) y vuelve
    al subir, dejando el hueco libre para ver más gastos de una vez.
  - **Agrupar por día, por semana o por mes**: un selector de tres botones bajo el contador
    cambia cómo se agrupan las cabeceras sin tocar nada más de la lista. Cada cabecera
    muestra el nombre del tramo y su total con un escalón por encima de los importes de las
    filas, siempre pegado al borde derecho. El día se nombra de forma natural («Hoy»,
    «Ayer», «martes 7 octubre»), el mes igual («Este mes», «mes pasado», «octubre 2026») y
    la semana muestra el rango de días que cubre («5 – 11 oct»), que es lo único que aporta
    su agrupación; las semanas empiezan en lunes. La elección se recuerda al salir y volver
    de la pantalla. Los tramos vacíos no se inventan: solo aparece lo que tiene gastos.
  - **Cierre de mes** — una tabla con los últimos **3, 6 o 12 meses** (se elige en la propia
    sección): total de cada mes, número de movimientos, ticket medio y variación en porcentaje
    frente al mes anterior (verde si gastaste menos, roja si más), más el total del año en
    curso. Los meses sin gastos no se inventan: se saltan. Todo el cálculo vive en
    `domain/MonthClose.kt` (Kotlin puro, con tests).
  - **Presupuestos** — si has puesto uno o más límites mensuales por categoría, aquí aparece cada
    uno con su barra: verde por debajo del 85 %, ámbar al acercarse y rojo al pasarse, con lo que
    queda o lo que te has pasado. **Sin avisos ni notificaciones**: la app no te avisa de nada,
    solo te lo enseña cuando abres el Resumen, que es como se pidió.
- **Ajustes** — **apps de las que se leen avisos**: es la parte de privacidad del gadget y
  está pensada para que se entienda sin leer nada más. Arriba hay una explicación corta
  (*"solo de las apps que marques aquí; lo de cualquier otra app se descarta sin mirarlo y
  la app no tiene permiso de internet"*). Debajo, dos opciones excluyentes con su
  consecuencia escrita: **todas las apps** o **solo las apps marcadas**. Estando en modo
  cerrado aparece un aviso en rojo diciendo que ahora mismo se leen los avisos de todo el
  teléfono, con un botón para dejar solo las marcadas de un toque.
  **Por defecto solo se leen las apps marcadas**: leer los avisos de todo el teléfono es la
  opción más invasiva, así que tiene que elegirse a propósito (las instalaciones que ya
  tuvieran la otra opción activa la conservan). Cada fila dice **cuántos gastos** ha
  aportado esa app y tiene un **Olvidar** que borra todos esos gastos y le quita el
  permiso a la app de paso, con confirmación, porque olvidar y dejar de leer van juntos.
  *Limitación conocida*: si el sistema no expone un nombre público para una app, se muestra
  su identificador (`de.traderepublic.app`) y la etiqueta *"Sin nombre público"*; el
  identificador es justo el dato que importa para saber a quién se leen los avisos.
- **Ajustes** — **apariencia** (tema claro u oscuro), estado del acceso a
  notificaciones, lista de apps emisoras permitidas, diagnóstico (servicio conectado,
  optimización de batería), exportación a CSV, revisión de los gastos dudosos, reglas
  aprendidas de ti, **presupuestos por categoría** (elegir categoría e importe mensual, o
  quitarlo), borrado total con doble confirmación, versión y aviso de privacidad. En los
  presupuestos se ofrecen tanto las 21 categorías del catálogo como **las que ya hayas usado
  en tus gastos** (las que hayas creado o renombrado tú), menos las que ya tengan un límite.
- **Primeros pasos** — dos pantallas con el mismo estilo: qué hace la app, cómo conceder el
  acceso a notificaciones y envío de una notificación de prueba.

### Apariencia

- **Tema**: **Sistema**, **Claro** u **Oscuro**, elegido en *Ajustes → Apariencia* y
  recordado entre arranques (clave `theme_mode` en las preferencias de la app). Con
  «Sistema» se sigue al ajuste del teléfono.
- **Paleta de marca**: verde `#1B6B4A` con degradados (`BrandColors` en
  `ui/theme/Theme.kt`), formas redondeadas grandes (24/32 dp) y cifras con cifras
  tabulares (`tnum`) para que los importes queden alineados.
- Las paletas completas de Material 3 (claro y oscuro) están escritas a mano, así que la app
  se ve igual en cualquier teléfono.

Interfaz en **español (por defecto) y en inglés**. No hay ni un solo texto escrito en el
código: todo sale de `res/values/strings.xml` y `res/values-en/strings.xml` (220 claves en
cada idioma, verificadas una a una).

---

## 6. Compatibilidad por ROM

`NotificationListenerService` es un servicio del sistema y funciona sin permisos extra en
Android stock y sus derivados. Donde hay que mirar es en las ROM chinas, que a veces matan
procesos en segundo plano o restringen el acceso a notificaciones.

| ROM / Fabricante | Estado | Qué hacer si no funciona |
|---|---|---|
| **AOSP, Pixel, LineageOS, crDroid, GrapheneOS, /e/ OS** | Funciona sin más | — |
| **Samsung One UI** | Funciona | Si aparece, activar *Apps sin restricción* para Mis Gastos |
| **Xiaomi / HyperOS / MIUI** | Requiere ajustes | Ajustes → Apps → Mis Gastos → *Autoinicio*, *Ahorro de energía* desactivado, y en *Notificaciones* activar todas las categorías |
| **Oppo / Realme (ColorOS)** | Requiere ajustes | Ajustes → Gestión de energía → *Optimización de energía* → *No restricción*; y permitir el autoinicio |
| **Vivo / iQOO (Funtouch)** | Requiere ajustes | Ajustes → Batería → *Alta prioridad en segundo plano* y *No restringir en segundo plano* |
| **Huawei / Honor (EMUI)** | Requiere ajustes | Ajustes → Batería → *Gestión de aplicaciones* → *Arranque automático* activado |
| **Meizu (Flyme)** | Requiere ajustes | Ajustes → Permissions → leer notificaciones permitido |

**Ajustes → Diagnóstico** te dice en un vistazo si el servicio está conectado y si la app
está exenta de la optimización de batería, con botones que abren la pantalla correcta del
sistema (y un enlace de reserva si el fabricante no ofrece la pantalla directa).

---

## 7. Privacidad

- **No hay permiso `INTERNET`.** Es imposible que la app envíe tus datos a ningún sitio.
- **Sin telemetría, sin analíticas, sin SDKs de terceros.**
- Lo único potencialmente identificable es `rawText`, el texto crudo de la notificación
  que el banco te manda. Puedes verlo y corregirlo en **Ajustes → Revisión**.
- **Los datos se quedan solo en este dispositivo.** `allowBackup="false"` y las reglas de
  `backup_rules.xml` y `data_extraction_rules.xml` excluyen todos los dominios, tanto en la
  copia en la nube como en el traspaso entre dispositivos.
- **La base de datos `mis_gastos.db` va cifrada con SQLCipher 4.20.0.** La clave se genera una
  sola vez (32 bytes aleatorios) y se guarda envuelta con AES-256-GCM en el AndroidKeyStore:
  nunca se escribe en claro en disco ni sale de la app. Al abrir una instalación anterior, la
  base plana se migra a cifrada y se borran los ficheros antiguos y sus auxiliares WAL/SHM.
- El acceso a notificaciones se concede de forma explícita y revocable desde los ajustes
  del sistema en cualquier momento.
- **La app no manda ninguna notificación de aviso**: ni superas un presupuesto, ni hay recordatorios
  ni nada parecido. La única notificación que emite es la de prueba que lanzas desde los Primeros pasos.

---

## 8. Decisiones de diseño

- **Importes en `Long` de céntimos**, nunca `Double`. La conversión de `"12,50"` a `1250` se
  hace parseando la cadena, sin pasar por coma flotante.
- **Inyección de dependencias manual** (`AppContainer`), sin Hilt: la app tiene un único
  módulo y cuatro objetos, y evitar una librería de inyección es una dependencia menos.
- **Gráficos con `Canvas` de Compose**, sin librería de terceros: menos peso, menos
  dependencias que actualizar.
- **El parser vive aislado** en `domain/parse/` (Kotlin puro, sin nada de Android) y todo
  el dominio (parser, detección de ingresos, reglas de categoría, clasificador, agrupación,
  cierre de mes, presupuestos y cola de ingesta) está cubierto por **240 tests unitarios** que
  corren en la JVM en menos de un segundo, sin emulador.
- **Solo se guardan gastos**: los avisos de dinero recibido (nómina, bizum entrante,
  devolución, abono…) se detectan y se descartan antes de insertar, porque una fila de más
  sumaría al total del mes y falsearía presupuestos y gráficos.
- **Paletas Material 3 escritas a mano** en lugar de generarlas: el color de marca es el
  verde de siempre, y las paletas de claro y oscuro se pueden ajustar sin depender de lo que
  devuelva una herramienta externa.- **Filtros en SQL, agregaciones en Kotlin**: la base filtra (índices) y `StatsCalculator`
  agrupa en memoria, porque los volúmenes de un gasto personal son pequeños y así el
  código es mucho más legible.
- **minSdk 26** (Android 8): cubre cualquier teléfono razonablemente actual. Sin
  `fallbackToDestructiveMigration`: si algún día se cambia el esquema, la app falla en
  lugar de borrar datos en silencio.
- **Procesar en lotes, no en tiempo real**: cada aviso por separado costaría una escritura y una
  transacción; esperando 1,5 s a agruparlos se hace una sola. Para un gasto personal, 1,5 s de
  retraso son invisibles y el ahorro es real.
- **Repositorio Git**: el proyecto se versiona en
  <https://github.com/rubensr91/MisGastos>. Las claves de firma, `local.properties` y las
  salidas de `build/` están en `.gitignore` y no se suben. El cifrado de la base y el resto
  de mejoras de privacidad viven en la rama `privacidad-p0`, que no se mezcla con `main`
  hasta probarla en un dispositivo real.

---

## 9. Estructura del proyecto

```
misgatos/
├── app/
│   ├── schemas/                       esquema Room exportado por KSP (1, 2 y 3)
│   └── src/
│       ├── main/
│       │   ├── kotlin/com/mfreya/misgastos/
│       │   │   ├── data/              Room, repositorio, ajustes (SharedPreferences)
│       │   │   ├── di/                AppContainer
│       │   │   ├── domain/            modelo, parser puro, reglas de categoría y agregaciones
│       │   │   ├── ingest/            NotificationIngestor
│       │   │   ├── listener/          NotificationListenerService
│       │   │   └── ui/                Compose: onboarding, resumen, lista, ajustes, tema
│       │   └── res/                   strings es/en, iconos, tema
│       └── test/                      240 tests unitarios (parser, ingresos, categorías,
│                                      agrupación, cierre de mes, presupuestos, prefiltro,
│                                      cola, fusión de avisos, gasto manual, ingestor, CSV
│                                      y edición atómica de gastos)
├── gradle/libs.versions.toml          catálogo de dependencias
├── keystore/                          clave de SUBIDA de Play (NO versionar)
├── keystore.properties                credenciales de esa clave (NO versionar)
├── keystore.properties.example        plantilla comentada de cómo crearlas
└── build.gradle.kts, settings.gradle.kts, gradlew
```

---

## 10. Publicar en Google Play

El proyecto ya está preparado para publicarse. Lo que queda son pasos manuales en Play Console
y subir el `.aab`. Resumen de lo que hace falta y de lo que ya está hecho.

### 10.1 Ya está hecho en el proyecto

| Requisito de Play | Estado |
|---|---|
| `targetSdk 36` | Obligatorio desde el 31/08/2026. Ya lo cumple. **No bajar.** |
| Paquete firmado | `keystore/misgastos-upload.jks` + `keystore.properties` (clave de **subida**) |
| Tarea `bundleRelease` | Genera el `.aab` que Play exige (Play no acepta APK) |
| Política de privacidad enlazada **dentro de la app** | Ajustes → Acerca de → «Política de privacidad» |
| Sin obfuscación obligatoria | Play no obliga a R8; `isMinifyEnabled = false` es válido |
| 64 bits / páginas de 16 KB | Cumple por defecto al no haber código nativo |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | **No** está en la lista de permisos restringidos de Play, así que no hace falta el Permissions Declaration Form |

### 10.2 Antes de la primera subida

1. **Cuenta de desarrollador.** 25 $ una sola vez, con verificación de identidad. Si la cuenta
   es personal y se creó **después del 13/11/2023**, Google exige una prueba cerrada con
   **12 testers durante 14 días consecutivos** y luego una revisión de hasta 7 días antes de
   poder publicar en producción. Comprueba si ya tienes cuenta en
   <https://play.google.com/console>.

2. **Política de privacidad publicada.** Obligatoria aunque la app no recoja datos personales.
   Crea una página web con la URL y **cámbiala** en
   `app/src/main/res/values/strings.xml`, clave `settings_privacy_policy_url`
   (ahora es un marcador de posición en el dominio reservado `.example`, que nunca resuelve).
   La misma URL se pone en la ficha de la tienda.

3. **`versionCode`.** Play no admite dos paquetes con el mismo `versionCode`: súbelo en
   **cada** publicación (`app/build.gradle.kts` → `defaultConfig`).

### 10.3 Formularios de Play Console

- **Seguridad de los datos (Data safety).** Obligatorio siempre. Se responde «la app no
  recoge ni comparte datos»: no tiene permiso de INTERNET y todo se queda en el teléfono.
- **Funciones financieras (Financial features declaration).** **El punto delicado.**
  La política *Financial Services* define lo financiero como la «gestión o inversión de
  dinero», y obliga a rellenar este formulario si la app contiene cualquier característica
  financiera. Registrar un gasto leído de la notificación de un banco puede entrar en esa
  definición y Google no concreta qué cuenta como tal. Se rellena describiendo que la app
  **no ofrece ningún producto ni servicio financiero**: no hay cuentas, no hay dinero, no
  hay pagos, solo un registro local de gastos que el usuario ya ha pagado.

### 10.4 Material gráfico de la ficha (obligatorio)

| Recurso | Formato |
|---|---|
| Icono de la app | PNG de 512×512 px **con canal alfa**, máx. 1024 KB |
| Gráfico de funciones | JPEG o PNG de 24 bits **sin alfa**, 1024×500 px |
| Capturas de pantalla | Mínimo **2** en dispositivos distintos; máx. 3840 px, mín. 320 px, y la dimensión mayor no puede pasar del doble de la menor |
| Descripción corta | Obligatoria, 80 caracteres como máximo |

### 10.5 Subir y publicar

```bash
./gradlew bundleRelease
# → app/build/outputs/bundle/release/app-release.aab  (≈8,4 MB)
```

Se sube en **Producción → Crear versión nueva**. **No** hay que subir el APK ni subir
manualmente el fichero de mapping de R8: Play asocia el que venga dentro del bundle.

### 10.6 Sobre perder la clave de subida

`keystore.properties` y `keystore/` están en `.gitignore` y no se versionan. Si se pierde el
`.jks`, en Play Console está **Configuración de la app → Firmado de aplicaciones →
Solicitar restablecimiento de la clave de subida**: Play genera una nueva y el siguiente
`.aab` se firma con ella. La app ya publicada no se rompe.

### 10.7 ¿Por qué no es lo mismo instalar el APK a mano?

Play Protect marca como de alto riesgo las apps **instaladas fuera de tienda** que usan
acceso a notificaciones, y puede bloquear su instalación. Publicar en Play es la vía «limpia»
justo para una app como esta. Aun así, sideload sigue siendo válido para uso personal.

## 11. Problemas frecuentes

**No se guarda ningún gasto.**
Abre **Ajustes → Diagnóstico**. Si el acceso a notificaciones no está concedido, actívalo.
Si la batería está restringida, toca el botón que abre la pantalla del fabricante. Después
usa «Enviar notificación de prueba»: si esa no aparece, el problema es del sistema, no del
parseo.

**Aparecen gastos con comercio raro («Desconocido») o marcados para revisar.**
El parser ha visto un formato que no reconoce. Ve a **Ajustes → Revisión**, corrige el
comercio y la categoría; queda corregido para siempre. Si tu banco tiene un formato
diferente, se añade en `domain/parse/` junto con su test.

**Sale un importe que no es.**
Borra el gasto deslizando la fila y confirma con **Ajustes → Borrar todo** si hace falta.
Los duplicados por reentrega del sistema no son posibles gracias al índice único de la tabla,
y los avisos duplicados del mismo pago (banco y Google Wallet a la vez, o un reenvío del banco
con hora nueva) se combinan en una sola fila por la ventana de fusión de 30 segundos descrita
más arriba.

**La app se cierra sola en segundo plano.**
Es el comportamiento típico de las ROM chinas: revisa la tabla de la sección 6.
