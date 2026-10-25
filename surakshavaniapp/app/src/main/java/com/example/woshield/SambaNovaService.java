package com.example.woshield;

import android.util.Log;
import androidx.annotation.NonNull;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SambaNovaService {
    private static final String BASE_URL = "https://api.sambanova.ai/";
    private static final String TAG = "SambaNovaService";
    private static final String MODEL = "Meta-Llama-3.1-8B-Instruct"; // Or "Meta-Llama-3.1-70B-Instruct"
    private static final String API_KEY = "60d2ca34-6828-4b27-a652-95f51e3a8898";

    public interface ChatCallback {
        void onResponse(String response);

        void onError(String error);
    }

    public static void getChatResponse(String userMessage, ChatCallback callback) {
        GroqSafetyService.getSingleQueryResponse(userMessage, new GroqSafetyService.SafetyChatCallback() {
            @Override
            public void onResponse(String response) {
                callback.onResponse(response);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }
}
