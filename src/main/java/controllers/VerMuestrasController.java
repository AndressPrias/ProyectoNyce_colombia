package controllers;

import domain.Muestra;
import domain.Usuario;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import service.MuestraService;

import java.util.List;

public class VerMuestrasController {

    @FXML private TableView<Muestra> tblMuestras;
    @FXML private TableColumn<Muestra, String> colCodigo;
    @FXML private TableColumn<Muestra, String> colCliente;
    @FXML private TableColumn<Muestra, String> colDescripcion;
    @FXML private TableColumn<Muestra, Integer> colCantidad;
    @FXML private TableColumn<Muestra, String> colEstado;
    @FXML private TableColumn<Muestra, String> colUbicacion;
    @FXML private TableColumn<Muestra, String> colFecha;
    @FXML private TableColumn<Muestra, String> colFoto;

    private MuestraService service = new MuestraService();

    @FXML
    public void initialize() {
        // Vincular columnas con propiedades de Muestra
        colCodigo.setCellValueFactory(new PropertyValueFactory<>("codigoInterno"));
        colCliente.setCellValueFactory(new PropertyValueFactory<>("rotuloCliente"));
        colDescripcion.setCellValueFactory(new PropertyValueFactory<>("descripcion"));
        colCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        colUbicacion.setCellValueFactory(new PropertyValueFactory<>("ubicacion"));
        colFecha.setCellValueFactory(new PropertyValueFactory<>("fechaRecepcion"));
        colFoto.setCellValueFactory(new PropertyValueFactory<>("rutaFoto"));

        // Cargar datos en la tabla
        cargarMuestras();
    }

    private void cargarMuestras() {
        List<Muestra> lista = service.obtenerTodasMuestras(); // método que retorna todas las muestras
        ObservableList<Muestra> data = FXCollections.observableArrayList(lista);
        tblMuestras.setItems(data);
    }

    public void setUsuario(Usuario usuario) {
    }
}