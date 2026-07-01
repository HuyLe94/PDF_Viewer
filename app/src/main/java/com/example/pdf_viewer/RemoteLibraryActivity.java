package com.example.pdf_viewer;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RemoteLibraryActivity extends AppCompatActivity {

    //private static final String BASE_URL = "http://10.0.2.2:5000";
    private static final String BASE_URL = "http://192.168.4.39:5000";

    private ListView remoteListView;
    private TextView remoteStatus;
    private TextView remoteTitle;

    private OkHttpClient client = new OkHttpClient();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    // Track current state
    private String currentManga = null;
    private List<String> currentChapters = new ArrayList<>();
    private int currentChapterIndex = 0;

    // Reader views
    private RecyclerView recyclerView;
    private PdfPageAdapter pdfPageAdapter;
    private List<Bitmap> pdfPages = new ArrayList<>();
    private Button nextChapterButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_library);

        remoteListView = findViewById(R.id.remoteListView);
        remoteStatus = findViewById(R.id.remoteStatus);
        remoteTitle = findViewById(R.id.remoteTitle);

        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        loadMangaList();
    }

    private void loadMangaList() {
        remoteStatus.setText("Loading manga list...");
        remoteTitle.setText("Remote Library");

        executor.execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(BASE_URL + "/manga")
                        .build();

                Response response = client.newCall(request).execute();
                String body = response.body().string();
                JSONObject json = new JSONObject(body);
                JSONArray mangaArray = json.getJSONArray("manga");

                List<String> mangaList = new ArrayList<>();
                for (int i = 0; i < mangaArray.length(); i++) {
                    mangaList.add(mangaArray.getString(i));
                }

                mainHandler.post(() -> {
                    remoteStatus.setText(mangaList.size() + " manga found");
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                            this,
                            android.R.layout.simple_list_item_1,
                            mangaList
                    ) {
                        @Override
                        public View getView(int position, View convertView, android.view.ViewGroup parent) {
                            View view = super.getView(position, convertView, parent);
                            TextView tv = view.findViewById(android.R.id.text1);
                            tv.setTextColor(android.graphics.Color.WHITE);
                            tv.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                            return view;
                        }
                    };
                    remoteListView.setAdapter(adapter);
                    remoteListView.setOnItemClickListener((parent, view, position, id) -> {
                        String selectedManga = mangaList.get(position);
                        loadChapterList(selectedManga);
                    });
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    remoteStatus.setText("Error: " + e.getMessage());
                    Toast.makeText(this, "Could not connect to server", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadChapterList(String mangaName) {
        currentManga = mangaName;
        remoteStatus.setText("Loading chapters...");
        remoteTitle.setText(mangaName);

        executor.execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(BASE_URL + "/manga/" + mangaName)
                        .build();

                Response response = client.newCall(request).execute();
                String body = response.body().string();
                JSONObject json = new JSONObject(body);
                JSONArray chaptersArray = json.getJSONArray("chapters");

                currentChapters.clear();
                for (int i = 0; i < chaptersArray.length(); i++) {
                    currentChapters.add(chaptersArray.getString(i));
                }

                mainHandler.post(() -> {
                    remoteStatus.setText(currentChapters.size() + " chapters");
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                            this,
                            android.R.layout.simple_list_item_1,
                            currentChapters
                    ) {
                        @Override
                        public View getView(int position, View convertView, android.view.ViewGroup parent) {
                            View view = super.getView(position, convertView, parent);
                            TextView tv = view.findViewById(android.R.id.text1);
                            tv.setTextColor(android.graphics.Color.WHITE);
                            tv.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                            return view;
                        }
                    };
                    remoteListView.setAdapter(adapter);
                    remoteListView.setOnItemClickListener((parent, view, position, id) -> {
                        currentChapterIndex = position;
                        loadChapter(currentManga, currentChapters.get(position));
                    });
                });

            } catch (Exception e) {
                mainHandler.post(() ->
                        remoteStatus.setText("Error loading chapters: " + e.getMessage()));
            }
        });
    }

    private void loadChapter(String mangaName, String chapterName) {
        remoteStatus.setText("Loading " + chapterName + "...");

        executor.execute(() -> {
            try {
                String url = BASE_URL + "/manga/" + mangaName + "/" + chapterName;
                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();

                // Save PDF bytes to a temp file
                InputStream inputStream = response.body().byteStream();
                File tempFile = new File(getCacheDir(), "temp_chapter.pdf");
                FileOutputStream fos = new FileOutputStream(tempFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                fos.close();
                inputStream.close();

                // Render PDF
                ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
                        tempFile, ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer pdfRenderer = new PdfRenderer(pfd);

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

                mainHandler.post(() -> showReader(chapterName));

            } catch (Exception e) {
                mainHandler.post(() ->
                        remoteStatus.setText("Error loading chapter: " + e.getMessage()));
            }
        });
    }

    private void showReader(String chapterName) {
        // Hide the list, show reader
        remoteListView.setVisibility(View.GONE);
        remoteStatus.setText("Reading: " + chapterName);

        // Use the existing recyclerView from layout or inflate reader view
        // For simplicity we launch a new intent to RemoteReaderActivity
        Intent intent = new Intent(this, RemoteReaderActivity.class);
        intent.putExtra("mangaName", currentManga);
        intent.putStringArrayListExtra("chapters", new ArrayList<>(currentChapters));
        intent.putExtra("chapterIndex", currentChapterIndex);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}