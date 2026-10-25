package com.example.woshield;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

public class WebViewActivity extends AppCompatActivity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);

        // Initialize WebView from the layout first.
        webView = findViewById(R.id.webView);

        // Enable JavaScript and set a WebViewClient.
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());

        // Get question from intent.
        String question = getIntent().getStringExtra("question");
        if (question != null) {
            String url = "https://chat.openai.com/?q=" + question.replace(" ", "%20");
            webView.loadUrl(url);
        } else {
            webView.loadUrl("https://chat.openai.com/");
        }
    }
}
