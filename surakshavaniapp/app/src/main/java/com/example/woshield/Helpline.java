package com.example.woshield;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

public class Helpline extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_helpline);
    }

    public void callWomenDistress(View view) {
        dialNumber("1091");  // Women in Distress Helpline
    }

    public void callDomesticAbuse(View view) {
        dialNumber("181");  // Domestic Abuse Helpline
    }

    public void callPolice(View view) {
        dialNumber("100");  // Police Emergency Number
    }

    public void callStudentHelpline(View view) {
        dialNumber("1098");  // Child / Student Helpline
    }
    public void callAmbulance(View view) {
        dialNumber("108");
    }
    private void dialNumber(String phoneNumber) {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + phoneNumber));
        startActivity(intent);
    }
}
