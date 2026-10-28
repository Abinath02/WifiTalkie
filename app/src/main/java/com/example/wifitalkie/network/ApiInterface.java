package com.example.wifitalkie.network;

import com.example.wifitalkie.model.LoginResponse;
import com.example.wifitalkie.model.User;
import com.example.wifitalkie.model.UserRequest;
import com.example.wifitalkie.model.UsersResponse;
import com.example.wifitalkie.model.MessagesResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiInterface {
        @POST("login.php")
        Call<LoginResponse> login(@Body User user);

        @POST("create_user.php")
        Call<com.google.gson.JsonObject> createUser(@Body User user);

        @POST("get_users.php")
        Call<UsersResponse> getUsers(@Body UserRequest request);

        @POST("get_messages.php")
        Call<MessagesResponse> getMessages(@Body UserRequest request);

        @retrofit2.http.Multipart
        @POST("send_message.php")
        Call<com.google.gson.JsonObject> sendMessage(
                        @retrofit2.http.Part("sender_id") okhttp3.RequestBody senderId,
                        @retrofit2.http.Part("receiver_id") okhttp3.RequestBody receiverId,
                        @retrofit2.http.Part("is_group") okhttp3.RequestBody isGroup,
                        @retrofit2.http.Part("message") okhttp3.RequestBody message,
                        @retrofit2.http.Part("type") okhttp3.RequestBody type,
                        @retrofit2.http.Part okhttp3.MultipartBody.Part file);

        @retrofit2.http.Multipart
        @POST("update_profile.php")
        Call<com.google.gson.JsonObject> updateProfile(
                        @retrofit2.http.Part("id") okhttp3.RequestBody id,
                        @retrofit2.http.Part("firstName") okhttp3.RequestBody firstName,
                        @retrofit2.http.Part("lastName") okhttp3.RequestBody lastName,
                        @retrofit2.http.Part("nic") okhttp3.RequestBody nic,
                        @retrofit2.http.Part("dob") okhttp3.RequestBody dob,
                        @retrofit2.http.Part("password") okhttp3.RequestBody password,
                        @retrofit2.http.Part okhttp3.MultipartBody.Part profilePic);

        @POST("get_profile.php")
        Call<LoginResponse> getProfile(@Body UserRequest request);
}
