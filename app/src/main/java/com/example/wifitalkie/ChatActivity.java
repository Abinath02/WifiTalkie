package com.example.wifitalkie;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.wifitalkie.adapter.MessageAdapter;
import com.example.wifitalkie.model.Message;
import com.example.wifitalkie.model.MessagesResponse;
import com.example.wifitalkie.model.UserRequest;
import com.example.wifitalkie.network.ApiClient;
import com.example.wifitalkie.network.ApiInterface;
import com.example.wifitalkie.network.ProgressRequestBody;
import com.example.wifitalkie.util.SessionManager;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity {

    private TextView tvStatus, tvOnlineStatus;
    private androidx.recyclerview.widget.RecyclerView chatRecyclerView;
    private EditText etMessage;
    private ImageButton btnSend;
    private ImageButton btnRecord;

    private MessageAdapter adapter;
    private List<Message> messageList;
    private SessionManager sessionManager;
    private ApiInterface apiInterface;

    private int currentUserId;
    private int receiverId;
    private String receiverName;
    private boolean isGroup = false;

    private MediaRecorder mediaRecorder;
    private String audioFilePath;
    private boolean isRecording = false;

    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<Intent> pdfPickerLauncher;

    private static final int REQUEST_AUDIO_PERMISSION_CODE = 201;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;

    // Notification related variables
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    private static final int UPLOAD_NOTIFICATION_ID = 2;
    private static final String UPLOAD_CHANNEL_ID = "upload_channel";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        sessionManager = new SessionManager(this);
        apiInterface = ApiClient.getClient(this).create(ApiInterface.class);
        currentUserId = sessionManager.getUserDetails().getId();

        Intent intent = getIntent();
        receiverId = intent.getIntExtra("receiver_id", -1);
        receiverName = intent.getStringExtra("receiver_name");
        isGroup = intent.getBooleanExtra("is_group", false);

        if (receiverId == -1 && !isGroup) {
            Toast.makeText(this, "Invalid User or Group", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvStatus = findViewById(R.id.tvStatus);
        tvOnlineStatus = findViewById(R.id.tvOnlineStatus);
        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnRecord = findViewById(R.id.btnRecord);
        ImageButton btnAttachImage = findViewById(R.id.btnAttachImage);
        ImageButton btnAttachPdf = findViewById(R.id.btnAttachPdf);
        ImageButton btnBack = findViewById(R.id.btnBack);

        tvStatus.setText(receiverName);
        tvOnlineStatus.setText(isGroup ? "Group Chat" : "Online");

        btnBack.setOnClickListener(v -> finish());

        initializeLaunchers();

        btnAttachImage.setOnClickListener(v -> {
            Intent pickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
            pickerIntent.setType("image/*");
            imagePickerLauncher.launch(pickerIntent);
        });
        btnAttachPdf.setOnClickListener(v -> {
            Intent pickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
            pickerIntent.setType("application/pdf");
            pdfPickerLauncher.launch(pickerIntent);
        });

        messageList = new ArrayList<>();
        adapter = new MessageAdapter(this, messageList, currentUserId);
        chatRecyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        chatRecyclerView.setAdapter(adapter);

        btnSend.setOnClickListener(v -> {
            String messageText = etMessage.getText().toString().trim();
            if (!messageText.isEmpty()) {
                sendMessage(messageText, "text", null);
                etMessage.setText("");
            }
        });

        setupAudioRecording();
        createUploadNotificationChannel(); // Create notification channel on startup
        startMessagePolling();
    }

    private void initializeLaunchers() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            File file = getFileFromUri(imageUri, "image");
                            if (file != null) {
                                sendMessage("", "image", file);
                            }
                        }
                    }
                });

        pdfPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri pdfUri = result.getData().getData();
                        if (pdfUri != null) {
                            File file = getFileFromUri(pdfUri, "pdf");
                            if (file != null) {
                                sendMessage("", "pdf", file);
                            }
                        }
                    }
                });
    }

    private File getFileFromUri(Uri uri, String type) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null)
                return null;
            String extension = type.equals("image") ? ".jpg" : ".pdf";
            File tempFile = File.createTempFile("upload_", extension, getCacheDir());
            tempFile.deleteOnExit();

            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            inputStream.close();
            return tempFile;
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to process file", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void sendMessage(String messageContent, String type, @Nullable File file) {
        RequestBody senderIdBody = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(currentUserId));
        RequestBody receiverIdBody = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(receiverId));
        RequestBody isGroupBody = RequestBody.create(MediaType.parse("text/plain"), isGroup ? "1" : "0");
        RequestBody messageBody = RequestBody.create(MediaType.parse("text/plain"), messageContent);
        RequestBody typeBody = RequestBody.create(MediaType.parse("text/plain"), type);

        MultipartBody.Part filePart = null;
        if (file != null && file.exists()) {
            String mime;
            String partName = "file";
            if (type.equals("audio")) {
                mime = "audio/3gp";
            } else if (type.equals("image")) {
                mime = "image/jpeg";
            } else if (type.equals("pdf")) {
                mime = "application/pdf";
            } else {
                mime = "application/octet-stream";
            }

            ProgressRequestBody.ProgressListener progressListener = new ProgressRequestBody.ProgressListener() {
                @Override
                public void onProgressUpdate(long bytesUploaded, long totalBytes) {
                    int progress = (int) ((100 * bytesUploaded) / totalBytes);
                    updateUploadNotification(progress);
                }

                @Override
                public void onError(Exception e) {
                    Log.e("UploadError", "File upload failed", e);
                    cancelUploadNotification(true, "Upload Failed");
                }

                @Override
                public void onFinish() {
                    new Handler(Looper.getMainLooper())
                            .postDelayed(() -> cancelUploadNotification(false, "Upload Complete"), 500);
                }
            };

            ProgressRequestBody requestFile = new ProgressRequestBody(file, mime, progressListener);
            filePart = MultipartBody.Part.createFormData(partName, file.getName(), requestFile);

            showUploadNotification(file.getName());
        }

        Call<JsonObject> call = apiInterface.sendMessage(senderIdBody, receiverIdBody, isGroupBody, messageBody,
                typeBody, filePart);
        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().get("success").getAsBoolean()) {
                        broadcastChatNotification();
                        refreshMessages();
                    } else {
                        String errMsg = response.body().has("message") ? response.body().get("message").getAsString()
                                : "Unknown error";
                        Toast.makeText(ChatActivity.this, "Failed: " + errMsg, Toast.LENGTH_SHORT).show();
                        if (file != null)
                            cancelUploadNotification(true, "Server Error");
                    }
                } else {
                    Toast.makeText(ChatActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show();
                    if (file != null)
                        cancelUploadNotification(true, "Server Error");
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                Toast.makeText(ChatActivity.this, "Network Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                if (file != null)
                    cancelUploadNotification(true, "Network Error");
            }
        });
    }

    private void setupAudioRecording() {
        btnRecord.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (checkAudioPermission()) {
                        startRecording();
                    } else {
                        requestAudioPermission();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (isRecording) {
                        stopRecording();
                    }
                    return true;
            }
            return false;
        });
    }

    private boolean checkAudioPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.RECORD_AUDIO },
                REQUEST_AUDIO_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                Toast.makeText(this, "Audio permission is required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startRecording() {
        if (isRecording)
            return;
        try {
            File audioFile = File.createTempFile("voice_", ".3gp", getCacheDir());
            audioFilePath = audioFile.getAbsolutePath();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mediaRecorder = new MediaRecorder(this);
            } else {
                mediaRecorder = new MediaRecorder();
            }
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(audioFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            Toast.makeText(this, "Recording...", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Recording failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (!isRecording || mediaRecorder == null)
            return;
        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
            Toast.makeText(this, "Recording Stopped", Toast.LENGTH_SHORT).show();
            if (audioFilePath != null) {
                sendMessage("", "audio", new File(audioFilePath));
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
            mediaRecorder = null;
            isRecording = false;
            if (audioFilePath != null) {
                new File(audioFilePath).delete();
            }
        }
    }

    private void refreshMessages() {
        Call<MessagesResponse> call = apiInterface.getMessages(new UserRequest(currentUserId, receiverId, isGroup));
        call.enqueue(new Callback<MessagesResponse>() {
            @Override
            public void onResponse(@NonNull Call<MessagesResponse> call, @NonNull Response<MessagesResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<Message> newMessages = response.body().getMessages();
                    if (newMessages != null && newMessages.size() != messageList.size()) {
                        messageList.clear();
                        messageList.addAll(newMessages);
                        adapter.notifyDataSetChanged();
                        chatRecyclerView.scrollToPosition(adapter.getItemCount() - 1);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<MessagesResponse> call, @NonNull Throwable t) {
                Log.e("ChatActivity", "Failed to refresh messages: " + t.getMessage());
            }
        });
    }

    private void broadcastChatNotification() {
        new Thread(() -> {
            try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
                socket.setBroadcast(true);
                java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(1 + 1 + 4 + 4 + 1);
                bb.put((byte) 0x57); // 'W'
                bb.put((byte) 3);    // PACKET_TYPE_CHAT_NOTIF
                bb.putInt(currentUserId);
                bb.putInt(receiverId);
                bb.put((byte) (isGroup ? 1 : 0));
                byte[] d = bb.array();
                java.net.DatagramPacket packet = new java.net.DatagramPacket(d, d.length,
                        java.net.InetAddress.getByName("255.255.255.255"), 50005);
                socket.send(packet);
            } catch (Exception ignored) {}
        }).start();
    }

    private final android.content.BroadcastReceiver chatNotifReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if (com.example.wifitalkie.service.AudioService.ACTION_NEW_MESSAGE_ARRIVED.equals(intent.getAction())) {
                int sender = intent.getIntExtra(com.example.wifitalkie.service.AudioService.EXTRA_SENDER_ID, -1);
                int rec = intent.getIntExtra(com.example.wifitalkie.service.AudioService.EXTRA_RECEIVER_ID, -1);
                boolean group = intent.getBooleanExtra(com.example.wifitalkie.service.AudioService.EXTRA_IS_GROUP, false);

                if (isGroup && group) {
                    refreshMessages();
                } else if (!isGroup && !group && (sender == receiverId || rec == receiverId)) {
                    refreshMessages();
                }
            }
        }
    };

    private void startMessagePolling() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                refreshMessages();
                handler.postDelayed(this, 25000);
            }
        };
        handler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(chatNotifReceiver);
        } catch (Exception ignored) {}
        handler.removeCallbacks(refreshRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        androidx.core.content.ContextCompat.registerReceiver(
                this,
                chatNotifReceiver,
                new android.content.IntentFilter(com.example.wifitalkie.service.AudioService.ACTION_NEW_MESSAGE_ARRIVED),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        refreshMessages();
        startMessagePolling();
    }

    // --- Notification Helper Methods ---

    private void createUploadNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "File Uploads";
            String description = "Shows progress of file uploads";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(UPLOAD_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        } else {
            notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        }
    }

    private void showUploadNotification(String fileName) {
        notificationBuilder = new NotificationCompat.Builder(this, UPLOAD_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_upload) // Add ic_upload.xml to your drawable folder
                .setContentTitle(fileName)
                .setContentText("Upload in progress")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, 0, false);

        notificationManager.notify(UPLOAD_NOTIFICATION_ID, notificationBuilder.build());
    }

    private void updateUploadNotification(int progress) {
        notificationBuilder.setProgress(100, progress, false)
                .setContentText("Uploading: " + progress + "%");
        notificationManager.notify(UPLOAD_NOTIFICATION_ID, notificationBuilder.build());
    }

    private void cancelUploadNotification(boolean isError, String message) {
        if (notificationManager != null && notificationBuilder != null) {
            notificationBuilder.setContentText(message)
                    .setProgress(0, 0, false)
                    .setOngoing(false);
            if (isError) {
                notificationBuilder.setSmallIcon(R.drawable.ic_upload_error); // Add ic_upload_error.xml
            }
            notificationManager.notify(UPLOAD_NOTIFICATION_ID, notificationBuilder.build());

            if (!isError) {
                new Handler(Looper.getMainLooper())
                        .postDelayed(() -> notificationManager.cancel(UPLOAD_NOTIFICATION_ID), 2000);
            }
        }
    }
}
