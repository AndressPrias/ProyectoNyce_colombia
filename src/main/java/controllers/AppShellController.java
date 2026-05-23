package controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import utilities.Navegacion;
import utilities.Paths;
import utilities.UsuarioSesion;
import domain.Usuario;

public class AppShellController {

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

            if (vista instanceof Region region) {
                region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            }
            StackPane.setAlignment(vista, Pos.TOP_LEFT);
            areaContenido.getChildren().setAll(vista);
            actualizarVisibilidadMenuLateral(fxmlPath);

            if (areaContenido.getScene() != null && areaContenido.getScene().getWindow() != null) {
                Stage stage = (Stage) areaContenido.getScene().getWindow();
                stage.setTitle(
                        tituloVentana != null ? "Sistema NYCE - " + tituloVentana : "Sistema NYCE"
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void actualizarVisibilidadMenuLateral(String fxmlPath) {
        boolean mostrarMenuLateral = !Paths.MENU_PRINCIPAL.equals(fxmlPath);
        menuLateral.setVisible(mostrarMenuLateral);
        menuLateral.setManaged(mostrarMenuLateral);
    }
}
