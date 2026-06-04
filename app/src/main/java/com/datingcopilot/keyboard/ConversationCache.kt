package com.datingcopilot.keyboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ConversationCache(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private val db: SQLiteDatabase get() = writableDatabase

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE conversations (
                match_id TEXT PRIMARY KEY,
                messages TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS conversations")
        onCreate(db)
    }

    fun saveMessage(matchId: String, sender: String, text: String) {
        val existing = getMessages(matchId)
        val messages = existing.toMutableList()
        messages.add(mapOf("sender" to sender, "text" to text))

        val gson = com.google.gson.Gson()
        val cv = ContentValues().apply {
            put("match_id", matchId)
            put("messages", gson.toJson(messages))
            put("updated_at", System.currentTimeMillis())
        }

        db.insertWithOnConflict(TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getMessages(matchId: String): List<Map<String, String>> {
        val cursor = db.query(TABLE, null, "match_id = ?", arrayOf(matchId), null, null, null)
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {}.type

        cursor.use {
            if (it.moveToFirst()) {
                val json = it.getString(it.getColumnIndexOrThrow("messages"))
                return gson.fromJson(json, type)
            }
        }
        return emptyList()
    }

    fun deleteMatch(matchId: String) {
        db.delete(TABLE, "match_id = ?", arrayOf(matchId))
    }

    fun clearAll() {
        db.execSQL("DELETE FROM $TABLE")
    }

    override fun close() {
        db.close()
    }

    companion object {
        const val DB_NAME = "dating_conversations.db"
        const val TABLE = "conversations"
        const val DB_VERSION = 1
    }
}
