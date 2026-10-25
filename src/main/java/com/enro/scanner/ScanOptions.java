package com.enro.scanner;

import com.enro.settings.AppSettings;
import java.nio.file.*;
import java.util.*;

public record ScanOptions(int maxEntries,int maxDepth,int throttleMillis,List<Path> exclusions,Set<String> extensions) {
    public ScanOptions { if(maxEntries<1||maxDepth<1||throttleMillis<0)throw new IllegalArgumentException();exclusions=exclusions.stream().map(ScanOptions::canonical).toList();extensions=Set.copyOf(extensions); }
    private static Path canonical(Path p){try{return p.toRealPath();}catch(java.io.IOException e){return p.toAbsolutePath().normalize();}}
    public static ScanOptions from(AppSettings s) {var excluded=new ArrayList<>(s.excludedDirectories.stream().map(Path::of).toList());String data=System.getProperty("enro.home");if(data!=null)excluded.add(Path.of(data));return new ScanOptions(s.maxEntries,s.maxDepth,s.throttleMillis,excluded,new HashSet<>(s.excludedExtensions.stream().map(e->e.toLowerCase(Locale.ROOT)).toList()));}
    public boolean excludes(Path p) {return exclusions.stream().anyMatch(p.toAbsolutePath().normalize()::startsWith);}
}
