package com.example.myapplication;

import android.content.Context;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.widget.ScrollView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class DouBaoManager {

    private static final String TAG = "DouBaoManager";
    private static final String ARK_API_KEY = "4d88ce7b-a920-44d2-bcb0-e83316a7bf78";
    private static final String MODEL_ID = "doubao-seed-1-8-251228";
    private static final String AUTH_HEADER = "Bearer " + ARK_API_KEY;

    private final DouBaoApiServices apiServices;
    private static volatile DouBaoManager instance;

    public static DouBaoManager getInstance(Context context) {
        if (instance == null) {
            synchronized (DouBaoManager.class) {
                if (instance == null) {
                    instance = new DouBaoManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }


    private DouBaoManager(Context context) {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(100, TimeUnit.SECONDS)
                .readTimeout(100, TimeUnit.SECONDS)
                .writeTimeout(100, TimeUnit.SECONDS) // ai 响应体过大，读取时间较长
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(DouBaoApiServices.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiServices = retrofit.create(DouBaoApiServices.class);
    }


    /**
     * 发送用户消息，获取豆包回复
     *
     * @param userContent 用户输入内容
     * @param callback    回调
     */
    public void sendMessage(String userContent, final DoubaoCallback callback) {
        ArrayList<ChatRequest.Messages> messagesArrayList = new ArrayList<>();

        ChatRequest.Messages messages = new ChatRequest.Messages("user", new ChatRequest.Content("text", userContent));
        messagesArrayList.add(messages);

        //构建请求体
        ChatRequest request = new ChatRequest(MODEL_ID, messagesArrayList);

        //发起异步请求
        apiServices.sendChatRequest(AUTH_HEADER, request).enqueue(new Callback<ChatResponse>() {
            @Override
            public void onResponse(@NonNull Call<ChatResponse> call, @NonNull Response<ChatResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ChatResponse.Choice> choices = response.body().getChoices();
                    if (!choices.isEmpty() && choices.get(0) != null) {
                        ChatResponse.Choice choice = choices.get(0);
                        ChatResponse.Message message = choice.getMessage();
                        if (message != null && message.getContent() != null) {
                            String text = message.getContent();
                            if (text != null) {
                                callback.onSuccess(text);
                            } else {
                                callback.onFailure(new Throwable("内容文本为空"));
                            }

                        }
                    } else {
                        callback.onFailure(new Throwable("消息或内容列表为空"));
                    }
                } else {
                    callback.onFailure(new Throwable("请求失败，状态码：" + response.code() + "------" + response.body()));
                }
            }


            @Override
            public void onFailure(@NonNull Call<ChatResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "请求失败：" + t.getMessage());
                callback.onFailure(new Throwable("网络异常：" + t.getMessage()));
            }
        });
    }


    public interface DoubaoCallback {
        void onSuccess(String response);

        void onFailure(Throwable throwable);
    }

}
