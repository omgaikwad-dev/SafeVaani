package com.example.woshield;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EmergencyContactActivity extends AppCompatActivity implements ContactAdapter.OnContactActionListener {

    private TextInputEditText etEmergencyName, etEmergencyContact;
    private MaterialButton btnSaveContact;
    private RecyclerView rvContacts;
    private ContactAdapter adapter;
    private List<JSONObject> contactList;
    private String generatedOtp;
    private int editingPosition = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_contact);

        etEmergencyName = findViewById(R.id.et_emergency_name);
        etEmergencyContact = findViewById(R.id.et_emergency_contact);
        btnSaveContact = findViewById(R.id.btn_save_contact);
        rvContacts = findViewById(R.id.rv_contacts);

        contactList = new ArrayList<>();
        loadContactsFromPrefs();

        adapter = new ContactAdapter(contactList, this);
        rvContacts.setLayoutManager(new LinearLayoutManager(this));
        rvContacts.setAdapter(adapter);

        btnSaveContact.setOnClickListener(v -> {
            String name = etEmergencyName.getText().toString().trim();
            String number = etEmergencyContact.getText().toString().trim();

            if (name.isEmpty() || number.isEmpty() || !number.matches("\\d{10}")) {
                Toast.makeText(this, "Enter valid name and 10-digit contact", Toast.LENGTH_SHORT).show();
                return;
            }

            if (editingPosition == -1) {
                // New Contact - using OTP verification for security
                generatedOtp = String.valueOf(1000 + new Random().nextInt(9000));
                Toast.makeText(this, "OTP sent: " + generatedOtp, Toast.LENGTH_LONG).show();
                showOtpDialog(name, number);
            } else {
                // Updating existing contact - skip OTP for simple edits
                updateContact(editingPosition, name, number);
            }
        });
    }

    private void loadContactsFromPrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String contactsJson = prefs.getString("emergency_contacts", "[]");
        try {
            JSONArray array = new JSONArray(contactsJson);
            contactList.clear();
            for (int i = 0; i < array.length(); i++) {
                contactList.add(array.getJSONObject(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void saveContactsToPrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        JSONArray array = new JSONArray();
        for (JSONObject contact : contactList) {
            array.put(contact);
        }
        prefs.edit().putString("emergency_contacts", array.toString()).apply();
    }

    private void showOtpDialog(final String name, final String number) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Verify Contact");
        final EditText inputOtp = new EditText(this);
        inputOtp.setHint("Enter OTP");
        inputOtp.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(inputOtp);

        builder.setPositiveButton("Verify", (dialog, which) -> {
            String enteredOtp = inputOtp.getText().toString().trim();
            if (enteredOtp.equals(generatedOtp)) {
                addNewContact(name, number);
            } else {
                Toast.makeText(this, "Incorrect OTP", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void addNewContact(String name, String number) {
        try {
            JSONObject newContact = new JSONObject();
            newContact.put("name", name);
            newContact.put("number", number);
            contactList.add(newContact);
            saveContactsToPrefs();
            adapter.notifyItemInserted(contactList.size() - 1);
            clearFields();
            Toast.makeText(this, "Contact saved", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void updateContact(int position, String name, String number) {
        try {
            JSONObject contact = contactList.get(position);
            contact.put("name", name);
            contact.put("number", number);
            saveContactsToPrefs();
            adapter.notifyItemChanged(position);
            clearFields();
            editingPosition = -1;
            btnSaveContact.setText("ADD CONTACT");
            Toast.makeText(this, "Contact updated", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void clearFields() {
        etEmergencyName.setText("");
        etEmergencyContact.setText("");
    }

    @Override
    public void onEdit(int position, JSONObject contact) {
        try {
            etEmergencyName.setText(contact.getString("name"));
            etEmergencyContact.setText(contact.getString("number"));
            editingPosition = position;
            btnSaveContact.setText("UPDATE CONTACT");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDelete(int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Contact")
                .setMessage("Remove this emergency contact?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    contactList.remove(position);
                    saveContactsToPrefs();
                    adapter.notifyItemRemoved(position);
                    if (editingPosition == position) {
                        clearFields();
                        editingPosition = -1;
                        btnSaveContact.setText("ADD CONTACT");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
