package com.gefolgwar.readwithkid

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * A text in the library of the child [childId]; removed together with the child. [id] is 0 until the text is saved.
 * A [sample] came with the app for trying it out; once edited it is the parent's own text.
 */
@Entity(
    tableName = "texts",
    foreignKeys = [
        ForeignKey(entity = Child::class, parentColumns = ["id"], childColumns = ["childId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("childId")]
)
data class SavedText(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val rawText: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "UK") val language: TextLanguage = TextLanguage.UK,
    val childId: Long,
    @ColumnInfo(defaultValue = "0") val sample: Boolean = false
)

interface TextRepository {
    /** All texts of the child, newest first. */
    fun observeAll(childId: Long): Flow<List<SavedText>>

    /** Inserts a text with id 0, otherwise updates it. Returns the text's id. */
    suspend fun save(text: SavedText): Long

    suspend fun delete(id: Long)
}

@Dao
abstract class TextDao : TextRepository {
    @Query("SELECT * FROM texts WHERE childId = :childId ORDER BY createdAt DESC, id DESC")
    abstract override fun observeAll(childId: Long): Flow<List<SavedText>>

    @Insert
    protected abstract suspend fun insert(text: SavedText): Long

    @Update
    protected abstract suspend fun update(text: SavedText)

    override suspend fun save(text: SavedText): Long =
        if (text.id == 0L) insert(text) else text.id.also { update(text) }

    @Query("DELETE FROM texts WHERE id = :id")
    abstract override suspend fun delete(id: Long)
}

@Database(
    entities = [Child::class, SavedText::class, ReadingRecord::class],
    version = 8,
    exportSchema = false
)
@TypeConverters(WordListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun texts(): TextDao
    abstract fun readings(): ReadingDao
    abstract fun children(): ChildDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: build(context, "read_with_kid.db", withSamples = true).also { instance = it }
        }

        /** [withSamples]: a new database also gets the [SampleTexts] for its first child. */
        fun build(context: Context, name: String, withSamples: Boolean = false): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .addCallback(object : Callback() {
                    // A new library starts with one child who has no name yet.
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("INSERT INTO `children` (`name`) VALUES ('')")
                        if (withSamples) addSamples(db)
                    }
                })
                .build()

        /** Adds the samples once, when the app is installed; the first sample is listed first. */
        private fun addSamples(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            SampleTexts.all.forEachIndexed { i, (raw, language) ->
                db.execSQL(
                    "INSERT INTO `texts` (`title`, `rawText`, `createdAt`, `language`, `childId`, `sample`) " +
                        "VALUES (?, ?, ?, ?, (SELECT MIN(`id`) FROM `children`), 1)",
                    arrayOf<Any>(TextPreparation.autoTitle(raw), raw, now - i, language.name)
                )
            }
        }

        /** Adds child profiles and text assignments; saved texts are kept. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `children` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `avatar` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `assignments` (`childId` INTEGER NOT NULL, `textId` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`childId`, `textId`), " +
                        "FOREIGN KEY(`childId`) REFERENCES `children`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`textId`) REFERENCES `texts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_assignments_textId` ON `assignments` (`textId`)")
            }
        }

        /** Adds the language of each text; texts saved before were Ukrainian. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `texts` ADD COLUMN `language` TEXT NOT NULL DEFAULT 'UK'")
            }
        }

        /** Adds saved readings; earlier data is kept. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `readings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`childId` INTEGER NOT NULL, `textId` INTEGER NOT NULL, `textTitle` TEXT NOT NULL, " +
                        "`readAt` INTEGER NOT NULL, `totalWords` INTEGER NOT NULL, `correctWords` INTEGER NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, `skippedWords` TEXT NOT NULL, `retriedWords` TEXT NOT NULL, " +
                        "FOREIGN KEY(`childId`) REFERENCES `children`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_readings_childId` ON `readings` (`childId`)")
            }
        }

        /** Adds the words said for the child; earlier readings had none. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `readings` ADD COLUMN `helpedWords` TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Removes child profiles and text assignments; readings now belong to their text only.
         * Texts and their readings are kept; readings of texts deleted earlier could no longer be shown and are dropped.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE `readings_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`textId` INTEGER NOT NULL, `textTitle` TEXT NOT NULL, `readAt` INTEGER NOT NULL, " +
                        "`totalWords` INTEGER NOT NULL, `correctWords` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                        "`skippedWords` TEXT NOT NULL, `retriedWords` TEXT NOT NULL, `helpedWords` TEXT NOT NULL DEFAULT '', " +
                        "FOREIGN KEY(`textId`) REFERENCES `texts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                val columns = "`id`, `textId`, `textTitle`, `readAt`, `totalWords`, `correctWords`, `durationMs`, " +
                    "`skippedWords`, `retriedWords`, `helpedWords`"
                db.execSQL(
                    "INSERT INTO `readings_new` ($columns) SELECT $columns FROM `readings` " +
                        "WHERE `textId` IN (SELECT `id` FROM `texts`)"
                )
                db.execSQL("DROP TABLE `readings`")
                db.execSQL("ALTER TABLE `readings_new` RENAME TO `readings`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_readings_textId` ON `readings` (`textId`)")
                db.execSQL("DROP TABLE `assignments`")
                db.execSQL("DROP TABLE `children`")
            }
        }

        /** Brings back child profiles: every saved text, with its readings, belongs to a first child who has no name yet. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE `children` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
                db.execSQL("INSERT INTO `children` (`name`) VALUES ('')")
                // A column with a foreign key cannot be added to a table, so the texts move to a new one.
                db.execSQL(
                    "CREATE TABLE `texts_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                        "`rawText` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `language` TEXT NOT NULL DEFAULT 'UK', " +
                        "`childId` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`childId`) REFERENCES `children`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "INSERT INTO `texts_new` (`id`, `title`, `rawText`, `createdAt`, `language`, `childId`) " +
                        "SELECT `id`, `title`, `rawText`, `createdAt`, `language`, (SELECT MIN(`id`) FROM `children`) FROM `texts`"
                )
                db.execSQL("DROP TABLE `texts`")
                db.execSQL("ALTER TABLE `texts_new` RENAME TO `texts`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_texts_childId` ON `texts` (`childId`)")
            }
        }

        /** Marks the samples that come with the app; texts saved before are the parent's own. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `texts` ADD COLUMN `sample` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
