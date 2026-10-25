package com.enro.cleanup;

import com.enro.core.Models.Snapshot;
import com.enro.windows.WindowsPaths;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

public final class SafetyPolicy {
    private final List<Path> protectedRoots;
    private final Path userTemp;
    public SafetyPolicy(){this(defaultProtected(),com.enro.windows.DriveService.userTemp());}
    public SafetyPolicy(List<Path> roots,Path temp){protectedRoots=roots.stream().map(SafetyPolicy::canonical).toList();userTemp=canonical(temp);}
    private static Path canonical(Path p){try{return p.toRealPath();}catch(IOException e){return p.toAbsolutePath().normalize();}}
    private static List<Path> defaultProtected(){var roots=new ArrayList<Path>();for(String key:List.of("SystemRoot","ProgramFiles","ProgramFiles(x86)","ProgramData")){var value=System.getenv(key);if(value!=null)roots.add(Path.of(value));}var home=Path.of(System.getProperty("user.home"));roots.add(home.resolve("AppData"));var dataHome=System.getProperty("enro.home");if(dataHome!=null)roots.add(Path.of(dataHome));else roots.add(home.resolve(".enro"));return roots;}
    public void validate(Snapshot snapshot,Path permittedRoot)throws IOException {
        var p=snapshot.file().toAbsolutePath().normalize();var root=permittedRoot.toAbsolutePath().normalize();
        if(p.equals(root)||!p.startsWith(root)||root.getParent()==null)throw new IOException("File is outside an explicitly permitted folder");
        WindowsPaths.requirePlainAncestors(root);WindowsPaths.requirePlainAncestors(p);
        Path real=p.toRealPath(),realRoot=root.toRealPath();
        if(!real.startsWith(realRoot)||real.equals(realRoot))throw new IOException("Canonical path outside permitted root");
        boolean tempException=realRoot.equals(userTemp)&&real.startsWith(userTemp);
        for(var protectedRoot:protectedRoots)if(real.startsWith(protectedRoot)&&!(tempException&&protectedRoot.getFileName()!=null&&protectedRoot.getFileName().toString().equalsIgnoreCase("AppData")))throw new IOException("Protected directory");
        for(var part:real){String s=part.toString().toLowerCase(Locale.ROOT);if(Set.of("$recycle.bin","system volume information","recovery","winsxs",".ssh",".aws",".gnupg",".git").contains(s))throw new IOException("Protected path component");}
        var now=Snapshot.capture(p);if(!snapshot.matches(now))throw new IOException("File changed since preview");
        if(!Files.isWritable(p)||!Files.isWritable(p.getParent()))throw new IOException("File is not writable with current privileges");
    }
}
