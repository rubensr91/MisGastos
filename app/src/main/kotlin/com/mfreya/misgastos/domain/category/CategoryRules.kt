package com.mfreya.misgastos.domain.category

/**
 * Regla de categorizacion: una categoria y las palabras clave que la aplican.
 *
 * Las palabras clave se comparan como secuencias de tokens (ver [MerchantKeys]), asi que
 * `uber eats` solo encaja si el texto contiene esas dos palabras seguidas y `bar` no encaja
 * dentro de `barcelona`.
 */
class CategoryRule(val category: String, keywords: List<String>) {

    val keywords: List<List<String>> = keywords
        .map(MerchantKeys::tokenize)
        .filter { it.isNotEmpty() }
}

/**
 * Motor de categorizacion automatica por reglas. Es Kotlin puro, sin dependencias de Android, asi
 * que se puede probar en la JVM con toda la logica real.
 *
 * Puntuacion: una palabra clave encontrada en el comercio pesa el doble que encontrada en el texto
 * de la notificacion, porque el comercio es mucho mas fiable que el texto completo. Se exige un
 * minimo de [MIN_SCORE] puntos para dar una categoria: de ese modo una coincidencia debil y suelta
 * en el cuerpo del aviso no decide nada por si sola.
 */
object CategoryRules {

    const val MIN_SCORE = 2

    private const val MERCHANT_WEIGHT = 2

