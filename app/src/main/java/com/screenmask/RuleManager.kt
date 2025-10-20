
package com.screenmask

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RuleManager(private val context: Context) {

    companion object {
        private const val TAG = "ScreenMask/Rules"
    }

    private val prefs = context.getSharedPreferences("rules", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getRules(): List<Rule> {
        return try {
            val json = prefs.getString("rules_list", "[]") ?: "[]"
            val type = object : TypeToken<List<Rule>>() {}.type
            val list: List<Rule> = gson.fromJson(json, type) ?: emptyList()
            Log.d(TAG, "getRules: size=${list.size}, enabled=${list.count { it.enabled }}")
            list
        } catch (e: Exception) {
            Log.e(TAG, "getRules failed", e)
            emptyList()
        }
    }

    fun addRule(rule: Rule) {
        Log.i(TAG, "addRule id=${rule.id}")
        val rules = getRules().toMutableList()
        rules.add(rule)
        saveRules(rules)
    }

    fun updateRule(rule: Rule) {
        Log.i(TAG, "updateRule id=${rule.id}")
        val rules = getRules().toMutableList()
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index != -1) {
            rules[index] = rule
            saveRules(rules)
        } else {
            Log.w(TAG, "updateRule: id not found -> ${rule.id}")
        }
    }

    fun deleteRule(id: Long) {
        Log.i(TAG, "deleteRule id=$id")
        val rules = getRules().toMutableList()
        val before = rules.size
        rules.removeAll { it.id == id }
        Log.d(TAG, "deleteRule removed=${before - rules.size}")
        saveRules(rules)
    }

    private fun saveRules(rules: List<Rule>) {
        try {
            val json = gson.toJson(rules)
            prefs.edit().putString("rules_list", json).apply()
            Log.d(TAG, "saveRules: size=${rules.size}, enabled=${rules.count { it.enabled }}")
        } catch (e: Exception) {
            Log.e(TAG, "saveRules failed", e)
        }
    }
}