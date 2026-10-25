package com.enro.windows;

import java.nio.file.*;
import java.util.*;
import java.io.IOException;

public final class DriveService {
    public record Drive(Path root,String name,String filesystem,long total,long free,long usable,String error){public long used(){return Math.max(0,total-free);}public double usage(){return total<=0?0:(double)used()/total;}}
    public List<Drive> drives(){var out=new ArrayList<Drive>();for(var root:FileSystems.getDefault().getRootDirectories())try{var fs=Files.getFileStore(root);long total=fs.getTotalSpace();if(total>0)out.add(new Drive(root,fs.name(),fs.type(),total,fs.getUnallocatedSpace(),fs.getUsableSpace(),""));}catch(IOException|SecurityException e){out.add(new Drive(root,"","",0,0,0,e.getClass().getSimpleName()));}return out;}
    public static Path userTemp(){if(WindowsPaths.WINDOWS)try{return Path.of(com.sun.jna.platform.win32.Shell32Util.getKnownFolderPath(com.sun.jna.platform.win32.KnownFolders.FOLDERID_LocalAppData),"Temp");}catch(RuntimeException ignored){}return Path.of(System.getProperty("user.home"),"AppData","Local","Temp");}
    public Path downloads(){if(WindowsPaths.WINDOWS)try{return Path.of(com.sun.jna.platform.win32.Shell32Util.getKnownFolderPath(com.sun.jna.platform.win32.KnownFolders.FOLDERID_Downloads));}catch(RuntimeException ignored){}return Path.of(System.getProperty("user.home"),"Downloads");}
}
