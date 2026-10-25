package com.enro.ui;

import javafx.scene.shape.SVGPath;

/** Original 24-unit outline icons, rendered as vectors. */
public final class Icons {
    private Icons(){}
    private static final String[] PATHS={
        "M3 3H10V10H3ZM14 3H21V10H14ZM3 14H10V21H3ZM14 14H21V21H14Z",
        "M3 3V21H22M7 17V11H10V17M13 17V5H16V17M19 17V8H22V17",
        "M14 3L21 10M18 5L9 14M4 13L11 20L6 22L2 18ZM6 15L4 19M9 17L7 21",
        "M8 8H21V21H8ZM3 16V3H16",
        "M5 2H14L20 8V22H5ZM14 2V8H20M9 13H16M9 17H16",
        "M12 2V15M7 10L12 15L17 10M3 16V22H21V16",
        "M5 8H1V4M2 8C5 1 17 1 21 8C26 18 12 26 5 18M12 7V13L16 15",
        "M12 2A10 10 0 1 1 11.99 2M12 6V12L17 15",
        "M9 3L10 1H14L15 3L18 4L21 4L23 8L21 10V14L23 16L21 20H18L15 21L14 23H10L9 21L6 20H3L1 16L3 14V10L1 8L3 4H6ZM16 12A4 4 0 1 1 15.99 12"
    };
    public static SVGPath node(int index){var path=new SVGPath();path.setContent(PATHS[index]);path.setScaleX(.72);path.setScaleY(.72);path.getStyleClass().add("nav-svg");return path;}
    public static SVGPath drive(){var path=new SVGPath();path.setContent("M3 11L6 4H18L21 11V20H3ZM3 12H21M7 16H8M11 16H12");path.setScaleX(.9);path.setScaleY(.9);path.getStyleClass().add("nav-svg");return path;}
    public static SVGPath folder(){var path=new SVGPath();path.setContent("M2 6H9L11 8H22V21H2ZM2 6V3H9L11 6H20V8");path.setScaleX(.75);path.setScaleY(.75);path.getStyleClass().add("nav-svg");return path;}
}
