package com.example.wifitalkie.util;

import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {

    private static final String TAG = "CryptoUtils";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CTR/NoPadding";
    public static final String DEFAULT_PASSPHRASE = "WifiTalkie_Secure_AES256_VoiceKey_2026!";

    private static SecretKeySpec secretKey;
    private static final SecureRandom secureRandom = new SecureRandom();

    static {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha.digest(DEFAULT_PASSPHRASE.getBytes(StandardCharsets.UTF_8));
            secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize AES key", e);
        }
    }

    /**
     * Generate 16-byte cryptographically secure random IV
     */
    public static byte[] generateRandomIv() {
        byte[] iv = new byte[16];
        secureRandom.nextBytes(iv);
        return iv;
    }

    /**
     * Encrypt byte range using AES-256 in Counter (CTR) mode
     */
    public static byte[] encrypt(byte[] data, int offset, int length, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));
            return cipher.doFinal(data, offset, length);
        } catch (Exception e) {
            Log.e(TAG, "Encryption error", e);
            return null;
        }
    }

    /**
     * Decrypt byte range using AES-256 in Counter (CTR) mode
     */
    public static byte[] decrypt(byte[] data, int offset, int length, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
            return cipher.doFinal(data, offset, length);
        } catch (Exception e) {
            Log.e(TAG, "Decryption error", e);
            return null;
        }
    }
}
