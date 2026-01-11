package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class CustomLineChartView extends View {
    // ========== 核心变量 ==========
    private Paint tempPaint, heartPaint, spo2Paint, gridPaint, textPaint; // 画笔
    private Path tempPath, heartPath, spo2Path; // 路径（绘制线条）
    private List<Float> tempDataList = new ArrayList<>(); // 体温数据
    private List<Float> heartDataList = new ArrayList<>(); // 心率数据
    private List<Float> spo2DataList = new ArrayList<>(); // 血氧数据

    // 图表参数（适配屏幕）
    private int viewWidth, viewHeight; // View宽高
    private final float padding = 50; // 内边距
    private final float maxX = 30; // X轴最多显示30个数据点（30秒）
    private float maxTemp = 40f, minTemp = 35f; // 体温范围
    private float maxHeart = 150f, minHeart = 50f; // 心率范围
    private float maxSpo2 = 100f, minSpo2 = 90f; // 血氧范围

    // 每个表格的高度（总高度的1/3）
    private float tableHeight;

    public CustomLineChartView(Context context) {
        super(context);
        init();
    }

    public CustomLineChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomLineChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    // ========== 1. 初始化画笔和路径 ==========
    private void init() {
        // 体温画笔（红色）
        tempPaint = new Paint();
        tempPaint.setColor(Color.RED);
        tempPaint.setStrokeWidth(3);
        tempPaint.setStyle(Paint.Style.STROKE);
        tempPaint.setAntiAlias(true); // 抗锯齿

        // 心率画笔（蓝色）
        heartPaint = new Paint();
        heartPaint.setColor(Color.BLUE);
        heartPaint.setStrokeWidth(3);
        heartPaint.setStyle(Paint.Style.STROKE);
        heartPaint.setAntiAlias(true);

        // 血氧画笔（绿色）
        spo2Paint = new Paint();
        spo2Paint.setColor(Color.GREEN);
        spo2Paint.setStrokeWidth(3);
        spo2Paint.setStyle(Paint.Style.STROKE);
        spo2Paint.setAntiAlias(true);

        // 网格画笔（灰色）
        gridPaint = new Paint();
        gridPaint.setColor(Color.LTGRAY);
        gridPaint.setStrokeWidth(1);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setAntiAlias(true);

        // 文字画笔（黑色）
        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(12);
        textPaint.setAntiAlias(true);

        // 初始化路径
        tempPath = new Path();
        heartPath = new Path();
        spo2Path = new Path();
    }

    // ========== 2. 接收数据（适配每秒1组） ==========
    public void addSensorData(float temp, float heart, float spo2) {
        // 1. 添加数据
        tempDataList.add(temp);
        heartDataList.add(heart);
        spo2DataList.add(spo2);

        // 2. 限制数据量（只保留最近30个点，避免卡顿）
        if (tempDataList.size() > maxX) {
            tempDataList.remove(0);
            heartDataList.remove(0);
            spo2DataList.remove(0);
        }

        // 3. 核心：数据更新后强制重绘
        invalidate();
    }

    // ========== 3. 测量View尺寸 ==========
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        // 每个表格高度为总高度的1/3
        tableHeight = (viewHeight - 2 * padding) / 3f;
    }

    // ========== 4. 核心绘制逻辑 ==========
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (viewWidth == 0 || viewHeight == 0) return; // 尺寸未初始化时不绘制

        // 绘制三个独立的表格
        drawTempTable(canvas);
        drawHeartTable(canvas);
        drawSpo2Table(canvas);
    }

    // 绘制体温表格
    private void drawTempTable(Canvas canvas) {
        // 计算体温表格的Y坐标范围
        float tableTop = padding;
        float tableBottom = tableTop + tableHeight;
        float tableLeft = padding;
        float tableRight = viewWidth - padding;

        // 绘制表格边框
        drawTableBorder(canvas, tableLeft, tableTop, tableRight, tableBottom);

        // 绘制体温数据
        if (!tempDataList.isEmpty()) {
            float xStep = (tableRight - tableLeft) / maxX; // 每个数据点的X轴间隔
            tempPath.reset();

            for (int i = 0; i < tempDataList.size(); i++) {
                float x = tableLeft + i * xStep; // 当前点X坐标

                // 体温Y坐标（映射到当前表格高度）
                float tempY = tableBottom - (tempDataList.get(i) - minTemp) * (tableHeight / (maxTemp - minTemp));

                // 初始化路径起点
                if (i == 0) {
                    tempPath.moveTo(x, tempY);
                } else {
                    // 绘制线条到当前点
                    tempPath.lineTo(x, tempY);
                }

                // 绘制数据点（小圆点）
                canvas.drawCircle(x, tempY, 4, tempPaint);
            }

            // 绘制体温线条
            canvas.drawPath(tempPath, tempPaint);
        }

        // 绘制标题
        canvas.drawText("体温(℃) - 红色", tableLeft, tableTop - 10, textPaint);

        // 绘制网格
        drawTableGrid(canvas, tableLeft, tableTop, tableRight, tableBottom, maxTemp, minTemp);
    }

    // 绘制心率表格
    private void drawHeartTable(Canvas canvas) {
        // 计算心率表格的Y坐标范围
        float tableTop = padding + tableHeight;
        float tableBottom = tableTop + tableHeight;
        float tableLeft = padding;
        float tableRight = viewWidth - padding;

        // 绘制表格边框
        drawTableBorder(canvas, tableLeft, tableTop, tableRight, tableBottom);

        // 绘制心率数据
        if (!heartDataList.isEmpty()) {
            float xStep = (tableRight - tableLeft) / maxX; // 每个数据点的X轴间隔
            heartPath.reset();

            for (int i = 0; i < heartDataList.size(); i++) {
                float x = tableLeft + i * xStep; // 当前点X坐标

                // 心率Y坐标（映射到当前表格高度）
                float heartY = tableBottom - (heartDataList.get(i) - minHeart) * (tableHeight / (maxHeart - minHeart));

                // 初始化路径起点
                if (i == 0) {
                    heartPath.moveTo(x, heartY);
                } else {
                    // 绘制线条到当前点
                    heartPath.lineTo(x, heartY);
                }

                // 绘制数据点（小圆点）
                canvas.drawCircle(x, heartY, 4, heartPaint);
            }

            // 绘制心率线条
            canvas.drawPath(heartPath, heartPaint);
        }

        // 绘制标题
        canvas.drawText("心率 - 蓝色", tableLeft, tableTop - 10, textPaint);

        // 绘制网格
        drawTableGrid(canvas, tableLeft, tableTop, tableRight, tableBottom, maxHeart, minHeart);
    }

    // 绘制血氧表格
    private void drawSpo2Table(Canvas canvas) {
        // 计算血氧表格的Y坐标范围
        float tableTop = padding + 2 * tableHeight;
        float tableBottom = tableTop + tableHeight;
        float tableLeft = padding;
        float tableRight = viewWidth - padding;

        // 绘制表格边框
        drawTableBorder(canvas, tableLeft, tableTop, tableRight, tableBottom);

        // 绘制血氧数据
        if (!spo2DataList.isEmpty()) {
            float xStep = (tableRight - tableLeft) / maxX; // 每个数据点的X轴间隔
            spo2Path.reset();

            for (int i = 0; i < spo2DataList.size(); i++) {
                float x = tableLeft + i * xStep; // 当前点X坐标

                // 血氧Y坐标（映射到当前表格高度）
                float spo2Y = tableBottom - (spo2DataList.get(i) - minSpo2) * (tableHeight / (maxSpo2 - minSpo2));

                // 初始化路径起点
                if (i == 0) {
                    spo2Path.moveTo(x, spo2Y);
                } else {
                    // 绘制线条到当前点
                    spo2Path.lineTo(x, spo2Y);
                }

                // 绘制数据点（小圆点）
                canvas.drawCircle(x, spo2Y, 4, spo2Paint);
            }

            // 绘制血氧线条
            canvas.drawPath(spo2Path, spo2Paint);
        }

        // 绘制标题
        canvas.drawText("血氧(%) - 绿色", tableLeft, tableTop - 10, textPaint);

        // 绘制网格
        drawTableGrid(canvas, tableLeft, tableTop, tableRight, tableBottom, maxSpo2, minSpo2);
    }

    // 绘制表格边框
    private void drawTableBorder(Canvas canvas, float left, float top, float right, float bottom) {
        // 绘制表格边框
        canvas.drawLine(left, top, right, top, gridPaint); // 上边框
        canvas.drawLine(left, bottom, right, bottom, gridPaint); // 下边框
        canvas.drawLine(left, top, left, bottom, gridPaint); // 左边框
        canvas.drawLine(right, top, right, bottom, gridPaint); // 右边框
    }

    // 绘制表格网格
    private void drawTableGrid(Canvas canvas, float left, float top, float right, float bottom, float maxValue, float minValue) {
        // X轴网格（每5个数据点画一条）
        float xStep = (right - left) / maxX;
        for (int i = 0; i <= maxX; i += 5) {
            float x = left + i * xStep;
            canvas.drawLine(x, top, x, bottom, gridPaint);
            canvas.drawText(String.valueOf(i), x, bottom + 15, textPaint);
        }

        // Y轴网格
        float yStep = (bottom - top) / (maxValue - minValue);
        for (float yVal = minValue; yVal <= maxValue; yVal += (maxValue - minValue) / 5) {
            float y = bottom - (yVal - minValue) * yStep;
            canvas.drawLine(left, y, right, y, gridPaint);
            canvas.drawText(String.valueOf((int)yVal), left - 30, y, textPaint);
        }
    }

    // 清空数据（可选）
    public void clearData() {
        tempDataList.clear();
        heartDataList.clear();
        spo2DataList.clear();
        invalidate();
    }
}
