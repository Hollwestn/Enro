package com.enro.duplicates;

import com.enro.core.Cancellation;
import com.enro.core.Models.*;
import com.enro.storage.Database;
import com.enro.windows.WindowsPaths;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public final class DuplicateFinder {
    public record Result(List<DuplicateGroup> groups,long inspected,long errors,boolean limited) {public Result{groups=List.copyOf(groups);}}
    private final Database db;
    public DuplicateFinder(Database db){this.db=db;}
    public Result find(long scan,Cancellation token,Consumer<Long> progress)throws Exception {
        var groups=new ArrayList<DuplicateGroup>();long inspected=0,errors=0;boolean limited=false;
        try(var c=db.open();var p=c.prepareStatement("SELECT size FROM entries WHERE scan_id=? AND directory=0 AND size>0 GROUP BY size HAVING COUNT(*)>1 ORDER BY size DESC")){
            p.setLong(1,scan);try(var sizes=p.executeQuery()){
                while(sizes.next()){
                    token.check();long size=sizes.getLong(1);
                    var entries=db.filesOfSize(scan,size,20_001);
                    if(entries.size()>20_000){limited=true;continue;}
                    var samples=new HashMap<String,List<Snapshot>>();var identities=new HashSet<String>();
                    for(var entry:entries){token.check();if(inspected++>=200_000){limited=true;break;}try{var snap=Snapshot.capture(entry.file());if(snap.size()!=size||!snap.identity().equals(entry.identity())||snap.modified()!=entry.modified()||snap.created()!=entry.created())continue;
                            if(!identities.add(snap.identity()))continue;String hash=hash(snap,true,token);samples.computeIfAbsent(hash,k->new ArrayList<>()).add(snap);
                        }catch(IOException e){errors++;}if(inspected%100==0)progress.accept(inspected);}
                    for(var sample:samples.values())if(sample.size()>1){var full=new LinkedHashMap<String,List<Snapshot>>();for(var snap:sample){token.check();try{full.computeIfAbsent(hash(snap,false,token),k->new ArrayList<>()).add(snap);}catch(IOException e){errors++;}}
                        for(var bucket:full.entrySet())if(bucket.getValue().size()>1){var verified=new ArrayList<Snapshot>();var keeper=bucket.getValue().getFirst();verified.add(keeper);
                            for(int i=1;i<bucket.getValue().size();i++)try{if(equalBytes(keeper,bucket.getValue().get(i),token))verified.add(bucket.getValue().get(i));}catch(IOException e){errors++;}
                            if(verified.size()>1){verified.sort(Comparator.comparing(Snapshot::path));groups.add(new DuplicateGroup(bucket.getKey(),size,verified));}
                            if(groups.size()>=1000){limited=true;break;}
                        }
                        if(groups.size()>=1000)break;
                    }
                    if(limited&&(inspected>=200_000||groups.size()>=1000))break;
                }
            }
        }progress.accept(inspected);return new Result(groups,inspected,errors,limited);
    }
    public static String hash(Snapshot snap,boolean sample,Cancellation token)throws IOException {
        try{WindowsPaths.requirePlainAncestors(snap.file());requireSame(snap);var md=MessageDigest.getInstance("SHA-256");var buf=ByteBuffer.allocate(64*1024);
            try(var ch=FileChannel.open(snap.file(),StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
                if(sample){md.update(ByteBuffer.allocate(8).putLong(snap.size()).array());for(long pos:new long[]{0,Math.max(0,snap.size()/2-32768),Math.max(0,snap.size()-65536)}){token.check();buf.clear();ch.position(pos);int n=ch.read(buf);if(n>0)md.update(buf.array(),0,n);}}
                else{int n;while((n=ch.read(buf))!=-1){token.check();if(n>0)md.update(buf.array(),0,n);buf.clear();}}
            }requireSame(snap);return HexFormat.of().formatHex(md.digest());
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static boolean equalBytes(Snapshot a,Snapshot b,Cancellation token)throws IOException {
        WindowsPaths.requirePlainAncestors(a.file());WindowsPaths.requirePlainAncestors(b.file());requireSame(a);requireSame(b);
        try(var x=FileChannel.open(a.file(),StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS);var y=FileChannel.open(b.file(),StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            var one=ByteBuffer.allocate(64*1024);var two=ByteBuffer.allocate(64*1024);while(true){token.check();one.clear();two.clear();int n=fill(x,one);int m=fill(y,two);if(n!=m)return false;if(n==0)break;one.flip();two.flip();if(!one.equals(two))return false;}
        }requireSame(a);requireSame(b);return true;
    }
    private static int fill(FileChannel ch,ByteBuffer buffer)throws IOException{int count=0,n;while(buffer.hasRemaining()&&(n=ch.read(buffer))>0)count+=n;return count;}
    public static void requireSame(Snapshot snap)throws IOException {if(!snap.matches(Snapshot.capture(snap.file())))throw new IOException("File changed; scan again");}
}
