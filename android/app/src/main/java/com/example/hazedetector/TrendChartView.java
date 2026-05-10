package com.example.hazedetector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class TrendChartView extends View {
    private static final int TEMPERATURE_COLOR = Color.rgb(232, 127, 72);
    private static final int HUMIDITY_COLOR = Color.rgb(18, 108, 115);
    private static final int AQI_COLOR = Color.rgb(123, 30, 58);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<TrendPoint> points = new ArrayList<>();

    public TrendChartView(Context context) {
        super(context);
        paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
    }

    public void setPoints(List<TrendPoint> rows) {
        points.clear();
        points.addAll(rows);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (points.isEmpty()) return;

        int width = getWidth();
        int height = getHeight();
        int left = dpLocal(38);
        int top = dpLocal(14);
        int right = dpLocal(14);
        int bottom = dpLocal(34);
        int chartWidth = width - left - right;
        int chartHeight = height - top - bottom;

        int min = 0;
        int max = 0;
        for (TrendPoint point : points) {
            max = Math.max(max, Math.max(point.temperature, Math.max(point.humidity, point.aqi)));
        }
        max = ((max + 19) / 20) * 20;

        paint.setTextSize(dpLocal(11));
        paint.setStrokeWidth(1);
        paint.setColor(Color.argb(45, 16, 38, 45));
        for (int i = 0; i <= 4; i++) {
            float y = top + chartHeight / 4f * i;
            canvas.drawLine(left, y, left + chartWidth, y, paint);
            paint.setColor(Color.rgb(99, 128, 135));
            canvas.drawText(String.valueOf(max - (max - min) / 4 * i), dpLocal(4), y + dpLocal(4), paint);
            paint.setColor(Color.argb(45, 16, 38, 45));
        }

        drawSeries(canvas, left, top, chartWidth, chartHeight, min, max, TEMPERATURE_COLOR, 0);
        drawSeries(canvas, left, top, chartWidth, chartHeight, min, max, HUMIDITY_COLOR, 1);
        drawSeries(canvas, left, top, chartWidth, chartHeight, min, max, AQI_COLOR, 2);

        drawLegend(canvas, left, height - dpLocal(8));
    }

    private void drawSeries(Canvas canvas, int left, int top, int chartWidth, int chartHeight, int min, int max, int color, int type) {
        Path path = new Path();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dpLocal(2));
        paint.setColor(color);
        for (int i = 0; i < points.size(); i++) {
            TrendPoint point = points.get(i);
            int value = type == 0 ? point.temperature : type == 1 ? point.humidity : point.aqi;
            float x = left + (points.size() == 1 ? 0 : chartWidth * i / (float) (points.size() - 1));
            float y = top + chartHeight - (value - min) * chartHeight / (float) Math.max(1, max - min);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        canvas.drawPath(path, paint);
    }

    private void drawLegend(Canvas canvas, int left, int baseline) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dpLocal(3));
        paint.setStrokeCap(Paint.Cap.ROUND);
        int x = left;
        x = drawLegendItem(canvas, x, baseline, TEMPERATURE_COLOR, "温度");
        x = drawLegendItem(canvas, x + dpLocal(22), baseline, HUMIDITY_COLOR, "湿度");
        drawLegendItem(canvas, x + dpLocal(22), baseline, AQI_COLOR, "AQI");
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private int drawLegendItem(Canvas canvas, int x, int baseline, int color, String label) {
        int lineY = baseline - dpLocal(4);
        paint.setColor(color);
        canvas.drawLine(x, lineY, x + dpLocal(22), lineY, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(dpLocal(11));
        paint.setColor(Color.rgb(65, 87, 93));
        canvas.drawText(label, x + dpLocal(28), baseline, paint);
        paint.setStyle(Paint.Style.STROKE);
        return x + dpLocal(28) + Math.round(paint.measureText(label));
    }

    private int dpLocal(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
