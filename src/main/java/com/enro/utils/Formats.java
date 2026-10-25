package com.enro.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Formats {
    private Formats() {}
    public static String bytes(long n) {
        if (n < 1024) return n + " B";
        int unit = Math.min(6, (int)(Math.log(n) / Math.log(1024)));
        return String.format(Locale.ROOT, "%.1f %s", n / Math.pow(1024, unit), new String[]{"B","KiB","MiB","GiB","TiB","PiB","EiB"}[unit]);
    }
    public static String date(long millis) { return millis <= 0 ? "—" : DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis)); }
    public static String extension(String name) { int i = name.lastIndexOf('.'); return i < 0 ? "" : name.substring(i).toLowerCase(Locale.ROOT); }
    public static String category(String name) {
        return switch (extension(name)) {
            case ".mp4", ".mkv", ".avi", ".mov", ".webm" -> "Video";
            case ".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic", ".svg", ".raw" -> "Images";
            case ".mp3", ".wav", ".flac", ".aac", ".ogg" -> "Audio";
            case ".zip", ".7z", ".rar", ".gz", ".tar", ".iso" -> "Archives";
            case ".pdf", ".docx", ".xlsx", ".pptx", ".txt", ".md", ".csv", ".odt" -> "Documents";
            case ".exe", ".msi", ".dll", ".jar", ".msix" -> "Programs";
            case ".sys", ".drv" -> "System";
            default -> "Other";
        };
    }
}
