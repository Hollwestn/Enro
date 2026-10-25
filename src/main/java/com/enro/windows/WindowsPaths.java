package com.enro.windows;

import com.sun.jna.platform.win32.Kernel32;
import java.nio.file.*;
import java.io.IOException;
import java.util.Locale;

public final class WindowsPaths {
    public static final boolean WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    private WindowsPaths() {}
    public static boolean redirected(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) return true;
        if (WINDOWS) {
            int attributes = Kernel32.INSTANCE.GetFileAttributes(longPath(path));
            if (attributes == -1) throw new IOException("Cannot inspect filesystem attributes: " + Kernel32.INSTANCE.GetLastError());
            return (attributes & 0x400) != 0;
        }
        return false;
    }
    public static void requirePlainAncestors(Path input) throws IOException {
        var path = input.toAbsolutePath().normalize();
        for (Path p = path; p != null; p = p.getParent()) if (redirected(p)) throw new IOException("Redirected path refused");
    }
    public static String identity(Path path,java.nio.file.attribute.BasicFileAttributes attributes)throws IOException {
        if(attributes.fileKey()!=null)return attributes.fileKey().toString();
        if(!WINDOWS)throw new IOException("Filesystem does not expose file identity");
        var handle=Kernel32.INSTANCE.CreateFile(longPath(path),0x80,7,null,3,0x00200000|0x02000000,null);
        if(com.sun.jna.platform.win32.WinBase.INVALID_HANDLE_VALUE.equals(handle))throw new IOException("Cannot open file identity: "+Kernel32.INSTANCE.GetLastError());
        try(var info=new com.sun.jna.Memory(24)){
            if(!Kernel32.INSTANCE.GetFileInformationByHandleEx(handle,18,info,new com.sun.jna.platform.win32.WinDef.DWORD(24)))throw new IOException("Cannot read file identity: "+Kernel32.INSTANCE.GetLastError());
            return Long.toUnsignedString(info.getLong(0),16)+":"+java.util.HexFormat.of().formatHex(info.getByteArray(8,16));
        }finally{Kernel32.INSTANCE.CloseHandle(handle);}
    }
    public static String longPath(Path path) {
        String s = path.toAbsolutePath().normalize().toString();
        if (!WINDOWS || s.startsWith("\\\\?\\")) return s;
        return s.startsWith("\\\\") ? "\\\\?\\UNC\\" + s.substring(2) : "\\\\?\\" + s;
    }
}
