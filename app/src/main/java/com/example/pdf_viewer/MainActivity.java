package com.example.pdf_viewer;

import android.content.Intent;
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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_OPEN_DOCUMENT = 1;
    private int currentPdfResId = R.raw.chapter_1;
    private List<Uri> pdfUris = new ArrayList<>();  // List to hold selected PDF URIs
    private int currentPdfIndex = -1; // Index of the currently loaded PDF
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

        // Set up the buttons
        Button selectFileButton = findViewById(R.id.selectPDF);
        selectFileButton.setOnClickListener(v -> openFilePicker());

        Button nextPdfButton = findViewById(R.id.nextPDF);
        nextPdfButton.setOnClickListener(v -> loadNextPdf());

        listFilesInDirectory();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf");
        selectPdfLauncher.launch(Intent.createChooser(intent, "Select PDF"));
    }

    private void loadNextPdf() {
        if (pdfUris.isEmpty() || currentPdfIndex == -1) {
            appendLogMessage("NO next PDF");
            return;
        }

        currentPdfIndex = (currentPdfIndex + 1) % pdfUris.size(); // Cycle through the list
        loadPdf(pdfUris.get(currentPdfIndex));
    }




    private void loadPdf(Uri uri) {
        // Convert content URI to file path
        String parentDir = getRealPathFromURI(uri);  // Assuming getRealFilePath(uri) is implemented correctly

        if (parentDir != null) {
            appendLogMessage("LoadPDF: File Selected: " + parentDir);
        } else {
            appendLogMessage("Selected PDF URI: " + uri.toString());
        }


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

                // Find and store the next PDF using the actual file path (parentDir)
                findAndStoreNextPdf(uri);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error loading PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }




    private void updatePdfListDisplay() {
        StringBuilder pdfList = new StringBuilder("PDF List:\n");
        for (Uri uri : pdfUris) {
            pdfList.append(uri.getLastPathSegment()).append("\n");
        }
        pdfListTextView.setText(pdfList.toString());
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

    private void findAndStoreNextPdf(Uri currentUri) {
        String currentFilePath = getRealPathFromURI(currentUri);
        if (currentFilePath == null) {
            appendLogMessage("Error retrieving file path.");
            return;
        }

        File currentFile = new File(currentFilePath);
        File parentDir = currentFile.getParentFile();

        if (parentDir != null) {
            appendLogMessage("findNEXT: Parent Directory: " + parentDir.getAbsolutePath() );

            // Get all PDF files in the directory
            File[] files = parentDir.listFiles((dir, name) -> true);

            if (files != null && files.length > 0) {
                appendLogMessage("PDF Files found: " + files.length);

                for (File file : files) {
                    appendLogMessage("File: " + file.getAbsolutePath());
                }

                Arrays.sort(files);

                for (int i = 0; i < files.length; i++) {
                    if (files[i].equals(currentFile)) {
                        if (i + 1 < files.length) {
                            Uri nextUri = Uri.fromFile(files[i + 1]);
                            pdfUris.add(nextUri);
                            //updatePdfListView();
                            return;
                        } else {
                            appendLogMessage("No next PDF found.");
                        }
                    }
                }
            } else {
                appendLogMessage("No PDF files found in the directory.");
            }
        } else {
            appendLogMessage("Error finding parent directory.");
        }
    }
    private void appendLogMessage(String message) {
        logTextView.append(message + "\n");
    }

    private void listFilesInDirectory() {
        //String directoryPath = "/storage/emulated/0/vo-tan-thon-phe"; // Replace with your absolute path
        String directoryPath = "/Internal storage/vo-tan-thon-phe"; // Replace with your absolute path

        File directory = new File(directoryPath);

        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();

            if (files != null && files.length > 0) {
                StringBuilder fileNames = new StringBuilder();
                for (File file : files) {
                    fileNames.append(file.getName()).append("\n");
                }

                // Update the TextView with the file names
                TextView textView = findViewById(R.id.logTextView); // Replace with your TextView ID
                textView.setText(fileNames.toString());
            } else {
                appendLogMessage("No files found in the directory.");
            }
        } else {
            appendLogMessage("Invalid directory path.");
        }
    }


}
