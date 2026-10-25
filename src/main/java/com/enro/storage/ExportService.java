package com.enro.storage;

import com.enro.core.Cancellation;
import java.nio.file.*;
import java.io.*;

public final class ExportService {
    private final Database db;
    public ExportService(Database db){this.db=db;}
    public void scanCsv(long scan,Path target,Cancellation token)throws Exception {
        Path temporary=Files.createTempFile(target.toAbsolutePath().getParent(),"export-",".csv");
        try(var out=Files.newBufferedWriter(temporary);var c=db.open();var p=c.prepareStatement("SELECT path,directory,size,files,category,modified FROM entries WHERE scan_id=? ORDER BY id")){
            out.write('\ufeff');out.write("path,directory,logical_bytes,file_count,category,modified_epoch_ms\r\n");p.setLong(1,scan);try(var r=p.executeQuery()){while(r.next()){token.check();out.write(csv(r.getString(1))+","+r.getInt(2)+","+r.getLong(3)+","+r.getLong(4)+","+csv(r.getString(5))+","+r.getLong(6)+"\r\n");}}
        }catch(Exception e){Files.deleteIfExists(temporary);throw e;}
        try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temporary);}
    }
    public void diagnostics(Path target)throws Exception {
        var text=new StringBuilder("Enro 1.1.0 diagnostics\nGenerated: "+java.time.Instant.now()+"\nJava: "+System.getProperty("java.version")+"\nOS: "+System.getProperty("os.name")+" "+System.getProperty("os.version")+"\nArchitecture: "+System.getProperty("os.arch")+"\nProcessors: "+Runtime.getRuntime().availableProcessors()+"\nMax heap: "+Runtime.getRuntime().maxMemory()+"\nPaths and filenames are excluded from this report.\n");
        for(var s:db.scans(20))text.append("scan=").append(s.id()).append(" status=").append(s.status()).append(" files=").append(s.files()).append(" errors=").append(s.errors()).append('\n');
        Files.writeString(target,text);
    }
    public static String csv(String value){if(value==null)return "\"\"";String s=value;if(!s.isEmpty()&&"=+-@\t\r\n".indexOf(s.charAt(0))>=0)s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
}
