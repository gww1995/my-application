package com.example.myapplication;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@RequiresApi(api = Build.VERSION_CODES.S)
public class MainActivity extends AppCompatActivity {
    // 日志标签
    private static final String TAG = "MainActivity_BT";
    // 经典蓝牙串口固定UUID，掌控板2必须匹配此值，不可修改
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    // 蓝牙开启请求码
    private static final int REQUEST_ENABLE_BT = 1001;
    // 蓝牙权限申请请求码
    private static final int REQUEST_BT_PERMISSIONS = 2001;

    // API33必须申请的蓝牙权限数组
    private final String[] BT_PERMISSIONS = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.VIBRATE};

    // 蓝牙核心对象
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private boolean isConnected = false;
    private boolean isScanning = false;

    // 页面控件
    private Button btnConnect;
    private Vibrator vibrator;
    private AlertDialog deviceDialog;
    private AlertDialog.Builder scanDialogBuilder;
    private LinearLayout monitorPage;
    private LinearLayout aiChatPage;
    //AI对话控件
    private TextView tvChatRecord;
    private EditText etAiInput;
    private ScrollView scrollChat;
    //监控控件
    private LineChart tempLineChart, heartLineChart, spo2LineChart;

    // ========== 数据集合（每个图表独立数据，避免串扰） ==========
    private final List<Entry> tempEntries = new ArrayList<>();
    private final List<Entry> heartEntries = new ArrayList<>();
    private final List<Entry> spo2Entries = new ArrayList<>();

    // 蓝牙设备列表
    private final List<BluetoothDevice> scanDeviceList = new ArrayList<>();
    private final List<String> deviceNameList = new ArrayList<>();

    // 生理数据阈值配置
    private static final int MAX_DATA_COUNT = 30; // 每个图表最多显示30个数据点
    private static final float TEMP_MIN = 35.0f, TEMP_MAX = 40.0f;
    private static final float HEART_MIN = 60.0f, HEART_MAX = 120.0f;
    private static final float SPO2_MIN = 95.0f, SPO2_MAX = 100.0f;
    private int bufferIndex = 0; // 缓冲区索引
    private int dataIndex = 0;
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());

    // 主线程更新UI的Handler
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 蓝牙扫描广播接收器 - 实时接收扫描到的蓝牙设备
    private final BroadcastReceiver bluetoothScanReceiver = new BroadcastReceiver() {
        @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN})
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    addDeviceToList(device);
                    refreshDeviceDialog(false);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                isScanning = false;
                btnConnect.setText("连接掌控板监测设备");
                btnConnect.setEnabled(true);
                refreshDeviceDialog(true);
                Log.i(TAG, "蓝牙扫描结束，共发现设备数量：" + scanDeviceList.size());
            }
        }
    };

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //初始化导航栏
        initNavigation();
        // 初始化控件
        initView();
        // 初始化蓝牙适配器
        initBluetooth();
        // 初始化 AI对话功能
        initAIChat();
        // 注册蓝牙扫描广播接收器
        registerScanReceiver();
        // 获取震动服务
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);


        // 连接按钮点击事件
        btnConnect.setOnClickListener(v -> {
            if (!checkBtPermissionsAllGranted()) {
                requestBtPermissions();
                return;
            }
            if (isConnected) {
                disconnectBluetooth();
                return;
            }
            if (bluetoothAdapter == null) {
                Toast.makeText(MainActivity.this, "当前设备不支持蓝牙功能", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                startScanClassicDevice();
//                startSimulateDataUpdate();
            }
        });
    }

    // 初始化 AI对话功能
    private void initAIChat() {
        tvChatRecord = findViewById(R.id.tv_chat_record);
        etAiInput = findViewById(R.id.et_ai_input);
        scrollChat = findViewById(R.id.scroll_chat);
        Button btnAiSend = findViewById(R.id.btn_ai_send);

        //初始化豆包管理器
        DouBaoManager douBaoManager = DouBaoManager.getInstance(this);

        //发送按钮事件
        btnAiSend.setOnClickListener(v -> {
            String userInput = etAiInput.getText().toString().trim();
            if (userInput.isEmpty()) {
                Toast.makeText(this, "请输入内容～", Toast.LENGTH_SHORT).show();
            }

            tvChatRecord.append(Html.fromHtml(String.format("<font color='#FFB74D'>我：%s</font> <br>", userInput), Html.FROM_HTML_MODE_LEGACY));
            etAiInput.setText("");
            scrollChat.post(() -> scrollChat.fullScroll(ScrollView.FOCUS_DOWN));

            Log.e(TAG, "用户输入：" + userInput);

            NetworkTestUtil.testNetworkConnectivity(new NetworkTestUtil.OnNetworkTestListener() {
                @Override
                public void onResult(boolean isConnected) {
                    if (isConnected) {
                        Log.d("Network", "网络通畅");
                    } else {
                        Log.d("Network", "网络异常");
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e("Network", "网络异常：" + e.getMessage());
                }
            });

            douBaoManager.sendMessage(userInput, new DouBaoManager.DoubaoCallback() {
                @Override
                public void onSuccess(String response) {
                    // 主线程更新 UI
                    runOnUiThread(() -> {
                        tvChatRecord.append(Html.fromHtml(String.format("<font color='#757575'>AI：%s</font> <br>", response), Html.FROM_HTML_MODE_LEGACY));
                        tvChatRecord.setMovementMethod(LinkMovementMethod.getInstance());
                        scrollChat.post(() -> scrollChat.fullScroll(ScrollView.FOCUS_DOWN));
                    });
                }

                @Override
                public void onFailure(Throwable throwable) {
                    runOnUiThread(() -> tvChatRecord.append("错误：" + throwable));
                    scrollChat.post(() -> scrollChat.fullScroll(ScrollView.FOCUS_DOWN));
                }
            });

        });

    }

    // 初始化导航栏
    private void initNavigation() {
        // 获取控件
        monitorPage = findViewById(R.id.monitor_page);
        aiChatPage = findViewById(R.id.ai_chat_page);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        // 默认显示设备检测页面
        monitorPage.setVisibility(View.VISIBLE);
        aiChatPage.setVisibility(View.GONE);

        //导航栏切换事件
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_monitor) {
                // 切换到设备监测
                monitorPage.setVisibility(View.VISIBLE);
                aiChatPage.setVisibility(View.GONE);
                return true;
            } else if (id == R.id.nav_ai_chat) {
                // 切换到 AI 对话
                monitorPage.setVisibility(View.GONE);
                aiChatPage.setVisibility(View.VISIBLE);
                return true;
            }
            return false;
        });

    }


    // 初始化页面控件
    private void initView() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("自闭症儿童情绪监测装置");
        setSupportActionBar(toolbar);
        btnConnect = findViewById(R.id.btnConnectDevice);

        //
        tempLineChart = findViewById(R.id.tempLineChart);
        heartLineChart = findViewById(R.id.heartLineChart);
        spo2LineChart = findViewById(R.id.spo2LineChart);

        // 初始化体温图表（红色折线）
        initSingleLineChart(tempLineChart, tempEntries, "体温(℃)", 0xFFFF5722, TEMP_MIN - 5, TEMP_MAX + 5);

        // 初始化心率图表（橙色折线）
        initSingleLineChart(heartLineChart, heartEntries, "心率(次/分)", 0xFFFFB74D, HEART_MIN - 20, HEART_MAX + 50);

        // 初始化血氧图表（绿色折线）
        initSingleLineChart(spo2LineChart, spo2Entries, "血氧(%)", 0xFF4CAF50, SPO2_MIN - 5, SPO2_MAX);

    }


    /**
     * 模拟数据定时更新（每秒1次，生成随机数据）
     */
    private void startSimulateDataUpdate() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // 1. 生成随机数据（贴合实际范围）
                float tempValue = TEMP_MIN + random.nextFloat() * (TEMP_MAX - TEMP_MIN);
                float heartValue = HEART_MIN + random.nextFloat() * (HEART_MAX - HEART_MIN);
                float spo2Value = SPO2_MIN + random.nextFloat() * (SPO2_MAX - SPO2_MIN);

                //// 2. 添加数据到折线图表
                addToLineChart(tempValue, heartValue, spo2Value);

                // 3. 打印日志验证
                Log.d(TAG, String.format("更新数据：体温=%.1f℃，心率=%.0f次/分，血氧=%.1f%%",
                        tempValue, heartValue, spo2Value));

                // 4. 递增索引，循环执行
                dataIndex++;
                handler.postDelayed(this, 1000); // 1000ms = 1秒
            }
        }, 1000);
    }

    /**
     * 添加数据到折线图表
     *
     * @param tempValue  体温
     * @param heartValue 心率
     * @param spo2Value  血氧
     */
    private void addToLineChart(float heartValue, float spo2Value, float tempValue) {
        addDataToSingleChart(tempLineChart, tempEntries, tempLineChart.getData(), tempValue);
        addDataToSingleChart(heartLineChart, heartEntries, heartLineChart.getData(), heartValue);
        addDataToSingleChart(spo2LineChart, spo2Entries, spo2LineChart.getData(), spo2Value);
        dataIndex++;
    }

    /**
     * 单个图表添加数据（保证数据隔离，仅更新当前图表）
     */
    private void addDataToSingleChart(LineChart lineChart, List<Entry> entries,
                                      LineData lineData, float value) {
        // 1. 添加新数据（X轴为数据索引，Y轴为随机生成的数值）
        entries.add(new Entry(dataIndex, value));

        // 2. 限制数据量，超过30个则移除最旧数据
        if (entries.size() > MAX_DATA_COUNT) {
            entries.remove(0);
            // 更新X轴范围（实现滑动效果）
            lineChart.getXAxis().setAxisMinimum(dataIndex - MAX_DATA_COUNT);
            lineChart.getXAxis().setAxisMaximum(dataIndex);
        }

        // 3. 通知图表数据更新
        lineData.notifyDataChanged();
        lineChart.notifyDataSetChanged();
        lineChart.setVisibleXRangeMaximum(MAX_DATA_COUNT); // 最多显示30个数据点
        lineChart.moveViewToX(dataIndex); // 自动滑动到最新数据

        // 4. 刷新图表
        lineChart.invalidate();
    }


    /**
     * 初始化单个折线图（通用配置，复用性强）
     *
     * @param lineChart 目标折线图
     * @param entries   数据集合
     * @param label     折线标签
     * @param color     折线颜色
     * @param yMin      Y轴最小值
     * @param yMax      Y轴最大值
     */
    private void initSingleLineChart(LineChart lineChart, List<Entry> entries,
                                     String label, int color, float yMin, float yMax) {
        // 1. 基础交互配置（支持拖拽、禁止缩放、支持滑动）
        lineChart.setDragEnabled(true); // 允许拖拽
        lineChart.setScaleEnabled(false); // 禁止缩放（保持X轴数据整洁）
        lineChart.setPinchZoom(false); // 禁止双指缩放
        lineChart.setAutoScaleMinMaxEnabled(false); // 禁止自动缩放Y轴

        // 2. 隐藏描述文字（右下角默认描述）
        Description description = new Description();
        description.setEnabled(false);
        lineChart.setDescription(description);

        // 3. X轴配置（底部显示，整数刻度，最大显示30个点）
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM); // X轴显示在底部
        xAxis.setGranularity(1f); // 刻度间隔1
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(MAX_DATA_COUNT); // 最大显示30个数据点
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                return String.valueOf((int) value); // X轴标签显示整数
            }
        });

        // 4. Y轴配置（左侧显示，固定取值范围，右侧隐藏）
        YAxis leftYAxis = lineChart.getAxisLeft();
        leftYAxis.setAxisMinimum(yMin);
        leftYAxis.setAxisMaximum(yMax);
        leftYAxis.setGranularity((yMax - yMin) / 5f); // 5个刻度间隔
        lineChart.getAxisRight().setEnabled(false); // 隐藏右侧Y轴

        // 5. 初始化折线数据集（配置样式）
        LineDataSet lineDataSet = new LineDataSet(entries, label);
        lineDataSet.setColor(color); // 折线颜色
        lineDataSet.setCircleColor(color); // 数据点圆圈颜色
        lineDataSet.setLineWidth(2f); // 折线宽度
        lineDataSet.setCircleRadius(3f); // 数据点圆圈半径
        lineDataSet.setDrawCircleHole(false); // 不绘制数据点圆圈内孔
        lineDataSet.setDrawValues(false); // 不显示数据点数值（简化界面）
        lineDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); // 平滑曲线（可选：LINEAR 直线）

        // 6. 绑定数据到图表
        List<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(lineDataSet);
        LineData lineData = new LineData(dataSets);
        lineChart.setData(lineData);

        // 7. 刷新图表
        lineChart.invalidate();
    }


    // 初始化蓝牙适配器
    private void initBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "设备不支持经典蓝牙模块！", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // 注册蓝牙扫描广播接收器
    private void registerScanReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(bluetoothScanReceiver, filter);
    }

    // 检查蓝牙权限是否全部授予
    private boolean checkBtPermissionsAllGranted() {
        for (String permission : BT_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    // 申请蓝牙相关权限
    private void requestBtPermissions() {
        ActivityCompat.requestPermissions(this, BT_PERMISSIONS, REQUEST_BT_PERMISSIONS);
    }

    // 权限申请回调
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BT_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startScanClassicDevice();
            } else {
                Toast.makeText(this, "请授予全部蓝牙权限，否则无法连接掌控板", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 蓝牙开启回调
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                startScanClassicDevice();
            } else {
                Toast.makeText(this, "请开启蓝牙后再尝试连接", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 开始扫描经典蓝牙设备
    private void startScanClassicDevice() {
        if (isScanning) {
            Toast.makeText(this, "正在扫描蓝牙设备中，请稍候...", Toast.LENGTH_SHORT).show();
            return;
        }
        scanDeviceList.clear();
        deviceNameList.clear();
        isScanning = true;
        btnConnect.setText("正在扫描蓝牙设备...");
        btnConnect.setEnabled(false);

        // 添加已配对的蓝牙设备
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
            addDeviceToList(device);
        }

        // 开始扫描周边蓝牙设备
        bluetoothAdapter.startDiscovery();
        // 显示设备选择弹窗
        showDeviceDialog();

        // 10秒后自动停止扫描，节省电量
        mainHandler.postDelayed(() -> {
            if (isScanning) {
                bluetoothAdapter.cancelDiscovery();
            }
        }, 10000);
    }

    // 添加设备到列表并自动去重，去掉没有名字的设备
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void addDeviceToList(BluetoothDevice device) {
        if (device.getAddress() == null || scanDeviceList.contains(device) || device.getName() == null) {
            return;
        }
        scanDeviceList.add(device);
        String deviceName = device.getName() == null || device.getName().isEmpty() ? "未知设备(" + device.getAddress() + ")" : device.getName() + "(" + device.getAddress() + ")";
        deviceNameList.add(deviceName);
        Log.d(TAG, "发现蓝牙设备：" + deviceName);
    }

    // 显示蓝牙设备选择弹窗
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private void showDeviceDialog() {
        scanDialogBuilder = new AlertDialog.Builder(this).setTitle("经典蓝牙设备列表（正在扫描...）").setCancelable(false).setNegativeButton("取消", (dialog, which) -> {
            if (isScanning) bluetoothAdapter.cancelDiscovery();
            dialog.dismiss();
        }).setNeutralButton("停止扫描", (dialog, which) -> {
            bluetoothAdapter.cancelDiscovery();
            dialog.dismiss();
        });
        refreshDeviceDialog(false);
    }

    // 刷新弹窗中的设备列表
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private void refreshDeviceDialog(boolean isFinished) {
        if (scanDialogBuilder == null) return;
        String title = isFinished ? "经典蓝牙设备列表（扫描完成）" : "经典蓝牙设备列表（正在扫描...）";
        String[] listItems = deviceNameList.toArray(new String[0]);

        scanDialogBuilder.setTitle(title).setItems(listItems, (dialog, position) -> {
            bluetoothAdapter.cancelDiscovery();
            BluetoothDevice selectedDevice = scanDeviceList.get(position);
            connectClassicBluetooth(selectedDevice);
        });

        if (deviceDialog != null && deviceDialog.isShowing()) {
            deviceDialog.dismiss();
        }
        deviceDialog = scanDialogBuilder.create();
        (deviceDialog).show();
    }

    // 连接经典蓝牙设备 - 子线程执行，防止主线程阻塞
    private void connectClassicBluetooth(BluetoothDevice device) {
        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                bluetoothSocket = device.createRfcommSocketToServiceRecord(BT_UUID);
                bluetoothAdapter.cancelDiscovery();
                bluetoothSocket.connect();
                isConnected = true;
                inputStream = bluetoothSocket.getInputStream();

                // 主线程更新UI
                mainHandler.post(() -> {
                    if (deviceDialog != null && deviceDialog.isShowing()) deviceDialog.dismiss();
                    Toast.makeText(MainActivity.this, "蓝牙连接成功！开始接收监测数据", Toast.LENGTH_SHORT).show();
                    btnConnect.setText("断开连接");
                });
                Log.i(TAG, "蓝牙连接成功 → 设备名称：" + device.getName());
                // 开始读取硬件数据
                readHardwareData();

            } catch (IOException e) {
                Log.e(TAG, "蓝牙连接失败：" + e.getMessage());
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "连接失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    disconnectBluetooth();
                });
            }
        }).start();
    }

    // 循环读取掌控板发送的传感器数据
    private void readHardwareData() {
        byte[] buffer = new byte[3];
        int bytesRead;
        while (isConnected && inputStream != null) {
            try {
                bytesRead = inputStream.read(buffer, bufferIndex, 1);
                Log.d(TAG, "readHardwareData: ----" + Arrays.toString(buffer));
                if (bytesRead > 0) {
                    bufferIndex++;
                    if (bufferIndex == 3) {
                        parseHardwareData(buffer);
                        bufferIndex = 0;
                        Log.d(TAG, "接收并解析1组数据：体温=" + (buffer[0] & 0xFF) + "℃ 心率=" + (buffer[1] & 0xFF) + " 血氧=" + (buffer[2] & 0xFF) + "%");
                    }
                } else if (bytesRead == -1) {
                    //输入流关闭 ，断开连接
                    Log.e(TAG, "输入流已关闭，断开连接");
                    break;
                }
            } catch (IOException e) {
                Log.e(TAG, "数据读取异常：" + e.getMessage());
                mainHandler.post(this::disconnectBluetooth);
                break;
            }
        }
    }

    // 解析掌控板的字节数据 - 唯一可微调的地方
    private void parseHardwareData(byte[] data) {
        if (data.length >= 3) {
            // ====================== 可修改区域 ======================
            // 适配掌控板发送格式：如果是 数值*10 发送（如365=36.5℃），则除以10.0f
            float heart = data[0];
            float spo2 = data[1];
            float temp = data[2];
            // =======================================================

            Log.d(TAG, "解析数据 → 体温：" + temp + "℃ 心率：" + heart + "次/分 血氧：" + spo2 + "%");
            mainHandler.post(() -> {
                addToLineChart(heart, spo2, temp);
                checkDataAbnormal(temp, heart, spo2);
            });
        } else {
            Log.w(TAG, "接收数据长度异常，长度：" + data.length);
        }
    }

    // 检测生理数据是否异常，并触发震动提醒
    private void checkDataAbnormal(float temp, float heart, float spo2) {
        boolean isTempAb = temp >= TEMP_MAX;
        boolean isHeartAb = heart < HEART_MIN || heart > HEART_MAX;
        boolean isSpo2Ab = spo2 < SPO2_MIN;

        //todo AI助手发送信息到页面

        if ((isTempAb || isHeartAb || isSpo2Ab) && vibrator.hasVibrator()) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED) {
                vibrator.vibrate(1000);
            }
        } else {
            vibrator.cancel();
        }
    }

    // 断开蓝牙连接，释放资源
    private void disconnectBluetooth() {
        try {
            isConnected = false;
            if (inputStream != null) inputStream.close();
            if (bluetoothSocket != null) bluetoothSocket.close();
            inputStream = null;
            bluetoothSocket = null;
            btnConnect.setText("连接掌控板监测设备");
            Toast.makeText(this, "蓝牙已断开连接", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "断开蓝牙连接失败：" + e.getMessage());
        }
    }

    // 页面销毁，释放所有资源
    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectBluetooth();
        unregisterReceiver(bluetoothScanReceiver);
        mainHandler.removeCallbacksAndMessages(null);
        if (deviceDialog != null && deviceDialog.isShowing()) deviceDialog.dismiss();
    }
}