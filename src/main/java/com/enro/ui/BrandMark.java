package com.enro.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;

/** Compact, code-native Enro monogram. */
public final class BrandMark extends Canvas {
    public BrandMark(){super(34,34);var g=getGraphicsContext2D();g.setFill(Color.web("#6c5ce7"));g.fillRoundRect(0,0,34,34,10,10);g.setFill(Color.WHITE);g.fillRoundRect(10,9,15,4,2,2);g.fillRoundRect(10,15,11,4,2,2);g.fillRoundRect(10,21,15,4,2,2);g.fillRoundRect(8,9,4,16,2,2);}
}
