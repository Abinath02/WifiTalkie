package com.example.wifitalkie.network;

import android.content.Context;

import com.example.wifitalkie.util.Constants;
import com.example.wifitalkie.util.SessionManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static Retrofit retrofit = null;
    private static String currentBaseUrl = "";
    private static Context appContext = null;

    public static void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    public static Retrofit getClient() {
        return getClient(appContext);
    }

    public static synchronized Retrofit getClient(Context context) {
        if (context != null && appContext == null) {
            appContext = context.getApplicationContext();
        }

        String baseUrl = (context != null) ? Constants.getBaseUrl(context) : Constants.BASE_URL;

        if (retrofit == null || !baseUrl.equals(currentBaseUrl)) {
            currentBaseUrl = baseUrl;

            OkHttpClient.Builder httpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS);

            // Add Auth Interceptor
            httpClient.addInterceptor(new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Request original = chain.request();
                    Request.Builder requestBuilder = original.newBuilder();

                    if (appContext != null) {
                        SessionManager sessionManager = new SessionManager(appContext);
                        String token = sessionManager.getAuthToken();
                        if (token != null && !token.isEmpty()) {
                            requestBuilder.header("Authorization", "Bearer " + token);
                        }
                    }

                    Request request = requestBuilder.build();
                    return chain.proceed(request);
                }
            });

            com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                    .setLenient()
                    .create();

            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(httpClient.build())
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();
        }
        return retrofit;
    }
}
