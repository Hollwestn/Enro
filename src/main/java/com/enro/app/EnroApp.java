package com.enro.app;

import com.enro.ui.MainController;
import javafx.application.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import java.nio.file.Path;

public final class EnroApp extends Application {
    private AppContext context;private MainController controller;
    @Override public void start(Stage stage){
        try{
            String base=System.getenv("LOCALAPPDATA");Path home=Path.of(System.getProperty("enro.home",base==null?Path.of(System.getProperty("user.home"),".enro").toString():Path.of(base,"Enro").toString()));System.setProperty("enro.home",home.toString());
            context=new AppContext(home);var loader=new FXMLLoader(getClass().getResource("/fxml/main.fxml"));Parent root=loader.load();controller=loader.getController();
            var scene=new Scene(root,1320,860);scene.getStylesheets().add(getClass().getResource("/css/enro.css").toExternalForm());stage.setTitle("Enro");stage.getIcons().add(new javafx.scene.image.Image(getClass().getResourceAsStream("/icons/enro.png")));stage.setMinWidth(1080);stage.setMinHeight(720);stage.setScene(scene);controller.initialize(context,stage);stage.show();
            Thread.setDefaultUncaughtExceptionHandler((t,e)->{org.slf4j.LoggerFactory.getLogger(getClass()).error("Uncaught {} on {}",e.getClass().getSimpleName(),t.getName());Platform.runLater(()->new Alert(Alert.AlertType.ERROR,"Operation failed / Ошибка операции: "+e.getClass().getSimpleName()).showAndWait());});
        }catch(Exception e){new Alert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();Platform.exit();}
    }
    @Override public void stop()throws Exception{if(controller!=null)controller.cancel();if(context!=null)context.close();}
}
