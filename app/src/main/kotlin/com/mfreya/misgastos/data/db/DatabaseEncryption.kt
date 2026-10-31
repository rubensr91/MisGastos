package com.mfreya.misgastos.data.db

import android.content.Context
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Prepara la base de datos para SQLCipher y convierte una base antigua en claro.
 *
 * La app guardaba el fichero `mis_gastos.db` como SQLite normal. Al abrirlo por primera vez
 * con esta version se copia a una base cifrada con `sqlcipher_export`, se comprueba que la
 * copia se puede leer y solo entonces se sustituye el original. Si el proceso muere a mitad,
 * la siguiente arranque reutiliza o repite la conversion sin dejar dos bases abiertas.
 */
internal object DatabaseEncryption {

    private const val PLAINTEXT_HEADER = "SQLite format 3\u0000"
    private const val TEMP_SUFFIX = "-enc"
    private val databases = setOf("", "-wal", "-shm", "-journal")

    @Volatile
    private var libraryLoaded = false

    /** Carga la libreria nativa una sola vez por proceso. */
    fun loadSqlCipher() {
        if (libraryLoaded) return
        synchronized(this) {
            if (libraryLoaded) return
            System.loadLibrary("sqlcipher")
            libraryLoaded = true
        }
    }

    /**
     * Convierte `mis_gastos.db` en una base cifrada si todavia esta en claro. No hace nada si
     * la base ya esta cifrada o si aun no existe (primer arranque).
     */
    fun prepare(context: Context, passphrase: ByteArray) {
        val databaseFile = context.getDatabasePath(AppDatabase.NAME)
        val parent = databaseFile.parentFile ?: return
        val tempFile = File(parent, "${AppDatabase.NAME}$TEMP_SUFFIX")

        // Muerte despues de borrar el original pero antes de renombrar: la copia ya esta
        // completa y cifrada, asi que se recupera.
        if (!databaseFile.exists()) {
            if (tempFile.exists()) promote(tempFile, databaseFile)
            return
        }

        if (!isPlaintext(databaseFile)) {
            // Ya esta cifrada: cualquier resto de una conversion anterior sobra.
            deleteDatabaseFiles(tempFile)
            return
        }

        loadSqlCipher()
        export(databaseFile, tempFile, passphrase)
        verifyEncrypted(tempFile, passphrase)
        deleteDatabaseFiles(databaseFile)
        promote(tempFile, databaseFile)
    }

    fun isPlaintext(file: File): Boolean {
        if (!file.isFile) return false
        val header = ByteArray(PLAINTEXT_HEADER.length)
        val read = file.inputStream().use { it.read(header) }
        return read == header.size && String(header, Charsets.ISO_8859_1) == PLAINTEXT_HEADER
    }

    private fun export(source: File, target: File, passphrase: ByteArray) {
        deleteDatabaseFiles(target)
        check(target.createNewFile()) { "No se pudo crear la copia cifrada" }
        val key = passphrase.decodeToString()
        val plaintext = SQLiteDatabase.openDatabase(
            source.path,
            null as SQLiteDatabase.CursorFactory?,
            SQLiteDatabase.OPEN_READWRITE,
        )
        try {
            // Todo lo que quede en el WAL del SQLite antiguo tiene que entrar en la copia.
            runCatching { plaintext.rawExecSQL("PRAGMA wal_checkpoint(FULL)") }
            val targetPath = target.path.replace("'", "''")
            plaintext.rawExecSQL("ATTACH DATABASE '$targetPath' AS encrypted KEY '$key'")
            plaintext.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            plaintext.rawExecSQL("DETACH DATABASE encrypted")
        } finally {
            plaintext.close()
        }
    }

    /** Abre la copia con la clave para no sustituir la base buena por un fichero corrupto. */
    private fun verifyEncrypted(file: File, passphrase: ByteArray) {
        val database = SQLiteDatabase.openDatabase(
            file.path,
            passphrase,
            null as SQLiteDatabase.CursorFactory?,
            SQLiteDatabase.OPEN_READWRITE,
            null,
        )
        try {
            database.rawQuery("PRAGMA cipher_version", null).use { cursor ->
                check(cursor.moveToFirst()) { "La copia cifrada no se puede abrir" }
            }
        } finally {
            database.close()
        }
    }

    private fun promote(tempFile: File, databaseFile: File) {
        check(tempFile.renameTo(databaseFile)) {
            "No se pudo sustituir la base de datos por la version cifrada"
        }
        // El renombrado solo mueve el fichero principal; los auxiliares del temporal ya no
        // sirven para la base nueva.
        deleteDatabaseFiles(File(tempFile.path + "-wal"))
        deleteDatabaseFiles(File(tempFile.path + "-shm"))
    }

    private fun deleteDatabaseFiles(file: File) {
        val base = file.path.removeSuffix("-wal").removeSuffix("-shm").removeSuffix("-journal")
        for (suffix in databases) {
            File(base + suffix).delete()
        }
    }
}