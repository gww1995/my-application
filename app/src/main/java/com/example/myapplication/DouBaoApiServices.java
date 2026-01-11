package com.example.myapplication;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface DouBaoApiServices {
    // 豆包对话接口地址
    String BASE_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/";

    @POST("completions")
    Call<ChatResponse> sendChatRequest(@Header("Authorization") String apiKey,
                                       @Body ChatRequest request
    );

}
