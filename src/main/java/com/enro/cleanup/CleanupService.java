package com.enro.cleanup;

import com.enro.core.*;
import com.enro.core.Models.*;
import com.enro.duplicates.DuplicateFinder;
import com.enro.storage.Database;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CleanupService {
    @FunctionalInterface public interface Disposal {void recycle(Path file)throws IOException;}
    public static final class Plan {
        private final List<Candidate> items;private final List<Snapshot> keepers;private final AtomicBoolean used=new AtomicBoolean();
        private Plan(List<Candidate> items,List<Snapshot> keepers){this.items=List.copyOf(items);this.keepers=List.copyOf(keepers);}
        public List<Candidate> items(){return items;}
        public long bytes(){return items.stream().mapToLong(c->c.snapshot().size()).sum();}
    }
    public record Report(int succeeded,int failed,long recycledLogicalBytes){}
    private final Database db;private final SafetyPolicy policy;private final Disposal disposal;
    public CleanupService(Database db,SafetyPolicy policy,Disposal disposal){this.db=db;this.policy=policy;this.disposal=disposal;}
    public Plan preview(List<Candidate> candidates,List<Snapshot> keepers)throws IOException {
        if(candidates.isEmpty()||candidates.size()>10_000)throw new IOException("Select 1–10000 files");
        var seen=new HashSet<Path>();for(var item:candidates){if(item.risk()==Risk.HIGH||item.risk()==Risk.UNKNOWN)throw new IOException("Unsupported risk level");policy.validate(item.snapshot(),item.permittedRoot());if(!seen.add(item.snapshot().file()))throw new IOException("Repeated candidate");}
        for(var keeper:keepers){if(seen.contains(keeper.file()))throw new IOException("Cannot remove the retained copy");DuplicateFinder.requireSame(keeper);}
        return new Plan(candidates,keepers);
    }
    /** Called only after the UI has shown and confirmed this exact immutable preview. */
    public Report executeConfirmed(Plan plan,Cancellation token)throws Exception {
        if(!plan.used.compareAndSet(false,true))throw new IOException("Plan already consumed");
        int success=0,failed=0;long bytes=0;String batch=UUID.randomUUID().toString();
        for(var item:plan.items){token.check();long id=db.operation(batch,item.snapshot(),"Windows Recycle Bin","RECYCLE");
            try{for(var keeper:plan.keepers){com.enro.windows.WindowsPaths.requirePlainAncestors(keeper.file());DuplicateFinder.requireSame(keeper);}policy.validate(item.snapshot(),item.permittedRoot());disposal.recycle(item.snapshot().file());db.operationResult(id,"SUCCESS","Restore through Windows Recycle Bin; disk savings not guaranteed");success++;bytes+=item.snapshot().size();}
            catch(IOException|RuntimeException e){db.operationResult(id,"FAILED",e.getClass().getSimpleName());failed++;}
        }return new Report(success,failed,bytes);
    }
    public List<Candidate> discover(Path root,String category,Cancellation token)throws IOException {
        var result=new ArrayList<Candidate>();com.enro.windows.WindowsPaths.requirePlainAncestors(root);
        try(var stream=Files.newDirectoryStream(root)){
            for(var file:stream){token.check();if(result.size()>=5000)break;if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;
                try{var snap=Snapshot.capture(file);if(category.equals("User Temp")&&System.currentTimeMillis()-snap.modified()<7L*86400000)continue;policy.validate(snap,root);result.add(new Candidate(snap,category,category.equals("User Temp")?"Inside user Temp; modified more than 7 days ago. Review application ownership.":"Manual review only. Age, type and size do not imply that a file is unnecessary.",Risk.MEDIUM,root));}catch(IOException ignored){/* Protected or inaccessible candidates are not recommended. */}
            }
        }return result;
    }
}
