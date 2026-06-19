package controllers;

import domain.Usuario;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;
import utilities.Navegacion;
import utilities.UsuarioSesion;

import java.io.File;
import java.net.URL;

public class MenuPrincipalController {

    @FXML private Circle imgfotoPerfil;
    @FXML private Label lblBienvenida;
    @FXML private Label lblFechaHora;

    private Timeline timelineFechaHora;

    @FXML
    public void initialize() {
        actualizarFechaHora();
    }

    public void setUsuario(Usuario usuarioLogueado) {
        UsuarioSesion.setUsuario(usuarioLogueado);
        if (lblBienvenida != null && usuarioLogueado != null) {
            lblBienvenida.setText(usuarioLogueado.getNombre());
        }
        cargarFotoPerfil(usuarioLogueado);
    }

    private void cargarFotoPerfil(Usuario usuarioLogueado) {
        if (imgfotoPerfil == null) {
            return;
        }

        imgfotoPerfil.setFill(Color.web("#d9d9d9"));
        if (usuarioLogueado == null || usuarioLogueado.getRutaFoto() == null || usuarioLogueado.getRutaFoto().isBlank()) {
            return;
        }

        try {
            Image imagen = cargarImagen(usuarioLogueado.getRutaFoto());
            if (imagen != null && !imagen.isError()) {
                imgfotoPerfil.setFill(new ImagePattern(imagen));
            }
        } catch (Exception e) {
            imgfotoPerfil.setFill(Color.web("#d9d9d9"));
        }
    }

    private Image cargarImagen(String rutaFoto) {
        if (rutaFoto.startsWith("/")) {
            URL recurso = getClass().getResource(rutaFoto);
            if (recurso != null) {
                return new Image(recurso.toExternalForm(), false);
            }

            File archivoRecurso = new File("src/main/resources" + rutaFoto);
            if (archivoRecurso.exists()) {
                return new Image(archivoRecurso.toURI().toString(), false);
            }
            return null;
        }
        return new Image(new File(rutaFoto).toURI().toString(), false);
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
    void abrirCargarBaseDatos() {
        Navegacion.irCargarBaseDatos();
    }

    @FXML
    void cerrarSesion(MouseEvent event) {
        Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
        Navegacion.cerrarSesion(stage);
    }
}
