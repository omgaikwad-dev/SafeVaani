package com.example.woshield;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface GroqApiService {
    @POST("v1/chat/completions")
    Call<GroqChatResponse> getChatCompletion(
            @Header("Authorization") String authorization,
            @Body GroqChatRequest request);
}
