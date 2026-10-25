package com.enro.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Small presentation helpers shared by every Enro screen. */
public final class Ui {
    private Ui(){}
    public static Label text(String value,String style){var label=new Label(value);label.getStyleClass().add(style);label.setMinWidth(0);return label;}
    public static Label paragraph(String value,String style){var label=text(value,style);label.setWrapText(true);label.setMaxWidth(Double.MAX_VALUE);return label;}
    public static HBox row(Node...nodes){var row=new HBox(12,nodes);row.setAlignment(Pos.CENTER_LEFT);return row;}
    public static Region space(){var region=new Region();HBox.setHgrow(region,Priority.ALWAYS);return region;}
    public static VBox card(Node...nodes){var box=new VBox(16,nodes);box.getStyleClass().add("card");box.setMinWidth(0);return box;}
    public static Button button(String title,Runnable action,String style){var button=new Button(title);button.getStyleClass().add(style);button.setOnAction(e->action.run());return button;}
    public static ColumnConstraints percent(double percent){var c=new ColumnConstraints();c.setPercentWidth(percent);c.setHgrow(Priority.ALWAYS);return c;}
    public static Label pill(String value,String style){var l=text(value,"pill");l.getStyleClass().add(style);return l;}
}
