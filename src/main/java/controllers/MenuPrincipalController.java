package controllers;

import domain.Usuario;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import utilities.AppDialog;
import utilities.AppUpdateService;
import utilities.AppVersion;
import utilities.ImageStorage;
import utilities.Navegacion;
import utilities.UsuarioSesion;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MenuPrincipalController {

    @FXML private Circle imgfotoPerfil;
    @FXML private Label lblBienvenida;
    @FXML private Label lblFechaHora;
    @FXML private VBox cardRegistrarMuestra;
    @FXML private VBox cardCargarDatos;
    @FXML private VBox cardRemisionMuestras;
    @FXML private HBox filaTarjetasSuperior;
    @FXML private HBox filaTarjetasInferior;

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
        boolean controlMuestras = usuarioLogueado != null && usuarioLogueado.puedeControlarMuestras();
        configurarTarjetaRestringida(cardRegistrarMuestra, controlMuestras);
        configurarTarjetaRestringida(cardCargarDatos, controlMuestras);
        configurarTarjetaRestringida(cardRemisionMuestras, controlMuestras);
        reorganizarTarjetasVisibles();
        verificarActualizacionDisponible();
        notificarNuevaVersionSiAplica();
    }

    private void configurarTarjetaRestringida(VBox tarjeta, boolean habilitada) {
        tarjeta.setVisible(habilitada);
        tarjeta.setManaged(habilitada);
        tarjeta.setDisable(!habilitada);
    }

    private void reorganizarTarjetasVisibles() {
        List<Node> tarjetas = new ArrayList<>(filaTarjetasSuperior.getChildren());
        tarjetas.addAll(filaTarjetasInferior.getChildren());
        tarjetas.removeIf(tarjeta -> !tarjeta.isVisible());

        filaTarjetasSuperior.getChildren().clear();
        filaTarjetasInferior.getChildren().clear();

        int limitePrimeraFila = Math.min(3, tarjetas.size());
        filaTarjetasSuperior.getChildren().addAll(tarjetas.subList(0, limitePrimeraFila));
        filaTarjetasInferior.getChildren().addAll(tarjetas.subList(limitePrimeraFila, tarjetas.size()));

        boolean haySegundaFila = !filaTarjetasInferior.getChildren().isEmpty();
        filaTarjetasInferior.setVisible(haySegundaFila);
        filaTarjetasInferior.setManaged(haySegundaFila);
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
        String url = ImageStorage.resolveImageUrl(rutaFoto);
        return url == null || url.isBlank() ? null : new Image(url, false);
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

    private void notificarNuevaVersionSiAplica() {
        if (!AppVersion.shouldNotifyCurrentVersion()) {
            return;
        }

        Platform.runLater(() -> {
            AppDialog.showInformation(
                    "Versión actualizada",
                    "Control Muestras LENC actualizado",
                    AppVersion.getDisplayVersion() + "\n\n" + AppVersion.getNotes());
            AppVersion.markCurrentVersionSeen();
        });
    }

    private void verificarActualizacionDisponible() {
        Optional<AppUpdateService.UpdateInfo> update = AppUpdateService.findAvailableUpdate();
        if (update.isEmpty()) {
            return;
        }

        Platform.runLater(() -> {
            AppUpdateService.UpdateInfo info = update.get();
            boolean shouldUpdate = AppDialog.confirmUpdate(
                    "Actualización disponible",
                    "Hay una versión más reciente de Control Muestras LENC",
                    "Versión " + info.version() + "\nBuild: " + info.build()
                    + "\n\n" + info.notes()
                    + "\n\n¿Desea abrir el instalador ahora?");

            if (shouldUpdate) {
                try {
                    AppUpdateService.openInstaller(info);
                    cerrarAplicacionParaActualizar();
                } catch (Exception e) {
                    AppDialog.showError(
                            "No se pudo abrir el instalador",
                            "Revise la carpeta de actualizaciones",
                            e.getMessage());
                }
            }
        });
    }

    private void cerrarAplicacionParaActualizar() {
        if (timelineFechaHora != null) {
            timelineFechaHora.stop();
        }
        PauseTransition espera = new PauseTransition(Duration.millis(800));
        espera.setOnFinished(event -> {
            Platform.exit();
            System.exit(0);
        });
        espera.play();
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
    void abrirRemisionMuestras() {
        Navegacion.irRemisionMuestras();
    }

    @FXML
    void cerrarSesion(MouseEvent event) {
        Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
        Navegacion.cerrarSesion(stage);
    }
}
