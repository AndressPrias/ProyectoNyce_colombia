package controllers;

import domain.Usuario;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;
import utilities.Paths;

public class MenuPrincipalController {

    @FXML private Circle fotoPerfil;        // avatar (cargado desde Scene Builder)
    @FXML private Label lblBienvenida;      // muestra "Bienvenido, Usuario"
    @FXML private Label lblFechaHora;       // muestra fecha y hora en tiempo real

    private Usuario usuario;                 // usuario logueado

    @FXML
    public void initialize() {
        // Iniciar actualización de fecha y hora
        actualizarFechaHora();
    }

    // Guardar usuario logueado y actualizar label
    public void setUsuario(Usuario usuarioLogueado) {
        this.usuario = usuarioLogueado;
        if (lblBienvenida != null && usuarioLogueado != null) {
            lblBienvenida.setText(usuarioLogueado.getNombre());
        }
        System.out.println("Usuario conectado: " + usuarioLogueado.getNombre());
    }

    // Actualiza fecha y hora cada segundo
    private void actualizarFechaHora() {
        Timeline timeline = new Timeline(
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
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    // Abrir ventana de Registrar Muestra
    @FXML
    void abrirRegistroMuestra() {
        try {
            // Cargar el FXML de Registrar Muestra
            FXMLLoader loader = new FXMLLoader(getClass().getResource(Paths.REGISTRAR_MUESTRA));
            Parent root = loader.load();

            // Pasar usuario logueado al controlador de la ventana de registro
            RegistrarMuestraController controller = loader.getController();
            controller.setUsuario(usuario); // 'usuario' es el logueado en el dashboard

            // Crear la nueva ventana
            Stage stage = new Stage();
            stage.setTitle("Registro de Muestras");
            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error al abrir la ventana de Registro de Muestra");
        }
    }

    // Abrir ventana de Registrar Usuario
    @FXML
    void abrirRegistrarUsuario() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(Paths.REGISTRAR_USUARIO));
            Parent root = loader.load();

            // Pasar usuario logueado
            RegistrarUsuarioController controller = loader.getController();
            controller.setUsuario(this.usuario); // <-- aquí está el paso clave

            Stage stage = new Stage();
            stage.setTitle("Registrar Usuario");
            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Abrir ventana de Ver Muestras
    @FXML
    void abrirVerMuestras() { abrirVentana("/VerMuestras.fxml", "Ver Muestras"); }

    // Cerrar sesión
    @FXML
    void cerrarSesion() {
        Stage stage = (Stage) Stage.getWindows().filtered(w -> w.isShowing()).get(0);
        stage.close();
    }

    // Método genérico para abrir ventanas desde el dashboard
    private void abrirVentana(String pathFXML, String titulo) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(pathFXML));
            Parent root = loader.load();

            // Pasar usuario logueado al controlador de la nueva ventana
            Object controller = loader.getController();
            if (controller instanceof RegistrarUsuarioController) {
                ((RegistrarUsuarioController) controller).setUsuario(usuario);
            }
            // También puedes pasar usuario a otras ventanas si quieres
            else if (controller instanceof RegistrarMuestraController) {
                ((RegistrarMuestraController) controller).setUsuario(usuario);
            } else if (controller instanceof VerMuestrasController) {
                ((VerMuestrasController) controller).setUsuario(usuario);
            }

            Stage stage = new Stage();
            stage.setTitle(titulo);
            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}