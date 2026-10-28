package com.example.wifitalkie;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.wifitalkie.util.SessionManager;

public class EmployeeDashboardActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private TextView tvWelcome;
    private View cardWalkieTalkie, cardFindDevices, btnProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee_dashboard);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        tvWelcome = findViewById(R.id.tvWelcome);
        tvWelcome.setText(sessionManager.getUserName());

        cardWalkieTalkie = findViewById(R.id.cardWalkieTalkie);
        cardFindDevices = findViewById(R.id.cardFindDevices);
        btnProfile = findViewById(R.id.btnProfile);
        View cardFileTransfer = findViewById(R.id.cardFileTransfer);
        View cardTextChat = findViewById(R.id.cardTextChat);

        cardWalkieTalkie.setOnClickListener(
                v -> startActivity(new Intent(EmployeeDashboardActivity.this, WalkieTalkieActivity.class)));

        cardFileTransfer.setOnClickListener(v -> {
            Intent intent = new Intent(EmployeeDashboardActivity.this, FileTransferActivity.class);
            startActivity(intent);
        });

        cardTextChat.setOnClickListener(
                v -> startActivity(new Intent(EmployeeDashboardActivity.this, UserListActivity.class)));

        cardFindDevices.setOnClickListener(
                v -> startActivity(new Intent(EmployeeDashboardActivity.this, WiFiDirectActivity.class)));

        btnProfile.setOnClickListener(
                v -> startActivity(new Intent(EmployeeDashboardActivity.this, ProfileActivity.class)));

        View cardLogout = findViewById(R.id.cardLogout);
        cardLogout.setOnClickListener(v -> {
            sessionManager.logoutUser();
            startActivity(new Intent(EmployeeDashboardActivity.this, LoginActivity.class));
            finish();
        });
    }
}
