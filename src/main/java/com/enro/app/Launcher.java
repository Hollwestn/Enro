package com.enro.app;
public final class Launcher {
    private Launcher(){}
    public static void main(String[] args){
        if(args.length==2&&args[0].equals("--self-check")){System.exit(SelfCheck.run(java.nio.file.Path.of(args[1])));return;}
        javafx.application.Application.launch(EnroApp.class,args);
    }
}
