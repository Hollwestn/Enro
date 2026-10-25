package com.enro.app;

import com.enro.core.Cancellation;
import com.enro.core.Models.Snapshot;
import com.enro.scanner.*;
import com.enro.duplicates.DuplicateFinder;
import com.enro.storage.Database;
import com.enro.settings.*;
import com.enro.recovery.OrganizerService;
import com.enro.cleanup.SafetyPolicy;
import java.nio.file.*;
import java.util.*;

/** Non-UI release probe. Mutates only its own newly generated test directory. */
public final class SelfCheck {
    private SelfCheck(){}
    public static int run(Path output){
        var report=new LinkedHashMap<String,Object>();Path fixture=null;int exit=0;
        report.put("java",System.getProperty("java.version"));report.put("os",System.getProperty("os.name"));var passed=new ArrayList<String>();
        try{
            output=output.toAbsolutePath().normalize();Files.createDirectories(output.getParent());fixture=Files.createTempDirectory(output.getParent(),"enro-release-check-");
            require(Runtime.version().feature()==25,"JDK25");passed.add("JDK25");
            var module=Class.forName("javafx.scene.control.Button",false,Launcher.class.getClassLoader()).getModule();require(module.isNamed(),"JavaFX must be a named module");report.put("javafxModule",module.getDescriptor().toNameAndVersion());passed.add("JavaFX named modules");
            require(Launcher.class.getResource("/fxml/main.fxml")!=null,"FXML resource");require(Launcher.class.getResource("/css/enro.css")!=null,"CSS resources");require(Launcher.class.getResource("/icons/enro.png")!=null,"Icon resource");passed.add("FXML/CSS/icon resources");
            var db=new Database(fixture.resolve("check.db"));var root=Files.createDirectory(fixture.resolve("files"));var one=Files.writeString(root.resolve("one.txt"),"Enro release fixture");Files.copy(one,root.resolve("two.txt"));
            var result=new FileScanner(db).scan(root,new ScanOptions(100,10,0,List.of(),Set.of()),new Cancellation(),p->{});require(result.files()==2&&result.status().equals("COMPLETE"),"Scanner");passed.add("NIO/SQLite/JNA Windows File ID");
            require(new DuplicateFinder(db).find(result.scanId(),new Cancellation(),p->{}).groups().size()==1,"Duplicates");passed.add("Sample/SHA-256/byte comparison");
            var settings=new SettingsStore(fixture.resolve("settings.json"));settings.save(new AppSettings());require(settings.load().theme.equals("Iris"),"Jackson settings");passed.add("Jackson/atomic settings");
            var organizer=new OrganizerService(db,new SafetyPolicy(List.of(),fixture.resolve("userTemp")));organizer.executeConfirmed(new OrganizerService.Move(Snapshot.capture(one),root.resolve("renamed.txt"),root));organizer.restore(db.operations(1,0).getFirst());require(Files.exists(one),"Move restore");passed.add("Journaled move/undo");
            report.put("status","PASS");
        }catch(Throwable e){report.put("status","FAIL");report.put("error",e.getClass().getSimpleName()+": "+e.getMessage());exit=1;}
        finally{
            if(fixture!=null)try(var paths=Files.walk(fixture)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}catch(Exception e){report.put("fixtureCleanup",e.getClass().getSimpleName());exit=1;}
            report.put("passed",passed);try{new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(),report);}catch(Exception e){exit=1;}
        }
        return exit;
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
