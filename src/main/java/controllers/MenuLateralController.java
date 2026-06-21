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
    private Button btnCargarDatos;

    @FXML
    private Button btnRemisionMuestras;

    @FXML
    private Button btnCerrarSesion;

    @FXML
    public void initialize() {
        btnCerrarSesion.getStyleClass().add("menu-cerrar");
        Navegacion.registrarMenuLateral(this);
        marcarActivo(SeccionApp.INICIO);
    }

    public void marcarActivo(SeccionApp seccion) {
        actualizarPermisos();
        List<Button> botonesNavegacion = List.of(
                btnInicio,
                btnRegistrarMuestra,
                btnRegistrarUsuario,
                btnBuscarMuestra,
                btnCargarDatos,
                btnRemisionMuestras
        );

        for (Button boton : botonesNavegacion) {
            boton.getStyleClass().remove("menu-activo");
        }

        Button activo = switch (seccion) {
            case INICIO -> btnInicio;
            case REGISTRAR_MUESTRA -> btnRegistrarMuestra;
            case GESTIONAR_USUARIOS -> btnRegistrarUsuario;
            case BUSCAR_MUESTRAS -> btnBuscarMuestra;
            case CARGAR_BASE_DATOS -> btnCargarDatos;
            case REMISION_MUESTRAS -> btnRemisionMuestras;
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

    private void actualizarPermisos() {
        domain.Usuario usuario = utilities.UsuarioSesion.getUsuario();
        boolean controlMuestras = usuario != null && usuario.puedeControlarMuestras();
        btnRegistrarMuestra.setVisible(controlMuestras);
        btnRegistrarMuestra.setManaged(controlMuestras);
        btnCargarDatos.setVisible(controlMuestras);
        btnCargarDatos.setManaged(controlMuestras);
        btnRemisionMuestras.setVisible(controlMuestras);
        btnRemisionMuestras.setManaged(controlMuestras);
    }

    @FXML
    private void abrirCargarDatos() {
        Navegacion.irCargarBaseDatos();
    }

    @FXML
    private void abrirRemisionMuestras() {
        Navegacion.irRemisionMuestras();
    }

    @FXML
    private void cerrarSesion(ActionEvent event) {
        Stage stage = (Stage) btnCerrarSesion.getScene().getWindow();
        Navegacion.cerrarSesion(stage);
    }
}
