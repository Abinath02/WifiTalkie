package com.example.wifitalkie;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.wifitalkie.model.User;

public class CreateUserActivity extends AppCompatActivity {

    private EditText etFirstName, etLastName, etNic, etDob, etEmail, etPassword;
    private Button btnCreate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_user);

        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etNic = findViewById(R.id.etNic);
        etDob = findViewById(R.id.etDob);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnCreate = findViewById(R.id.btnCreate);

        btnCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String firstName = etFirstName.getText().toString().trim();
                String lastName = etLastName.getText().toString().trim();
                String nic = etNic.getText().toString().trim();
                String dob = etDob.getText().toString().trim();
                String email = etEmail.getText().toString().trim();
                String password = etPassword.getText().toString().trim();

                if (firstName.isEmpty() || lastName.isEmpty() || nic.isEmpty() || dob.isEmpty() || email.isEmpty()
                        || password.isEmpty()) {
                    Toast.makeText(CreateUserActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                User newUser = new User(firstName, lastName, nic, email, password, dob, "EMPLOYEE");

                com.example.wifitalkie.network.ApiInterface apiInterface = com.example.wifitalkie.network.ApiClient
                        .getClient().create(com.example.wifitalkie.network.ApiInterface.class);
                retrofit2.Call<com.google.gson.JsonObject> call = apiInterface.createUser(newUser);

                call.enqueue(new retrofit2.Callback<com.google.gson.JsonObject>() {
                    @Override
                    public void onResponse(retrofit2.Call<com.google.gson.JsonObject> call,
                            retrofit2.Response<com.google.gson.JsonObject> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            if (response.body().get("success").getAsBoolean()) {
                                Toast.makeText(CreateUserActivity.this, "Employee Created Successfully",
                                        Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                Toast.makeText(CreateUserActivity.this,
                                        "Error: " + response.body().get("message").getAsString(), Toast.LENGTH_SHORT)
                                        .show();
                            }
                        } else {
                            Toast.makeText(CreateUserActivity.this, "Server Error", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<com.google.gson.JsonObject> call, Throwable t) {
                        Toast.makeText(CreateUserActivity.this, "Network Error: " + t.getMessage(), Toast.LENGTH_SHORT)
                                .show();
                    }
                });
            }
        });
    }
}
