package com.enro.ui;

import com.enro.app.AppContext;
import com.enro.automation.AutomationService;
import com.enro.cleanup.CleanupService;
import com.enro.core.Cancellation;
import com.enro.core.Models.*;
import com.enro.duplicates.DuplicateFinder;
import com.enro.recovery.OrganizerService;
import com.enro.scanner.*;
import com.enro.settings.AppSettings;
import com.enro.storage.ExportService;
import com.enro.utils.Formats;
import com.enro.windows.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;
import javafx.util.StringConverter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.*;
import static com.enro.ui.Ui.*;

/** Enro presentation layer. Filesystem mutations remain in independently tested services. */
public final class MainController {
    @FXML private BorderPane shell;
    @FXML private VBox navigation,bottomNavigation;
    @FXML private StackPane content;
    @FXML private HBox pathbar;
    @FXML private Label localLabel,breadcrumb,title,subtitle,status;
    @FXML private TextField pathField;
    @FXML private Button mainAction,helpButton,languageButton,refreshButton,browseButton,scanButton,cancelButton;
    @FXML private ProgressIndicator progress;
    private final Map<Integer,Button> navButtons=new HashMap<>();
    private final DriveService drives=new DriveService();
    private AppContext app;
    private Stage stage;
    private int page,revision;
    private long scanId,lastProgress;
    private Entry directory;
    private boolean busy,duplicatesKnown;
    private Cancellation cancellation=new Cancellation();
    private List<DuplicateGroup> duplicateGroups=List.of();
    private final Map<String,List<Candidate>> cleanupCache=new HashMap<>();

    private String t(String en,String ru){return app.settings.language.equals("ru")?ru:en;}
    private boolean russian(){return app.settings.language.equals("ru");}

    public void initialize(AppContext context,Stage window){
        app=context;stage=window;pathField.setText(System.getProperty("user.home"));
        applyTheme();localize();
        app.automation=new AutomationService(app.db,()->ScanOptions.from(app.settings),message->{
            if(app.settings.notifications)Platform.runLater(()->status.setText(t("Scheduled check: ","Плановая проверка: ")+message));
        });
        app.automation.start();
        load(()->app.db.scans(1),scans->{
            if(!scans.isEmpty()){scanId=scans.getFirst().id();pathField.setText(scans.getFirst().root());}
            showPage(0);
            if(app.settings.scanOnStartup&&!app.settings.startupRoot.isBlank())startScan(Path.of(app.settings.startupRoot),1,null);
        });
    }