    val DEFAULT: List<CategoryRule> = listOf(
        CategoryRule(
            "Supermercado",
            listOf(
                "supermercado", "super", "supermercat", "hipermercado", "grocery", "groceries",
                "mercadona", "meradona", "mercadona online", "mercadonaonline",
                "carrefour", "eroski", "caprabo", "consum", "jumbo", "aldi", "lidl", "alcampo",
                "auchan", "bonarea", "coviran", "alberts", "dia", "dias", "superconumo",
            ),
        ),
        CategoryRule(
            "Servicios",
            listOf(
                "servicios", "servicio", "suministro", "suministros", "factura", "recibo", "recibos",
                "luz", "electricidad", "agua", "gas natural", "comunidades", "comunidad",
                "fibra", "internet", "movistar", "vodafone", "orange", "yettel", "masmovil",
                "jazztel", "telecable", "iberdrola", "endesa", "naturgy", "canal isabel", "ono",
            ),
        ),
        CategoryRule(
            "Restaurante",
            listOf(
                "restaurante", "restaurantes", "bar", "barras", "cafe", "cafeteria", "coffee",
                "taverna", "taberna", "pizzeria", "asador", "sushi", "brunch", "tapas",
                "vermuteria", "marisqueria", "heladeria", "panaderia", "pasteleria", "comida",
                "cena", "almuerzo", "menu", "glovo", "ubereats", "uber eats", "deliveroo",
                "just eat", "justeat",
            ),
        ),
        CategoryRule(
            "Comida rápida",
            listOf(
                "comida rapida", "fast food", "fastfood", "mcdonalds", "burger", "burger king",
                "kfc", "subway", "dominos", "domino", "fiveguys", "wendys", "dunkin", "popeyes",
                "doner", "kebab", "taco", "tacos", "telepizza", "chicken", "jollibee",
            ),
        ),
        CategoryRule(
            "Combustible",
            listOf(
                "combustible", "carburante", "gasolina", "gasolinera", "gasolineras", "petrol",
                "repsol", "shell", "bp", "cepsa", "totalenergies", "totalenergias", "total energies", "ballesta",
                "galp", "genuine", "repostaje",
            ),
        ),
        CategoryRule(
            "Transporte",
            listOf(
                "transporte", "taxi", "taxis", "uber", "cabify", "bolt", "capigo", "free now",
                "renfe", "cercanias", "ave", "alvia", "eurostar", "iryo", "trenitalia", "sncf",
                "db bahn", "metro", "emt", "tmb", "amb", "smsa", "tranvia", "billete", "billetes",
            ),
        ),
        CategoryRule(
            "Estacionamiento",
            listOf(
                "parking", "aparcamiento", "aparcamientos", "estacionamiento",
                "parkingandalucia", "parkmobile", "parkmo", "telpark", "appark", "saba",
            ),
        ),
        CategoryRule(
            "Viajes",
            listOf(
                "viajes", "travel", "trip", "airline", "airlines", "hotel", "hoteles", "hostal",
                "hostel", "hostalworld", "booking", "airbnb", "expedia", "edreams", "kiwi",
                "trivago", "ryanair", "easyjet", "iberia", "vueling", "wizz air", "crucero",
                "cruise", "vuelo", "vuelos", "escapada",
            ),
        ),
        CategoryRule(
            "Compras online",
            listOf(
                "compras online", "tienda online", "amazon", "amzn", "ebay", "aliexpress", "temu",
                "shein", "zalando", "wallapop", "vinted", "primark", "zara", "ikea",
                "leroy merlin", "bricomart", "media markt", "mediamarkt", "decathlon", "pccompone",
                "coolmod", "newegg", "fnac", "corte ingles", "el corte ingles", "worten",
            ),
        ),
        CategoryRule(
            "Suscripciones",
            listOf(
                "suscripcion", "suscripciones", "subscription", "netflix", "spotify", "hbo",
                "hbo max", "disney", "disneyplus", "disney plus", "prime video", "amazon prime",
                "canal+", "sky", "filmin", "movi star", "icloud", "google one", "microsoft 365",
                "notion", "dropbox", "patreon", "substack", "linkedin premium",
                "youtube premium", "adobe", "office 365",
            ),
        ),
        CategoryRule(
            "Salud",
            listOf(
                "salud", "sanidad", "farmacia", "farmacias", "cross", "parafarmacia", "hospital",
                "clinica", "dental", "dentista", "medico", "medicina", "optica", "opticas",
                "sanitas", "adeslas", "dkv", "medicamento", "medicamentos", "analisis",
                "fisioterapia", "fisio", "psicologo", "psiquiatria", "dentistas", "veterinario",
            ),
        ),
        CategoryRule(
            "Ocio",
            listOf(
                "ocio", "entretenimiento", "cine", "cines", "cinepolis", "yelmo", "kinepolis",
                "odeon", "cinemark", "teatro", "musical", "museo", "museos", "concierto",
                "conciertos", "ticketmaster", "eventbrite", "livenation", "steam", "playstation",
                "xbox", "nintendo", "disneyland", "portaventura", "aqualandia", "parque warner",
                "capea", "toros", "fiesta", "fiestas",
            ),
        ),
        CategoryRule(
            "Educación",
            listOf(
                "educacion", "formacion", "academia", "colegio", "escuela", "instituto", "institu",
                "universidad", "university", "campus", "curso", "cursos", "udemy", "coursera",
                "skillshare", "masterclass", "autoescuela", "matricula", "inscripcion", "examen",
                "babbel", "duolingo", "inlingua",
            ),
        ),
        CategoryRule(
            "Deportes",
            listOf(
                "deportes", "deporte", "deportivo", "gimnasio", "gym", "basic fit", "basicfit",
                "mcfit", "mcgym", "vida fitness", "padel", "tenis", "futbol", "futebol",
                "ciclismo", "bici", "running", "yoga", "pilates", "crossfit", "natacion",
                "alquiler de pistas",
            ),
        ),
        CategoryRule(
            "Mascotas",
            listOf(
                "mascotas", "veterinario", "veterinaria", "petshop", "pets", "tiendanimal",
                "kiwoko", "purina", "royal canin", "fressnapf", "grooming", "peluqueria canina",
                "animales",
            ),
        ),
        CategoryRule(
            "Regalos",
            listOf(
                "regalo", "regalos", "flores", "floristeria", "interflora", "millorflors",
                "confiteria", "bomboneria", "chocolates",
            ),
        ),
        CategoryRule(
            "Banca",
            listOf(
                "banca", "comision", "comisiones", "mantenimiento", "interes", "intereses",
                "transferencia", "transferencias", "bizum", "domiciliacion",
            ),
        ),
        CategoryRule(
            "Impuestos",
            listOf(
                "impuesto", "impuestos", "hacienda", "aeat", "agencia tributaria",
                "seguridad social", "ibi", "tasa municipal", "impuesto municipal", "ayuntamiento",
                "catastro",
            ),
        ),
        CategoryRule(
            "Efectivo",
            listOf(
                "efectivo", "atm", "cajero", "cajeros", "reintegro", "retirada", "disposicion",
            ),
        ),
        CategoryRule(
            "Otros",
            listOf("otros", "other", "varios"),
        ),
    )

    /**
     * Devuelve la categoria mas probable para [merchant] / [rawText], o `null` si ninguna regla
     * alcanza [MIN_SCORE]. En caso de empate gana la primera regla del catalogo, de forma que el
     * resultado sea siempre determinista.
     */
    fun match(merchant: String, rawText: String): String? = match(merchant, rawText, DEFAULT)

    fun match(merchant: String, rawText: String, rules: List<CategoryRule>): String? {
        val merchantTokens = MerchantKeys.tokenize(merchant)
        val rawTokens = MerchantKeys.tokenize(rawText)
        if (merchantTokens.isEmpty() && rawTokens.isEmpty()) return null

        var best: CategoryRule? = null
        var bestScore = 0
        for (rule in rules) {
            var score = 0
            for (keyword in rule.keywords) {
                if (MerchantKeys.containsTokens(merchantTokens, keyword)) {
                    score += MERCHANT_WEIGHT * keyword.size
                }
                if (MerchantKeys.containsTokens(rawTokens, keyword)) {
                    score += keyword.size
                }
            }
            if (score > bestScore) {
                bestScore = score
                best = rule
            }
        }
        return if (bestScore >= MIN_SCORE) best?.category else null
    }
}
