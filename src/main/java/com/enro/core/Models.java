package com.enro.core;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.IOException;
import java.util.List;

public final class Models {
    private Models() {}
    public record Entry(long id, long scanId, long parentId, String path, String name, boolean directory,
                        long size, long files, long modified, long created, String identity, String category) {
        public Path file() { return Path.of(path); }
        @Override public String toString() { return name; }
    }
    public record Scan(long id, String root, long started, long finished, String status, long bytes, long files, long errors) {}
    public record Category(String name, long bytes, long files) {}
    public record ScanIssue(String path,String kind) {}
    public record Snapshot(String path, long size, long modified, long created, String identity) {
        public static Snapshot capture(Path path) throws IOException {
            var p = path.toAbsolutePath().normalize();
            var a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!a.isRegularFile() || a.isSymbolicLink()) throw new IOException("Not an identifiable regular file");
            return new Snapshot(p.toString(), a.size(), a.lastModifiedTime().toMillis(), a.creationTime().toMillis(), com.enro.windows.WindowsPaths.identity(p,a));
        }
        public boolean matches(Snapshot other) { return size == other.size && modified == other.modified && created == other.created && identity.equals(other.identity); }
        public Path file() { return Path.of(path); }
    }
    public enum Risk { LOW, MEDIUM, HIGH, UNKNOWN }
    public record Candidate(Snapshot snapshot, String category, String reason, Risk risk, Path permittedRoot) {}
    public record DuplicateGroup(String hash, long size, List<Snapshot> copies) {
        public DuplicateGroup { copies = List.copyOf(copies); }
        public long redundantBytes() { return Math.multiplyExact(size, copies.size() - 1L); }
        public Snapshot keeper() { return copies.getFirst(); }
    }
    public record Operation(long id, String batch, long time, String source, String target, long bytes,
                            String method, String status, String detail, String identity, long modified, long created) {}
    public record Schedule(long id, String root, long intervalHours, long nextRun, boolean enabled) {}
    public record TaskRun(long id, long scheduleId, long started, String status, String detail) {}
}
