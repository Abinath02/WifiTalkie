package com.example.wifitalkie;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wifitalkie.adapter.UserListAdapter;
import com.example.wifitalkie.model.User;
import com.example.wifitalkie.model.UserRequest;
import com.example.wifitalkie.model.UsersResponse;
import com.example.wifitalkie.network.ApiClient;
import com.example.wifitalkie.network.ApiInterface;
import com.example.wifitalkie.util.SessionManager;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserListActivity extends AppCompatActivity {

    private RecyclerView recyclerViewUsers;
    private ProgressBar progressBar;
    private UserListAdapter adapter;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_list);

        sessionManager = new SessionManager(this);
        recyclerViewUsers = findViewById(R.id.recyclerViewUsers);
        progressBar = findViewById(R.id.progressBar);

        recyclerViewUsers.setLayoutManager(new LinearLayoutManager(this));

        fetchUsers();
    }

    private void fetchUsers() {
        progressBar.setVisibility(View.VISIBLE);
        ApiInterface apiInterface = ApiClient.getClient(this).create(ApiInterface.class);

        // Pass current user ID to exclude self
        int currentUserId = sessionManager.getUserDetails().getId();
        Call<UsersResponse> call = apiInterface.getUsers(new UserRequest(currentUserId));

        call.enqueue(new Callback<UsersResponse>() {
            @Override
            public void onResponse(Call<UsersResponse> call, Response<UsersResponse> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().isSuccess()) {
                        List<User> users = response.body().getUsers();

                        // Add Global Group at the top
                        User groupChat = new User();
                        groupChat.setId(0); // 0 means group
                        groupChat.setFirstName("Global");
                        groupChat.setLastName("Group");
                        groupChat.setRole("GROUP");
                        users.add(0, groupChat);

                        adapter = new UserListAdapter(UserListActivity.this, users);
                        recyclerViewUsers.setAdapter(adapter);
                    } else {
                        Toast.makeText(UserListActivity.this, response.body().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(UserListActivity.this, "Failed to load users", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<UsersResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(UserListActivity.this, "Network Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
