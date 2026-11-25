package com.school.birthdaywidget

import android.app.Activity
import android.os.Bundle
import android.widget.*

class MainActivity : Activity() {
    private lateinit var tv: TextView
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        SyncScheduler.periodic(this); SyncScheduler.now(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 40) }
        tv = TextView(this).apply { textSize = 18f }
        val btn = Button(this).apply {
            text = "Sync now"
            setOnClickListener { SyncScheduler.now(this@MainActivity); postDelayed({ show() }, 4000) }
        }
        val help = TextView(this).apply {
            text = "\nWidget lagane ke liye: home screen par long-press > Widgets > Birthday Widget."
            textSize = 13f
        }
        box.addView(btn); box.addView(tv); box.addView(help)
        setContentView(ScrollView(this).apply { addView(box) })
        show()
    }
    private fun show() {
        val l = loadToday(this)
        tv.text = if (l.isEmpty()) "\nAaj koi birthday nahi" else "\n" + l.joinToString("\n") { "• ${it.first} (Std ${it.second})" }
    }
}
