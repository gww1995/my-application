package com.example.myapplication;

import android.os.AsyncTask;
import android.util.Log;

import java.net.InetAddress;

public class NetworkTestUtil {

    private static final String TAG = "NetworkTestUtil";
    private static final String TEST_HOST = "www.baidu.com";

    private static final int TIMEOUT = 5000;

    // 定义回调接口，返回测试结果
    public interface OnNetworkTestListener {
        void onResult(boolean isConnected);
        void onError(Exception e);
    }

    // 测试网络连通性
    public static void testNetworkConnectivity(OnNetworkTestListener listener) {
        new AsyncTask<Void, Void, Boolean>() {
            private Exception error;

            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    // isReachable() 会尝试连接目标主机
                    InetAddress address = InetAddress.getByName(TEST_HOST);
                    return address.isReachable(TIMEOUT);
                } catch (Exception e) {
                    error = e;
                    Log.e(TAG, "网络测试失败！", e);
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean isConnected) {
                if (error != null) {
                    listener.onError(error);
                } else {
                    listener.onResult(isConnected);
                }
            }
        }.execute();

    }
}
