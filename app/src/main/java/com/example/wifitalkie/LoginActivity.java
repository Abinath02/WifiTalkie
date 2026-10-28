package com.example.wifitalkie;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.wifitalkie.model.LoginResponse;
import com.example.wifitalkie.model.User;
import com.example.wifitalkie.network.ApiClient;
import com.example.wifitalkie.network.ApiInterface;
import com.example.wifitalkie.util.Constants;
import com.example.wifitalkie.util.SessionManager;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvServerConfig;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        ApiClient.init(getApplicationContext());
        sessionManager = new SessionManager(this);

        if (sessionManager.isLoggedIn()) {
            redirectUser(sessionManager.getUserRole());
            return;
        }

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvServerConfig = findViewById(R.id.tvServerConfig);

        updateServerConfigDisplay();

        tvServerConfig.setOnClickListener(v -> showServerConfigDialog());

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = etEmail.getText().toString().trim();
                String password = etPassword.getText().toString().trim();

                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(LoginActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                loginUser(email, password);
            }
        });
    }

    private void updateServerConfigDisplay() {
        if (tvServerConfig != null) {
            String currentIp = Constants.getServerIp(this);
            tvServerConfig.setText("⚙️ Server IP: " + currentIp + " (Tap to change)");
        }
    }

    private void showServerConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Configure Server IP");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("e.g. 192.168.0.115");
        input.setText(Constants.getServerIp(this));
        input.setSelection(input.getText().length());
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newIp = input.getText().toString().trim();
            if (!newIp.isEmpty()) {
                Constants.setServerIp(LoginActivity.this, newIp);
                ApiClient.getClient(LoginActivity.this);
                updateServerConfigDisplay();
                Toast.makeText(LoginActivity.this, "Server IP updated to " + newIp, Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void loginUser(String email, String password) {
        ApiInterface apiInterface = ApiClient.getClient(this).create(ApiInterface.class);
        User loginRequestUser = new User();
        loginRequestUser.setEmail(email);
        loginRequestUser.setPassword(password);

        Call<LoginResponse> call = apiInterface.login(loginRequestUser);
        call.enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().isSuccess()) {
                        Toast.makeText(LoginActivity.this, "Login Successful", Toast.LENGTH_SHORT).show();
                        User user = response.body().getUser();
                        String token = response.body().getToken();
                        sessionManager.createLoginSession(user, token);
                        redirectUser(user.getRole());
                    } else {
                        Toast.makeText(LoginActivity.this, response.body().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    String errorMsg = "Server Error (" + response.code() + ")";
                    try {
                        if (response.errorBody() != null) {
                            errorMsg += ": " + response.errorBody().string();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Network Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void redirectUser(String role) {
        Intent intent;
        if ("ADMIN".equals(role)) {
            intent = new Intent(LoginActivity.this, AdminDashboardActivity.class);
        } else {
            intent = new Intent(LoginActivity.this, EmployeeDashboardActivity.class);
        }
        startActivity(intent);
        finish();
    }
}
