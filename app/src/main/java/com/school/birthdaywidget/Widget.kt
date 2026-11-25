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

fun todayMD(): String = SimpleDateFormat("MM-dd", Locale.US).format(Date())

// cache se aaj ki list (naam, standard); cache purana ho to khali
fun loadToday(c: Context): List<Pair<String, String>> {
    val p = c.getSharedPreferences("bday", 0)
    if (p.getString("date", "") != todayMD()) return emptyList()
    val a = JSONArray(p.getString("list", "[]"))
    return (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("n") to o.getString("s") } }
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
        private var items = listOf<Pair<String, String>>()
        override fun onCreate() {}
        override fun onDataSetChanged() { items = loadToday(c) }
        override fun onDestroy() {}
        override fun getCount() = items.size
        override fun getViewAt(p: Int): RemoteViews = RemoteViews(c.packageName, R.layout.item).apply {
            setTextViewText(R.id.name, items[p].first)
            setTextViewText(R.id.std, "Std ${items[p].second}")
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(p: Int) = p.toLong()
        override fun hasStableIds() = true
    }
}
