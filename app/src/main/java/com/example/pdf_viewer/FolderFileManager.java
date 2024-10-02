package com.example.pdf_viewer;

import android.net.Uri;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FolderFileManager {
    private String folderName;
    private String folderPath;
    private List<FileItem> fileItems;

    // Inner class for FileItem
    public static class FileItem {
        String name;
        Uri uri;

        public FileItem(String name, Uri uri) {
            this.name = name;
            this.uri = uri;
        }
    }

    // Constructor
    public FolderFileManager(String folderName, String folderPath) {
        this.folderName = folderName;
        this.folderPath = folderPath;
        this.fileItems = new ArrayList<>(); // Initialize the list
    }

    // Method to add a FileItem to the folder
    public void addFileItem(FileItem fileItem) {
        this.fileItems.add(fileItem);
    }

    // Method to get the list of FileItems
    public List<FileItem> getFileItems() {
        return fileItems;
    }

    // Static method to sort a list of FileItem objects
    public void sortFileItems(List<FileItem> fileItems) {
        Collections.sort(fileItems, Comparator.comparingDouble(FolderFileManager::extractNumber));
    }

    // Helper method to extract numeric value from file names
    // Helper method to extract numeric value from file names, including decimals
    private static double extractNumber(FileItem fileItem) {
        Pattern pattern = Pattern.compile("\\d+(\\.\\d+)?"); // Match whole numbers and decimals
        Matcher matcher = pattern.matcher(fileItem.name);
        return matcher.find() ? Double.parseDouble(matcher.group()) : Double.MAX_VALUE; // Return large number if no number found
    }


    // Additional methods can be added as needed (e.g., getters for folderName and folderPath)
    public String getFolderName() {
        return folderName;
    }

    public String getFolderPath() {
        return folderPath;
    }
}
