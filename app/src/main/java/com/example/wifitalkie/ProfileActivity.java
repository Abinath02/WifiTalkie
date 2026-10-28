package com.example.wifitalkie;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.wifitalkie.model.LoginResponse;
import com.example.wifitalkie.model.User;
import com.example.wifitalkie.model.UserRequest;
import com.example.wifitalkie.network.ApiClient;
import com.example.wifitalkie.network.ApiInterface;
import com.example.wifitalkie.util.Constants;
import com.example.wifitalkie.util.SessionManager;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import android.provider.OpenableColumns;
import android.util.Log;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int STORAGE_PERMISSION_CODE = 2;

    private SessionManager sessionManager;
    private ApiInterface apiInterface;

    private ImageView ivProfilePic, ivEditIcon;
    private TextInputEditText etFirstName, etLastName, etNic, etDob, etPassword;
    private TextView tvProfileName, tvProfileRole;
    private Button btnEditSave, btnLogout;

    private boolean isEditMode = false;
    private Uri selectedImageUri;
    private User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        sessionManager = new SessionManager(this);
        apiInterface = ApiClient.getClient(this).create(ApiInterface.class);

        initViews();
        setupListeners();

        // Load initial data from session
        currentUser = sessionManager.getUserDetails(); // This gives partial data (ID, Role)

        // Fetch full profile
        fetchUserProfile();
    }

    private void initViews() {
        ivProfilePic = findViewById(R.id.ivProfilePic);
        ivEditIcon = findViewById(R.id.ivEditIcon);
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etNic = findViewById(R.id.etNic);
        etDob = findViewById(R.id.etDob);
        etPassword = findViewById(R.id.etPassword);
        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileRole = findViewById(R.id.tvProfileRole);
        btnEditSave = findViewById(R.id.btnEditSave);
        btnLogout = findViewById(R.id.btnLogout);
    }

    private void setupListeners() {
        btnEditSave.setOnClickListener(v -> {
            if (isEditMode) {
                saveProfile();
            } else {
                enableEditMode(true);
            }
        });

        ivEditIcon.setOnClickListener(v -> openImagePicker());

        btnLogout.setOnClickListener(v -> {
            sessionManager.logoutUser();
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void fetchUserProfile() {
        // Fetch fresh data using ID from session
        int userId = sessionManager.getUserDetails().getId();
        UserRequest request = new UserRequest(userId);

        apiInterface.getProfile(request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    User user = response.body().getUser();
                    populateUserData(user);
                } else {
                    Toast.makeText(ProfileActivity.this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                    // Fallback to session data if needed, but session data is minimal
                    populateUserData(sessionManager.getUserDetails());
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                Toast.makeText(ProfileActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void populateUserData(User user) {
        this.currentUser = user; // Keep reference to fetched user data

        tvProfileName.setText(user.getFirstName() + " " + user.getLastName());
        tvProfileRole.setText(user.getRole());

        etFirstName.setText(user.getFirstName());
        etLastName.setText(user.getLastName());
        etNic.setText(user.getNic());
        etDob.setText(user.getDob());

        // Load Image
        if (user.getProfilePic() != null && !user.getProfilePic().isEmpty()) {
            String imageUrl = Constants.getBaseUrl(this) + "uploads/" + user.getProfilePic();
            Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.mipmap.ic_launcher_round)
                    .error(R.mipmap.ic_launcher_round)
                    .into(ivProfilePic);
        }
    }

    private void enableEditMode(boolean enable) {
        isEditMode = enable;

        etFirstName.setEnabled(enable);
        etLastName.setEnabled(enable);
        etNic.setEnabled(enable);
        etDob.setEnabled(enable);
        etPassword.setEnabled(enable);

        ivEditIcon.setVisibility(enable ? View.VISIBLE : View.GONE);

        if (enable) {
            btnEditSave.setText("Save Changes");
            btnEditSave.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.holo_green_light));
        } else {
            btnEditSave.setText("Edit Profile");
            btnEditSave.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.teal_200)); // or logic for
                                                                                                        // original
                                                                                                        // color
        }
    }

    private void openImagePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[] { Manifest.permission.READ_MEDIA_IMAGES }, STORAGE_PERMISSION_CODE);
                return;
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[] { Manifest.permission.READ_EXTERNAL_STORAGE }, STORAGE_PERMISSION_CODE);
                return;
            }
        }

        // Intent to pick image compatible with Scoped Storage & PhotoPicker
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select Profile Picture"), PICK_IMAGE_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openImagePicker();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            ivProfilePic.setImageURI(selectedImageUri); // Show preview
        }
    }

    private void saveProfile() {
        // Collect data
        String fName = etFirstName.getText().toString().trim();
        String lName = etLastName.getText().toString().trim();
        String nic = etNic.getText().toString().trim();
        String dob = etDob.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();

        if (fName.isEmpty() || lName.isEmpty()) {
            Toast.makeText(this, "Name fields cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        // Prepare Multipart parts
        RequestBody idPart = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(currentUser.getId()));
        RequestBody fNamePart = RequestBody.create(MediaType.parse("text/plain"), fName);
        RequestBody lNamePart = RequestBody.create(MediaType.parse("text/plain"), lName);
        RequestBody nicPart = RequestBody.create(MediaType.parse("text/plain"), nic);
        RequestBody dobPart = RequestBody.create(MediaType.parse("text/plain"), dob);

        RequestBody passPart = null;
        if (!pass.isEmpty()) {
            passPart = RequestBody.create(MediaType.parse("text/plain"), pass);
        } else {
            passPart = RequestBody.create(MediaType.parse("text/plain"), "");
        }

        MultipartBody.Part body = null;
        if (selectedImageUri != null) {
            File file = getFileFromUri(selectedImageUri);
            if (file != null && file.exists()) {
                String mimeType = getContentResolver().getType(selectedImageUri);
                if (mimeType == null || !mimeType.startsWith("image/")) {
                    mimeType = "image/jpeg";
                }
                RequestBody requestFile = RequestBody.create(MediaType.parse(mimeType), file);
                body = MultipartBody.Part.createFormData("profile_pic", file.getName(), requestFile);
            }
        }

        // Call API
        apiInterface.updateProfile(idPart, fNamePart, lNamePart, nicPart, dobPart, passPart, body)
                .enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            if (response.body().has("success") && response.body().get("success").getAsBoolean()) {
                                Toast.makeText(ProfileActivity.this, "Profile Updated!", Toast.LENGTH_SHORT).show();
                                enableEditMode(false);
                                fetchUserProfile(); // Refresh data to confirm and get new image URL if needed
                            } else {
                                String msg = response.body().has("message")
                                        ? response.body().get("message").getAsString()
                                        : "Update failed";
                                Toast.makeText(ProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(ProfileActivity.this, "Update Failed: " + response.message(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<JsonObject> call, Throwable t) {
                        Toast.makeText(ProfileActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Safely copies content URI stream into app cache directory.
     * Works on all Android versions (including Android 10, 11, 12, 13, 14) without Scoped Storage failures.
     */
    private File getFileFromUri(Uri uri) {
        if (uri == null) return null;
        try {
            String fileName = "profile_" + System.currentTimeMillis() + ".jpg";
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        String displayName = cursor.getString(nameIndex);
                        if (displayName != null && !displayName.isEmpty()) {
                            fileName = new File(displayName).getName();
                        }
                    }
                }
            } catch (Exception ignored) {}

            File tempFile = new File(getCacheDir(), "upload_" + System.currentTimeMillis() + "_" + fileName);
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(tempFile)) {
                if (in == null) return null;
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }
            return tempFile;
        } catch (Exception e) {
            Log.e("ProfileActivity", "Error copying URI to temp file", e);
            return null;
        }
    }
}
