package controllers;

import domain.Usuario;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;
import utilities.Navegacion;
import utilities.UsuarioSesion;

public class MenuPrincipalController {

    @FXML private Circle imgfotoPerfil;
    @FXML private Label lblBienvenida;
    @FXML private Label lblFechaHora;

    private Usuario usuario;
    private Timeline timelineFechaHora;

    @FXML
    public void initialize() {
        actualizarFechaHora();
    }

    public void setUsuario(Usuario usuarioLogueado) {
        this.usuario = usuarioLogueado;
        UsuarioSesion.setUsuario(usuarioLogueado);
        if (lblBienvenida != null && usuarioLogueado != null) {
            lblBienvenida.setText(usuarioLogueado.getNombre());
        }
    }

    private void actualizarFechaHora() {
        if (timelineFechaHora != null) {
            timelineFechaHora.stop();
        }
        timelineFechaHora = new Timeline(
                new KeyFrame(Duration.seconds(0), event -> {
                    java.time.LocalDateTime now = java.time.LocalDateTime.now();
                    java.time.format.DateTimeFormatter formatter =
                            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                    if (lblFechaHora != null) {
                        lblFechaHora.setText(now.format(formatter));
                    }
                }),
                new KeyFrame(Duration.seconds(1))
        );
        timelineFechaHora.setCycleCount(Timeline.INDEFINITE);
        timelineFechaHora.play();
    }

    @FXML
    void abrirRegistroMuestra() {
        Navegacion.irRegistrarMuestra();
    }

    @FXML
    void abrirRegistrarUsuario() {
        Navegacion.irRegistrarUsuario();
    }

    @FXML
    void abrirBuscarMuestras() {
        Navegacion.irBuscarMuestras();
    }

    @FXML
    void cerrarSesion(MouseEvent event) {
        Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
        Navegacion.cerrarSesion(stage);
    }
}
