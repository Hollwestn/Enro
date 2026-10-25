package com.enro.storage;

import com.enro.core.Models.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public final class Database {
    private final Path path;
    public Database(Path path) throws Exception {
        this.path = path.toAbsolutePath(); Files.createDirectories(this.path.getParent());
        try (var c = open(); var s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("CREATE TABLE IF NOT EXISTS scans(id INTEGER PRIMARY KEY,root TEXT NOT NULL,started INTEGER NOT NULL,finished INTEGER DEFAULT 0,status TEXT NOT NULL,bytes INTEGER DEFAULT 0,files INTEGER DEFAULT 0,errors INTEGER DEFAULT 0)");
            s.execute("CREATE TABLE IF NOT EXISTS entries(id INTEGER PRIMARY KEY,scan_id INTEGER REFERENCES scans(id) ON DELETE CASCADE,parent_id INTEGER,path TEXT NOT NULL,name TEXT NOT NULL,directory INTEGER NOT NULL,size INTEGER NOT NULL,files INTEGER NOT NULL,modified INTEGER,created INTEGER,identity TEXT,category TEXT)");
            s.execute("CREATE INDEX IF NOT EXISTS entries_parent ON entries(scan_id,parent_id,size DESC)");
            s.execute("CREATE INDEX IF NOT EXISTS entries_size ON entries(scan_id,directory,size DESC)");
            s.execute("CREATE TABLE IF NOT EXISTS scan_errors(scan_id INTEGER REFERENCES scans(id) ON DELETE CASCADE,path TEXT,kind TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS operations(id INTEGER PRIMARY KEY,batch TEXT,time INTEGER,source TEXT,target TEXT,bytes INTEGER,method TEXT,status TEXT,detail TEXT,identity TEXT,modified INTEGER,created INTEGER)");
            s.execute("CREATE TABLE IF NOT EXISTS schedules(id INTEGER PRIMARY KEY,root TEXT,interval_hours INTEGER,next_run INTEGER,enabled INTEGER)");
            s.execute("CREATE TABLE IF NOT EXISTS task_runs(id INTEGER PRIMARY KEY,schedule_id INTEGER,started INTEGER,status TEXT,detail TEXT)");
            s.executeUpdate("UPDATE operations SET status='INTERRUPTED',detail='Interrupted; inspect source and destination before recovery' WHERE status='PENDING'");
            s.executeUpdate("UPDATE scans SET status='INTERRUPTED',finished=" + System.currentTimeMillis() + " WHERE status='RUNNING'");
            s.executeUpdate("UPDATE task_runs SET status='INTERRUPTED' WHERE status='RUNNING'");
        }
    }
    public Path path() { return path; }
    public Connection open() throws SQLException {
        var c = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (var s = c.createStatement()) { s.execute("PRAGMA busy_timeout=15000"); s.execute("PRAGMA foreign_keys=ON"); }
        return c;
    }
    public long createScan(Path root) throws SQLException {
        try (var c = open(); var p = c.prepareStatement("INSERT INTO scans(root,started,status) VALUES(?,?,'RUNNING')", Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1,root.toString()); p.setLong(2,System.currentTimeMillis()); p.executeUpdate(); return key(p);
        }
    }
    public Scan scanById(long id)throws SQLException {try(var c=open();var p=c.prepareStatement("SELECT * FROM scans WHERE id=?")){p.setLong(1,id);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Scan not found");return new Scan(id,r.getString("root"),r.getLong("started"),r.getLong("finished"),r.getString("status"),r.getLong("bytes"),r.getLong("files"),r.getLong("errors"));}}}
    public List<ScanIssue> scanIssues(long id)throws SQLException {try(var c=open();var p=c.prepareStatement("SELECT path,kind FROM scan_errors WHERE scan_id=? LIMIT 1000")){p.setLong(1,id);try(var r=p.executeQuery()){var list=new ArrayList<ScanIssue>();while(r.next())list.add(new ScanIssue(r.getString(1),r.getString(2)));return list;}}}
    public void finishScan(long id,String status,long bytes,long files,long errors) throws SQLException {
        update("UPDATE scans SET finished=?,status=?,bytes=?,files=?,errors=? WHERE id=?",System.currentTimeMillis(),status,bytes,files,errors,id);
    }
    public List<Scan> scans(int limit) throws SQLException {
        try(var c=open();var p=c.prepareStatement("SELECT * FROM scans ORDER BY id DESC LIMIT ?")) {
            p.setInt(1,limit);try(var r=p.executeQuery()){var out=new ArrayList<Scan>();while(r.next())out.add(new Scan(r.getLong("id"),r.getString("root"),r.getLong("started"),r.getLong("finished"),r.getString("status"),r.getLong("bytes"),r.getLong("files"),r.getLong("errors")));return out;}
        }
    }
    public List<Entry> entries(long scan,Long parent,long minSize,long modifiedBefore,String category,String search,boolean directories,int limit,int offset) throws SQLException {
        String sql="SELECT * FROM entries WHERE scan_id=?"+(parent!=null?" AND parent_id=?":" AND directory=0")+" AND size>=? AND modified<=?"+(category==null||category.equals("All")?"":" AND category=?")+" AND instr(lower(path),lower(?))>0"+(directories?"":" AND directory=0")+" ORDER BY size DESC,id LIMIT ? OFFSET ?";
        try(var c=open();var p=c.prepareStatement(sql)){
            int i=1;p.setLong(i++,scan);if(parent!=null)p.setLong(i++,parent);p.setLong(i++,minSize);p.setLong(i++,modifiedBefore);if(category!=null&&!category.equals("All"))p.setString(i++,category);p.setString(i++,search==null?"":search);p.setInt(i++,limit);p.setInt(i,offset);
            try(var r=p.executeQuery()){var out=new ArrayList<Entry>();while(r.next())out.add(entry(r));return out;}
        }
    }
    public Entry entry(long id) throws SQLException { try(var c=open();var p=c.prepareStatement("SELECT * FROM entries WHERE id=?")){p.setLong(1,id);try(var r=p.executeQuery()){return r.next()?entry(r):null;}} }
    public List<Entry> filesOfSize(long scan,long size,int limit)throws SQLException {try(var c=open();var p=c.prepareStatement("SELECT * FROM entries WHERE scan_id=? AND directory=0 AND size=? ORDER BY path LIMIT ?")){p.setLong(1,scan);p.setLong(2,size);p.setInt(3,limit);try(var r=p.executeQuery()){var list=new ArrayList<Entry>();while(r.next())list.add(entry(r));return list;}}}
    public Entry root(long scan) throws SQLException {try(var c=open();var p=c.prepareStatement("SELECT * FROM entries WHERE scan_id=? AND parent_id=0 LIMIT 1")){p.setLong(1,scan);try(var r=p.executeQuery()){return r.next()?entry(r):null;}}}
    public List<Category> categories(long scan) throws SQLException {
        try(var c=open();var p=c.prepareStatement("SELECT category,SUM(size) bytes,COUNT(*) files FROM entries WHERE scan_id=? AND directory=0 GROUP BY category ORDER BY bytes DESC")){p.setLong(1,scan);try(var r=p.executeQuery()){var out=new ArrayList<Category>();while(r.next())out.add(new Category(r.getString(1),r.getLong(2),r.getLong(3)));return out;}}
    }
    public long logicalBytesAtLeast(long scan,long minimum)throws SQLException {try(var c=open();var p=c.prepareStatement("SELECT COALESCE(SUM(size),0) FROM entries WHERE scan_id=? AND directory=0 AND size>=?")){p.setLong(1,scan);p.setLong(2,minimum);try(var r=p.executeQuery()){return r.getLong(1);}}}
    public long operation(String batch,Snapshot snap,String target,String method) throws SQLException {
        try(var c=open();var p=c.prepareStatement("INSERT INTO operations(batch,time,source,target,bytes,method,status,detail,identity,modified,created) VALUES(?,?,?,?,?,?,'PENDING','',?,?,?)",Statement.RETURN_GENERATED_KEYS)){
            p.setString(1,batch);p.setLong(2,System.currentTimeMillis());p.setString(3,snap.path());p.setString(4,target);p.setLong(5,snap.size());p.setString(6,method);p.setString(7,snap.identity());p.setLong(8,snap.modified());p.setLong(9,snap.created());p.executeUpdate();return key(p);
        }
    }
    public void operationResult(long id,String status,String detail) throws SQLException {update("UPDATE operations SET status=?,detail=? WHERE id=?",status,detail,id);}
    public List<Operation> operations(int limit,int offset) throws SQLException {
        try(var c=open();var p=c.prepareStatement("SELECT * FROM operations ORDER BY id DESC LIMIT ? OFFSET ?")){p.setInt(1,limit);p.setInt(2,offset);try(var r=p.executeQuery()){var out=new ArrayList<Operation>();while(r.next())out.add(new Operation(r.getLong("id"),r.getString("batch"),r.getLong("time"),r.getString("source"),r.getString("target"),r.getLong("bytes"),r.getString("method"),r.getString("status"),r.getString("detail"),r.getString("identity"),r.getLong("modified"),r.getLong("created")));return out;}}
    }
    public long recycledLogicalBytes() throws SQLException {try(var c=open();var s=c.createStatement();var r=s.executeQuery("SELECT COALESCE(SUM(bytes),0) FROM operations WHERE method='RECYCLE' AND status='SUCCESS'")){return r.getLong(1);}}
    public List<Schedule> schedules() throws SQLException {try(var c=open();var s=c.createStatement();var r=s.executeQuery("SELECT * FROM schedules ORDER BY id")){var out=new ArrayList<Schedule>();while(r.next())out.add(new Schedule(r.getLong(1),r.getString(2),r.getLong(3),r.getLong(4),r.getBoolean(5)));return out;}}
    public void addSchedule(Path root,long hours) throws SQLException {if(hours!=24&&hours!=168)throw new IllegalArgumentException("Daily or weekly only");update("INSERT INTO schedules(root,interval_hours,next_run,enabled) VALUES(?,?,?,1)",root.toString(),hours,System.currentTimeMillis()+hours*3_600_000);}
    public void enableSchedule(long id,boolean enabled) throws SQLException {update("UPDATE schedules SET enabled=? WHERE id=?",enabled?1:0,id);}
    public void nextSchedule(Schedule s) throws SQLException {update("UPDATE schedules SET next_run=? WHERE id=?",System.currentTimeMillis()+s.intervalHours()*3_600_000,s.id());}
    public long startTask(long schedule) throws SQLException {try(var c=open();var p=c.prepareStatement("INSERT INTO task_runs(schedule_id,started,status,detail) VALUES(?,?,'RUNNING','')",Statement.RETURN_GENERATED_KEYS)){p.setLong(1,schedule);p.setLong(2,System.currentTimeMillis());p.executeUpdate();return key(p);}}
    public void finishTask(long id,String status,String detail) throws SQLException {update("UPDATE task_runs SET status=?,detail=? WHERE id=?",status,detail,id);}
    public List<TaskRun> taskRuns() throws SQLException {try(var c=open();var s=c.createStatement();var r=s.executeQuery("SELECT * FROM task_runs ORDER BY id DESC LIMIT 200")){var out=new ArrayList<TaskRun>();while(r.next())out.add(new TaskRun(r.getLong(1),r.getLong(2),r.getLong(3),r.getString(4),r.getString(5)));return out;}}
    public static Entry entry(ResultSet r) throws SQLException {return new Entry(r.getLong("id"),r.getLong("scan_id"),r.getLong("parent_id"),r.getString("path"),r.getString("name"),r.getBoolean("directory"),r.getLong("size"),r.getLong("files"),r.getLong("modified"),r.getLong("created"),r.getString("identity"),r.getString("category"));}
    public void update(String sql,Object...args) throws SQLException {try(var c=open();var p=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);p.executeUpdate();}}
    private static long key(PreparedStatement p) throws SQLException {try(var r=p.getGeneratedKeys()){if(!r.next())throw new SQLException("Missing generated id");return r.getLong(1);}}
}
