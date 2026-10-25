package com.example.woshield;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class GroqChatResponse {
    @SerializedName("id")
    private String id;

    @SerializedName("choices")
    private List<Choice> choices;

    public String getId() { return id; }
    public List<Choice> getChoices() { return choices; }

    public static class Choice {
        @SerializedName("message")
        private Message message;

        @SerializedName("finish_reason")
        private String finishReason;

        public Message getMessage() { return message; }
        public String getFinishReason() { return finishReason; }
    }

    public static class Message {
        @SerializedName("role")
        private String role;

        @SerializedName("content")
        private String content;

        @SerializedName("reasoning")
        private String reasoning;

        public String getRole() { return role; }
        public String getContent() { return content; }
        public String getReasoning() { return reasoning; }
    }
}
