package controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import utilities.Navegacion;
import utilities.SeccionApp;

import java.util.List;

public class MenuLateralController {

    @FXML
    private Button btnInicio;

    @FXML
    private Button btnRegistrarMuestra;

    @FXML
    private Button btnRegistrarUsuario;

    @FXML
    private Button btnBuscarMuestra;

    @FXML
    private Button btnCerrarSesion;

    @FXML
    public void initialize() {
        btnCerrarSesion.getStyleClass().add("menu-cerrar");
        Navegacion.registrarMenuLateral(this);
        marcarActivo(SeccionApp.INICIO);
    }

    public void marcarActivo(SeccionApp seccion) {
        List<Button> botonesNavegacion = List.of(
                btnInicio,
                btnRegistrarMuestra,
                btnRegistrarUsuario,
                btnBuscarMuestra
        );

        for (Button boton : botonesNavegacion) {
            boton.getStyleClass().remove("menu-activo");
        }

        Button activo = switch (seccion) {
            case INICIO -> btnInicio;
            case REGISTRAR_MUESTRA -> btnRegistrarMuestra;
            case REGISTRAR_USUARIO -> btnRegistrarUsuario;
            case BUSCAR_MUESTRAS -> btnBuscarMuestra;
        };

        if (!activo.getStyleClass().contains("menu-activo")) {
            activo.getStyleClass().add("menu-activo");
        }
    }

    @FXML
    private void inicio() {
        Navegacion.irInicio();
    }

    @FXML
    private void abrirRegistrarMuestra() {
        Navegacion.irRegistrarMuestra();
    }

    @FXML
    private void abrirRegistrarUsuario() {
        Navegacion.irRegistrarUsuario();
    }

    @FXML
    private void abrirBuscarMuestra() {
        Navegacion.irBuscarMuestras();
    }

    @FXML
    private void cerrarSesion(ActionEvent event) {
        Stage stage = (Stage) btnCerrarSesion.getScene().getWindow();
        Navegacion.cerrarSesion(stage);
    }
}
