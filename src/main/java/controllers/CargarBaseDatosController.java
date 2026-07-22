package controllers;

import domain.Usuario;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import utilities.ExcelHelper;
import utilities.Navegacion;
import utilities.UsuarioSesion;

import java.io.File;

public class CargarBaseDatosController {

    @FXML private Label lblArchivo;

    private Usuario usuario;

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    @FXML
    private void descargarPlantilla() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar plantilla de carga");
        chooser.setInitialFileName("plantilla_carga_muestras.xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo Excel", "*.xlsx"));
        File destino = chooser.showSaveDialog(lblArchivo.getScene().getWindow());
        if (destino == null) return;

        if (!destino.getName().toLowerCase().endsWith(".xlsx")) {
            destino = new File(destino.getParentFile(), destino.getName() + ".xlsx");
        }

        try {
            ExcelHelper.crearPlantilla(destino);
            mostrarAlerta(Alert.AlertType.INFORMATION, "Plantilla creada",
                    "La plantilla se guardó correctamente en:\n" + destino.getAbsolutePath());
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo crear la plantilla", e.getMessage());
        }
    }

    @FXML
    private void seleccionarArchivo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar plantilla completada");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo Excel", "*.xlsx"));
        File archivo = chooser.showOpenDialog(lblArchivo.getScene().getWindow());
        if (archivo == null) return;

        lblArchivo.setText(archivo.getName());
        ExcelHelper.ResultadoLectura resultado = ExcelHelper.leerExcel(archivo);
        mostrarVistaPrevia(archivo, resultado);
    }

    private void mostrarVistaPrevia(File archivo, ExcelHelper.ResultadoLectura resultado) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/VistaPreviaCarga.fxml"));
            Parent root = loader.load();
            VistaPreviaCargaController controller = loader.getController();

            Stage ventana = new Stage();
            ventana.setTitle("Vista previa de importación - " + archivo.getName());
            ventana.initOwner(lblArchivo.getScene().getWindow());
            ventana.initModality(Modality.WINDOW_MODAL);
            ventana.setScene(new Scene(root));

            Rectangle2D area = Screen.getPrimary().getVisualBounds();
            ventana.setWidth(Math.min(1180, area.getWidth() * 0.92));
            ventana.setHeight(Math.min(720, area.getHeight() * 0.88));
            ventana.setMinWidth(Math.min(820, area.getWidth() * 0.85));
            ventana.setMinHeight(Math.min(520, area.getHeight() * 0.75));

            Usuario usuarioActual = usuario != null ? usuario : UsuarioSesion.getUsuario();
            controller.configurar(ventana, usuarioActual, archivo.getName(), resultado);
            ventana.showAndWait();
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo abrir la vista previa", e.getMessage());
        }
    }

    @FXML
    private void volverInicio() {
        Navegacion.irInicio();
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alerta = new Alert(tipo);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje == null || mensaje.isBlank() ? "Ocurrió un error inesperado." : mensaje);
        alerta.showAndWait();
    }
}
