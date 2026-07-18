package utilities;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.Comparator;

public final class AppWindow {

    private AppWindow() {
    }

    public static void ocuparAreaVisible(Stage stage) {
        if (stage == null) {
            return;
        }

        Rectangle2D bounds = obtenerPantalla(stage).getVisualBounds();
        stage.setFullScreen(false);
        stage.setMaximized(false);
        stage.setResizable(false);
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
    }

    private static Screen obtenerPantalla(Stage stage) {
        double x = stage.getX();
        double y = stage.getY();
        double width = Math.max(stage.getWidth(), 1);
        double height = Math.max(stage.getHeight(), 1);

        return Screen.getScreensForRectangle(x, y, width, height).stream()
                .max(Comparator.comparingDouble(screen -> areaInterseccion(screen.getVisualBounds(), x, y, width, height)))
                .orElse(Screen.getPrimary());
    }

    private static double areaInterseccion(Rectangle2D bounds, double x, double y, double width, double height) {
        double minX = Math.max(bounds.getMinX(), x);
        double minY = Math.max(bounds.getMinY(), y);
        double maxX = Math.min(bounds.getMaxX(), x + width);
        double maxY = Math.min(bounds.getMaxY(), y + height);
        return Math.max(0, maxX - minX) * Math.max(0, maxY - minY);
    }
}
