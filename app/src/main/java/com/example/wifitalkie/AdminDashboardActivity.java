package com.example.wifitalkie;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.wifitalkie.util.SessionManager;

public class AdminDashboardActivity extends AppCompatActivity {

        private SessionManager sessionManager;
        private TextView tvWelcome;
        private Button btnCreateEmployee, btnFindDevices, btnProfile, btnLogout;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_admin_dashboard);

                sessionManager = new SessionManager(this);
                if (!sessionManager.isLoggedIn()) {
                        startActivity(new Intent(this, LoginActivity.class));
                        finish();
                        return;
                }

                tvWelcome = findViewById(R.id.tvWelcome);
                tvWelcome.setText("Welcome Admin: " + sessionManager.getUserName());

                View cardCreateEmployee = findViewById(R.id.cardCreateEmployee);
                View cardJoinWalkie = findViewById(R.id.cardJoinWalkie);
                View cardFileTransfer = findViewById(R.id.cardFileTransfer);
                View cardTextChat = findViewById(R.id.cardTextChat);
                View cardFindDevices = findViewById(R.id.cardFindDevices);
                View cardLogout = findViewById(R.id.cardLogout);
                View btnProfile = findViewById(R.id.btnProfile);

                cardCreateEmployee.setOnClickListener(
                                v -> startActivity(new Intent(AdminDashboardActivity.this, CreateUserActivity.class)));

                cardJoinWalkie.setOnClickListener(
                                v -> startActivity(
                                                new Intent(AdminDashboardActivity.this, WalkieTalkieActivity.class)));

                cardFileTransfer.setOnClickListener(v -> {
                        Intent intent = new Intent(AdminDashboardActivity.this, FileTransferActivity.class);
                        startActivity(intent);
                });

                cardTextChat.setOnClickListener(
                                v -> startActivity(new Intent(AdminDashboardActivity.this, UserListActivity.class)));

                cardFindDevices.setOnClickListener(
                                v -> startActivity(new Intent(AdminDashboardActivity.this, WiFiDirectActivity.class)));

                btnProfile.setOnClickListener(
                                v -> startActivity(new Intent(AdminDashboardActivity.this, ProfileActivity.class)));

                cardLogout.setOnClickListener(v -> {
                        sessionManager.logoutUser();
                        startActivity(new Intent(AdminDashboardActivity.this, LoginActivity.class));
                        finish();
                });
        }
}
