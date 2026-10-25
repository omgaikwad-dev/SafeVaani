package com.example.woshield;

import android.os.Bundle;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ChatBotActivity extends AppCompatActivity {

    private static final String TAG = "ChatBotActivity";

    private RecyclerView chatRecyclerView;
    private EditText messageInput;
    private ImageView sendBtn;
    private ChatAdapter chatAdapter;
    private final List<ChatMessage> messageList = new ArrayList<>();
    private final List<GroqChatRequest.Message> conversationHistory = new ArrayList<>();
    private boolean isAwaitingResponse = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_bot);

        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        messageInput = findViewById(R.id.messageInput);
        sendBtn = findViewById(R.id.sendBtn);
        ImageView backBtn = findViewById(R.id.backBtn);

        if (backBtn != null) {
            backBtn.setOnClickListener(v -> finish());
        }

        setupRecyclerView();
        initGroqAssistant();

        if (sendBtn != null) {
            sendBtn.setOnClickListener(v -> handleSendMessage());
        }

        if (messageInput != null) {
            messageInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    handleSendMessage();
                    return true;
                }
                return false;
            });
        }
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter(messageList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);
        chatRecyclerView.setAdapter(chatAdapter);
    }

    private void initGroqAssistant() {
        // System instruction for night-worker safety context
        conversationHistory.clear();
        // Use night-worker system prompt if available, fall back to default
        String systemPrompt = "You are NightShift Navigator's AI Companion — an expert in night-time city safety for shift workers in India. " +
                "You understand: public transport schedules at night, which services are open at 2-4 AM, safe route planning, " +
                "incident patterns in urban areas, and emergency procedures. " +
                "Always be honest about data uncertainty. If you don't know something, say so clearly. " +
                "Label your confidence: KNOWN (verified), PROBABLE (pattern-based), or UNKNOWN (no data). " +
                "You help with: safe route questions, transport options at night, open services (pharmacy/hospital/police), " +
                "emergency guidance, and worker rights. Keep answers concise and actionable for workers on the go.";
        conversationHistory.add(new GroqChatRequest.Message("system", systemPrompt));

        // Night-worker welcome message
        addMessage("🌙 Hello! I'm your Night AI Companion. I can help you with:\n" +
                "• Safe routes home at night\n" +
                "• Which services are open right now\n" +
                "• Transport options after your shift\n" +
                "• Emergency guidance\n\n" +
                "What do you need help with tonight?", false);
    }

    private void handleSendMessage() {
        if (isAwaitingResponse) {
            return;
        }

        String userInput = messageInput.getText().toString().trim();
        if (userInput.isEmpty()) {
            return;
        }

        // Post user message
        addMessage(userInput, true);
        conversationHistory.add(new GroqChatRequest.Message("user", userInput));
        messageInput.setText("");

        // Set UI loading state
        isAwaitingResponse = true;
        if (sendBtn != null) {
            sendBtn.setEnabled(false);
            sendBtn.setAlpha(0.5f);
        }

        // Add thinking indicator message
        final int thinkingIndex = messageList.size();
        addMessage("Safety Assistant is analyzing...", false);

        GroqSafetyService.getChatResponse(conversationHistory, new GroqSafetyService.SafetyChatCallback() {
            @Override
            public void onResponse(String response) {
                runOnUiThread(() -> {
                    isAwaitingResponse = false;
                    if (sendBtn != null) {
                        sendBtn.setEnabled(true);
                        sendBtn.setAlpha(1.0f);
                    }

                    // Replace the thinking message with actual response
                    if (thinkingIndex < messageList.size()) {
                        messageList.set(thinkingIndex, new ChatMessage(response, false));
                        chatAdapter.notifyItemChanged(thinkingIndex);
                    } else {
                        addMessage(response, false);
                    }
                    conversationHistory.add(new GroqChatRequest.Message("assistant", response));
                    chatRecyclerView.scrollToPosition(messageList.size() - 1);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    isAwaitingResponse = false;
                    if (sendBtn != null) {
                        sendBtn.setEnabled(true);
                        sendBtn.setAlpha(1.0f);
                    }

                    String friendlyError = "I'm having trouble connecting right now (" + error + "). If you are in immediate danger, please call 112 or emergency services immediately.";
                    if (thinkingIndex < messageList.size()) {
                        messageList.set(thinkingIndex, new ChatMessage(friendlyError, false));
                        chatAdapter.notifyItemChanged(thinkingIndex);
                    } else {
                        addMessage(friendlyError, false);
                    }
                    chatRecyclerView.scrollToPosition(messageList.size() - 1);
                });
            }
        });
    }

    private void addMessage(String message, boolean isUser) {
        messageList.add(new ChatMessage(message, isUser));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        chatRecyclerView.scrollToPosition(messageList.size() - 1);
    }
}
