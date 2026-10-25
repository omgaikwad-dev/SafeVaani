package com.example.woshield;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
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

public class GroqSafetyService {

    private static final String BASE_URL = "https://api.groq.com/openai/";
    private static final String TAG = "GroqSafetyService";

    // User-provided Groq API Key (Replace with your actual key or configure via BuildConfig)
    public static final String API_KEY = "YOUR_GROQ_API_KEY";

    // Verified supported Groq models for this account
    public static final String PRIMARY_MODEL = "openai/gpt-oss-120b";
    public static final String FALLBACK_MODEL = "openai/gpt-oss-20b";
    public static final String TERTIARY_MODEL = "qwen/qwen3.8-27b";

    public static final String SYSTEM_SAFETY_PROMPT =
            "You are WoShield's AI Safety Assistant. You provide calm, clear, reassuring, and practical personal safety advice for women and individuals in distress. " +
            "Provide actionable steps for staying safe, de-escalating tense encounters, reaching safe locations, and seeking help. " +
            "If someone is in immediate danger, advise them to call emergency services (112, 100, or 1091 for Women Helpline in India), reach a crowded well-lit public space, and alert trusted family or friends. " +
            "Keep answers concise, easy to read, and empowering.";

    public interface SafetyChatCallback {
        void onResponse(String response);
        void onError(String error);
    }

    private static GroqApiService createApiClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> chain.proceed(
                        chain.request().newBuilder()
                                .header("User-Agent", "WoShieldApp/1.0 (Android; GroqAI)")
                                .build()
                ))
                .addInterceptor(logging)
                .readTimeout(45, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit.create(GroqApiService.class);
    }

    /**
     * Send full conversation history to Groq for contextual safety assistant dialog.
     */
    public static void getChatResponse(List<GroqChatRequest.Message> conversation, SafetyChatCallback callback) {
        GroqApiService api = createApiClient();
        GroqChatRequest request = new GroqChatRequest(PRIMARY_MODEL, conversation, 1024, 0.7);
        String authHeader = "Bearer " + API_KEY;

        api.getChatCompletion(authHeader, request).enqueue(new Callback<GroqChatResponse>() {
            @Override
            public void onResponse(@NonNull Call<GroqChatResponse> call, @NonNull Response<GroqChatResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<GroqChatResponse.Choice> choices = response.body().getChoices();
                    if (choices != null && !choices.isEmpty()) {
                        GroqChatResponse.Message message = choices.get(0).getMessage();
                        String content = message != null ? message.getContent() : null;

                        // If content is empty (e.g. reasoning model token finish), fallback to reasoning or text
                        if (content != null && !content.trim().isEmpty()) {
                            callback.onResponse(content.trim());
                            return;
                        } else if (message != null && message.getReasoning() != null && !message.getReasoning().trim().isEmpty()) {
                            callback.onResponse(message.getReasoning().trim());
                            return;
                        }
                    }
                    callback.onError("Assistant returned an empty response. Please try again.");
                } else {
                    // Try fallback model if primary model fails (404, 400, 429, 500+)
                    Log.w(TAG, "Primary model (" + PRIMARY_MODEL + ") returned error code: " + response.code() + ", trying fallback...");
                    tryFallbackModel(api, authHeader, conversation, callback);
                }
            }

            @Override
            public void onFailure(@NonNull Call<GroqChatResponse> call, @NonNull Throwable t) {
                Log.w(TAG, "Primary model failed: " + t.getMessage() + ", trying fallback...");
                tryFallbackModel(api, authHeader, conversation, callback);
            }
        });
    }

    private static void tryFallbackModel(GroqApiService api, String authHeader,
                                         List<GroqChatRequest.Message> conversation,
                                         SafetyChatCallback callback) {
        GroqChatRequest fallbackReq = new GroqChatRequest(FALLBACK_MODEL, conversation, 1024, 0.7);
        api.getChatCompletion(authHeader, fallbackReq).enqueue(new Callback<GroqChatResponse>() {
            @Override
            public void onResponse(@NonNull Call<GroqChatResponse> call, @NonNull Response<GroqChatResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getChoices() != null && !response.body().getChoices().isEmpty()) {
                    GroqChatResponse.Message message = response.body().getChoices().get(0).getMessage();
                    String botReply = message != null ? message.getContent() : null;
                    if (botReply != null && !botReply.trim().isEmpty()) {
                        callback.onResponse(botReply.trim());
                        return;
                    } else if (message != null && message.getReasoning() != null && !message.getReasoning().trim().isEmpty()) {
                        callback.onResponse(message.getReasoning().trim());
                        return;
                    }
                }
                Log.w(TAG, "Fallback model (" + FALLBACK_MODEL + ") failed, trying tertiary model...");
                tryTertiaryModel(api, authHeader, conversation, callback);
            }

            @Override
            public void onFailure(@NonNull Call<GroqChatResponse> call, @NonNull Throwable t) {
                Log.w(TAG, "Fallback model call failed: " + t.getMessage() + ", trying tertiary model...");
                tryTertiaryModel(api, authHeader, conversation, callback);
            }
        });
    }

    private static void tryTertiaryModel(GroqApiService api, String authHeader,
                                         List<GroqChatRequest.Message> conversation,
                                         SafetyChatCallback callback) {
        GroqChatRequest tertiaryReq = new GroqChatRequest(TERTIARY_MODEL, conversation, 1024, 0.7);
        api.getChatCompletion(authHeader, tertiaryReq).enqueue(new Callback<GroqChatResponse>() {
            @Override
            public void onResponse(@NonNull Call<GroqChatResponse> call, @NonNull Response<GroqChatResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getChoices() != null && !response.body().getChoices().isEmpty()) {
                    GroqChatResponse.Message message = response.body().getChoices().get(0).getMessage();
                    String botReply = message != null ? message.getContent() : null;
                    if (botReply != null && !botReply.trim().isEmpty()) {
                        callback.onResponse(botReply.trim());
                        return;
                    }
                }
                callback.onError("Safety Assistant is currently unavailable. Please verify connection or retry.");
            }

            @Override
            public void onFailure(@NonNull Call<GroqChatResponse> call, @NonNull Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Single query helper.
     */
    public static void getSingleQueryResponse(String userQuery, SafetyChatCallback callback) {
        List<GroqChatRequest.Message> messages = new ArrayList<>();
        messages.add(new GroqChatRequest.Message("system", SYSTEM_SAFETY_PROMPT));
        messages.add(new GroqChatRequest.Message("user", userQuery));
        getChatResponse(messages, callback);
    }
}
