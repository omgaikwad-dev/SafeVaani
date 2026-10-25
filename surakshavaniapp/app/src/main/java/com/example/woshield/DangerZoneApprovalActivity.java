package com.example.woshield;

import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DangerZoneApprovalActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private DangerZoneAdapter adapter;
    private List<Dataholder> list = new ArrayList<>();
    private List<String> keys = new ArrayList<>();
    private DatabaseReference ref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_danger_zone_approval);

        recyclerView = findViewById(R.id.rvDangerZones);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        ref = FirebaseDatabase.getInstance().getReference("DangerZones");

        adapter = new DangerZoneAdapter(list, keys, new DangerZoneAdapter.OnActionClickListener() {
            @Override
            public void onApprove(String key) {
                ref.child(key).child("status").setValue("approved")
                        .addOnSuccessListener(aVoid -> Toast.makeText(DangerZoneApprovalActivity.this, "Zone Approved", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onReject(String key) {
                ref.child(key).child("status").setValue("rejected")
                        .addOnSuccessListener(aVoid -> Toast.makeText(DangerZoneApprovalActivity.this, "Report Rejected", Toast.LENGTH_SHORT).show());
            }
        });

        recyclerView.setAdapter(adapter);

        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                list.clear();
                keys.clear();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    Dataholder d = ds.getValue(Dataholder.class);
                    if (d != null && "pending".equals(d.getStatus())) {
                        list.add(d);
                        keys.add(ds.getKey());
                    }
                }
                Collections.reverse(list);
                Collections.reverse(keys);
                adapter.notifyDataSetChanged();
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}
