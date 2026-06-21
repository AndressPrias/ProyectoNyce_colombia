package controllers;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import utilities.Navegacion;
import utilities.Paths;
import utilities.UsuarioSesion;
import domain.Usuario;

public class ControladorBaseController {

    private static final Duration DURACION_TRANSICION = Duration.millis(320);

    @FXML
    private StackPane areaContenido;

    @FXML
    private Node menuLateral;

    @FXML
    public void initialize() {
        Navegacion.registrarShell(this);
    }

    public void iniciarSesion(Usuario usuario) {
        UsuarioSesion.setUsuario(usuario);
        Navegacion.irInicio();
    }

    public void cargarVista(String fxmlPath, String tituloVentana, Navegacion.ConfiguradorVista configurador) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent vista = loader.load();
            Object controller = loader.getController();

            if (configurador != null) {
                configurador.configurar(controller);
            }

            ajustarVistaAlContenedor(vista, fxmlPath);
            mostrarVistaConTransicion(vista);
            actualizarVisibilidadMenuLateral(fxmlPath);
            programarAjusteVentana(fxmlPath, tituloVentana);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void actualizarVisibilidadMenuLateral(String fxmlPath) {
        boolean mostrarMenuLateral = !Paths.MENU_PRINCIPAL.equals(fxmlPath);
        menuLateral.setVisible(mostrarMenuLateral);
        menuLateral.setManaged(mostrarMenuLateral);
    }

    private void ajustarVistaAlContenedor(Parent vista, String fxmlPath) {
        if (!(vista instanceof Region region)) {
            StackPane.setAlignment(vista, Pos.TOP_LEFT);
            return;
        }

        if (Paths.MENU_PRINCIPAL.equals(fxmlPath)) {
            region.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            StackPane.setAlignment(vista, Pos.CENTER);
            return;
        }

        boolean vistaConPosicionesFijas = vista instanceof AnchorPane;
        if (vistaConPosicionesFijas) {
            region.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            StackPane.setAlignment(vista, Pos.CENTER);
            return;
        }

        region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        StackPane.setAlignment(vista, Pos.TOP_LEFT);
    }

    private void mostrarVistaConTransicion(Parent vista) {
        if (areaContenido.getChildren().isEmpty()) {
            vista.setOpacity(1);
            vista.setTranslateX(0);
            areaContenido.getChildren().setAll(vista);
            return;
        }

        Node vistaActual = areaContenido.getChildren().get(0);
        vista.setOpacity(0);
        vista.setTranslateX(24);
        areaContenido.getChildren().add(vista);

        FadeTransition salida = new FadeTransition(DURACION_TRANSICION, vistaActual);
        salida.setFromValue(vistaActual.getOpacity());
        salida.setToValue(0);
        salida.setInterpolator(Interpolator.EASE_BOTH);

        FadeTransition entrada = new FadeTransition(DURACION_TRANSICION, vista);
        entrada.setFromValue(0);
        entrada.setToValue(1);
        entrada.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition desplazamiento = new TranslateTransition(DURACION_TRANSICION, vista);
        desplazamiento.setFromX(24);
        desplazamiento.setToX(0);
        desplazamiento.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition transicion = new ParallelTransition(salida, entrada, desplazamiento);
        transicion.setOnFinished(event -> {
            vista.setOpacity(1);
            vista.setTranslateX(0);
            areaContenido.getChildren().setAll(vista);
        });
        transicion.play();
    }

    private void ajustarTamanoVentana(Stage stage, String fxmlPath) {
        if (Paths.MENU_PRINCIPAL.equals(fxmlPath)) {
            stage.setMinWidth(0);
            stage.setMinHeight(0);
            stage.setMaximized(true);
            return;
        }

        stage.setMinWidth(0);
        stage.setMinHeight(0);
        stage.setMaximized(true);
    }

    private void programarAjusteVentana(String fxmlPath, String tituloVentana) {
        Platform.runLater(() -> {
            if (areaContenido.getScene() == null || areaContenido.getScene().getWindow() == null) {
                return;
            }

            Stage stage = (Stage) areaContenido.getScene().getWindow();
            stage.setTitle(tituloVentana != null ? "Sistema NYCE - " + tituloVentana : "Sistema NYCE");
            ajustarTamanoVentana(stage, fxmlPath);
        });
    }
}
