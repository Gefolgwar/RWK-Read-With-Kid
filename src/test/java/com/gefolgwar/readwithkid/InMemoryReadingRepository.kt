package com.gefolgwar.readwithkid

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class InMemoryReadingRepository : ReadingRepository {
    private val readings = MutableStateFlow<List<ReadingRecord>>(emptyList())
    private var nextId = 1L

    val all: List<ReadingRecord> get() = readings.value

    override fun observeReadings(): Flow<List<ReadingRecord>> =
        readings.map { all -> all.sortedWith(compareBy<ReadingRecord> { it.readAt }.thenBy { it.id }) }

    override suspend fun saveReading(record: ReadingRecord): Long {
        val saved = record.copy(id = nextId++)
        readings.update { it + saved }
        return saved.id
    }
}
