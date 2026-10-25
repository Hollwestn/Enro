import com.enro.storage.Database;
import com.enro.scanner.*;
import com.enro.core.Cancellation;
import com.enro.settings.*;
import java.nio.file.*;
import java.util.*;

/** Developer preview: reads a real selected workspace into a new isolated data folder. */
class PreparePreview {
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("scope new-data-folder");
        Path root=Path.of(args[0]).toAbsolutePath(),data=Path.of(args[1]).toAbsolutePath();
        Files.createDirectory(data);System.setProperty("enro.home",data.toString());
        var settings=new AppSettings();settings.language="ru";settings.theme="Iris";settings.appearance="light";settings.visualRevision=2;new SettingsStore(data.resolve("settings.json")).save(settings);
        var db=new Database(data.resolve("enro.db"));
        var result=new FileScanner(db).scan(root,new ScanOptions(200000,128,0,List.of(root.resolve(".git"),root.resolve(".smoke")),Set.of()),new Cancellation(),p->{});
        System.out.println(result.status()+" · "+result.files()+" files · "+result.bytes()+" logical bytes");
    }
}
