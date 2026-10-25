package com.enro.app;

import com.enro.settings.*;
import com.enro.storage.Database;
import com.enro.cleanup.*;
import com.enro.recovery.OrganizerService;
import com.enro.windows.RecycleBin;
import com.enro.automation.AutomationService;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.concurrent.*;

public final class AppContext implements AutoCloseable {
    public final Path home;public final Database db;public final SettingsStore settingsStore;public volatile AppSettings settings;
    public final ThreadPoolExecutor workers;public final CleanupService cleanup;public final OrganizerService organizer;public AutomationService automation;
    private final FileChannel lockChannel;private final FileLock lock;
    public AppContext(Path home)throws Exception {
        this.home=home;Files.createDirectories(home);lockChannel=FileChannel.open(home.resolve("instance.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);lock=lockChannel.tryLock();if(lock==null){lockChannel.close();throw new IllegalStateException("Enro is already running / Enro уже запущен");}
        settingsStore=new SettingsStore(home.resolve("settings.json"));settings=settingsStore.load();
        if(settings.visualRevision<2){settings.theme="Iris";settings.appearance="light";settings.visualRevision=2;settingsStore.save(settings);}
        db=new Database(home.resolve("enro.db"));
        workers=new ThreadPoolExecutor(settings.parallelism,settings.parallelism,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),Thread.ofPlatform().daemon(true).name("enro-worker-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
        var safety=new SafetyPolicy();cleanup=new CleanupService(db,safety,new RecycleBin());organizer=new OrganizerService(db,safety);applyLogLevel();
    }
    public void save(AppSettings next)throws Exception{next.visualRevision=2;next.validate();settingsStore.save(next);settings=next;if(next.parallelism>workers.getMaximumPoolSize()){workers.setMaximumPoolSize(next.parallelism);workers.setCorePoolSize(next.parallelism);}else{workers.setCorePoolSize(next.parallelism);workers.setMaximumPoolSize(next.parallelism);}applyLogLevel();}
    private void applyLogLevel(){((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.toLevel(settings.logLevel));}
    @Override public void close()throws Exception{if(automation!=null)automation.close();workers.shutdownNow();workers.awaitTermination(5,TimeUnit.SECONDS);lock.release();lockChannel.close();}
}
