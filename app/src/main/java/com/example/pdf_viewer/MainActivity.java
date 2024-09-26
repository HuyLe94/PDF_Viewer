package com.example.pdf_viewer;


import android.content.Intent;

import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.Manifest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_OPEN_DOCUMENT = 1;
    private static final int PICK_FOLDER_REQUEST_CODE = 42;
    private int currentPdfResId = R.raw.chapter_1;
    private int currentPdfIndex = 0; // Index of the currently loaded PDF
    private RecyclerView recyclerView;
    private PdfPageAdapter pdfPageAdapter;
    private List<Bitmap> pdfPages;
    private TextView logTextView;
    private TextView currentFileTextView;
    private static final int BATCH_SIZE = 10; // Number of items to load per batch
    private int currentBatchIndex = 0; // Track the current batch index
    private List<FolderFileManager.FileItem> allFileItems = new ArrayList<>(); // Store all file items
    private GestureDetector gestureDetector;
    private boolean canLoadPdf = true;
    private boolean isAtBottom = false;
    private Button showFolderButton;
    private ListView folderListView;

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
        currentFileTextView = findViewById(R.id.currentFileTextView);
        ListView fileListView = findViewById(R.id.fileListView);
        //ScrollView scrollView = findViewById(R.id.scrollView);
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        checkStoragePermission();
        // Set up the buttons
        //Button selectFileButton = findViewById(R.id.fileButton);
        //selectFileButton.setOnClickListener(v -> openFilePicker());

        loadJsonFiles();

        Button nextPdfButton = findViewById(R.id.nextPDF);
        nextPdfButton.setOnClickListener(v -> {
            if (canLoadPdf) {
                loadNextPdf();
            }
        });

        Button openFolderButton = findViewById(R.id.folderButton);
        openFolderButton.setOnClickListener(v -> openFolderPicker());

        Button deleteAllJsonButton = findViewById(R.id.deleteJson);
        deleteAllJsonButton.setOnClickListener(v -> deleteAllJsonFiles());
        //deleteAllJsonButton.setOnClickListener(v -> updateFolders());

        Button updateAllJsonButton = findViewById(R.id.updateJson);
        updateAllJsonButton.setOnClickListener(v ->  updateFolders());

        Button toggleFileListButton = findViewById(R.id.toggleFileListButton);
        toggleFileListButton.setOnClickListener(v -> {
            if (fileListView.getVisibility() == View.GONE) {
                fileListView.setVisibility(View.VISIBLE);
                toggleFileListButton.setText("Hide File List");
            } else {
                fileListView.setVisibility(View.GONE);
                toggleFileListButton.setText("Show File List");
            }
        });

        showFolderButton = findViewById(R.id.showFolder);
        folderListView = findViewById(R.id.folderListView);
        showFolderButton.setOnClickListener(v -> {
                    toggleFolderListVisibility();
                    fileListView.setVisibility(View.GONE);
                }
        );

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                // Check if we're at the bottom
                isAtBottom = !recyclerView.canScrollVertically(1); // 1 means down
            }
        });

        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (isAtBottom && (e1.getY() - e2.getY() > 200)) { // Swipe up threshold
                    loadNextPdf(); // Trigger loading the next PDF
                    isAtBottom = false;
                    return true; // Event consumed
                }
                return false; // Not consumed
            }
        });

        // When an item is clicked in folderListView
        folderListView.setOnItemClickListener((parent, view, position, id) -> {
            // Get the selected folder name from the list (which should be the JSON file name)
            String selectedJsonFile = (String) parent.getItemAtPosition(position);

            // Construct the JSON file path (assuming JSON files are stored in the app's internal files directory)
            String jsonFilePath = new File(getFilesDir(), selectedJsonFile).getAbsolutePath();

            // Load the file items from the JSON file
            allFileItems.clear();
            // Call the modified function to get the FolderFileManager instance
            // Call the modified function to get the FolderFileManager instance
            FolderFileManager folderFileManager = getInfoFromJsonFile2(jsonFilePath);

            // Now retrieve the list of FileItems from the FolderFileManager
            List<FolderFileManager.FileItem> allFileItems = folderFileManager.getFileItems();


            // Check if fileItems are null or empty and handle the case
            if (allFileItems != null && !allFileItems.isEmpty()) {
                // Pass the fileItems list to the function to display in the ListView
                pickChapterFromList(allFileItems);
                folderListView.setVisibility(View.GONE);
            } else {
                // Log or notify the user if no items are found
                //Log.d("FileItemsError", "No items found in the JSON file.");
                //Toast.makeText(this, "No items found in the selected folder", Toast.LENGTH_SHORT).show();
            }
        });

