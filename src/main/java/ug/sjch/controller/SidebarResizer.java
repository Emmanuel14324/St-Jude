package ug.sjch.controller;

import javafx.scene.Cursor;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

final class SidebarResizer {
    private static final double DEFAULT_WIDTH = 238;
    private static final double MIN_WIDTH = 190;
    private static final double MAX_WIDTH = 320;
    private static double preferredWidth = DEFAULT_WIDTH;

    private SidebarResizer() {
    }

    static void install(VBox sidebar, Region resizeHandle) {
        sidebar.setMinWidth(MIN_WIDTH);
        sidebar.setMaxWidth(MAX_WIDTH);
        sidebar.setPrefWidth(preferredWidth);

        resizeHandle.setMinWidth(6);
        resizeHandle.setPrefWidth(6);
        resizeHandle.setMaxWidth(6);
        resizeHandle.setMaxHeight(Double.MAX_VALUE);
        resizeHandle.setCursor(Cursor.H_RESIZE);
        Tooltip.install(resizeHandle,
                new Tooltip("Drag to resize; double-click to reset"));

        double[] dragStart = new double[2];
        boolean[] dragging = new boolean[1];
        resizeHandle.setOnMousePressed(event -> {
            if (event.getClickCount() == 2) {
                dragging[0] = false;
                preferredWidth = DEFAULT_WIDTH;
                sidebar.setPrefWidth(preferredWidth);
                event.consume();
                return;
            }
            dragging[0] = true;
            dragStart[0] = event.getSceneX();
            dragStart[1] = sidebar.getWidth();
            event.consume();
        });
        resizeHandle.setOnMouseDragged(event -> {
            if (!dragging[0]) {
                return;
            }
            preferredWidth = clamp(
                    dragStart[1] + event.getSceneX() - dragStart[0]);
            sidebar.setPrefWidth(preferredWidth);
            event.consume();
        });
        resizeHandle.setOnMouseReleased(event -> {
            dragging[0] = false;
            event.consume();
        });
    }

    private static double clamp(double width) {
        return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width));
    }
}
