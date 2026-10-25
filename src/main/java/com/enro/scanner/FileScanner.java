package com.enro.scanner;

import com.enro.core.Cancellation;
import com.enro.storage.Database;
import com.enro.utils.Formats;
import com.enro.windows.WindowsPaths;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

public final class FileScanner {
    public record Progress(long entries,long files,long bytes,long errors) {}
    public record Result(long scanId,String status,long files,long bytes,long errors) {}
    private final Database db;
    public FileScanner(Database db) {this.db=db;}
    public Result scan(Path input,ScanOptions options,Cancellation token,Consumer<Progress> progress) throws Exception {
        Path root=input.toAbsolutePath().normalize();WindowsPaths.requirePlainAncestors(root);root=root.toRealPath();
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Select an accessible directory");
        long scan=db.createScan(root);var state=new State();String status="COMPLETE";
        try(var c=db.open();var insert=c.prepareStatement("INSERT INTO entries(scan_id,parent_id,path,name,directory,size,files,modified,created,identity,category) VALUES(?,?,?,?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);var aggregate=c.prepareStatement("UPDATE entries SET size=?,files=? WHERE id=?");var error=c.prepareStatement("INSERT INTO scan_errors VALUES(?,?,?)")){
            c.setAutoCommit(false);var stack=new ArrayDeque<Frame>();
            class Walker extends SimpleFileVisitor<Path> {
                void check() throws IOException {
                    token.check();if(state.entries>=options.maxEntries())throw new LimitReached();
                    if(state.entries%1000==0){try{c.commit();}catch(SQLException e){throw new IOException(e);}progress.accept(new Progress(state.entries,state.files,state.bytes,state.errors));
                        if(options.throttleMillis()>0)try{Thread.sleep(options.throttleMillis());}catch(InterruptedException e){Thread.currentThread().interrupt();token.check();}}
                }
                void issue(Path p,String kind) throws IOException {state.errors++;if(state.errors<=10000)try{error.setLong(1,scan);error.setString(2,p.toString());error.setString(3,kind);error.executeUpdate();}catch(SQLException e){throw new IOException(e);}}
                long add(Path p,BasicFileAttributes a,boolean dir) throws IOException {
                    try {insert.setLong(1,scan);insert.setLong(2,stack.isEmpty()?0:stack.peek().id);insert.setString(3,p.toString());insert.setString(4,p.getFileName()==null?p.toString():p.getFileName().toString());insert.setBoolean(5,dir);insert.setLong(6,dir?0:a.size());insert.setLong(7,dir?0:1);insert.setLong(8,a.lastModifiedTime().toMillis());insert.setLong(9,a.creationTime().toMillis());insert.setString(10,dir?"":WindowsPaths.identity(p,a));insert.setString(11,dir?"Folder":Formats.category(p.toString()));insert.executeUpdate();state.entries++;
                        if(dir)try(var r=insert.getGeneratedKeys()){r.next();return r.getLong(1);}return 0;
                    }catch(SQLException e){throw new IOException(e);}
                }
                @Override public FileVisitResult preVisitDirectory(Path p,BasicFileAttributes a)throws IOException {
                    check();if(options.excludes(p))return FileVisitResult.SKIP_SUBTREE;
                    try{if(a.isSymbolicLink()||WindowsPaths.redirected(p)){issue(p,"REPARSE_SKIPPED");return FileVisitResult.SKIP_SUBTREE;}}catch(IOException e){issue(p,"DIRECTORY_ATTRIBUTES_UNAVAILABLE");return FileVisitResult.SKIP_SUBTREE;}
                    stack.push(new Frame(add(p,a,true)));return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path p,BasicFileAttributes a)throws IOException {
                    check();if(options.excludes(p)||options.extensions().contains(Formats.extension(p.toString())))return FileVisitResult.CONTINUE;
                    try {if(a.isSymbolicLink()||a.isOther()||WindowsPaths.redirected(p)){issue(p,"REPARSE_SKIPPED");return FileVisitResult.CONTINUE;}}catch(IOException e){issue(p,"ATTRIBUTES_UNAVAILABLE");return FileVisitResult.CONTINUE;}
                    if(a.isDirectory()){issue(p,"DEPTH_LIMIT");state.limited=true;return FileVisitResult.CONTINUE;}
                    if(a.isRegularFile()){try{add(p,a,false);}catch(IOException e){if(e.getCause() instanceof SQLException)throw e;issue(p,e.getClass().getSimpleName());return FileVisitResult.CONTINUE;}state.files++;state.bytes=Math.addExact(state.bytes,a.size());if(!stack.isEmpty()){stack.peek().bytes=Math.addExact(stack.peek().bytes,a.size());stack.peek().files++;}}
                    return FileVisitResult.CONTINUE;
                }
                void finishFrame()throws IOException {var f=stack.pop();try{aggregate.setLong(1,f.bytes);aggregate.setLong(2,f.files);aggregate.setLong(3,f.id);aggregate.executeUpdate();}catch(SQLException e){throw new IOException(e);}if(!stack.isEmpty()){stack.peek().bytes=Math.addExact(stack.peek().bytes,f.bytes);stack.peek().files+=f.files;}}
                @Override public FileVisitResult postVisitDirectory(Path p,IOException e)throws IOException {if(e!=null)issue(p,e.getClass().getSimpleName());finishFrame();return FileVisitResult.CONTINUE;}
                @Override public FileVisitResult visitFileFailed(Path p,IOException e)throws IOException {token.check();issue(p,e.getClass().getSimpleName());return FileVisitResult.CONTINUE;}
            }
            var walker=new Walker();
            try{Files.walkFileTree(root,EnumSet.noneOf(FileVisitOption.class),options.maxDepth(),walker);if(state.limited)status="LIMIT_REACHED";else if(state.errors>0)status="PARTIAL";}
            catch(CancellationException e){status="CANCELLED";}
            catch(LimitReached e){status="LIMIT_REACHED";}
            catch(Exception e){status="FAILED";walker.issue(root,e.getClass().getSimpleName());}
            while(!stack.isEmpty())walker.finishFrame();c.commit();
        }catch(Exception e){db.finishScan(scan,"FAILED",state.bytes,state.files,state.errors+1);throw e;}
        db.finishScan(scan,status,state.bytes,state.files,state.errors);progress.accept(new Progress(state.entries,state.files,state.bytes,state.errors));
        org.slf4j.LoggerFactory.getLogger(FileScanner.class).info("Scan {}: {}, {} files, {} errors",scan,status,state.files,state.errors);
        return new Result(scan,status,state.files,state.bytes,state.errors);
    }
    private static final class Frame {final long id;long bytes,files;Frame(long id){this.id=id;}}
    private static final class State {long entries,files,bytes,errors;boolean limited;}
    private static final class LimitReached extends IOException {}
}
