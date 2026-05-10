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

        drawSeries(canvas, left, top, chartWidth, chartHeight, min, max, Color.rgb(232, 127, 72), 0);
        drawSeries(canvas, left, top, chartWidth, chartHeight, min, max, Color.rgb(18, 108, 115), 1);
        drawSeries(canvas, left, top, chartWidth, chartHeight, min, max, Color.rgb(123, 30, 58), 2);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(dpLocal(11));
        paint.setColor(Color.rgb(99, 128, 135));
        canvas.drawText("温度", left, height - dpLocal(8), paint);
        canvas.drawText("湿度", left + dpLocal(58), height - dpLocal(8), paint);
        canvas.drawText("AQI", left + dpLocal(116), height - dpLocal(8), paint);
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

    private int dpLocal(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