    private String pageName(int id){return switch(id){
        case 0->t("Overview","Обзор");case 1->t("Files","Файлы");case 2->t("Cleanup","Очистка");
        case 3->t("Duplicates","Дубликаты");case 4->t("Large files","Крупные файлы");case 5->t("Downloads","Загрузки");
        case 6->t("History","История");case 7->t("Scheduled checks","Расписание");default->t("Settings","Настройки");
    };}
    private void localize(){
        navigation.getChildren().clear();bottomNavigation.getChildren().clear();navButtons.clear();
        navigation.getChildren().add(text(t("YOUR SPACE","ВАШЕ ХРАНИЛИЩЕ"),"nav-section"));
        for(int id:new int[]{0,1,4,3,2})addNavigation(id,navigation);
        navigation.getChildren().add(text(t("ORGANIZE","УПРАВЛЕНИЕ"),"nav-section"));
        for(int id:new int[]{5,6})addNavigation(id,navigation);
        for(int id:new int[]{7,8})addNavigation(id,bottomNavigation);
        localLabel.setText(t("On your computer","На вашем компьютере"));
        helpButton.setText(t("Help","Помощь"));languageButton.setText(app.settings.language.toUpperCase(Locale.ROOT));
        refreshButton.setText("↻");refreshButton.setTooltip(new Tooltip(t("Refresh this page","Обновить страницу")));
        browseButton.setText(t("Change…","Изменить…"));scanButton.setText(t("Analyze","Проверить"));
        cancelButton.setText(t("Stop","Остановить"));status.setText(t("Ready · Enro works locally","Готово · Enro работает локально"));
    }
    private void addNavigation(int id,VBox parent){
        var button=button(pageName(id),()->showPage(id),"nav-button");button.setGraphic(Icons.node(id));button.setGraphicTextGap(11);button.setMaxWidth(Double.MAX_VALUE);
        navButtons.put(id,button);parent.getChildren().add(button);
    }
    private void applyTheme(){
        shell.getStyleClass().removeAll("dark","jade","obsidian","arctic","crimson","mono");
        if(app.settings.appearance.equals("dark"))shell.getStyleClass().add("dark");
        String accent=switch(app.settings.theme){case "Jade"->"jade";case "Obsidian Yellow"->"obsidian";case "Arctic Silver"->"arctic";case "Crimson Noir"->"crimson";case "Monochrome"->"mono";default->"";};
        if(!accent.isEmpty())shell.getStyleClass().add(accent);
    }
    private void showPage(int id){
        page=id;revision++;content.getChildren().clear();
        navButtons.forEach((key,button)->{button.getStyleClass().remove("active");if(key==id)button.getStyleClass().add("active");});
        title.setText(pageName(id));breadcrumb.setText(pageName(id));
        subtitle.setText(switch(id){
            case 0->t("More room for what matters.","Больше места для того, что важно.");
            case 1->t("See what takes space, in a list or on a map.","Узнайте, что занимает место: в списке или на карте.");
            case 2->t("Choose what to remove. Enro will check each file again.","Вы выбираете файлы. Enro повторно проверяет их перед действием.");
            case 3->t("Find identical content and keep the copy you need.","Найдите одинаковое содержимое и сохраните нужный экземпляр.");
            case 4->t("Start with the biggest files. Keep anything you need.","Начните с самых больших файлов. Сохраните всё нужное.");
            case 5->t("Bring order to your downloaded files.","Наведите порядок в загруженных файлах.");
            case 6->t("Your file operations and available recovery options.","Ваши операции с файлами и доступное восстановление.");
            case 7->t("Regular analysis, without automatic deletion.","Регулярный анализ без автоматического удаления.");
            default->t("Make Enro comfortable for you.","Настройте Enro под себя.");
        });
        visible(pathbar,id==1||id==4||id==3);visible(mainAction,id!=2&&id!=8);
        mainAction.setText(switch(id){case 3->t("Find duplicates","Найти дубликаты");case 5->t("Analyze Downloads","Проверить загрузки");case 6->t("Open Recycle Bin","Открыть Корзину");case 7->t("Add a schedule","Добавить расписание");default->t("Choose a folder","Выбрать папку");});
        mainAction.setDisable(busy);
        switch(id){case 0->dashboard();case 1->filesPage();case 2->cleanupPage();case 3->duplicatesPage();case 4->largeFilesPage();case 5->downloadsPage();case 6->recoveryPage();case 7->automationPage();case 8->settingsPage();default->throw new IllegalArgumentException();}
    }
    private void visible(Node node,boolean shown){node.setVisible(shown);node.setManaged(shown);}
    private void display(Node node){content.getChildren().setAll(node);}
    private ScrollPane scrolling(Node node){var pane=new ScrollPane(node);pane.setFitToWidth(true);pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);display(pane);return pane;}

    @FXML public void primaryAction(){switch(page){case 3->findDuplicates();case 5->startScan(drives.downloads(),5,null);case 6->openBin();case 7->addSchedule();default->chooseAndScan(page==4?4:1);}}
    @FXML public void refresh(){if(!busy)showPage(page);}
    @FXML public void cancel(){cancellation.cancel();status.setText(t("Stopping after the current file…","Останавливаем после текущего файла…"));}
    @FXML public void browse(){var root=chooseFolder();if(root!=null)pathField.setText(root.toString());}
    @FXML public void scan(){try{startScan(Path.of(pathField.getText()),page,null);}catch(Exception e){error(e);}}
    private Path chooseFolder(){
        var chooser=new DirectoryChooser();chooser.setTitle(t("Choose a folder for Enro","Выберите папку для Enro"));
        try{Path current=Path.of(pathField.getText());if(Files.isDirectory(current))chooser.setInitialDirectory(current.toFile());}catch(Exception ignored){}
        var selected=chooser.showDialog(stage);return selected==null?null:selected.toPath();
    }
    private void chooseAndScan(int destination){Path root=chooseFolder();if(root!=null){pathField.setText(root.toString());startScan(root,destination,destination==3?this::findDuplicates:null);}}
    private void startScan(Path root,int destination,Runnable after){
        if(busy)return;
        run(t("Analyzing files…","Проверяем файлы…"),()->new FileScanner(app.db).scan(root,ScanOptions.from(app.settings),cancellation,p->{
            long now=System.nanoTime();if(now-lastProgress>180_000_000){lastProgress=now;Platform.runLater(()->status.setText(p.files()+t(" files · "," файлов · ")+Formats.bytes(p.bytes())+(p.errors()>0?" · "+p.errors()+t(" skipped/issues"," пропусков/замечаний"):"")));}
        }),result->{
            scanId=result.scanId();directory=null;duplicateGroups=List.of();duplicatesKnown=false;pathField.setText(root.toAbsolutePath().toString());
            showPage(destination);status.setText(state(result.status())+" · "+result.files()+t(" files · "," файлов · ")+Formats.bytes(result.bytes()));if(after!=null&&result.status().equals("COMPLETE"))after.run();
        });
    }
    private <T>void load(Callable<T> work,Consumer<T> done){
        int current=revision;
        try{app.workers.execute(()->{try{T value=work.call();Platform.runLater(()->{if(current==revision)try{done.accept(value);}catch(Exception e){error(e);}});}catch(Exception e){Platform.runLater(()->{if(current==revision)error(e);});}});}catch(Exception e){error(e);}
    }
    private <T>void run(String message,Callable<T> work,Consumer<T> done){
        if(busy){status.setText(t("An operation is already running. You can stop it below.","Операция уже выполняется. Её можно остановить внизу окна."));return;}
        busy=true;cancellation=new Cancellation();visible(progress,true);visible(cancelButton,true);mainAction.setDisable(true);scanButton.setDisable(true);status.setText(message);
        try{app.workers.execute(()->{try{T value=work.call();Platform.runLater(()->{finishBusy();try{done.accept(value);}catch(Exception e){error(e);}});}catch(Exception e){Platform.runLater(()->{finishBusy();if(e instanceof java.util.concurrent.CancellationException)status.setText(t("Stopped","Остановлено"));else error(e);});}});}catch(Exception e){finishBusy();error(e);}
    }
    private void finishBusy(){busy=false;visible(progress,false);visible(cancelButton,false);mainAction.setDisable(false);scanButton.setDisable(false);status.setText(t("Done","Готово"));}
    private void decorate(Dialog<?> dialog){dialog.initOwner(stage);dialog.getDialogPane().getStylesheets().add(getClass().getResource("/css/enro.css").toExternalForm());dialog.getDialogPane().getStyleClass().addAll(shell.getStyleClass());for(var type:dialog.getDialogPane().getButtonTypes()){var node=dialog.getDialogPane().lookupButton(type);if(node instanceof Button button){if(type==ButtonType.CANCEL)button.setText(t("Cancel","Отмена"));else if(type==ButtonType.CLOSE)button.setText(t("Close","Закрыть"));}}}
    private void error(Throwable failure){
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("UI operation: {}",failure.getClass().getSimpleName());
        var alert=new Alert(Alert.AlertType.ERROR);alert.setTitle("Enro");alert.setHeaderText(t("This action could not be completed","Не удалось выполнить действие"));
        alert.setContentText(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());decorate(alert);alert.showAndWait();
    }
    private void info(String heading,String message){var alert=new Alert(Alert.AlertType.INFORMATION,message,ButtonType.OK);alert.setTitle("Enro");alert.setHeaderText(heading);decorate(alert);alert.showAndWait();}
    private boolean confirm(String heading,String message,String action){var yes=new ButtonType(action,ButtonBar.ButtonData.OK_DONE);var alert=new Alert(Alert.AlertType.CONFIRMATION,message,yes,ButtonType.CANCEL);alert.setTitle("Enro");alert.setHeaderText(heading);decorate(alert);return alert.showAndWait().orElse(ButtonType.CANCEL)==yes;}
    @FXML public void toggleLanguage(){try{var next=copySettings();next.language=russian()?"en":"ru";app.save(next);localize();showPage(page);}catch(Exception e){error(e);}}
    @FXML public void help(){info(t("A simple way to use Enro","Как пользоваться Enro"),t("1. Choose a folder and wait for the analysis.\n2. Open Files, Large files or Duplicates.\n3. Review selected files before moving them to the Windows Recycle Bin.\n\nNothing is selected for deletion by default. Scheduled checks only analyze.","1. Выберите папку и дождитесь проверки.\n2. Откройте Файлы, Крупные файлы или Дубликаты.\n3. Проверьте выбранные файлы перед перемещением в Корзину Windows.\n\nПо умолчанию удаление не выбрано. Проверки по расписанию только анализируют."));}
    private String state(String code){return switch(code){case "SUCCESS","COMPLETE"->t("Completed","Завершено");case "RUNNING"->t("Running","Выполняется");case "CANCELLED"->t("Stopped","Остановлено");case "PARTIAL"->t("Some items skipped","Есть пропуски");case "LIMIT_REACHED"->t("Analysis limit reached","Достигнут лимит");case "FAILED"->t("Failed","Ошибка");case "RESTORED"->t("Restored","Восстановлено");default->t("Needs review","Требует проверки");};}
    private String categoryName(String value){return switch(value){case "All"->t("All types","Все типы");case "Folder"->t("Folder","Папка");case "Video"->t("Video","Видео");case "Images"->t("Images","Изображения");case "Audio"->t("Audio","Аудио");case "Archives"->t("Archives","Архивы");case "Documents"->t("Documents","Документы");case "Programs"->t("Applications","Программы");case "System"->t("System","Системные");default->t("Other","Прочее");};}
    private ComboBox<String> categoryFilter(){var box=new ComboBox<String>(FXCollections.observableArrayList("All","Video","Images","Audio","Archives","Documents","Programs","System","Other"));box.setValue("All");box.setConverter(new StringConverter<>(){public String toString(String v){return v==null?"":categoryName(v);}public String fromString(String v){return v;}});box.setPrefWidth(160);return box;}
    private Node empty(int icon,String heading,String description,String action,Runnable run){var mark=new StackPane(Icons.node(icon));mark.getStyleClass().add("icon-tile");var box=new VBox(15,mark,text(heading,"empty-title"),paragraph(description,"muted"),button(action,run,"primary"));box.getStyleClass().add("empty-state");return box;}
    private boolean requireScan(){if(scanId!=0)return true;display(empty(1,t("Choose a folder to begin","Сначала выберите папку"),t("Enro will build a list of your files. Then you can explore their sizes, dates and contents.","Enro составит список файлов. После этого можно посмотреть размеры, даты и совпадения."),t("Choose a folder","Выбрать папку"),()->chooseAndScan(page)));return false;}

    private void dashboard(){load(()->new DashboardView.Data(drives.drives(),app.db.scans(3),app.db.operations(3,0)),data->scrolling(new DashboardView(data,new DashboardView.Actions(()->chooseAndScan(1),root->{pathField.setText(root.toString());startScan(root,1,null);},this::showPage),russian())));}

    private TableView<Entry> fileTable(){
        var table=new TableView<Entry>();table.setPlaceholder(text(t("No matching files","Подходящих файлов нет"),"muted"));table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);table.setMinHeight(100);table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        var name=new TableColumn<Entry,Entry>(t("File or folder","Файл или папка"));name.setCellValueFactory(c->new ReadOnlyObjectWrapper<>(c.getValue()));name.setPrefWidth(480);name.setMinWidth(220);name.setSortable(false);
        name.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(Entry item,boolean empty){super.updateItem(item,empty);if(empty||item==null){setGraphic(null);return;}var glyph=item.directory()?Icons.folder():Icons.node(4);glyph.getStyleClass().add("file-icon");var words=new VBox(3,text(item.name(),"file-name"),text(item.path(),"file-path"));words.setMinWidth(0);HBox.setHgrow(words,Priority.ALWAYS);setGraphic(row(glyph,words));setTooltip(new Tooltip(item.path()));}});
        table.getColumns().add(name);column(table,t("Size","Размер"),e->Formats.bytes(e.size()),110);column(table,t("Type","Тип"),e->categoryName(e.category()),115);column(table,t("Modified","Изменён"),e->Formats.date(e.modified()),155);
        table.setRowFactory(t->{var row=new TableRow<Entry>();row.setOnContextMenuRequested(e->{if(row.isEmpty())return;table.getSelectionModel().clearAndSelect(row.getIndex());var location=new MenuItem(t("Show in Explorer","Показать в Проводнике"));location.setOnAction(a->reveal(row.getItem().file()));var properties=new MenuItem(t("Properties","Свойства"));properties.setOnAction(a->properties(row.getItem()));new ContextMenu(location,properties).show(row,e.getScreenX(),e.getScreenY());});return row;});
        return table;
    }
    private <T>void column(TableView<T> table,String heading,Function<T,String> value,double width){var col=new TableColumn<T,String>(heading);col.setCellValueFactory(c->new ReadOnlyStringWrapper(value.apply(c.getValue())));col.setPrefWidth(width);col.setMinWidth(70);col.setSortable(false);table.getColumns().add(col);}
    private HBox selectionActions(TableView<Entry> table){
        var count=text(t("Select a file for actions","Выберите файл для действий"),"small-muted");
        var location=button(t("Explorer","Проводник"),()->{var item=table.getSelectionModel().getSelectedItem();if(item!=null)reveal(item.file());},"quiet");
        var copy=button(t("Copy path","Копировать путь"),()->{var item=table.getSelectionModel().getSelectedItem();if(item!=null)copy(item.path());},"quiet");
        var details=button(t("Properties","Свойства"),()->{var item=table.getSelectionModel().getSelectedItem();if(item!=null)properties(item);},"quiet");
        var review=button(t("Review selected…","Проверить выбранные…"),()->reviewEntries(List.copyOf(table.getSelectionModel().getSelectedItems())),"secondary");
        var actions=row(location,copy,details,review);visible(actions,false);
        table.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<Entry>)change->{var items=table.getSelectionModel().getSelectedItems();visible(actions,!items.isEmpty());count.setText(items.isEmpty()?t("Select a file for actions","Выберите файл для действий"):items.size()+t(" selected · "," выбрано · ")+Formats.bytes(items.stream().mapToLong(Entry::size).sum()));review.setDisable(items.stream().anyMatch(Entry::directory));});
        var bar=row(count,space(),actions);bar.getStyleClass().add("selection-bar");return bar;
    }
    private void copy(String value){var clipboard=new ClipboardContent();clipboard.putString(value);Clipboard.getSystemClipboard().setContent(clipboard);status.setText(t("Path copied","Путь скопирован"));}
    private void reveal(Path file){load(()->{new ProcessBuilder("explorer.exe","/select,"+file.toAbsolutePath()).start();return true;},v->{});}
    private void openBin(){load(()->{new ProcessBuilder("explorer.exe","shell:RecycleBinFolder").start();return true;},v->{});}
    private void properties(Entry item){
        var box=new VBox(15,text(item.name(),"section-title"),paragraph(item.path(),"muted"),new Separator(),row(text(t("Logical size","Логический размер"),"muted"),space(),text(Formats.bytes(item.size()),"card-title")),row(text(t("Modified","Изменён"),"muted"),space(),text(Formats.date(item.modified()),"muted")),row(text(t("Created","Создан"),"muted"),space(),text(Formats.date(item.created()),"muted")));
        box.setPrefWidth(570);
        if(!item.directory()&&item.category().equals("Images")&&Set.of(".png",".jpg",".jpeg",".gif").contains(Formats.extension(item.name()))&&item.size()<8L*1048576){var image=new javafx.scene.image.Image(item.file().toUri().toString(),550,300,true,true,true);box.getChildren().add(new javafx.scene.image.ImageView(image));}
        var dialog=new Dialog<ButtonType>();dialog.setTitle(t("File properties","Свойства файла"));dialog.getDialogPane().setContent(box);dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);decorate(dialog);dialog.showAndWait();
    }

    private void filesPage(){
        if(!requireScan())return;
        var table=fileTable();var tree=new TreemapView();var tabs=new TabPane();tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        var listTab=new Tab(t("List","Список"),table);var mapHint=text(t("Double-click a folder to open it. Tile area represents logical size.","Двойной щелчок открывает папку. Площадь соответствует логическому размеру."),"small-muted");
        var mapSelection=text(t("Select an item on the map","Выберите элемент на карте"),"small-muted");var showInList=button(t("Show in list","Показать в списке"),()->tabs.getSelectionModel().select(listTab),"link-button");
        var mapBox=new VBox(10,mapHint,tree,row(mapSelection,space(),showInList));VBox.setVgrow(tree,Priority.ALWAYS);var mapTab=new Tab(t("Space map","Карта места"),mapBox);tabs.getTabs().addAll(listTab,mapTab);VBox.setVgrow(tabs,Priority.ALWAYS);
        var folder=text("…","card-title");var meta=text("…","small-muted");var search=new TextField();search.setPromptText(t("Search by path…","Поиск по пути…"));search.setPrefWidth(230);var category=categoryFilter();int[] offset={0},generation={0};
        Runnable[] reload={null};var up=button(t("← Back","← Назад"),()->{if(directory!=null&&directory.parentId()!=0)load(()->app.db.entry(directory.parentId()),parent->{directory=parent;offset[0]=0;reload[0].run();});},"quiet");
        var previous=button("←",()->{offset[0]=Math.max(0,offset[0]-350);reload[0].run();},"quiet");var next=button("→",()->{offset[0]+=350;reload[0].run();},"quiet");var paging=text("","small-muted");
        reload[0]=()->{
            int request=++generation[0],skip=offset[0];String filter=category.getValue(),query=search.getText();Entry focus=directory;long currentScan=scanId;
            load(()->{var dir=focus==null?app.db.root(currentScan):focus;return dir==null?null:new Object[]{dir,app.db.entries(currentScan,dir.id(),0,Long.MAX_VALUE,filter,query,true,350,skip),app.db.scanById(currentScan)};},value->{
                if(request!=generation[0])return;if(value==null){display(empty(1,t("No saved files in this analysis","В этой проверке нет сохранённых файлов"),t("The analysis may have stopped before the first folder.","Проверка могла остановиться до чтения первой папки."),t("Analyze again","Проверить снова"),this::scan));return;}
                directory=(Entry)value[0];@SuppressWarnings("unchecked")var entries=(List<Entry>)value[1];var scan=(Scan)value[2];table.getItems().setAll(entries);tree.setEntries(entries);folder.setText(directory.path());folder.setTooltip(new Tooltip(directory.path()));up.setDisable(directory.parentId()==0);meta.setText(Formats.bytes(directory.size())+" · "+directory.files()+t(" files · "," файлов · ")+state(scan.status()));previous.setDisable(skip==0);next.setDisable(entries.size()<350);paging.setText(t("Page ","Страница ")+(skip/350+1)+" · "+entries.size()+t(" items · largest first"," элементов · сначала крупные"));
            });
        };
        var debounce=new PauseTransition(Duration.millis(280));debounce.setOnFinished(e->{offset[0]=0;reload[0].run();});search.textProperty().addListener((o,a,b)->debounce.playFromStart());category.setOnAction(e->{offset[0]=0;reload[0].run();});
        tree.onOpen(entry->{directory=entry;offset[0]=0;reload[0].run();});tree.onReveal(entry->reveal(entry.file()));tree.onSelect(entry->{table.getSelectionModel().clearSelection();table.getSelectionModel().select(entry);table.scrollTo(entry);mapSelection.setText(entry.name()+" · "+Formats.bytes(entry.size()));});
        table.setOnMouseClicked(e->{if(e.getClickCount()==2){var selected=table.getSelectionModel().getSelectedItem();if(selected!=null){if(selected.directory()){directory=selected;offset[0]=0;reload[0].run();}else properties(selected);}}});
        var tools=row(up,folder,space(),button(t("Issues","Пропуски"),this::scanIssues,"quiet"),button(t("Export CSV","Экспорт CSV"),this::exportScan,"quiet"));HBox.setHgrow(folder,Priority.ALWAYS);folder.setMaxWidth(Double.MAX_VALUE);
        var filters=row(search,category,space(),meta);var box=new VBox(14,tools,filters,tabs,row(previous,paging,next),selectionActions(table));box.setMinHeight(0);display(box);reload[0].run();
    }
    private void largeFilesPage(){
        if(!requireScan())return;
        var table=fileTable();VBox.setVgrow(table,Priority.ALWAYS);var minimum=new TextField("100");minimum.setPrefWidth(75);var days=new TextField("0");days.setPrefWidth(60);var category=categoryFilter();var search=new TextField();search.setPromptText(t("Search by path…","Поиск по пути…"));HBox.setHgrow(search,Priority.ALWAYS);var meta=text("","small-muted");int[] offset={0};
        var previous=button("←",()->{},"quiet");var next=button("→",()->{},"quiet");Runnable[] reload={null};
        reload[0]=()->{try{long min=Math.multiplyExact(Long.parseLong(minimum.getText()),1048576L);long age=Long.parseLong(days.getText());if(min<0||age<0||age>36500)throw new IllegalArgumentException(t("Check size and date values","Проверьте размер и число дней"));String type=category.getValue(),query=search.getText();int skip=offset[0];long currentScan=scanId;load(()->app.db.entries(currentScan,null,min,age==0?Long.MAX_VALUE:System.currentTimeMillis()-age*86400000L,type,query,false,500,skip),entries->{table.getItems().setAll(entries);previous.setDisable(skip==0);next.setDisable(entries.size()<500);meta.setText(t("Page ","Страница ")+(skip/500+1)+" · "+entries.size()+t(" files · largest first"," файлов · сначала крупные"));});}catch(Exception e){error(e);}};
        previous.setOnAction(e->{offset[0]=Math.max(0,offset[0]-500);reload[0].run();});next.setOnAction(e->{offset[0]+=500;reload[0].run();});
        var apply=button(t("Apply","Применить"),()->{offset[0]=0;reload[0].run();},"secondary");
        var filterBox=new VBox(12,row(text(t("At least, MiB","От, МиБ"),"small-muted"),minimum,category,text(t("Older than, days","Старше, дней"),"small-muted"),days,space(),apply),row(search,text(t("0 days means any date","0 дней — любая дата"),"small-muted")));filterBox.getStyleClass().add("filters");
        display(new VBox(14,filterBox,table,row(previous,meta,next),selectionActions(table)));reload[0].run();
    }

    private void duplicatesPage(){
        if(!requireScan())return;
        if(!duplicatesKnown){display(empty(3,t("Find the extra copies","Найдём лишние копии"),t("Enro compares file contents. Names alone are never enough to call files duplicates.","Enro сравнивает содержимое файлов. Совпадение имён само по себе не считается дубликатом."),t("Find duplicates","Найти дубликаты"),this::findDuplicates));return;}
        if(duplicateGroups.isEmpty()){display(empty(3,t("No duplicate copies found","Одинаковых копий не найдено"),t("You can analyze another folder or run the comparison again.","Можно проверить другую папку или повторить сравнение после изменений."),t("Choose another folder","Выбрать другую папку"),()->chooseAndScan(3)));return;}
        var groups=new ListView<DuplicateGroup>();groups.getItems().setAll(duplicateGroups);groups.setMinWidth(210);groups.setPrefWidth(265);
        groups.setCellFactory(v->new ListCell<>(){@Override protected void updateItem(DuplicateGroup group,boolean empty){super.updateItem(group,empty);if(empty||group==null){setGraphic(null);return;}var title=text(group.copies().size()+t(" identical files"," одинаковых файла"),"card-title");setGraphic(new VBox(6,title,text(Formats.bytes(group.size())+t(" each"," каждый"),"small-muted")));setTooltip(new Tooltip("SHA-256: "+group.hash()));}});
        var keeper=paragraph("","muted");var copies=new ListView<Snapshot>();copies.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);copies.setCellFactory(v->new ListCell<>(){@Override protected void updateItem(Snapshot snapshot,boolean empty){super.updateItem(snapshot,empty);if(empty||snapshot==null){setGraphic(null);return;}var words=new VBox(5,text(snapshot.file().getFileName().toString(),"file-name"),paragraph(snapshot.path(),"file-path"));words.setMinWidth(0);words.prefWidthProperty().bind(v.widthProperty().subtract(42));setGraphic(words);setTooltip(new Tooltip(snapshot.path()));}});
        var review=button(t("Review selected copies…","Проверить выбранные копии…"),()->{var group=groups.getSelectionModel().getSelectedItem();if(group==null)return;var selected=List.copyOf(copies.getSelectionModel().getSelectedItems());var candidates=selected.stream().map(s->new Candidate(s,"Duplicate",t("Identical content. A retained copy is protected.","Содержимое совпадает. Сохраняемый экземпляр защищён."),Risk.MEDIUM,s.file().getParent())).toList();if(!candidates.isEmpty())previewCleanup(candidates,List.of(group.keeper()));},"secondary");review.setDisable(true);
        copies.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<Snapshot>)e->review.setDisable(copies.getSelectionModel().getSelectedItems().isEmpty()));
        groups.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(b==null)return;keeper.setText(b.keeper().path());copies.getItems().setAll(b.copies().subList(1,b.copies().size()));});
        var retained=card(row(pill(t("Kept","Сохраняется"),"success"),text(t("This copy will stay","Этот экземпляр останется"),"card-title")),keeper);
        var right=new VBox(14,retained,text(t("Other copies · select with Ctrl or Shift","Другие копии · выбирайте с Ctrl или Shift"),"small-muted"),copies,row(review,space(),button(t("Copy path","Копировать путь"),()->{var selected=copies.getSelectionModel().getSelectedItem();if(selected!=null)copy(selected.path());},"quiet")));VBox.setVgrow(copies,Priority.ALWAYS);
        var split=new SplitPane(groups,right);split.setDividerPositions(.29);VBox.setVgrow(split,Priority.ALWAYS);
        long redundant=duplicateGroups.stream().mapToLong(DuplicateGroup::redundantBytes).sum();
        var meta=row(pill(duplicateGroups.size()+t(" groups"," групп"),"neutral"),text(Formats.bytes(redundant)+t(" in extra copies · logical size"," в лишних копиях · логический размер"),"muted"));
        display(new VBox(16,meta,split));groups.getSelectionModel().selectFirst();
    }
    private void findDuplicates(){
        if(scanId==0){chooseAndScan(3);return;}long input=scanId;
        try{Path requested=Path.of(pathField.getText()).toAbsolutePath().normalize();load(()->app.db.scanById(input),saved->{if(saved==null||!Path.of(saved.root()).toAbsolutePath().normalize().equals(requested)){startScan(requested,3,this::findDuplicates);return;}compareDuplicates(input);});}catch(Exception e){error(e);}
    }
    private void compareDuplicates(long input){
        run(t("Comparing file contents…","Сравниваем содержимое файлов…"),()->new DuplicateFinder(app.db).find(input,cancellation,count->Platform.runLater(()->status.setText(t("Files compared: ","Проверено файлов: ")+count))),result->{duplicateGroups=result.groups();duplicatesKnown=true;showPage(3);status.setText(t("Groups found: ","Найдено групп: ")+result.groups().size()+(result.errors()>0?" · "+result.errors()+t(" read errors"," ошибок чтения"):"")+(result.limited()?t(" · partial result: limit reached"," · результат неполный: достигнут лимит"):""));});
    }

    private void cleanupPage(){
        var steps=row(pill("1", "neutral"),text(t("Find candidates","Найти кандидатов"),"step-label"),text("→","small-muted"),pill("2","neutral"),text(t("Choose files","Выбрать файлы"),"step-label"),text("→","small-muted"),pill("3","neutral"),text(t("Confirm","Подтвердить"),"step-label"));
        var categories=card(text(t("What would you like to review?","Что хотите проверить?"),"section-title"));
        categories.getChildren().add(cleanupCategory(2,"User Temp",t("Temporary files","Временные файлы"),t("Files in your Temp folder that have not changed in at least 7 days.","Файлы в пользовательской Temp, которые не изменялись как минимум 7 дней."),DriveService.userTemp()));
        categories.getChildren().add(cleanupCategory(5,"Downloads",t("Downloads","Загрузки"),t("Installers, archives and other downloaded files for your review.","Установщики, архивы и другие загрузки для вашей проверки."),drives.downloads()));
        var bin=card(row(new StackPane(Icons.node(6)),new VBox(5,text(t("Need something back?","Нужно что-то вернуть?"),"card-title"),paragraph(t("Files moved to the Windows Recycle Bin can be restored there while they remain available.","Файлы, перемещённые в Корзину Windows, можно восстановить там, пока они доступны."),"muted")),space(),button(t("Open Recycle Bin","Открыть Корзину"),this::openBin,"quiet")));
        scrolling(new VBox(20,steps,categories,paragraph(t("Enro does not decide whether a file is needed. Nothing is selected automatically. System files and sensitive application data are protected.","Enro не решает за вас, нужен ли файл. Ничего не выбирается автоматически. Системные файлы и чувствительные данные приложений защищены."),"safe-note"),bin));
    }
    private Node cleanupCategory(int icon,String key,String heading,String description,Path root){
        var marker=new StackPane(Icons.node(icon));marker.getStyleClass().add("icon-tile");var detail=new VBox(6,text(heading,"card-title"),paragraph(description,"muted"));detail.setMinWidth(0);HBox.setHgrow(detail,Priority.ALWAYS);
        var found=cleanupCache.get(key);var size=found==null?text(t("Not checked","Не проверено"),"small-muted"):text(Formats.bytes(found.stream().mapToLong(c->c.snapshot().size()).sum()),"card-title");
        var inspect=button(t("Review files","Проверить файлы"),()->run(t("Finding review candidates…","Ищем кандидатов для проверки…"),()->app.cleanup.discover(root,key,cancellation),items->{cleanupCache.put(key,List.copyOf(items));if(items.isEmpty()){showPage(2);info(t("No supported candidates","Кандидатов не найдено"),t("No files matched the supported rules. Nothing was changed.","Ни один файл не подошёл под поддерживаемые правила. Ничего не изменено."));}else previewCleanup(items,List.of());}),"secondary");
        var line=row(marker,detail,size,inspect);line.getStyleClass().add("category-row");return line;
    }
    private void reviewEntries(List<Entry> selected){
        if(selected.isEmpty())return;
        load(()->{var list=new ArrayList<Candidate>();for(var entry:selected){if(entry.directory())throw new IOException(t("Select individual files, not folders.","Выберите отдельные файлы, а не папки."));var snapshot=Snapshot.capture(entry.file());if(snapshot.size()!=entry.size()||snapshot.modified()!=entry.modified()||snapshot.created()!=entry.created()||!snapshot.identity().equals(entry.identity()))throw new IOException(t("A file has changed. Analyze the folder again.","Один из файлов изменился. Проверьте папку ещё раз."));list.add(new Candidate(snapshot,"Manual",t("Selected by you. Review whether it is needed.","Выбрано вами. Проверьте, нужен ли этот файл."),Risk.MEDIUM,entry.file().getParent()));}return list;},items->previewCleanup(items,List.of()));
    }
    private static final class Choice {
        final Candidate item;final BooleanProperty chosen=new SimpleBooleanProperty(false);
        Choice(Candidate item){this.item=item;}
    }
    private String candidateReason(Candidate item){return switch(item.category()){case "User Temp"->t("Inside user Temp; unchanged for 7 days. Check which application owns it.","В пользовательской Temp, без изменений 7 дней. Проверьте принадлежность приложению.");case "Downloads"->t("Downloaded file for manual review. Age and size do not imply that it is unnecessary.","Загруженный файл для ручной проверки. Возраст и размер не означают, что он не нужен.");default->item.reason();};}
    private void previewCleanup(List<Candidate> items,List<Snapshot> keepers){
        if(items.isEmpty())return;
        var choices=items.stream().map(Choice::new).toList();var table=new TableView<Choice>();table.setEditable(true);table.getItems().setAll(choices);table.setPrefSize(960,370);table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        var pick=new TableColumn<Choice,Boolean>("");pick.setCellValueFactory(c->c.getValue().chosen);pick.setCellFactory(CheckBoxTableCell.forTableColumn(pick));pick.setMinWidth(42);pick.setMaxWidth(42);pick.setSortable(false);table.getColumns().add(pick);
        column(table,t("Full path","Полный путь"),c->c.item.snapshot().path(),370);column(table,t("Size","Размер"),c->Formats.bytes(c.item.snapshot().size()),100);column(table,t("Category","Категория"),c->switch(c.item.category()){case "User Temp"->t("Temp","Временные");case "Downloads"->t("Downloads","Загрузки");case "Duplicate"->t("Duplicate","Дубликат");default->t("Manual","Вручную");},110);column(table,t("Why review it","Почему предложен"),c->candidateReason(c.item),220);column(table,t("Risk","Риск"),c->c.item.risk()==Risk.MEDIUM?t("Medium","Средний"):c.item.risk().toString(),110);
        var fullPath=paragraph(t("Select a row to read its full path.","Выберите строку, чтобы прочитать полный путь."),"small-muted");table.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(b!=null)fullPath.setText(b.item.snapshot().path()+"\n"+candidateReason(b.item));});
        var count=text(t("Nothing selected","Ничего не выбрано"),"card-title");var dialog=new Dialog<ButtonType>();dialog.setTitle(t("Review files","Проверка файлов"));
        var next=new ButtonType(t("Continue","Далее"),ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().getButtonTypes().addAll(next,ButtonType.CANCEL);var proceed=dialog.getDialogPane().lookupButton(next);proceed.setDisable(true);
        Runnable update=()->{var checked=choices.stream().filter(c->c.chosen.get()).toList();count.setText(checked.size()+t(" selected · "," выбрано · ")+Formats.bytes(checked.stream().mapToLong(c->c.item.snapshot().size()).sum()));proceed.setDisable(checked.isEmpty());};choices.forEach(c->c.chosen.addListener((o,a,b)->update.run()));
        var controls=row(count,space(),button(t("Select all","Выбрать все"),()->choices.forEach(c->{if(c.item.risk()!=Risk.HIGH&&c.item.risk()!=Risk.UNKNOWN)c.chosen.set(true);}),"quiet"),button(t("Clear","Снять выбор"),()->choices.forEach(c->c.chosen.set(false)),"quiet"));
        var copy=paragraph(t("Only the checked files will be processed. Method: Windows Recycle Bin. Recycling does not guarantee that space is freed immediately.","Будут обработаны только отмеченные файлы. Способ: Корзина Windows. Перемещение в неё не гарантирует немедленного освобождения места."),"muted");
        dialog.getDialogPane().setContent(new VBox(14,text(t("Keep what matters. Choose the rest.","Сохраните важное. Выберите остальное."),"section-title"),copy,controls,table,fullPath));decorate(dialog);
        if(dialog.showAndWait().orElse(ButtonType.CANCEL)!=next)return;
        var selected=choices.stream().filter(c->c.chosen.get()).map(c->c.item).toList();
        load(()->app.cleanup.preview(selected,keepers),plan->{
            if(!confirm(t("Move these files to the Recycle Bin?","Переместить эти файлы в Корзину?"),plan.items().size()+t(" files · "," файлов · ")+Formats.bytes(plan.bytes())+"\n\n"+t("Changed files will be skipped. Recovery is managed by Windows while files remain in the Recycle Bin.","Изменённые файлы будут пропущены. Восстановление доступно средствами Windows, пока файлы находятся в Корзине."),t("Move to Recycle Bin","В Корзину")))return;
            run(t("Moving selected files…","Перемещаем выбранные файлы…"),()->app.cleanup.executeConfirmed(plan,cancellation),report->{cleanupCache.clear();duplicatesKnown=false;duplicateGroups=List.of();info(t("Operation complete","Операция завершена"),t("Moved: ","Перемещено: ")+report.succeeded()+t("\nSkipped or failed: ","\nПропущено или ошибка: ")+report.failed()+t("\nLogical size: ","\nЛогический размер: ")+Formats.bytes(report.recycledLogicalBytes()));showPage(6);});
        });
    }

    private void exportScan(){
        if(scanId==0||!confirm(t("Export file paths?","Экспортировать пути файлов?"),t("The CSV includes full file paths and may contain personal information.","CSV содержит полные пути файлов, в которых может быть личная информация."),t("Choose location","Выбрать место")))return;
        var chooser=new FileChooser();chooser.setInitialFileName("Enro-scan-"+scanId+".csv");var file=chooser.showSaveDialog(stage);if(file!=null){long id=scanId;run(t("Exporting…","Экспортируем…"),()->{new ExportService(app.db).scanCsv(id,file.toPath(),cancellation);return true;},v->status.setText(t("CSV saved","CSV сохранён")));}
    }
    private void scanIssues(){
        long id=scanId;load(()->app.db.scanIssues(id),issues->{var table=new TableView<ScanIssue>();table.getItems().setAll(issues);table.setPrefSize(830,380);table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);column(table,t("Path","Путь"),ScanIssue::path,620);column(table,t("Reason","Причина"),ScanIssue::kind,180);var dialog=new Dialog<ButtonType>();dialog.setTitle(t("Skipped items · first 1000 records","Пропущенные элементы · первые 1000 записей"));dialog.getDialogPane().setContent(table);dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);decorate(dialog);dialog.showAndWait();});
    }

    private void downloadsPage(){
        Path root=drives.downloads();var table=fileTable();VBox.setVgrow(table,Priority.ALWAYS);var category=categoryFilter();var path=text(root.toString(),"small-muted");path.setTooltip(new Tooltip(root.toString()));var search=new TextField();search.setPromptText(t("Search downloads…","Поиск в загрузках…"));HBox.setHgrow(search,Priority.ALWAYS);
        var summary=new FlowPane(9,9);var paging=text("","small-muted");int[] offset={0};long[] downloadScan={0};Runnable[] reload={null};
        var previous=button("←",()->{offset[0]=Math.max(0,offset[0]-500);reload[0].run();},"quiet");var next=button("→",()->{offset[0]+=500;reload[0].run();},"quiet");
        reload[0]=()->{String filter=category.getValue(),query=search.getText();int skip=offset[0];load(()->{
            var found=app.db.scans(200).stream().filter(s->Path.of(s.root()).equals(root)).findFirst();
            if(found.isEmpty())return null;var scan=found.get();return new Object[]{scan,app.db.entries(scan.id(),null,0,Long.MAX_VALUE,filter,query,false,500,skip),app.db.categories(scan.id())};
        },data->{
            if(data==null){table.getItems().clear();table.setPlaceholder(text(t("Click Analyze Downloads to get started.","Нажмите «Проверить загрузки», чтобы начать."),"muted"));paging.setText(t("Not analyzed yet","Пока не проверено"));previous.setDisable(true);next.setDisable(true);return;}
            var scan=(Scan)data[0];downloadScan[0]=scan.id();@SuppressWarnings("unchecked")var entries=(List<Entry>)data[1];@SuppressWarnings("unchecked")var types=(List<Category>)data[2];table.getItems().setAll(entries);summary.getChildren().clear();for(var item:types)summary.getChildren().add(pill(categoryName(item.name())+" · "+Formats.bytes(item.bytes()),"neutral"));paging.setText(t("Page ","Страница ")+(skip/500+1)+" · "+entries.size()+t(" files · snapshot "," файлов · проверка ")+Formats.date(scan.started()));previous.setDisable(skip==0);next.setDisable(entries.size()<500);
        });};
        category.setOnAction(e->{offset[0]=0;reload[0].run();});search.setOnAction(e->{offset[0]=0;reload[0].run();});
        var move=button(t("Move…","Переместить…"),()->organize(table,root,false),"quiet");var rename=button(t("Rename…","Переименовать…"),()->organize(table,root,true),"quiet");move.setDisable(true);rename.setDisable(true);table.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<Entry>)e->{boolean empty=table.getSelectionModel().getSelectedItems().isEmpty();move.setDisable(empty);rename.setDisable(empty);});
        var duplicates=button(t("Find duplicates","Найти дубликаты"),()->{if(downloadScan[0]==0){pathField.setText(root.toString());startScan(root,3,this::findDuplicates);}else{scanId=downloadScan[0];directory=null;duplicatesKnown=false;duplicateGroups=List.of();pathField.setText(root.toString());findDuplicates();}},"quiet");
        var box=new VBox(14,row(path,space(),duplicates,move,rename),row(search,category,button(t("Find","Найти"),()->{offset[0]=0;reload[0].run();},"secondary")),summary,table,row(previous,paging,next),selectionActions(table));box.setMinHeight(0);display(box);reload[0].run();
    }
    private void organize(TableView<Entry> table,Path root,boolean renaming){
        var selected=List.copyOf(table.getSelectionModel().getSelectedItems());if(selected.isEmpty())return;String prefix=null;Path destination=null;
        if(renaming){var input=new TextInputDialog(t("File","Файл"));input.setTitle(t("Batch rename","Пакетное переименование"));input.setHeaderText(t("Name prefix","Начало имени файла"));input.setContentText(t("Enro adds a number and keeps the extension.","Enro добавит номер и сохранит расширение."));decorate(input);var value=input.showAndWait();if(value.isEmpty())return;prefix=value.get();}
        else{var chooser=new DirectoryChooser();chooser.setTitle(t("Choose an existing folder inside Downloads","Выберите существующую папку внутри Загрузок"));if(Files.isDirectory(root))chooser.setInitialDirectory(root.toFile());var folder=chooser.showDialog(stage);if(folder==null)return;destination=folder.toPath();}
        String namePrefix=prefix;Path target=destination;
        load(()->{var moves=new ArrayList<OrganizerService.Move>();int sequence=1;for(var entry:selected){var snapshot=Snapshot.capture(entry.file());if(!snapshot.identity().equals(entry.identity())||snapshot.size()!=entry.size()||snapshot.modified()!=entry.modified()||snapshot.created()!=entry.created())throw new IOException(t("A selected file changed. Analyze Downloads again.","Выбранный файл изменился. Проверьте Загрузки ещё раз."));var to=renaming?OrganizerService.renamed(entry.file(),namePrefix,sequence++):target.resolve(entry.name());var move=new OrganizerService.Move(snapshot,to,root);app.organizer.validate(move);moves.add(move);}if(moves.stream().map(OrganizerService.Move::target).distinct().count()!=moves.size())throw new IOException(t("Some destination names conflict.","Некоторые новые имена совпадают."));return moves;},moves->{
            var preview=new TableView<OrganizerService.Move>();preview.getItems().setAll(moves);preview.setPrefSize(900,370);preview.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);column(preview,t("Current path","Текущий путь"),m->m.source().path(),450);column(preview,t("New path","Новый путь"),m->m.target().toString(),450);
            var dialog=new Dialog<ButtonType>();dialog.setTitle(t("Review changes","Проверьте изменения"));dialog.getDialogPane().setContent(new VBox(14,paragraph(t("Existing files will not be overwritten. These moves can be undone in History while the file remains unchanged.","Существующие файлы не будут перезаписаны. Перемещения можно отменить в Истории, пока файл не изменился."),"muted"),preview));var execute=new ButtonType(t("Apply changes","Применить изменения"),ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().getButtonTypes().addAll(execute,ButtonType.CANCEL);decorate(dialog);
            if(dialog.showAndWait().orElse(ButtonType.CANCEL)!=execute)return;
            run(t("Organizing selected files…","Обрабатываем выбранные файлы…"),()->{int success=0,failed=0;for(var move:moves){cancellation.check();try{app.organizer.executeConfirmed(move);success++;}catch(Exception e){failed++;}}return new int[]{success,failed};},report->{info(t("Changes complete","Изменения завершены"),t("Completed: ","Выполнено: ")+report[0]+t("\nSkipped or failed: ","\nПропущено или ошибка: ")+report[1]);showPage(6);});
        });
    }

    private void recoveryPage(){
        var table=new TableView<Operation>();table.setMinHeight(120);table.setPlaceholder(text(t("Your file operations will appear here.","Здесь появятся ваши операции с файлами."),"muted"));table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        column(table,t("When","Когда"),o->Formats.date(o.time()),140);column(table,t("Action","Действие"),o->o.method().equals("RECYCLE")?t("Recycle Bin","В Корзину"):t("Move","Перемещение"),125);column(table,t("Status","Статус"),o->state(o.status()),140);column(table,t("File","Файл"),Operation::source,400);column(table,t("Size","Размер"),o->Formats.bytes(o.bytes()),100);VBox.setVgrow(table,Priority.ALWAYS);
        var detail=paragraph(t("Select an operation to see its source and destination.","Выберите операцию, чтобы увидеть исходный и новый путь."),"small-muted");detail.getStyleClass().add("detail-strip");
        var undo=button(t("Undo move","Отменить перемещение"),()->{var item=table.getSelectionModel().getSelectedItem();if(item!=null&&confirm(t("Restore the original location?","Вернуть файл на прежнее место?"),item.source(),t("Restore","Восстановить")))run(t("Restoring location…","Восстанавливаем расположение…"),()->{app.organizer.restore(item);return true;},v->showPage(6));},"secondary");undo.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{undo.setDisable(b==null||!b.method().equals("MOVE")||!b.status().equals("SUCCESS"));if(b!=null)detail.setText(b.source()+"\n→ "+b.target());});
        int[] offset={0};var count=text("","small-muted");Runnable reload=()->load(()->app.db.operations(500,offset[0]),entries->{table.getItems().setAll(entries);count.setText(t("Page ","Страница ")+(offset[0]/500+1)+" · "+entries.size()+t(" records"," записей"));});
        var previous=button("←",()->{offset[0]=Math.max(0,offset[0]-500);reload.run();},"quiet");var next=button("→",()->{if(table.getItems().size()==500){offset[0]+=500;reload.run();}},"quiet");
        var box=new VBox(14,paragraph(t("Restore recycled files through Windows. Moves can be undone here if the file is unchanged and its old name is available.","Файлы из Корзины восстанавливаются средствами Windows. Перемещение можно отменить здесь, если файл не изменился и прежнее имя свободно."),"muted"),table,detail,row(previous,count,next,space(),undo));box.setMinHeight(0);display(box);reload.run();
    }

    private void automationPage(){
        var schedules=new TableView<Schedule>();schedules.setPlaceholder(text(t("Add a folder for regular analysis.","Добавьте папку для регулярной проверки."),"muted"));schedules.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);schedules.setPrefHeight(230);schedules.setMinHeight(120);
        column(schedules,t("Folder","Папка"),Schedule::root,440);column(schedules,t("Frequency","Как часто"),s->s.intervalHours()==24?t("Daily","Каждый день"):t("Weekly","Каждую неделю"),140);column(schedules,t("Next check","Следующая проверка"),s->Formats.date(s.nextRun()),170);column(schedules,t("Status","Статус"),s->s.enabled()?t("Enabled","Включено"):t("Paused","На паузе"),110);
        var history=new TableView<TaskRun>();history.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);history.setPlaceholder(text(t("Completed checks will appear here.","Здесь появятся результаты проверок."),"muted"));history.setMinHeight(120);VBox.setVgrow(history,Priority.ALWAYS);column(history,t("Started","Начало"),h->Formats.date(h.started()),170);column(history,t("Schedule","Задание"),h->"#"+h.scheduleId(),100);column(history,t("Status","Статус"),h->state(h.status()),150);column(history,t("Result","Результат"),TaskRun::detail,400);
        Runnable reload=()->load(()->new Object[]{app.db.schedules(),app.db.taskRuns()},value->{@SuppressWarnings("unchecked")var tasks=(List<Schedule>)value[0];@SuppressWarnings("unchecked")var runs=(List<TaskRun>)value[1];schedules.getItems().setAll(tasks);history.getItems().setAll(runs);});
        var toggle=button(t("Pause / enable","Приостановить / включить"),()->{var selected=schedules.getSelectionModel().getSelectedItem();if(selected!=null)load(()->{app.db.enableSchedule(selected.id(),!selected.enabled());return true;},v->reload.run());},"secondary");toggle.setDisable(true);schedules.getSelectionModel().selectedItemProperty().addListener((o,a,b)->toggle.setDisable(b==null));
        var box=new VBox(14,paragraph(t("Enro must be open. Scheduled tasks only analyze; they never remove files. A missed check runs once after the next launch.","Enro должен быть открыт. Задания только анализируют и не удаляют файлы. Пропущенная проверка выполнится один раз после следующего запуска."),"hint"),schedules,row(text(t("Use Pause to keep a schedule without running it.","Пауза сохраняет задание, но останавливает его выполнение."),"small-muted"),space(),toggle),text(t("Check history","История проверок"),"section-title"),history);box.setMinHeight(0);display(box);reload.run();
    }
    private void addSchedule(){
        var path=new TextField(pathField.getText());HBox.setHgrow(path,Priority.ALWAYS);var frequency=new ComboBox<String>(FXCollections.observableArrayList(t("Every day","Каждый день"),t("Every week","Каждую неделю")));frequency.getSelectionModel().selectFirst();frequency.setMaxWidth(Double.MAX_VALUE);
        var choose=button(t("Choose…","Выбрать…"),()->{var selected=chooseFolder();if(selected!=null)path.setText(selected.toString());},"quiet");
        var dialog=new Dialog<ButtonType>();dialog.setTitle(t("Add a regular check","Добавить регулярную проверку"));dialog.getDialogPane().setContent(new VBox(12,text(t("Folder","Папка"),"card-title"),row(path,choose),text(t("Frequency","Как часто"),"card-title"),frequency,paragraph(t("Only analysis. Enro must remain open for the task to run.","Только анализ. Для выполнения задания Enro должен быть открыт."),"muted")));dialog.getDialogPane().setPrefWidth(620);var add=new ButtonType(t("Add schedule","Добавить"),ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().getButtonTypes().addAll(add,ButtonType.CANCEL);decorate(dialog);
        if(dialog.showAndWait().orElse(ButtonType.CANCEL)!=add)return;
        try{Path root=Path.of(path.getText()).toAbsolutePath().normalize();long hours=frequency.getSelectionModel().getSelectedIndex()==0?24:168;run(t("Saving schedule…","Сохраняем расписание…"),()->{WindowsPaths.requirePlainAncestors(root);if(!Files.isDirectory(root))throw new IOException(t("This folder is unavailable.","Эта папка недоступна."));app.db.addSchedule(root,hours);return true;},v->showPage(7));}catch(Exception e){error(e);}
    }

    private record Option(String key,String title){@Override public String toString(){return title;}}
    private AppSettings copySettings(){return new com.fasterxml.jackson.databind.ObjectMapper().convertValue(app.settings,AppSettings.class);}
    private void settingsPage(){
        var next=copySettings();
        var look=new ComboBox<Option>(FXCollections.observableArrayList(new Option("light",t("Light","Светлая")),new Option("dark",t("Dark","Тёмная"))));look.getSelectionModel().select(next.appearance.equals("dark")?1:0);
        var themes=new ComboBox<Option>(FXCollections.observableArrayList(new Option("Iris",t("Iris","Индиго")),new Option("Jade",t("Mint","Мята")),new Option("Arctic Silver",t("Arctic","Серебро")),new Option("Crimson Noir",t("Rose","Роза")),new Option("Obsidian Yellow",t("Amber","Янтарь")),new Option("Monochrome",t("Graphite","Графит"))));themes.getItems().stream().filter(o->o.key.equals(next.theme)).findFirst().ifPresent(themes::setValue);if(themes.getValue()==null)themes.getSelectionModel().selectFirst();
        Runnable applyAppearance=()->{try{var live=copySettings();live.theme=themes.getValue().key;live.appearance=look.getValue().key;app.save(live);applyTheme();}catch(Exception e){error(e);}};look.setOnAction(e->applyAppearance.run());themes.setOnAction(e->applyAppearance.run());
        var language=new ComboBox<Option>(FXCollections.observableArrayList(new Option("ru","Русский"),new Option("en","English")));language.getSelectionModel().select(next.language.equals("ru")?0:1);
        var appearance=card(text(t("Appearance","Оформление"),"section-title"),form(new Object[][]{{t("Theme","Тема"),look},{t("Accent color","Цвет акцента"),themes},{t("Language","Язык"),language}}),text(t("Theme and accent apply immediately.","Тема и цвет применяются сразу."),"small-muted"));
        var notifications=new CheckBox(t("Show low-space and scheduled-check notifications","Сообщать о нехватке места и плановых проверках"));notifications.setSelected(next.notifications);
        var startup=new CheckBox(t("Analyze a folder when Enro starts","Проверять папку при запуске Enro"));startup.setSelected(next.scanOnStartup);var startupRoot=new TextField(next.startupRoot);startupRoot.setPromptText(t("Folder path","Путь к папке"));var startupChoose=button(t("Choose…","Выбрать…"),()->{Path selected=chooseFolder();if(selected!=null)startupRoot.setText(selected.toString());},"quiet");HBox.setHgrow(startupRoot,Priority.ALWAYS);
        var exclusions=new TextArea(String.join("\n",next.excludedDirectories));exclusions.setPrefRowCount(3);exclusions.setPromptText(t("One full folder path per line","По одному полному пути к папке в строке"));var extensions=new TextField(String.join(",",next.excludedExtensions));extensions.setPromptText(".iso, .tmp");
        var scanning=card(text(t("Analysis preferences","Проверка файлов"),"section-title"),notifications,startup,row(startupRoot,startupChoose),form(new Object[][]{{t("Excluded folders","Исключённые папки"),exclusions},{t("Excluded extensions","Исключённые расширения"),extensions}}));
        var workers=new Spinner<Integer>(1,8,next.parallelism);var depth=new Spinner<Integer>(1,512,next.maxDepth);var delay=new Spinner<Integer>(0,100,next.throttleMillis);var low=new Spinner<Integer>(1,50,next.lowSpacePercent);var limit=new TextField(Integer.toString(next.maxEntries));var logs=new ComboBox<String>(FXCollections.observableArrayList("INFO","WARN","ERROR","DEBUG"));logs.setValue(next.logLevel);
        var advanced=new TitledPane(t("Advanced preferences","Дополнительные параметры"),form(new Object[][]{{t("Worker limit","Количество потоков"),workers},{t("Entry limit per scan","Лимит элементов на проверку"),limit},{t("Maximum depth","Глубина обхода"),depth},{t("Delay per 1000 entries, ms","Пауза на 1000 элементов, мс"),delay},{t("Low-space threshold, %","Порог свободного места, %"),low},{t("Log level","Уровень логирования"),logs}}));advanced.setExpanded(false);
        var save=button(t("Save preferences","Сохранить настройки"),()->{try{next.appearance=look.getValue().key;next.theme=themes.getValue().key;next.language=language.getValue().key;next.parallelism=workers.getValue();next.maxDepth=depth.getValue();next.throttleMillis=delay.getValue();next.maxEntries=Integer.parseInt(limit.getText());next.lowSpacePercent=low.getValue();next.logLevel=logs.getValue();next.notifications=notifications.isSelected();next.scanOnStartup=startup.isSelected();next.startupRoot=startupRoot.getText();next.excludedDirectories=exclusions.getText().lines().map(String::trim).filter(s->!s.isEmpty()).toList();next.excludedExtensions=Arrays.stream(extensions.getText().split(",")).map(String::trim).filter(s->!s.isEmpty()).toList();app.save(next);applyTheme();localize();showPage(8);status.setText(t("Preferences saved","Настройки сохранены"));}catch(Exception e){error(e);}},"primary");
        var data=card(text(t("Data and diagnostics","Данные и диагностика"),"section-title"),paragraph(t("File operations always require confirmation. Scheduled checks never delete. Diagnostic reports omit file paths.","Операции с файлами всегда требуют подтверждения. Плановые проверки ничего не удаляют. Диагностический отчёт исключает пути файлов."),"muted"),row(button(t("Export preferences","Экспорт настроек"),()->settingsTransfer(false),"quiet"),button(t("Import preferences","Импорт настроек"),()->settingsTransfer(true),"quiet"),button(t("Save diagnostics","Сохранить диагностику"),this::diagnostics,"quiet")));
        scrolling(new VBox(20,appearance,scanning,advanced,row(save),data));
    }
    private GridPane form(Object[][] rows){var grid=new GridPane();grid.setHgap(24);grid.setVgap(14);var labels=new ColumnConstraints();labels.setPrefWidth(230);labels.setMinWidth(170);var fields=new ColumnConstraints();fields.setHgrow(Priority.ALWAYS);grid.getColumnConstraints().addAll(labels,fields);int row=0;for(var pair:rows){var caption=paragraph((String)pair[0],"muted");grid.add(caption,0,row);var control=(Node)pair[1];if(control instanceof Region region)region.setMaxWidth(Double.MAX_VALUE);grid.add(control,1,row++);}return grid;}
    private void settingsTransfer(boolean importing){var chooser=new FileChooser();chooser.setInitialFileName("Enro-settings.json");var file=importing?chooser.showOpenDialog(stage):chooser.showSaveDialog(stage);if(file==null)return;run(t("Preferences…","Настройки…"),()->{if(importing)return app.settingsStore.read(file.toPath());app.settingsStore.write(file.toPath(),app.settings);return app.settings;},settings->{try{if(importing){app.save(settings);applyTheme();localize();showPage(8);}status.setText(t("Preferences saved","Настройки сохранены"));}catch(Exception e){error(e);}});}
    private void diagnostics(){if(!confirm(t("Save a diagnostic report?","Сохранить диагностический отчёт?"),t("Paths can contain personal data. Enro omits paths from this report; review it before sharing.","Пути могут содержать личные данные. Enro исключает их из этого отчёта; проверьте его перед отправкой."),t("Choose location","Выбрать место")))return;var chooser=new FileChooser();chooser.setInitialFileName("Enro-diagnostics.txt");var file=chooser.showSaveDialog(stage);if(file!=null)run(t("Saving diagnostics…","Сохраняем диагностику…"),()->{new ExportService(app.db).diagnostics(file.toPath());return true;},v->status.setText(t("Report saved","Отчёт сохранён")));}
}
