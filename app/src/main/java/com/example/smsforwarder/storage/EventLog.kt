package com.example.smsforwarder.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_LOG_ENTRIES = 100

/**
 * 内存日志：保存最近 N 条事件，供 UI 实时展示
 */
object EventLog {

    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun add(message: String) {
        val time = timeFormat.format(Date())
        val entry = "[$time] $message"
        val newList = (_entries.value + entry).takeLast(MAX_LOG_ENTRIES)
        _entries.value = newList
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
