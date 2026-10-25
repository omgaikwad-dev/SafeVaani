package com.example.woshield;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class SecuritySuggestionsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_suggestions);

        RecyclerView recyclerView = findViewById(R.id.rvGuidelines);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<Guideline> guidelines = new ArrayList<>();
        guidelines.add(new Guideline("General Safety", "Stay aware of your surroundings and trust your instincts. Keep emergency contacts on speed dial."));
        guidelines.add(new Guideline("Public Places", "Avoid isolated areas at night. Plan your route and use reliable transportation."));
        guidelines.add(new Guideline("Traveling Alone", "Share your itinerary with family. Keep valuables hidden and stay in secure locations."));
        guidelines.add(new Guideline("Workplace Safety", "Know workplace exits and security protocols. Report any inappropriate behavior immediately."));
        guidelines.add(new Guideline("Online Safety", "Use strong passwords and 2FA. Be selective about sharing your real-time location."));
        guidelines.add(new Guideline("Home Security", "Keep doors and windows locked. Do not open doors to strangers without verification."));

        GuidelineAdapter adapter = new GuidelineAdapter(guidelines);
        recyclerView.setAdapter(adapter);
    }
}
