package com.enro.ui;

import com.enro.core.Models.Entry;
import com.enro.utils.Formats;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import java.util.*;
import java.util.function.Consumer;

/** A bounded, quiet map of the current folder; one Canvas rather than one node per file. */
public final class TreemapView extends Region {
    private final Canvas canvas=new Canvas();private final Tooltip tooltip=new Tooltip();
    private List<Entry> entries=List.of();private List<SquarifiedLayout.Rect> rectangles=List.of();private Entry selected;
    private Consumer<Entry> select=e->{},open=e->{},reveal=e->{};
    public TreemapView(){getChildren().add(canvas);setMinHeight(100);setPrefHeight(390);Tooltip.install(canvas,tooltip);
        canvas.setOnMouseMoved(e->{var item=hit(e.getX(),e.getY());tooltip.setText(item==null?"":item.path()+"\n"+Formats.bytes(item.size()));});
        canvas.setOnMouseClicked(e->{var item=hit(e.getX(),e.getY());if(item==null)return;selected=item;paint();select.accept(item);if(e.getClickCount()==2&&item.directory())open.accept(item);});
        canvas.setOnContextMenuRequested(e->{var item=hit(e.getX(),e.getY());if(item==null)return;var action=new MenuItem("Explorer / Проводник");action.setOnAction(a->reveal.accept(item));new ContextMenu(action).show(canvas,e.getScreenX(),e.getScreenY());});
    }
    public void setEntries(List<Entry> entries){this.entries=List.copyOf(entries);selected=null;paint();}
    public void onSelect(Consumer<Entry> callback){select=callback;}public void onOpen(Consumer<Entry> callback){open=callback;}public void onReveal(Consumer<Entry> callback){reveal=callback;}
    public void select(Entry item){selected=item;paint();}
    @Override protected void layoutChildren(){canvas.setWidth(getWidth());canvas.setHeight(getHeight());paint();}
    private Entry hit(double x,double y){for(var r:rectangles)if(x>=r.x()&&x<=r.x()+r.width()&&y>=r.y()&&y<=r.y()+r.height())return entries.get(r.index());return null;}
    public static Color color(String category){return Color.web(switch(category){case "Video"->"#a8b7f2";case "Images"->"#d4b0e9";case "Audio"->"#efc69c";case "Archives"->"#e5c78a";case "Documents"->"#9acdc7";case "Programs"->"#bba8e8";case "System"->"#a8c5d8";case "Folder"->"#acbced";default->"#c4ccdc";});}
    private void paint(){var g=canvas.getGraphicsContext2D();double width=canvas.getWidth(),height=canvas.getHeight();g.clearRect(0,0,width,height);rectangles=SquarifiedLayout.layout(entries.stream().map(Entry::size).toList(),width,height);
        for(var r:rectangles){if(r.width()<2||r.height()<2)continue;var entry=entries.get(r.index());g.setFill(color(entry.category()));g.fillRoundRect(r.x()+3,r.y()+3,r.width()-6,r.height()-6,9,9);
            if(entry.equals(selected)){g.setStroke(Color.web("#6554d9"));g.setLineWidth(2);g.strokeRoundRect(r.x()+3,r.y()+3,r.width()-6,r.height()-6,9,9);}
            if(r.width()>64&&r.height()>35){g.setTextAlign(TextAlignment.LEFT);g.setFill(Color.web("#253149"));g.setFont(Font.font("Segoe UI",FontWeight.SEMI_BOLD,12));int limit=Math.max(1,(int)((r.width()-25)/7));String name=entry.name().length()>limit?entry.name().substring(0,Math.max(0,limit-1))+"…":entry.name();g.fillText(name,r.x()+13,r.y()+25,r.width()-24);if(r.height()>57){g.setFont(Font.font("Segoe UI",11));g.setFill(Color.web("#46516a"));g.fillText(Formats.bytes(entry.size()),r.x()+13,r.y()+43,r.width()-24);}}
        }
    }
}