// Set a touch listener on the RecyclerView
        recyclerView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
    }

    private String getRealPathFromURI(Uri contentUri) {
        String[] projection = {MediaStore.Files.FileColumns.DATA};
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

    private void toggleFolderListVisibility() {
        if (folderListView.getVisibility() == View.GONE) {
            folderListView.setVisibility(View.VISIBLE);
        } else {
            folderListView.setVisibility(View.GONE);
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf");
        selectPdfLauncher.launch(Intent.createChooser(intent, "Select PDF"));
    }

    private void loadNextPdf() {

        if (!canLoadPdf) {
            return; // Prevent loading if not allowed
        }

        canLoadPdf = false;

        if (allFileItems.isEmpty() || currentPdfIndex == -1) {
            appendLogMessage("No PDFs loaded or invalid current index.");
            return;
        }

        // Increment the current index
        currentPdfIndex++;

        //appendLogMessage("Index after: "+currentPdfIndex);
        // Ensure current index is valid
        if (currentPdfIndex >= allFileItems.size()) {
            currentPdfIndex = 0; // Loop back to the first PDF
        }

        //appendLogMessage("Loading next PDF at index: " + currentPdfIndex);
        Uri nextPdfUri = allFileItems.get(currentPdfIndex).uri; // Get URI from FileItem
        loadPdf(nextPdfUri);

        // Log the current state
        //appendLogMessage("Current PDF URI: " + nextPdfUri.toString());
    }

    private void loadPdf(Uri uri) {

        try {
            // Open the PDF and render it
            ParcelFileDescriptor fileDescriptor = getContentResolver().openFileDescriptor(uri, "r");
            if (fileDescriptor != null) {
                PdfRenderer pdfRenderer = new PdfRenderer(fileDescriptor);
                pdfPages.clear();

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

                // Set the text view with the current file name
                String fileName = uri.getLastPathSegment(); // or any method to get the file name
                currentFileTextView.setText("Current File: " + fileName);

            }

        } catch (IOException e) {
            e.printStackTrace();
            //Toast.makeText(this, "Error loading PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        canLoadPdf = true;
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
                //Log.d("MainActivity", "Permission denied.");
            }
        }
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
                //saveSelectedFolder(folderUri.toString());

                // List the files in the selected folder
                readAndSaveFolderInfo(folderUri);
            }
        }
    }

    private String getPathFromUri(Uri uri) {
        String path = null;

        // Check if the URI is a file URI
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            path = uri.getPath(); // Directly get the path from URI
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            // Use a content resolver to query the URI
            String[] projection = {MediaStore.Files.FileColumns.DATA};

            try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA);
                    path = cursor.getString(columnIndex);
                }
            } catch (Exception e) {
                e.printStackTrace(); // Log the exception
            }
        }

        // Log the resulting path or failure
        if (path == null) {
            //appendLogMessage("Failed to get path from URI: " + uri.toString());
        } else {
            //appendLogMessage("Successfully retrieved path: " + path);
        }

        return path;
    }

    private void readAndSaveFolderInfo(Uri folderUri) {
        DocumentFile folder = DocumentFile.fromTreeUri(this, folderUri);
        if (folder != null && folder.isDirectory()) {
            // Clear previous entries
            allFileItems.clear();

            // Load all file items into the list
            for (DocumentFile file : folder.listFiles()) {
                if (file.isFile() && file.getName() != null && file.getName().endsWith(".pdf")) {
                    allFileItems.add(new FolderFileManager.FileItem(file.getName(), file.getUri())); // Use FolderFileManager.FileItem
                    //appendLogMessage("File Order: Adding file: " + file.getName());
                }
            }

            // Sort all file items using the method from FolderFileManager
            String folderName = folder.getName(); // Get the name of the folder
            FolderFileManager fileManager = new FolderFileManager(folderName, folderUri.toString()); // Use the actual folder name
            fileManager.sortFileItems(allFileItems); // Sort the file items

            // Prepare the data for JSON
            String folderPath = folderUri.toString(); // Get the folder URI as a string
            List<FolderFileManager.FileItem> itemsToSave = new ArrayList<>(allFileItems);

            // Save the folder information as JSON
            saveFolderInfoAsJson(folderName, folderPath, itemsToSave);
            loadJsonFiles();

        } else {
            //appendLogMessage("No files found in the directory.");
        }
    }

    private void saveFolderInfoAsJson(String folderName, String folderPath, List<FolderFileManager.FileItem> items) {
        // Create a JSONObject to hold folder information
        JSONObject folderInfo = new JSONObject();
        try {
            folderInfo.put("folderName", folderName);
            folderInfo.put("folderPath", folderPath);

            // Create a JSONArray to hold file items
            JSONArray filesArray = new JSONArray();
            for (FolderFileManager.FileItem item : items) {
                // Create a new JSONObject for each file item
                JSONObject fileObject = new JSONObject();

                // Put the file name into the JSON object
                fileObject.put("fileName", item.name);

                // Convert the URI to a String and log it before adding to the JSON object
                String uriString = item.uri.toString();

                // Add the URI to the JSON object
                fileObject.put("fileUri", uriString);

                filesArray.put(fileObject);
            }

            folderInfo.put("files", filesArray);

            // Save the JSON to a file in app storage
            File file = new File(getFilesDir(), folderName + "_info.json"); // File name can be customized
            FileWriter writer = new FileWriter(file);
            writer.write(folderInfo.toString());
            writer.flush();
            writer.close();

            appendLogMessage("Folder info saved to JSON: " + file.getAbsolutePath());
        } catch (JSONException | IOException e) {
            appendLogMessage("Error saving folder info to JSON: " + e.getMessage());
        }
    }

    private void deleteAllJsonFiles() {
        File dir = getFilesDir(); // Get the directory where JSON files are stored
        File[] jsonFiles = dir.listFiles((d, name) -> name.endsWith(".json")); // Filter for JSON files

        if (jsonFiles != null && jsonFiles.length > 0) {
            for (File jsonFile : jsonFiles) {
                if (jsonFile.delete()) {
                    appendLogMessage("Deleted JSON file: " + jsonFile.getName());
                } else {
                    //appendLogMessage("Failed to delete JSON file: " + jsonFile.getName());
                }
            }
            // Optionally, refresh the folder list view after deletion
            loadJsonFiles(); // Call your method to reload the list of JSON files
        } else {
            //appendLogMessage("No JSON files found to delete.");
        }
    }

    private List<FolderFileManager.FileItem> getInfoFromJsonFile(String jsonFilePath) {
        try {
            // Read the JSON file
            FileInputStream fis = new FileInputStream(jsonFilePath);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader bufferedReader = new BufferedReader(isr);
            StringBuilder stringBuilder = new StringBuilder();
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            // Close the resources
            bufferedReader.close();
            isr.close();
            fis.close();

            // Parse the JSON
            String jsonData = stringBuilder.toString();
            //Log.d("readingJsonItems", "JSON Data: " + jsonData); // Log the raw JSON data

            JSONObject jsonObject = new JSONObject(jsonData); // Try to create a JSONObject
            JSONArray jsonArray = jsonObject.getJSONArray("files"); // Get the "files" array

            //Log.d("readingJsonItems", "Enter reading 3");

            // Convert each JSON object to FileItem and add to the list
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject fileObject = jsonArray.getJSONObject(i);
                String name = fileObject.getString("fileName"); // Use correct key
                String uriString = fileObject.getString("fileUri"); // Use correct key
                Log.d("readingJsonItems", "File Item: " + name + " - " + uriString);
                Uri uri = Uri.parse(uriString);

                allFileItems.add(new FolderFileManager.FileItem(name, uri));
            }
        } catch (IOException e) {
            //Log.e("readingJsonItems", "IOException while reading JSON file: " + e.getMessage());
        } catch (JSONException e) {
            //Log.e("readingJsonItems", "JSONException while parsing JSON: " + e.getMessage());
        }
        return allFileItems;
    }

    private FolderFileManager getInfoFromJsonFile2(String jsonFilePath) {
        String folderName = ""; // Initialize folder name
        String folderPath = ""; // Initialize folder path
        FolderFileManager folderFileManager = new FolderFileManager(folderName, folderPath); // Create an instance of FolderFileManager

        try {
            // Read the JSON file
            FileInputStream fis = new FileInputStream(jsonFilePath);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader bufferedReader = new BufferedReader(isr);
            StringBuilder stringBuilder = new StringBuilder();
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            // Close the resources
            bufferedReader.close();
            isr.close();
            fis.close();

            // Parse the JSON
            String jsonData = stringBuilder.toString();
            JSONObject jsonObject = new JSONObject(jsonData); // Try to create a JSONObject

            // Retrieve folder information
            folderName = jsonObject.getString("folderName"); // Assuming this key exists
            folderPath = jsonObject.getString("folderPath"); // Assuming this key exists
            folderFileManager = new FolderFileManager(folderName, folderPath); // Update folder manager with name and path

            JSONArray jsonArray = jsonObject.getJSONArray("files"); // Get the "files" array

            // Convert each JSON object to FileItem and add to the list
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject fileObject = jsonArray.getJSONObject(i);
                String name = fileObject.getString("fileName"); // Use correct key
                String uriString = fileObject.getString("fileUri"); // Use correct key
                Uri uri = Uri.parse(uriString);
                folderFileManager.addFileItem(new FolderFileManager.FileItem(name, uri)); // Add FileItem to FolderFileManager
                allFileItems.add(new FolderFileManager.FileItem(name, uri));
            }
        } catch (IOException e) {
            // Handle exception
        } catch (JSONException e) {
            // Handle exception
        }

        return folderFileManager; // Return the FolderFileManager instance
    }


    private void pickChapterFromList(List<FolderFileManager.FileItem> fileItems) {

        // Set up the ListView
        ListView fileListView = findViewById(R.id.fileListView);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        fileListView.setAdapter(adapter);

        fileListView.setVisibility(View.VISIBLE);

        // Clear any previous items
        adapter.clear();

        // Add items to the adapter
        for (FolderFileManager.FileItem item : fileItems) {
            //appendLogMessage("FileItem"+ "Name: " + item.name + ", URI: " + item.uri);
            adapter.add(item.name);
        }


        // Continue loading more batches in the background if needed
        new Thread(() -> {
            while (currentBatchIndex < adapter.getCount()) {
                try {
                    Thread.sleep(500); // Optional delay for demonstration, adjust as needed
                    // You might want to implement batch loading if your list is large
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        // Set an item click listener
        fileListView.setOnItemClickListener((parent, view, position, id) -> {
            // Make sure the selected position is valid
            if (position < fileItems.size()) {
                Uri selectedFileUri = fileItems.get(position).uri; // Use position from the loaded adapter
                currentPdfIndex = position; // Update current index based on loaded items
                loadPdf(selectedFileUri); // Load the selected PDF
                fileListView.setVisibility(View.GONE);
            }
        });
    }

    private void listFilesInSelectedFolder(Uri folderUri) {
        // Use DocumentFile API to access files in the selected folder
        DocumentFile folder = DocumentFile.fromTreeUri(this, folderUri);
        if (folder != null && folder.isDirectory()) {
            // Clear previous entries
            allFileItems.clear();

            // Load all file items into the list
            for (DocumentFile file : folder.listFiles()) {
                if (file.isFile() && file.getName() != null && file.getName().endsWith(".pdf")) {
                    allFileItems.add(new FolderFileManager.FileItem(file.getName(), file.getUri())); // Update to use FolderFileManager.FileItem
                    appendLogMessage("File Order: Adding file: " + file.getName());
                }
            }

            // Sort all file items using the method from FolderFileManager
            String folderName = folder.getName(); // Get the name of the folder
            FolderFileManager fileManager = new FolderFileManager(folderName, folderUri.toString()); // Use the actual folder name
            fileManager.sortFileItems(allFileItems); // Sort the file items

            // Set up the ListView
            ListView fileListView = findViewById(R.id.fileListView);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
            fileListView.setAdapter(adapter);

            fileListView.setVisibility(View.VISIBLE);

            // Load the first batch immediately
            displayNextBatch(adapter);

            // Continue loading more batches in the background
            new Thread(() -> {
                while (currentBatchIndex < allFileItems.size()) {
                    try {
                        Thread.sleep(500); // Optional delay for demonstration, adjust as needed
                        displayNextBatch(adapter); // Load the next batch
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

            // Set an item click listener
            fileListView.setOnItemClickListener((parent, view, position, id) -> {
                // Make sure the selected position is valid
                if (position < allFileItems.size()) {
                    Uri selectedFileUri = allFileItems.get(position).uri; // Use position from the loaded adapter
                    currentPdfIndex = position; // Update current index based on loaded items
                    loadPdf(selectedFileUri); // Load the selected PDF
                    fileListView.setVisibility(View.GONE);
                }
            });

        } else {
            //appendLogMessage("No files found in the directory.");
        }
    }

    private void loadJsonFiles() {

        // Assuming the JSON files are stored in the app's internal storage
        File dir = getFilesDir(); // Get the internal storage directory
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json")); // Filter for JSON files

        // Clear the previous entries
        List<String> jsonFileNames = new ArrayList<>();

        if (files != null) {
            for (File file : files) {
                jsonFileNames.add(file.getName()); // Add the name of each JSON file
            }
        }
        // Set up the adapter for the ListView
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, jsonFileNames);
        ListView folderListView = findViewById(R.id.folderListView);
        folderListView.setAdapter(adapter);

    }

    private void displayNextBatch(ArrayAdapter<String> adapter) {
        // Retrieve the batch of FileItem objects from FolderFileManager
        List<FolderFileManager.FileItem> batch = getBatch();

        // Check if the batch is empty
        if (batch.isEmpty()) return;

        // Extract names for the current batch and add to the adapter
        runOnUiThread(() -> {
            for (FolderFileManager.FileItem item : batch) {
                adapter.add(item.name);
            }
            adapter.notifyDataSetChanged(); // Notify the adapter of data changes
        });

        // Increment the batch index for the next load
        currentBatchIndex += BATCH_SIZE;
    }

    private List<FolderFileManager.FileItem> getBatch() {
        int endIndex = Math.min(currentBatchIndex + BATCH_SIZE, allFileItems.size());
        return allFileItems.subList(currentBatchIndex, endIndex);
    }

    private List<DocumentFile> getFilesFromUri(Uri uri) {
        List<DocumentFile> fileList = new ArrayList<>();

        // Create a DocumentFile instance from the provided URI
        DocumentFile directory = DocumentFile.fromTreeUri(this, uri);

        if (directory != null && directory.isDirectory()) {
            // List all files in the directory
            for (DocumentFile file : directory.listFiles()) {
                fileList.add(file);
            }
        }

        return fileList;
    }


    private void updateFolders() {
        File dir = getFilesDir(); // Get the directory where JSON files are stored
        File[] jsonFiles = dir.listFiles((d, name) -> name.endsWith(".json")); // Filter for JSON files

        Log.d("Updating", "about to update");

        if (jsonFiles != null && jsonFiles.length > 0) {
            for (File jsonFile : jsonFiles) {
                Log.d("Updating", "Processing JSON file: " + jsonFile.getName());

                // Load existing file items from the JSON
                FolderFileManager folderInfo = getInfoFromJsonFile2(jsonFile.getAbsolutePath());
                Log.d("itemsINFO", "Name: " + folderInfo.getFolderName() + " Chapter: " + folderInfo.getFileItems() + " Path: " + folderInfo.getFolderPath());

                // Assuming folderInfo has the correct folder path stored as a URI
                Uri folderUri = Uri.parse(folderInfo.getFolderPath());

                // Now, access the directory using the URI
                if (folderUri != null) {
                    // Get the list of files in the directory
                    List<DocumentFile> filesInDirectory = getFilesFromUri(folderUri); // You need to implement this
                    Log.d("itemsINFO", "Found " + (filesInDirectory != null ? filesInDirectory.size() : 0) + " files in directory.");

                    // Get existing file names from folderInfo
                    List<String> existingFileNames = new ArrayList<>();
                    for (FolderFileManager.FileItem item : folderInfo.getFileItems()) {
                        existingFileNames.add(item.name); // Collect existing file names
                    }

                    // Check each file in the directory
                    if (filesInDirectory != null) {
                        for (DocumentFile file : filesInDirectory) {
                            String fileName = file.getName();
                            if (fileName != null && !existingFileNames.contains(fileName)) {
                                Log.d("itemsINFO", "New file found: " + fileName);
                                // Add the new file to folderInfo
                                folderInfo.addFileItem(new FolderFileManager.FileItem(fileName, file.getUri())); // Assuming you have a way to get the URI
                            } else {
                                Log.d("itemsINFO", "File already exists: " + fileName);
                            }
                        }
                        // Save updated folderInfo back to the JSON file using your method
                        saveFolderInfoAsJson(folderInfo.getFolderName(), folderInfo.getFolderPath(), folderInfo.getFileItems());
                    } else {
                        Log.w("itemsINFO", "No files found or an error occurred while accessing the directory.");
                    }
                } else {
                    Log.e("itemsINFO", "Invalid folder URI: " + folderInfo.getFolderPath());
                }
            }
        } else {
            Log.w("Updating", "No JSON files found for updating.");
        }
    }

}



