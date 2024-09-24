package com.example.pdf_viewer;

import android.net.Uri;

import android.net.Uri;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileItem {
    String name;
    Uri uri;

    public FileItem(String name, Uri uri) {
        this.name = name;
        this.uri = uri;
    }

    // Static method to sort a list of FileItem objects
    public static void sortFileItems(List<FileItem> fileItems) {
        Collections.sort(fileItems, Comparator.comparingInt(FileItem::extractNumber));
    }

    // Helper method to extract numeric value from file names
    private static int extractNumber(FileItem fileItem) {
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(fileItem.name);
        return matcher.find() ? Integer.parseInt(matcher.group()) : Integer.MAX_VALUE; // Return a large number if no number found
    }
}
