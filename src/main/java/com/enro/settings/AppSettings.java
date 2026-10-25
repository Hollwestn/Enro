package com.enro.settings;

import java.util.ArrayList;
import java.util.List;

public final class AppSettings {
    public String theme = "Iris";
    public String appearance = "light";
    public int visualRevision = 0;
    public String language = "ru";
    public int parallelism = 2;
    public int maxEntries = 5_000_000;
    public int maxDepth = 256;
    public int throttleMillis = 0;
    public int lowSpacePercent = 10;
    public boolean notifications = true;
    public boolean scanOnStartup = false;
    public String startupRoot = "";
    public String logLevel = "INFO";
    public List<String> excludedDirectories = new ArrayList<>();
    public List<String> excludedExtensions = new ArrayList<>();
    public void validate() {
        if (!List.of("Iris","Obsidian Yellow","Jade","Arctic Silver","Crimson Noir","Monochrome").contains(theme)) throw new IllegalArgumentException("Invalid theme");
        if (!List.of("light","dark").contains(appearance)) throw new IllegalArgumentException("Invalid appearance");
        if (!List.of("ru","en").contains(language)) throw new IllegalArgumentException("Invalid language");
        if (parallelism < 1 || parallelism > 8 || maxEntries < 100 || maxEntries > 20_000_000 || maxDepth < 1 || maxDepth > 512 || throttleMillis < 0 || throttleMillis > 100 || lowSpacePercent < 1 || lowSpacePercent > 50) throw new IllegalArgumentException("Settings out of range");
        if (!List.of("INFO","WARN","ERROR","DEBUG").contains(logLevel)) throw new IllegalArgumentException("Invalid log level");
        if (excludedDirectories == null || excludedExtensions == null || startupRoot == null || excludedDirectories.size() > 1000 || excludedExtensions.size() > 1000) throw new IllegalArgumentException("Invalid exclusions");
        excludedDirectories.forEach(java.nio.file.Path::of);
        if (excludedExtensions.stream().anyMatch(e -> !e.matches("\\.[a-zA-Z0-9_-]{1,32}"))) throw new IllegalArgumentException("Extensions must start with a dot");
    }
}
