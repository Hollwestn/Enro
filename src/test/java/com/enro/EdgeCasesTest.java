package com.enro;

import com.enro.core.*;
import com.enro.core.Models.*;
import com.enro.scanner.*;
import com.enro.storage.*;
import com.enro.cleanup.*;
import com.enro.duplicates.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EdgeCasesTest {
    @TempDir Path temp;
    @Test void cancelMidScanKeepsValidAggregation()throws Exception {
        var root=Files.createDirectory(temp.resolve("fixture"));for(int i=0;i<1200;i++)Files.writeString(root.resolve("f"+i),"abc");var db=new Database(temp.resolve("db"));var token=new Cancellation();
        var s=new FileScanner(db).scan(root,new ScanOptions(10000,10,0,List.of(),Set.of()),token,p->{if(p.files()>0)token.cancel();});assertEquals("CANCELLED",s.status());assertTrue(s.files()>0&&s.files()<1200);assertEquals(s.files()*3,s.bytes());assertEquals(s.bytes(),db.root(s.scanId()).size());
    }
    @Test void emptyFilesAreNotSpaceSavings()throws Exception {
        var root=Files.createDirectory(temp.resolve("fixture"));Files.createFile(root.resolve("a"));Files.createFile(root.resolve("b"));var db=new Database(temp.resolve("db"));var s=new FileScanner(db).scan(root,new ScanOptions(100,10,0,List.of(),Set.of()),new Cancellation(),p->{});assertEquals(2,s.files());assertTrue(new DuplicateFinder(db).find(s.scanId(),new Cancellation(),p->{}).groups().isEmpty());
    }
    @Test void interruptedIntentNeverBecomesSuccess()throws Exception {
        var path=temp.resolve("db");var db=new Database(path);var snap=Snapshot.capture(Files.writeString(temp.resolve("a"),"x"));db.operation("batch",snap,"target","MOVE");var reopened=new Database(path);assertEquals("INTERRUPTED",reopened.operations(1,0).getFirst().status());
    }
    @Test void protectedTempDoesNotOverrideWindowsRoot()throws Exception {
        var windows=Files.createDirectory(temp.resolve("Windows"));var userTemp=Files.createDirectory(windows.resolve("Temp"));var file=Files.writeString(userTemp.resolve("a"),"x");var policy=new SafetyPolicy(List.of(windows),userTemp);assertThrows(java.io.IOException.class,()->policy.validate(Snapshot.capture(file),userTemp));
    }
    @Test void csvExportIncludesWholeScan()throws Exception {
        var root=Files.createDirectory(temp.resolve("fixture"));Files.writeString(root.resolve("=test.txt"),"abc");var db=new Database(temp.resolve("db"));var s=new FileScanner(db).scan(root,new ScanOptions(100,10,0,List.of(),Set.of()),new Cancellation(),p->{});var output=temp.resolve("scan.csv");new ExportService(db).scanCsv(s.scanId(),output,new Cancellation());assertEquals(3,Files.readAllLines(output).size());assertTrue(Files.readString(output).contains("logical_bytes"));
    }
    @Test void diagnosticsOmitPersonalPaths()throws Exception {
        var db=new Database(temp.resolve("db"));db.createScan(temp.resolve("private-user-secret"));var output=temp.resolve("report.txt");new ExportService(db).diagnostics(output);assertFalse(Files.readString(output).contains("private-user-secret"));assertFalse(Files.readString(output).contains(temp.toString()));
    }
    @Test void dueAutomationReallyScansAndRecordsHistory()throws Exception {
        var root=Files.createDirectory(temp.resolve("scheduled"));Files.writeString(root.resolve("a"),"abc");var db=new Database(temp.resolve("db"));db.addSchedule(root,24);var s=db.schedules().getFirst();db.update("UPDATE schedules SET next_run=0 WHERE id=?",s.id());var messages=new ArrayList<String>();
        try(var service=new com.enro.automation.AutomationService(db,()->new ScanOptions(100,10,0,List.of(),Set.of()),messages::add)){service.tick();}
        assertEquals(1,db.scans(10).size());assertEquals(3,db.scans(1).getFirst().bytes());assertEquals("COMPLETE",db.taskRuns().getFirst().status());assertTrue(db.schedules().getFirst().nextRun()>System.currentTimeMillis());assertEquals(1,messages.size());assertTrue(db.operations(10,0).isEmpty());
    }
    @Test void windowsLongPathsScan()throws Exception {
        var root=Files.createDirectory(temp.resolve("long"));var dir=root;for(int i=0;i<8;i++)dir=Files.createDirectory(dir.resolve("long_directory_component_"+i+"_1234567890"));Files.writeString(dir.resolve("file.txt"),"abc");var db=new Database(temp.resolve("db"));var result=new FileScanner(db).scan(root,new ScanOptions(100,20,0,List.of(),Set.of()),new Cancellation(),p->{});assertEquals(1,result.files());assertEquals(3,result.bytes());assertEquals("COMPLETE",result.status());
    }
}
