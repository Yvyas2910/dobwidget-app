package com.school.birthdaywidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

const val DAYS_AHEAD = 7          // aaj se kitne din aage tak list dikhani hai
const val INCLUDE_TODAY = true    // false karoge to aaj ka birthday list me nahi dikhega

data class Entry(val header: Boolean, val text: String, val std: String = "")

private fun mdOf(cal: Calendar) = SimpleDateFormat("MM-dd", Locale.US).format(cal.time)
private fun dayAt(offset: Int): Calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
private fun startOffset() = if (INCLUDE_TODAY) 0 else 1

// Firestore query ke liye: dikhane wale saare dino ke "MM-dd"
fun windowMDs(): List<String> = (startOffset()..DAYS_AHEAD).map { mdOf(dayAt(it)) }

// cache se date-wise list: har din ka header + uske students
fun loadUpcoming(c: Context): List<Entry> {
    val a = JSONArray(c.getSharedPreferences("bday", 0).getString("list", "[]"))
    val all = (0 until a.length()).map { a.getJSONObject(it) }
    val out = mutableListOf<Entry>()
    for (off in startOffset()..DAYS_AHEAD) {
        val cal = dayAt(off)
        val day = all.filter { it.optString("md") == mdOf(cal) }
        if (day.isEmpty()) continue
        val first = when (off) {
            0 -> "Aaj"
            1 -> "Kal"
            else -> SimpleDateFormat("EEEE", Locale.ENGLISH).format(cal.time)
        }
        out.add(Entry(true, first + " • " + SimpleDateFormat("dd MMM", Locale.ENGLISH).format(cal.time)))
        day.forEach { out.add(Entry(false, it.getString("n"), it.getString("s"))) }
    }
    return out
}

class BirthdayWidget : AppWidgetProvider() {
    override fun onEnabled(c: Context) { SyncScheduler.periodic(c) }
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        refresh(c); SyncScheduler.now(c)
    }
    override fun onReceive(c: Context, i: Intent) {
        super.onReceive(c, i)
        if (i.action == Intent.ACTION_DATE_CHANGED || i.action == Intent.ACTION_TIME_CHANGED ||
            i.action == Intent.ACTION_TIMEZONE_CHANGED) { refresh(c); SyncScheduler.now(c) }
    }
    companion object {
        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            val ids = m.getAppWidgetIds(ComponentName(c, BirthdayWidget::class.java))
            ids.forEach { m.updateAppWidget(it, build(c, it)) }
            m.notifyAppWidgetViewDataChanged(ids, R.id.list)
        }
        private fun build(c: Context, id: Int): RemoteViews {
            val rv = RemoteViews(c.packageName, R.layout.widget)
            val svc = Intent(c, ListService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            rv.setRemoteAdapter(R.id.list, svc)
            rv.setEmptyView(R.id.list, R.id.empty)
            rv.setTextViewText(R.id.date, SimpleDateFormat("dd MMM yyyy, EEEE", Locale.ENGLISH).format(Date()))
            val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            rv.setOnClickPendingIntent(R.id.date, pi)
            return rv
        }
    }
}

class ListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)
    class Factory(private val c: Context) : RemoteViewsFactory {
        private var items = listOf<Entry>()
        override fun onCreate() {}
        override fun onDataSetChanged() { items = loadUpcoming(c) }
        override fun onDestroy() {}
        override fun getCount() = items.size
        override fun getViewAt(p: Int): RemoteViews {
            val e = items[p]
            return if (e.header) {
                RemoteViews(c.packageName, R.layout.item_header).apply { setTextViewText(R.id.hdr, e.text) }
            } else {
                RemoteViews(c.packageName, R.layout.item).apply {
                    setTextViewText(R.id.name, e.text)
                    setTextViewText(R.id.std, "Std ${e.std}")
                }
            }
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 2
        override fun getItemId(p: Int) = p.toLong()
        override fun hasStableIds() = true
    }
}
