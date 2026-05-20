package controllers;

import db.Database;
import domain.Estado;
import domain.Muestra;
import domain.Usuario;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

public class BuscarMuestrasController {

    @FXML private TextField txtDescripcion;
    @FXML private TextField txtRotuloCliente;
    @FXML private TextField txtCantidadDesde;
    @FXML private TextField txtCantidadHasta;
    @FXML private ComboBox<String> comboEstado;
    @FXML private DatePicker fechaDesde;
    @FXML private DatePicker fechaHasta;
    @FXML private ComboBox<String> comboUbicacion;

    @FXML private TableView<Muestra> tblResultados;
    @FXML private TableColumn<Muestra, String> colDescripcion;
    @FXML private TableColumn<Muestra, String> colRotulo;
    @FXML private TableColumn<Muestra, Integer> colCantidad;
    @FXML private TableColumn<Muestra, Estado> colEstado;
    @FXML private TableColumn<Muestra, LocalDate> colFecha;
    @FXML private TableColumn<Muestra, String> colUbicacion;

    @FXML private ImageView imgDetalle;
    @FXML private Label lblDetalleDescripcion;
    @FXML private Label lblDetalleRotulo;
    @FXML private Label lblDetalleCantidad;
    @FXML private Label lblDetalleEstado;
    @FXML private Label lblDetalleFecha;
    @FXML private Label lblDetalleUbicacion;

    private ObservableList<Muestra> listaMuestras = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Configurar columnas de la tabla
        colDescripcion.setCellValueFactory(new PropertyValueFactory<>("descripcion"));
        colRotulo.setCellValueFactory(new PropertyValueFactory<>("rotuloCliente"));
        colCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        colFecha.setCellValueFactory(new PropertyValueFactory<>("fechaRecepcion"));
        colUbicacion.setCellValueFactory(new PropertyValueFactory<>("ubicacion"));

        tblResultados.setItems(listaMuestras);

        // Listener para actualizar panel de detalle al seleccionar una fila
        tblResultados.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                mostrarDetalle(newSel);
            }
        });

        // Cargar todos los datos inicialmente
        buscarMuestras();
    }

    @FXML
    void buscarMuestras() {
        listaMuestras.clear();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM muestras WHERE descripcion LIKE ? AND rotuloCliente LIKE ?")) {

            ps.setString(1, "%" + txtDescripcion.getText() + "%");
            ps.setString(2, "%" + txtRotuloCliente.getText() + "%");

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Muestra m = new Muestra();
                m.setDescripcion(rs.getString("descripcion"));
                m.setRotuloCliente(rs.getString("rotuloCliente"));
                m.setCantidad(rs.getInt("cantidad"));
                m.setEstado(Estado.valueOf(rs.getString("estado")));
                m.setFechaRecepcion(rs.getDate("fechaRecepcion").toLocalDate());
                m.setUbicacion(rs.getString("ubicacion"));
                listaMuestras.add(m);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mostrarDetalle(Muestra muestra) {
        lblDetalleDescripcion.setText(muestra.getDescripcion());
        lblDetalleRotulo.setText(muestra.getRotuloCliente());
        lblDetalleCantidad.setText(String.valueOf(muestra.getCantidad()));
        lblDetalleEstado.setText(muestra.getEstado().name());
        lblDetalleFecha.setText(muestra.getFechaRecepcion().toString());
        lblDetalleUbicacion.setText(muestra.getUbicacion());

        // Cargar imagen si existe
        try {
            Image img = new Image("file:" + muestra.getRutaFoto());
            imgDetalle.setImage(img);
        } catch (Exception e) {
            imgDetalle.setImage(null);
        }
    }

    @FXML
    void limpiarFiltros() {
        txtDescripcion.clear();
        txtRotuloCliente.clear();
        txtCantidadDesde.clear();
        txtCantidadHasta.clear();
        comboEstado.getSelectionModel().clearSelection();
        comboUbicacion.getSelectionModel().clearSelection();
        fechaDesde.setValue(null);
        fechaHasta.setValue(null);
        buscarMuestras();
    }

    public void setUsuario(Usuario usuario) {
    }
}
