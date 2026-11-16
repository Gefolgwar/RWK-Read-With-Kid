package com.gefolgwar.readwithkid

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class InMemoryReadingRepository : ReadingRepository {
    private val readings = MutableStateFlow<List<ReadingRecord>>(emptyList())
    private var nextId = 1L

    val all: List<ReadingRecord> get() = readings.value

    override fun observeReadings(textId: Long): Flow<List<ReadingRecord>> =
        readings.map { all ->
            all.filter { it.textId == textId }.sortedWith(compareByDescending<ReadingRecord> { it.readAt }.thenByDescending { it.id })
        }

    override fun observeReadingsOf(textIds: List<Long>): Flow<List<ReadingRecord>> =
        readings.map { all -> all.filter { it.textId in textIds } }

    override suspend fun saveReading(record: ReadingRecord): Long {
        val saved = record.copy(id = nextId++)
        readings.update { it + saved }
        return saved.id
    }

    override suspend fun deleteReading(id: Long) {
        readings.update { all -> all.filter { it.id != id } }
    }
}
