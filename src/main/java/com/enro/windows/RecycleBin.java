package com.enro.windows;

import com.sun.jna.*;
import com.sun.jna.ptr.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.COM.*;
import com.sun.jna.win32.StdCallLibrary;
import java.nio.file.*;
import java.io.IOException;

/** Windows 8+ IFileOperation. No SHFileOperation or permanent-delete fallback. */
public final class RecycleBin implements com.enro.cleanup.CleanupService.Disposal {
    private interface Shell extends StdCallLibrary {
        Shell INSTANCE=Native.load("shell32",Shell.class);
        WinNT.HRESULT SHCreateItemFromParsingName(WString path,Pointer context,Guid.REFIID iid,PointerByReference item);
    }
    private static final class FileOperation extends Unknown {
        FileOperation(Pointer p){super(p);}
        void call(int slot,Object...args){Object[] all=new Object[args.length+1];all[0]=getPointer();System.arraycopy(args,0,all,1,args.length);COMUtils.checkRC((WinNT.HRESULT)_invokeNativeObject(slot,all,WinNT.HRESULT.class));}
    }
    @Override public void recycle(Path path)throws IOException {
        if(!WindowsPaths.WINDOWS)throw new IOException("Windows Recycle Bin required");
        if(path.toString().startsWith("\\\\")||Kernel32.INSTANCE.GetDriveType(path.getRoot().toString())!=3)throw new IOException("Recycle supports local fixed drives only");
        WinNT.HRESULT init=Ole32.INSTANCE.CoInitializeEx(Pointer.NULL,Ole32.COINIT_APARTMENTTHREADED);COMUtils.checkRC(init);
        FileOperation operation=null;Unknown item=null;
        try{
            var ptr=new PointerByReference();COMUtils.checkRC(Ole32.INSTANCE.CoCreateInstance(new Guid.GUID("{3AD05575-8857-4850-9277-11B85BDB8E09}"),Pointer.NULL,1,new Guid.GUID("{947AAB5F-0A5C-4C13-B4D6-4BF7836FC9F8}"),ptr));operation=new FileOperation(ptr.getValue());
            // RECYCLEONDELETE + ADDUNDORECORD + EARLYFAILURE + NOERRORUI + SILENT + WANTNUKEWARNING + NO_CONNECTED_ELEMENTS.
            // Do not set NOCONFIRMATION: Windows must warn if it cannot recycle.
            operation.call(5,0x00080000|0x20000000|0x00100000|0x0400|0x0004|0x4000|0x2000);
            var itemPtr=new PointerByReference();COMUtils.checkRC(Shell.INSTANCE.SHCreateItemFromParsingName(new WString(path.toString()),Pointer.NULL,new Guid.REFIID(new Guid.IID("{43826D1E-E718-42EE-BC55-A1E261C37BFE}")),itemPtr));item=new Unknown(itemPtr.getValue());
            operation.call(18,item.getPointer(),Pointer.NULL);operation.call(21);var aborted=new IntByReference();operation.call(22,aborted);
            if(aborted.getValue()!=0||Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Windows cancelled or refused recycling");
        }catch(COMException e){throw new IOException("Windows recycle failed: "+e.getMessage(),e);}
        finally{if(item!=null)item.Release();if(operation!=null)operation.Release();Ole32.INSTANCE.CoUninitialize();}
    }
}
