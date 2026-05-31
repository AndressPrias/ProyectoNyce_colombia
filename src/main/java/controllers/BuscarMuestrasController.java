package controllers;

import db.Database;
import domain.Estado;
import domain.Muestra;
import domain.Usuario;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.io.File;

import javafx.stage.Stage;
import utilities.UsuarioSesion;

public class BuscarMuestrasController {

    @FXML private TextField txtBusquedaGeneral;

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

    private Usuario usuario;

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
                     "SELECT * FROM muestras WHERE " +
                             "LOWER(COALESCE(codigoInterno, '')) LIKE ? OR " +
                             "LOWER(COALESCE(descripcion, '')) LIKE ? OR " +
                             "LOWER(COALESCE(rotuloCliente, '')) LIKE ? OR " +
                             "LOWER(COALESCE(estado, '')) LIKE ? OR " +
                             "LOWER(COALESCE(ubicacion, '')) LIKE ? OR " +
                             "CAST(cantidad AS VARCHAR) LIKE ? OR " +
                             "CAST(fechaRecepcion AS VARCHAR) LIKE ?")) {

            String busqueda = "%" + txtBusquedaGeneral.getText().trim().toLowerCase() + "%";
            for (int i = 1; i <= 7; i++) {
                ps.setString(i, busqueda);
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Muestra m = new Muestra();
                m.setDescripcion(rs.getString("descripcion"));
                m.setRotuloCliente(rs.getString("rotuloCliente"));
                m.setCantidad(rs.getInt("cantidad"));
                m.setEstado(Estado.valueOf(rs.getString("estado")));
                java.sql.Date fecha = rs.getDate("fechaRecepcion");
                m.setFechaRecepcion(fecha == null ? null : fecha.toLocalDate());
                m.setUbicacion(rs.getString("ubicacion"));
                m.setRutaFoto(rs.getString("rutaFoto"));
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
        lblDetalleFecha.setText(muestra.getFechaRecepcion() == null ? "" : muestra.getFechaRecepcion().toString());
        lblDetalleUbicacion.setText(muestra.getUbicacion());

        cargarImagenDetalle(muestra.getRutaFoto());
    }

    private void cargarImagenDetalle(String rutaFoto) {
        if (rutaFoto == null || rutaFoto.isBlank()) {
            imgDetalle.setImage(null);
            return;
        }

        try {
            File archivo = new File(rutaFoto);
            Image img = archivo.exists()
                    ? new Image(archivo.toURI().toString())
                    : new Image(rutaFoto);

            imgDetalle.setImage(img.isError() ? null : img);
        } catch (Exception e) {
            imgDetalle.setImage(null);
        }
    }

    @FXML
    void limpiarFiltros() {
        txtBusquedaGeneral.clear();
        buscarMuestras();
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
        UsuarioSesion.setUsuario(usuario);
    }

    @FXML
    void editarMuestra() {
        Muestra muestraSeleccionada = tblResultados.getSelectionModel().getSelectedItem();
        if (muestraSeleccionada == null) {
            System.out.println("No hay muestra seleccionada");
            return;
        }

        // Abrir ventana de registro de muestra
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/EditarMuestra.fxml"));
            Parent root = loader.load();

            // Pasar la muestra seleccionada al controlador de EditarMuestra
            EditarMuestraController controller = loader.getController();
            controller.editarMuestra(muestraSeleccionada);

            Stage stage = new Stage();
            stage.setTitle("Editar Muestra");
            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
