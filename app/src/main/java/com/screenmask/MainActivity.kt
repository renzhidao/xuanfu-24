
package com.screenmask

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ScreenMask/Main"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAddRule: Button
    private lateinit var adapter: RuleAdapter
    private val ruleManager by lazy { RuleManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate()")
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerRules)
        btnAddRule = findViewById(R.id.btnAddRule)

        adapter = RuleAdapter(
            rules = ruleManager.getRules().toMutableList(),
            onDelete = { rule ->
                Log.i(TAG, "onDelete id=${rule.id}")
                ruleManager.deleteRule(rule.id)
                refreshList()
                updateOverlay()
            },
            onToggle = { rule ->
                Log.i(TAG, "onToggle id=${rule.id}, enabled->${!rule.enabled}")
                ruleManager.updateRule(rule.copy(enabled = !rule.enabled))
                refreshList()
                updateOverlay()
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        btnAddRule.setOnClickListener {
            Log.i(TAG, "AddRule clicked, canDrawOverlays=${Settings.canDrawOverlays(this)}")
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission()
            } else {
                startSelectArea()
            }
        }

        updateOverlay()
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume()")
        refreshList()
        // 这里不自动跳转，避免从设置页回来造成惊扰；仅记录状态
        Log.d(TAG, "onResume after permission flow: canDrawOverlays=${Settings.canDrawOverlays(this)}")
        updateOverlay()
    }

    private fun startSelectArea() {
        Log.i(TAG, "startSelectArea()")
        try {
            startActivity(Intent(this, SelectAreaActivity::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "startSelectArea failed", e)
            Toast.makeText(this, "无法打开选择界面：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshList() {
        val rules = ruleManager.getRules()
        Log.d(TAG, "refreshList: size=${rules.size}, enabled=${rules.count { it.enabled }}")
        adapter.updateRules(rules)
    }

    private fun updateOverlay() {
        val hasPerm = Settings.canDrawOverlays(this)
        val rules = ruleManager.getRules()
        val enabledCount = rules.count { it.enabled }
        Log.i(TAG, "updateOverlay: hasPerm=$hasPerm, rules=${rules.size}, enabled=$enabledCount")

        val intent = Intent(this, OverlayService::class.java)
        try {
            stopService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "stopService ignored: ${e.message}")
        }

        if (hasPerm && enabledCount > 0) {
            try {
                val res = startService(intent)
                Log.i(TAG, "startService OverlayService -> $res")
            } catch (e: Exception) {
                Log.e(TAG, "startService failed", e)
                Toast.makeText(this, "启动悬浮服务失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Log.i(TAG, "Not starting service: hasPerm=$hasPerm, enabledCount=$enabledCount")
        }
    }

    private fun requestOverlayPermission() {
        Log.w(TAG, "requestOverlayPermission()")
        AlertDialog.Builder(this)
            .setTitle("需要悬浮窗权限")
            .setMessage("请在设置中允许应用显示悬浮窗")
            .setPositiveButton("去设置") { _, _ ->
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "open overlay settings with package failed, fallback", e)
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    } catch (e2: Exception) {
                        Log.e(TAG, "open overlay settings failed", e2)
                        Toast.makeText(this, "无法打开权限设置，请手动前往系统设置开启", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

class RuleAdapter(
    private var rules: MutableList<Rule>,
    private val onDelete: (Rule) -> Unit,
    private val onToggle: (Rule) -> Unit
) : RecyclerView.Adapter<RuleAdapter.RuleViewHolder>() {

    class RuleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPosition: TextView = view.findViewById(R.id.tvPosition)
        val tvColor: TextView = view.findViewById(R.id.tvColor)
        val viewColorPreview: View = view.findViewById(R.id.viewColorPreview)
        val btnToggle: Button = view.findViewById(R.id.btnToggle)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rule, parent, false)
        return RuleViewHolder(view)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        val rule = rules[position]
        holder.tvPosition.text = "位置: ${rule.left},${rule.top} - ${rule.right},${rule.bottom}"
        holder.tvColor.text = "颜色: #${Integer.toHexString(rule.color).uppercase()}"
        holder.viewColorPreview.setBackgroundColor(rule.color)
        holder.btnToggle.text = if (rule.enabled) "关闭" else "开启"
        holder.btnToggle.setOnClickListener { onToggle(rule) }
        holder.btnDelete.setOnClickListener { onDelete(rule) }
    }

    override fun getItemCount() = rules.size

    fun updateRules(newRules: List<Rule>) {
        rules.clear()
        rules.addAll(newRules)
        notifyDataSetChanged()
    }
}