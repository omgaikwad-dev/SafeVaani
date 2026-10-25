package com.example.woshield;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface SambaNovaApiService {
    @POST("v1/chat/completions")
    Call<SambaNovaResponse> getChatCompletion(
            @Header("Authorization") String authorization,
            @Body SambaNovaRequest request);
}
