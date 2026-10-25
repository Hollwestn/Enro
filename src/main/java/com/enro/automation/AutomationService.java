package com.enro.automation;

import com.enro.core.Cancellation;
import com.enro.scanner.*;
import com.enro.storage.Database;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.function.*;

/** Analysis only, while the application is open. Missed checks run once on next launch. */
public final class AutomationService implements AutoCloseable {
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true).name("enro-scheduler").factory());
    private final Database db;private final Supplier<ScanOptions> options;private final Consumer<String> notification;private final Cancellation cancellation=new Cancellation();
    public AutomationService(Database db,Supplier<ScanOptions> options,Consumer<String> notification){this.db=db;this.options=options;this.notification=notification;}
    public void start(){timer.scheduleWithFixedDelay(this::tick,15,30,TimeUnit.SECONDS);}
    public synchronized void tick(){
        try{for(var s:db.schedules())if(s.enabled()&&s.nextRun()<=System.currentTimeMillis()){
                cancellation.check();long run=db.startTask(s.id());try{var result=new FileScanner(db).scan(Path.of(s.root()),options.get(),cancellation,p->{});db.finishTask(run,result.status(),"scan="+result.scanId()+" files="+result.files());notification.accept("#"+s.id()+": "+result.status()+" · "+result.files());}catch(Exception e){db.finishTask(run,"FAILED",e.getClass().getSimpleName());notification.accept("#"+s.id()+": FAILED");}finally{db.nextSchedule(s);}
            }}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Scheduler: {}",e.getClass().getSimpleName());}
    }
    @Override public void close(){cancellation.cancel();timer.shutdownNow();}
}
