package com.enro.recovery;

import com.enro.core.Models.*;
import com.enro.cleanup.SafetyPolicy;
import com.enro.storage.Database;
import com.enro.windows.WindowsPaths;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

public final class OrganizerService {
    public record Move(Snapshot source,Path target,Path permittedRoot) {}
    private final Database db;private final SafetyPolicy policy;
    public OrganizerService(Database db,SafetyPolicy policy){this.db=db;this.policy=policy;}
    public void validate(Move move)throws IOException {
        policy.validate(move.source(),move.permittedRoot());var target=move.target().toAbsolutePath().normalize();
        if(!target.startsWith(move.permittedRoot().toAbsolutePath().normalize())||target.equals(move.source().file()))throw new IOException("Destination must be inside the permitted folder");
        WindowsPaths.requirePlainAncestors(target.getParent());if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new FileAlreadyExistsException(target.toString());
        if(!Files.getFileStore(move.source().file()).equals(Files.getFileStore(target.getParent())))throw new IOException("Only same-volume moves supported");
        String name=target.getFileName().toString();if(name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")||name.endsWith(".")||name.endsWith(" ")||name.contains(":"))throw new IOException("Invalid Windows filename");
    }
    public long executeConfirmed(Move move)throws Exception {
        validate(move);long id=db.operation(UUID.randomUUID().toString(),move.source(),move.target().toString(),"MOVE");
        try{validate(move);Files.move(move.source().file(),move.target());db.operationResult(id,"SUCCESS","Undo available if destination is unchanged");return id;}
        catch(Exception e){db.operationResult(id,"FAILED",e.getClass().getSimpleName());throw e;}
    }
    public void restore(Operation operation)throws Exception {
        if(!operation.method().equals("MOVE")||!operation.status().equals("SUCCESS"))throw new IOException("Only successful organizer moves support in-app undo");
        var snap=new Snapshot(operation.target(),operation.bytes(),operation.modified(),operation.created(),operation.identity());var source=Path.of(operation.source());
        Path common=source.getParent();while(common!=null&&!Path.of(operation.target()).startsWith(common))common=common.getParent();if(common==null||common.getParent()==null)throw new IOException("Invalid restore scope");
        executeConfirmed(new Move(snap,source,common));db.operationResult(operation.id(),"RESTORED","Original location restored");
    }
    public static Path renamed(Path source,String prefix,int sequence) {
        if(!prefix.matches("[\\p{L}\\p{N}_ -]{1,60}"))throw new IllegalArgumentException("Use letters, numbers, spaces, underscores or hyphens");
        return source.resolveSibling(prefix+String.format(Locale.ROOT,"_%03d",sequence)+com.enro.utils.Formats.extension(source.getFileName().toString()));
    }
}
