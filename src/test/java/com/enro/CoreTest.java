package com.enro;

import com.enro.core.*;
import com.enro.core.Models.*;
import com.enro.scanner.*;
import com.enro.storage.Database;
import com.enro.duplicates.DuplicateFinder;
import com.enro.cleanup.*;
import com.enro.recovery.OrganizerService;
import com.enro.settings.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
    @TempDir Path temp;
    Database db;Path root;SafetyPolicy policy;
    @BeforeEach void init()throws Exception {root=Files.createDirectory(temp.resolve("fixture"));db=new Database(temp.resolve("store.db"));policy=new SafetyPolicy(List.of(),temp.resolve("temp"));}
    FileScanner.Result scan()throws Exception{return new FileScanner(db).scan(root,new ScanOptions(10000,128,0,List.of(),Set.of()),new Cancellation(),p->{});}
    Path file(String name,String data)throws Exception {Path p=root.resolve(name);Files.createDirectories(p.getParent());return Files.writeString(p,data);}
    Candidate candidate(Path p)throws Exception{return new Candidate(Snapshot.capture(p),"Manual","Explicit selection",Risk.MEDIUM,root);}
    @Test void countsAndAggregates()throws Exception{file("a.txt","abc");file("sub/b.bin","12345");Files.createDirectory(root.resolve("empty"));var s=scan();assertEquals(8,s.bytes());assertEquals(2,s.files());assertEquals(8,db.root(s.scanId()).size());assertEquals(3,db.entries(s.scanId(),db.root(s.scanId()).id(),0,Long.MAX_VALUE,null,"",true,100,0).size());}
    @Test void emptyDirectory()throws Exception{var s=scan();assertEquals(0,s.files());assertEquals("COMPLETE",s.status());}
    @Test void exclusions()throws Exception{file("ignore/x.txt","123");file("x.tmp","12345");file("keep.txt","7");var s=new FileScanner(db).scan(root,new ScanOptions(100,128,0,List.of(root.resolve("ignore")),Set.of(".tmp")),new Cancellation(),p->{});assertEquals(1,s.bytes());}
    @Test void entryLimitIsExplicit()throws Exception{file("a","1");file("b","2");var s=new FileScanner(db).scan(root,new ScanOptions(2,128,0,List.of(),Set.of()),new Cancellation(),p->{});assertEquals("LIMIT_REACHED",s.status());assertEquals(s.bytes(),db.root(s.scanId()).size());}
    @Test void depthLimitIsExplicit()throws Exception{file("sub/deep/a","123");var s=new FileScanner(db).scan(root,new ScanOptions(100,1,0,List.of(),Set.of()),new Cancellation(),p->{});assertEquals("LIMIT_REACHED",s.status());assertTrue(s.errors()>0);}
    @Test void cancellationPersists()throws Exception{var token=new Cancellation();token.cancel();var s=new FileScanner(db).scan(root,new ScanOptions(100,128,0,List.of(),Set.of()),token,p->{});assertEquals("CANCELLED",s.status());assertEquals("CANCELLED",db.scans(1).getFirst().status());}
    @Test void missingRootRejected() {assertThrows(IOException.class,()->new FileScanner(db).scan(root.resolve("missing"),new ScanOptions(100,128,0,List.of(),Set.of()),new Cancellation(),p->{}));}
    @Test void duplicateContentNotNames()throws Exception{file("one/a","same data");file("two/b","same data");file("three/a","different");var s=scan();var result=new DuplicateFinder(db).find(s.scanId(),new Cancellation(),n->{});assertEquals(1,result.groups().size());assertEquals(2,result.groups().getFirst().copies().size());}
    @Test void sameSizeDifferentContent()throws Exception{file("one","abcd");file("two","abce");var s=scan();assertTrue(new DuplicateFinder(db).find(s.scanId(),new Cancellation(),n->{}).groups().isEmpty());}
    @Test void duplicateGroupsOfDifferentSizes()throws Exception{file("a","aaaa");file("b","aaaa");file("c","cccccccc");file("d","cccccccc");var s=scan();assertEquals(2,new DuplicateFinder(db).find(s.scanId(),new Cancellation(),n->{}).groups().size());}
    @Test void hardLinksAreNotAdditionalCopies()throws Exception{var p=file("one","same data");Files.createLink(root.resolve("two"),p);var s=scan();assertTrue(new DuplicateFinder(db).find(s.scanId(),new Cancellation(),n->{}).groups().isEmpty());}
    @Test void hashMatchesKnownSha256()throws Exception{var p=file("a","abc");assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",DuplicateFinder.hash(Snapshot.capture(p),false,new Cancellation()));}
    @Test void largeFileHashIsStreaming()throws Exception{Path p=root.resolve("large");try(var out=new RandomAccessFile(p.toFile(),"rw")){out.setLength(32L*1024*1024);}assertEquals(64,DuplicateFinder.hash(Snapshot.capture(p),false,new Cancellation()).length());assertEquals(32L*1024*1024,scan().bytes());}
    @Test void changedSnapshotRejected()throws Exception{var p=file("a","1");var snap=Snapshot.capture(p);Files.writeString(p,"longer");assertThrows(IOException.class,()->policy.validate(snap,root));assertThrows(IOException.class,()->DuplicateFinder.hash(snap,false,new Cancellation()));}
    @Test void duplicateCancellation()throws Exception{var t=new Cancellation();t.cancel();var p=file("a","1");assertThrows(java.util.concurrent.CancellationException.class,()->DuplicateFinder.hash(Snapshot.capture(p),false,t));}
    @Test void fileOutsideScopeRejected()throws Exception{var p=Files.writeString(temp.resolve("outside"),"x");assertThrows(IOException.class,()->policy.validate(Snapshot.capture(p),root));}
    @Test void protectedRootRejected()throws Exception{var p=file("protected/a","x");var guard=new SafetyPolicy(List.of(root.resolve("protected")),temp);assertThrows(IOException.class,()->guard.validate(Snapshot.capture(p),root));}
    @Test void appDataIsProtectedWithoutBlockingAnEnroNamedUserFolder()throws Exception{Path scope=Files.createTempDirectory(Path.of(System.getProperty("user.dir"),"target"),"enro-policy-");Path privateRoot=Files.createDirectory(scope.resolve("private-data")),ordinaryRoot=Files.createDirectory(scope.resolve("Enro"));Path data=Files.writeString(privateRoot.resolve("state"),"data"),ordinary=Files.writeString(ordinaryRoot.resolve("notes.txt"),"keep");String previous=System.getProperty("enro.home");try{System.setProperty("enro.home",privateRoot.toString());var guard=new SafetyPolicy();assertThrows(IOException.class,()->guard.validate(Snapshot.capture(data),scope));assertDoesNotThrow(()->guard.validate(Snapshot.capture(ordinary),scope));}finally{if(previous==null)System.clearProperty("enro.home");else System.setProperty("enro.home",previous);Files.deleteIfExists(data);Files.deleteIfExists(ordinary);Files.deleteIfExists(privateRoot);Files.deleteIfExists(ordinaryRoot);Files.deleteIfExists(scope);}}
    @Test void protectedComponentsRejected()throws Exception{var p=file(".ssh/key","x");assertThrows(IOException.class,()->policy.validate(Snapshot.capture(p),root));}
    @Test void cleanupIsExplicitAndOneUse()throws Exception{var p=file("a","x");var seen=new ArrayList<Path>();var service=new CleanupService(db,policy,seen::add);var plan=service.preview(List.of(candidate(p)),List.of());assertTrue(seen.isEmpty());assertEquals(1,service.executeConfirmed(plan,new Cancellation()).succeeded());assertEquals(List.of(p),seen);assertThrows(IOException.class,()->service.executeConfirmed(plan,new Cancellation()));assertEquals("SUCCESS",db.operations(1,0).getFirst().status());}
    @Test void changedAfterPreviewIsNotDisposed()throws Exception{var p=file("a","x");var service=new CleanupService(db,policy,path->{fail("Must not dispose");});var plan=service.preview(List.of(candidate(p)),List.of());Files.writeString(p,"new data");assertEquals(1,service.executeConfirmed(plan,new Cancellation()).failed());assertTrue(Files.exists(p));}
    @Test void highRiskRefused()throws Exception{var p=file("a","x");var service=new CleanupService(db,policy,path->{});assertThrows(IOException.class,()->service.preview(List.of(new Candidate(Snapshot.capture(p),"System","",Risk.HIGH,root)),List.of()));}
    @Test void retainedCopyProtected()throws Exception{var p=file("a","x");var service=new CleanupService(db,policy,path->{});assertThrows(IOException.class,()->service.preview(List.of(candidate(p)),List.of(Snapshot.capture(p))));}
    @Test void lostKeeperPreventsDisposal()throws Exception{var p=file("a","x");var keeper=file("b","x");var service=new CleanupService(db,policy,path->fail());var plan=service.preview(List.of(candidate(p)),List.of(Snapshot.capture(keeper)));Files.delete(keeper);assertEquals(1,service.executeConfirmed(plan,new Cancellation()).failed());}
    @Test void disposalFailureJournaled()throws Exception{var p=file("a","x");var service=new CleanupService(db,policy,path->{throw new FileSystemException(path.toString(),null,"Injected storage unavailable");});assertEquals(1,service.executeConfirmed(service.preview(List.of(candidate(p)),List.of()),new Cancellation()).failed());assertEquals("FAILED",db.operations(1,0).getFirst().status());assertTrue(Files.exists(p));}
    @Test void directoryNeverCandidate()throws Exception{assertThrows(IOException.class,()->Snapshot.capture(root));}
    @Test void moveAndUndo()throws Exception{var p=file("a","abc");var service=new OrganizerService(db,policy);service.executeConfirmed(new OrganizerService.Move(Snapshot.capture(p),root.resolve("b"),root));assertFalse(Files.exists(p));service.restore(db.operations(1,0).getFirst());assertEquals("abc",Files.readString(p));}
    @Test void moveDoesNotOverwrite()throws Exception{var p=file("a","abc");var target=file("b","keep");var service=new OrganizerService(db,policy);assertThrows(IOException.class,()->service.executeConfirmed(new OrganizerService.Move(Snapshot.capture(p),target,root)));assertEquals("keep",Files.readString(target));}
    @Test void moveChangedDestinationCannotUndo()throws Exception{var p=file("a","abc");var service=new OrganizerService(db,policy);service.executeConfirmed(new OrganizerService.Move(Snapshot.capture(p),root.resolve("b"),root));var op=db.operations(1,0).getFirst();Files.writeString(root.resolve("b"),"changed");assertThrows(IOException.class,()->service.restore(op));}
    @Test void settingsRoundtrip()throws Exception{var store=new SettingsStore(temp.resolve("settings.json"));var settings=new AppSettings();settings.theme="Jade";settings.appearance="dark";settings.language="en";settings.excludedExtensions.add(".tmp");store.save(settings);assertEquals("Jade",store.load().theme);assertEquals("dark",store.load().appearance);assertEquals(List.of(".tmp"),store.load().excludedExtensions);}
    @Test void invalidSettingsRejected()throws Exception{var s=new AppSettings();s.parallelism=999;assertThrows(IllegalArgumentException.class,()->new SettingsStore(temp.resolve("s.json")).save(s));}
    @Test void sqliteReopenAndHistory()throws Exception{file("a","abc");scan();var reopened=new Database(temp.resolve("store.db"));assertEquals(1,reopened.scans(10).size());assertEquals(3,reopened.scans(1).getFirst().bytes());}
    @Test void schedulePersistsAndToggles()throws Exception{db.addSchedule(root,24);var s=db.schedules().getFirst();assertTrue(s.enabled());db.enableSchedule(s.id(),false);assertFalse(db.schedules().getFirst().enabled());long id=db.startTask(s.id());db.finishTask(id,"SUCCESS","scan=1");assertEquals("SUCCESS",db.taskRuns().getFirst().status());}
    @Test void symbolicLinksNotFollowed()throws Exception{var outside=Files.createDirectory(temp.resolve("outside"));Files.writeString(outside.resolve("secret"),"123");try{Files.createSymbolicLink(root.resolve("link"),outside);}catch(IOException|UnsupportedOperationException e){Assumptions.abort("Symlink creation unavailable: "+e.getClass().getSimpleName());}var s=scan();assertEquals(0,s.files());assertTrue(s.errors()>0);assertThrows(IOException.class,()->Snapshot.capture(root.resolve("link")));}
}
