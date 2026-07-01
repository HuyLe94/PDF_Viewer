package com.example.pdf_viewer;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RemoteReaderActivity extends AppCompatActivity {

    private static final String BASE_URL = "http://192.168.4.39:5000";

    private RecyclerView recyclerView;
    private PdfPageAdapter pdfPageAdapter;
    private List<Bitmap> pdfPages = new ArrayList<>();
    private TextView currentFileTextView;

    private String mangaName;
    private ArrayList<String> chapters;
    private int currentChapterIndex;

    private boolean isAtBottom = false;

    private OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();

    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_reader);

        android.util.Log.d("RemoteReader", "onCreate called");

        recyclerView = findViewById(R.id.remoteRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        currentFileTextView = findViewById(R.id.remoteCurrentFile);

        mangaName = getIntent().getStringExtra("mangaName");
        chapters = getIntent().getStringArrayListExtra("chapters");
        currentChapterIndex = getIntent().getIntExtra("chapterIndex", 0);

        android.util.Log.d("RemoteReader", "mangaName: " + mangaName);
        android.util.Log.d("RemoteReader", "chapters size: " + (chapters != null ? chapters.size() : "null"));
        android.util.Log.d("RemoteReader", "chapterIndex: " + currentChapterIndex);

        // Track when user reaches bottom
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                isAtBottom = !recyclerView.canScrollVertically(1);
            }
        });

        // Swipe up gesture to load next chapter
        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (isAtBottom && (e1.getY() - e2.getY() > 200)) {
                    loadNextChapter();
                    isAtBottom = false;
                    return true;
                }
                return false;
            }
        });

        // Attach gesture detector to recyclerView
        recyclerView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));

        Button nextButton = findViewById(R.id.remoteNextButton);
        nextButton.setOnClickListener(v -> loadNextChapter());

        Button backButton = findViewById(R.id.remoteBackButton);
        backButton.setOnClickListener(v -> finish());

        if (chapters != null && !chapters.isEmpty()) {
            loadChapter(chapters.get(currentChapterIndex));
        } else {
            android.util.Log.e("RemoteReader", "chapters is null or empty!");
            currentFileTextView.setText("Error: no chapters received");
        }
    }

    private void loadNextChapter() {
        currentChapterIndex++;
        if (currentChapterIndex >= chapters.size()) {
            currentChapterIndex = 0;
        }
        loadChapter(chapters.get(currentChapterIndex));
    }

    private void loadChapter(String chapterName) {
        android.util.Log.d("RemoteReader", "loadChapter called: " + chapterName);
        currentFileTextView.setText("Loading: " + chapterName);
        Toast.makeText(this, "Loading...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try {
                String url = BASE_URL + "/manga/" + mangaName + "/" + chapterName;
                android.util.Log.d("RemoteReader", "Requesting: " + url);

                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();

                android.util.Log.d("RemoteReader", "Response code: " + response.code());
                android.util.Log.d("RemoteReader", "Content length: " + response.body().contentLength());

                if (!response.isSuccessful()) {
                    throw new Exception("Server error: " + response.code());
                }

                // Write to temp file
                File tempFile = new File(getCacheDir(), "temp_chapter.pdf");
                InputStream inputStream = response.body().byteStream();
                FileOutputStream fos = new FileOutputStream(tempFile);

                byte[] buffer = new byte[65536];
                int bytesRead;
                long totalBytes = 0;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                fos.flush();
                fos.close();
                inputStream.close();

                android.util.Log.d("RemoteReader", "Total bytes written: " + totalBytes);
                android.util.Log.d("RemoteReader", "Temp file size: " + tempFile.length());

                if (tempFile.length() == 0) {
                    throw new Exception("Downloaded file is empty");
                }

                // Render PDF
                ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
                        tempFile, ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer pdfRenderer = new PdfRenderer(pfd);

                android.util.Log.d("RemoteReader", "Page count: " + pdfRenderer.getPageCount());
                android.util.Log.d("RemoteReader", "pdfPages size before clear: " + pdfPages.size());

                pdfPages.clear();
                for (int i = 0; i < pdfRenderer.getPageCount(); i++) {
                    PdfRenderer.Page page = pdfRenderer.openPage(i);
                    Bitmap bitmap = Bitmap.createBitmap(
                            page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    pdfPages.add(bitmap);
                    page.close();
                }
                pdfRenderer.close();
                pfd.close();

                android.util.Log.d("RemoteReader", "pdfPages size after render: " + pdfPages.size());

                mainHandler.post(() -> {
                    android.util.Log.d("RemoteReader", "Setting adapter with pages: " + pdfPages.size());
                    pdfPageAdapter = new PdfPageAdapter(pdfPages);
                    recyclerView.setAdapter(pdfPageAdapter);
                    recyclerView.scrollToPosition(0);
                    isAtBottom = false; // reset when new chapter loads
                    currentFileTextView.setText("Reading: " + chapterName);
                });

            } catch (Exception e) {
                android.util.Log.e("RemoteReader", "Error loading chapter", e);
                mainHandler.post(() ->
                        currentFileTextView.setText("Error: " + e.getMessage()));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}