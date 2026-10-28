package com.example.wifitalkie.network;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;

public class ProgressRequestBody extends RequestBody {

    private final File mFile;
    private final String mMimeType;
    private final ProgressListener mListener;
    private static final int DEFAULT_BUFFER_SIZE = 4096;

    /**
     * Interface to listen for file upload progress.
     */
    public interface ProgressListener {
        /**
         * Called when the upload progress changes.
         * @param bytesUploaded The number of bytes uploaded so far.
         * @param totalBytes The total size of the file.
         */
        void onProgressUpdate(long bytesUploaded, long totalBytes);

        /**
         * Called when an error occurs during upload.
         * @param e The exception that occurred.
         */
        void onError(Exception e);

        /**
         * Called when the upload is successfully finished.
         */
        void onFinish();
    }

    public ProgressRequestBody(final File file, final String mimeType, final ProgressListener listener) {
        mFile = file;
        mMimeType = mimeType;
        mListener = listener;
    }

    @Nullable
    @Override
    public MediaType contentType() {
        // Returns the MIME type of the file.
        return MediaType.parse(mMimeType);
    }

    @Override
    public long contentLength() {
        // Returns the total size of the file.
        return mFile.length();
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        long fileLength = mFile.length();
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long uploaded = 0;
        // Handler to post updates to the main UI thread
        Handler handler = new Handler(Looper.getMainLooper());

        try (FileInputStream in = new FileInputStream(mFile)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                // Update the uploaded byte count
                uploaded += read;
                // Write the buffer to the sink
                sink.write(buffer, 0, read);

                // Post the progress update to the main thread
                final long finalUploaded = uploaded;
                handler.post(() -> mListener.onProgressUpdate(finalUploaded, fileLength));
            }
            // After the loop finishes, post the onFinish event
            handler.post(mListener::onFinish);
        } catch (IOException e) {
            // If an error occurs, post the onError event
            handler.post(() -> mListener.onError(e));
            // Re-throw the exception to allow Retrofit/OkHttp to handle it.
            throw e;
        }
    }
}
