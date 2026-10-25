package com.enro.ui;

import com.enro.core.Models.*;
import com.enro.utils.Formats;
import com.enro.windows.DriveService.Drive;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import static com.enro.ui.Ui.*;

/** An overview with a clear starting point, real drive data and direct tool entry points. */
public final class DashboardView extends VBox {
    public record Data(List<Drive> drives,List<Scan> scans,List<Operation> operations){}
    public record Actions(Runnable choose,Consumer<Path> scanDrive,Consumer<Integer> open){}
    private final boolean ru;
    public DashboardView(Data data,Actions actions,boolean russian){
        super(22);ru=russian;setFillWidth(true);
        var top=new GridPane();top.setHgap(20);top.getColumnConstraints().addAll(percent(60),percent(40));
        var drivePanel=card(row(text(t("Your storage","Ваше хранилище"),"section-title"),space(),pill(t("This computer","Этот компьютер"),"neutral")));
        if(data.drives.isEmpty())drivePanel.getChildren().add(paragraph(t("No accessible volumes. You can still choose a folder to analyze.","Доступных накопителей нет. Вы можете выбрать отдельную папку для анализа."),"muted"));
        int driveIndex=0;
        for(var drive:data.drives){
            if(!drive.error().isEmpty()){drivePanel.getChildren().add(row(text(drive.root().toString(),"card-title"),space(),pill(t("Unavailable","Недоступно"),"warning")));continue;}
            var glyph=new StackPane(Icons.drive());glyph.getStyleClass().add("icon-tile");
            var name=new VBox(4,text(drive.root()+"  "+t("Local disk","Локальный диск"),"card-title"),text(drive.filesystem(),"small-muted"));
            var action=button(t("Analyze","Проверить"),()->actions.scanDrive.accept(drive.root()),"link-button");
            var heading=row(glyph,name,space(),action);heading.getStyleClass().add("drive-row");
            if(driveIndex++==0){var free=row(text(Formats.bytes(drive.usable()),"hero-number"),text(t("available","свободно"),"muted"));free.setAlignment(Pos.BASELINE_LEFT);drivePanel.getChildren().addAll(heading,free);}
            else drivePanel.getChildren().add(heading);
            var meter=new ProgressBar(drive.usage());meter.setMaxWidth(Double.MAX_VALUE);
            drivePanel.getChildren().addAll(meter,row(text(t("Used ","Занято ")+Formats.bytes(drive.used()),"small-muted"),space(),text(t("Total ","Всего ")+Formats.bytes(drive.total()),"small-muted")));
        }
        var latest=card(row(text(t("Last analysis","Последняя проверка"),"section-title"),space(),text("↗","muted")));
        if(data.scans.isEmpty()){
            latest.getChildren().addAll(text(t("Start with a folder","Начните с папки"),"stat-number"),paragraph(t("Enro will show what takes space and help you decide what to keep.","Enro покажет, что занимает место, и поможет выбрать дальнейшие действия."),"muted"),button(t("Choose a folder","Выбрать папку"),actions.choose,"primary"));
        }else{
            var scan=data.scans.getFirst();String name=Path.of(scan.root()).getFileName()==null?scan.root():Path.of(scan.root()).getFileName().toString();
            var path=text(scan.root(),"small-muted");path.setTooltip(new Tooltip(scan.root()));
            latest.getChildren().addAll(pill(status(scan.status()),scan.status().equals("COMPLETE")?"success":"warning"),new VBox(6,text(name,"stat-number"),path),row(new VBox(6,text(Formats.bytes(scan.bytes()),"stat-number"),text(t("logical size","логический размер"),"stat-label")),space(),new VBox(6,text(String.format(Locale.ROOT,"%,d",scan.files()),"stat-number"),text(t("files","файлов"),"stat-label"))),button(t("View results  →","Открыть результаты  →"),()->actions.open.accept(1),"link-button"));
        }
        top.add(drivePanel,0,0);top.add(latest,1,0);getChildren().add(top);
        getChildren().add(text(t("What would you like to find?","Что хотите найти?"),"section-title"));
        var tools=new GridPane();tools.setHgap(16);tools.getColumnConstraints().addAll(percent(33.333),percent(33.333),percent(33.334));
        tools.add(tool(4,t("Large files","Крупные файлы"),t("Find the files that take up the most space.","Найдите файлы, которые занимают больше всего места."),()->actions.open.accept(4)),0,0);
        tools.add(tool(3,t("Duplicate copies","Одинаковые копии"),t("Compare file contents and keep the copy you need.","Сравните содержимое и сохраните нужный экземпляр."),()->actions.open.accept(3)),1,0);
        tools.add(tool(2,t("Thoughtful cleanup","Очистка с проверкой"),t("Review suggested files before moving them to the Recycle Bin.","Проверьте предложенные файлы перед перемещением в Корзину."),()->actions.open.accept(2)),2,0);
        getChildren().add(tools);
        var activity=card(row(text(t("Recent activity","Последние действия"),"section-title"),space(),button(t("History  →","История  →"),()->actions.open.accept(6),"link-button")));
        if(data.scans.isEmpty()&&data.operations.isEmpty())activity.getChildren().add(paragraph(t("Your analyses and file operations will appear here.","Здесь появятся ваши проверки и операции с файлами."),"muted"));
        for(var scan:data.scans.stream().limit(2).toList()){var icon=new StackPane(Icons.node(1));icon.getStyleClass().add("activity-icon");var words=new VBox(4,text(t("Folder analyzed","Проверка папки"),"file-name"),text(scan.root(),"file-path"));HBox.setHgrow(words,Priority.ALWAYS);words.setMinWidth(0);var line=row(icon,words,text(Formats.bytes(scan.bytes()),"muted"),text(Formats.date(scan.started()),"small-muted"));line.getStyleClass().add("activity-row");activity.getChildren().add(line);}
        for(var op:data.operations.stream().limit(2).toList()){var line=row(text(op.method().equals("RECYCLE")?t("Moved to Recycle Bin","Перемещение в Корзину"):t("File moved","Перемещение файла"),"file-name"),space(),pill(status(op.status()),op.status().equals("SUCCESS")?"success":"warning"),text(Formats.date(op.time()),"small-muted"));line.getStyleClass().add("activity-row");activity.getChildren().add(line);}
        getChildren().addAll(activity,text(t("Your files stay on this computer. Every change starts with your confirmation.","Файлы остаются на вашем компьютере. Изменения — только после вашего подтверждения."),"safe-note"));
    }
    private VBox tool(int icon,String title,String description,Runnable action){var mark=new StackPane(Icons.node(icon));mark.getStyleClass().add("icon-tile");var box=card(mark,text(title,"card-title"),paragraph(description,"muted"),button(t("Open  →","Открыть  →"),action,"link-button"));box.getStyleClass().add("tool-card");return box;}
    private String t(String en,String russian){return ru?russian:en;}
    private String status(String value){return switch(value){case "COMPLETE","SUCCESS"->t("Completed","Завершено");case "CANCELLED"->t("Cancelled","Отменено");case "PARTIAL","LIMIT_REACHED"->t("Partial result","Частичный результат");case "FAILED"->t("Failed","Ошибка");case "RESTORED"->t("Restored","Восстановлено");default->t("Needs review","Требует проверки");};}
}
