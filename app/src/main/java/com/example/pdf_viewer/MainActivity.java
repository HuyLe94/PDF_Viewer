package com.example.pdf_viewer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_OPEN_DOCUMENT = 1;
    private static final int PICK_FOLDER_REQUEST_CODE = 42;
    private int currentPdfResId = R.raw.chapter_1;
    private List<Uri> pdfUris = new ArrayList<>();  // List to hold selected PDF URIs
    private int currentPdfIndex = 0; // Index of the currently loaded PDF
    private TextView pdfListTextView;
    private RecyclerView recyclerView;
    private PdfPageAdapter pdfPageAdapter;
    private List<Bitmap> pdfPages;
    private TextView logTextView;


    // Create the ActivityResultLauncher for selecting a PDF
    private final ActivityResultLauncher<Intent> selectPdfLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        loadPdf(uri);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        pdfPages = new ArrayList<>();
        logTextView = findViewById(R.id.logTextView);



        // Check if the activity was started with an intent to view a PDF
        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri != null) {
                pdfUris.add(uri);  // Add the selected PDF URI to the list
                currentPdfIndex = 0; // Set current index to the first PDF
                loadPdf(uri); // Load the PDF directly
            }
        }

        checkStoragePermission();
        // Set up the buttons
        Button selectFileButton = findViewById(R.id.selectPDF);
        selectFileButton.setOnClickListener(v -> openFilePicker());

        Button nextPdfButton = findViewById(R.id.nextPDF);
        nextPdfButton.setOnClickListener(v -> loadNextPdf());


        openFolderPicker();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf");
        selectPdfLauncher.launch(Intent.createChooser(intent, "Select PDF"));
    }

    private void loadNextPdf() {
        if (pdfUris.isEmpty() || currentPdfIndex == -1) {
            appendLogMessage("No PDFs loaded or invalid current index.");
            return;
        }

        // Increment the current index
        currentPdfIndex++;
        if (currentPdfIndex >= pdfUris.size()) {
            currentPdfIndex = 0; // Loop back to the first PDF
        }

        appendLogMessage("Loading next PDF at index: " + currentPdfIndex);
        loadPdf(pdfUris.get(currentPdfIndex));
    }









    private void loadPdf(Uri uri) {

        try {
            // Open the PDF and render it
            ParcelFileDescriptor fileDescriptor = getContentResolver().openFileDescriptor(uri, "r");
            if (fileDescriptor != null) {
                PdfRenderer pdfRenderer = new PdfRenderer(fileDescriptor);
                pdfPages.clear();

                // Add the loaded PDF URI to the list
                if (!pdfUris.contains(uri)) {
                    pdfUris.add(uri);
                    currentPdfIndex = pdfUris.size() - 1; // Update the current index
                }

                for (int i = 0; i < pdfRenderer.getPageCount(); i++) {
                    PdfRenderer.Page page = pdfRenderer.openPage(i);

                    Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    pdfPages.add(bitmap);
                    page.close();
                }
                pdfRenderer.close();
                fileDescriptor.close();

                // Update the RecyclerView
                pdfPageAdapter = new PdfPageAdapter(pdfPages);
                recyclerView.setAdapter(pdfPageAdapter);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error loading PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


    private void appendLogMessage(String message) {
        logTextView.append(message + "\n");
    }

    private void checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        } else {
            // If permission is already granted, proceed with listing files
            //listFilesInDirectory();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); // Call the parent method first

        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //listFilesInDirectory();  // Permission granted, proceed with listing files
            } else {
                Log.d("MainActivity", "Permission denied.");
            }
        }
    }

    private void loadPdfsFromDirectory(Uri directoryUri) {
        File directory = new File(getRealPathFromURI(directoryUri));
        if (directory.isDirectory()) {
            File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf")); // Case insensitive

            if (files != null) {
                for (File file : files) {
                    appendLogMessage("Found file: " + file.getAbsolutePath());
                    pdfUris.add(Uri.fromFile(file));
                }
                appendLogMessage("Total PDFs available: " + pdfUris.size());
            } else {
                appendLogMessage("No PDF files found.");
            }
        } else {
            appendLogMessage("Selected path is not a directory.");
        }
    }

    private String getRealPathFromURI(Uri contentUri) {
        String[] projection = { MediaStore.Files.FileColumns.DATA };
        Cursor cursor = getContentResolver().query(contentUri, projection, null, null, null);
        if (cursor != null) {
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA);
            cursor.moveToFirst();
            String filePath = cursor.getString(columnIndex);
            cursor.close();
            return filePath;
        }
        return null;
    }


    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, PICK_FOLDER_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FOLDER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri folderUri = data.getData();
            if (folderUri != null) {
                getContentResolver().takePersistableUriPermission(folderUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                // Save the folder URI for future use
                saveSelectedFolder(folderUri.toString());

                // List the files in the selected folder
                listFilesInSelectedFolder(folderUri);
            }
        }
    }

    private void saveSelectedFolder(String folderUri) {
        SharedPreferences sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("selected_folder_uri", folderUri);
        editor.apply();
    }

    private String getSelectedFolder() {
        SharedPreferences sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE);
        return sharedPreferences.getString("selected_folder_uri", null);
    }

    private void listFilesInSelectedFolder(Uri folderUri) {
        // Use DocumentFile API to access files in the selected folder
        DocumentFile folder = DocumentFile.fromTreeUri(this, folderUri);
        if (folder != null && folder.isDirectory()) {
            List<String> fileNames = new ArrayList<>();
            List<Uri> fileUris = new ArrayList<>();

            for (DocumentFile file : folder.listFiles()) {
                if (file.isFile() && file.getName() != null && file.getName().endsWith(".pdf")) {
                    // Store file names and URIs
                    fileNames.add(file.getName());
                    fileUris.add(file.getUri());
                }
            }
            // Set up the ListView with an ArrayAdapter
            ListView fileListView = findViewById(R.id.fileListView);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, fileNames);
            fileListView.setAdapter(adapter);

            // Set an item click listener
            fileListView.setOnItemClickListener((parent, view, position, id) -> {
                // Get the URI of the clicked file
                Uri selectedFileUri = fileUris.get(position);
                // Call loadPdf() with the selected file's URI
                loadPdf(selectedFileUri);

            });

            //pdfUris.clear(); // Clear previous entries
            pdfUris.addAll(fileUris); // Add new entries

        } else {
            Log.d("MainActivity", "No files found in the directory.");
        }
    }


}
