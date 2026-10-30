package ru.zhurnal.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import ru.zhurnal.app.R

/** Коллекция строк для виджетов: список пар, предметов или оценок. */
class WidgetListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        return WidgetListFactory(applicationContext, widgetId)
    }
}

private class WidgetListFactory(
    private val context: Context,
    private val widgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {

    private var rows: List<WidgetRow> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        rows = WidgetStore.rows(context, widgetId)
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val row = rows.getOrNull(position) ?: WidgetRow()
        val views = RemoteViews(context.packageName, R.layout.widget_row)
        views.setTextViewText(R.id.widget_row_left, row.left)
        views.setTextViewText(R.id.widget_row_title, row.title)
        views.setTextViewText(R.id.widget_row_right, row.right)
        row.accent?.let { raw ->
            runCatching { Color.parseColor(raw) }.getOrNull()?.let { color ->
                views.setTextColor(R.id.widget_row_right, color)
            }
        }
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
