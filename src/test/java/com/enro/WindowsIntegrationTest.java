package com.enro;

import com.enro.scanner.*;
import com.enro.core.Cancellation;
import com.enro.storage.Database;
import com.enro.windows.RecycleBin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="enro.nativeTests",matches="true")
class WindowsIntegrationTest {
    @TempDir Path temp;
    @Test void junctionSkippedAndRootRejected()throws Exception {
        var root=Files.createDirectory(temp.resolve("scan"));var outside=Files.createDirectory(temp.resolve("target"));Files.writeString(outside.resolve("keep.txt"),"keep");var link=root.resolve("junction");
        var process=new ProcessBuilder("cmd.exe","/d","/c","mklink","/J",link.toString(),outside.toString()).redirectErrorStream(true).start();String output=new String(process.getInputStream().readAllBytes());assertEquals(0,process.waitFor(),output);
        try{var db=new Database(temp.resolve("store.db"));var scanner=new FileScanner(db);var options=new ScanOptions(100,10,0,List.of(),Set.of());var result=scanner.scan(root,options,new Cancellation(),p->{});assertEquals(0,result.files());assertEquals("PARTIAL",result.status());assertThrows(java.io.IOException.class,()->scanner.scan(link,options,new Cancellation(),p->{}));assertEquals("keep",Files.readString(outside.resolve("keep.txt")));}finally{Files.deleteIfExists(link);}
    }
    @Test void recycleAndRestoreOnlyGeneratedFixture()throws Exception {
        String name="Enro-QA-"+UUID.randomUUID()+".txt";Path file=Files.writeString(temp.resolve(name),"Enro native recycle fixture");new RecycleBin().recycle(file);assertFalse(Files.exists(file));
        String command="$shell=New-Object -ComObject Shell.Application; $bin=$shell.NameSpace(10); $matches=@($bin.Items() | Where-Object { $_.Name -eq '"+name+"' }); if($matches.Count -ne 1){exit 2}; $matches[0].InvokeVerb('undelete');";
        var restore=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command",command).redirectErrorStream(true).start();String output=new String(restore.getInputStream().readAllBytes());assertEquals(0,restore.waitFor(),output);
        for(int i=0;i<50&&!Files.exists(file);i++)Thread.sleep(100);assertTrue(Files.exists(file),"Generated fixture must be restored from the actual Windows Recycle Bin");assertEquals("Enro native recycle fixture",Files.readString(file));
    }
    @Test void lockedContentStillAllowsMetadataButHashReadFails()throws Exception {
        var root=Files.createDirectory(temp.resolve("scan"));var file=Files.writeString(root.resolve("locked.txt"),"fixture");var handle=com.sun.jna.platform.win32.Kernel32.INSTANCE.CreateFile(file.toString(),0x80000000,0,null,3,0,null);assertNotEquals(com.sun.jna.platform.win32.WinBase.INVALID_HANDLE_VALUE,handle);
        try{var db=new Database(temp.resolve("db"));var result=new FileScanner(db).scan(root,new ScanOptions(100,10,0,List.of(),Set.of()),new Cancellation(),p->{});assertEquals("COMPLETE",result.status());assertEquals(1,result.files());assertEquals(0,result.errors());var snapshot=com.enro.core.Models.Snapshot.capture(file);assertThrows(java.io.IOException.class,()->com.enro.duplicates.DuplicateFinder.hash(snapshot,false,new Cancellation()));}finally{com.sun.jna.platform.win32.Kernel32.INSTANCE.CloseHandle(handle);}
        assertEquals("fixture",Files.readString(file));
    }
    @Test void accessDeniedDirectoryIsRecordedWithoutBypass()throws Exception {
        var root=Files.createDirectory(temp.resolve("scan"));Files.writeString(root.resolve("allowed.txt"),"ok");var blocked=Files.createDirectory(root.resolve("blocked"));Files.writeString(blocked.resolve("private.txt"),"private fixture");var view=Files.getFileAttributeView(blocked,java.nio.file.attribute.AclFileAttributeView.class);var original=view.getAcl();
        var deny=java.nio.file.attribute.AclEntry.newBuilder().setType(java.nio.file.attribute.AclEntryType.DENY).setPrincipal(Files.getOwner(blocked)).setPermissions(java.nio.file.attribute.AclEntryPermission.READ_DATA).build();var acl=new ArrayList<>(original);acl.addFirst(deny);
        try{view.setAcl(acl);var db=new Database(temp.resolve("db"));var result=new FileScanner(db).scan(root,new ScanOptions(100,10,0,List.of(),Set.of()),new Cancellation(),p->{});assertEquals("PARTIAL",result.status());assertEquals(1,result.files());assertTrue(db.scanIssues(result.scanId()).stream().anyMatch(i->i.kind().contains("AccessDenied")));}finally{view.setAcl(original);}
        assertEquals("private fixture",Files.readString(blocked.resolve("private.txt")));
    }
}
