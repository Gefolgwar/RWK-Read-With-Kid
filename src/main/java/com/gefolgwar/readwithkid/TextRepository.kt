package com.gefolgwar.readwithkid

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
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

/** A text a parent saved in the library. [id] is 0 until the text is saved. */
@Entity(tableName = "texts")
data class SavedText(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val rawText: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "UK") val language: TextLanguage = TextLanguage.UK
)

interface TextRepository {
    /** All texts, newest first. */
    fun observeAll(): Flow<List<SavedText>>

    /** Inserts a text with id 0, otherwise updates it. Returns the text's id. */
    suspend fun save(text: SavedText): Long

    suspend fun delete(id: Long)
}

@Dao
abstract class TextDao : TextRepository {
    @Query("SELECT * FROM texts ORDER BY createdAt DESC, id DESC")
    abstract override fun observeAll(): Flow<List<SavedText>>

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
    entities = [SavedText::class, ChildProfile::class, TextAssignment::class, ReadingRecord::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(WordListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun texts(): TextDao
    abstract fun children(): ChildDao
    abstract fun readings(): ReadingDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: build(context, "read_with_kid.db").also { instance = it }
        }

        fun build(context: Context, name: String): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

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
    }
}
