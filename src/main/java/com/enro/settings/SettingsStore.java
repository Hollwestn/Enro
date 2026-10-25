package com.enro.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.io.IOException;

public final class SettingsStore {
    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper();
    public SettingsStore(Path path) { this.path = path; }
    public AppSettings load() throws IOException {
        if (!Files.exists(path)) return new AppSettings();
        return read(path);
    }
    public AppSettings read(Path source) throws IOException {
        if (Files.size(source) > 1_048_576) throw new IOException("Settings file exceeds 1 MiB");
        var settings = mapper.readValue(source.toFile(), AppSettings.class); settings.validate(); return settings;
    }
    public void save(AppSettings settings) throws IOException { write(path, settings); }
    public void write(Path target, AppSettings settings) throws IOException {
        settings.validate(); Files.createDirectories(target.toAbsolutePath().getParent());
        var temp = Files.createTempFile(target.toAbsolutePath().getParent(), "settings-", ".json");
        try { mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), settings); Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temp); }
    }
}
