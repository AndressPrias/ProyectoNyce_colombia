package controllers;

import db.Database;
import domain.Estado;
import domain.Muestra;
import domain.Usuario;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.io.File;
import java.util.List;
import java.util.Optional;

import javafx.stage.Stage;
import javafx.stage.Modality;
import service.MuestraService;
import utilities.UsuarioSesion;

public class BuscarMuestrasController {

    @FXML private TextField txtBusquedaGeneral;

    @FXML private TableView<Muestra> tblResultados;
    @FXML private TableColumn<Muestra, String> colCodigoInterno;
    @FXML private TableColumn<Muestra, String> colDescripcion;
    @FXML private TableColumn<Muestra, String> colRotulo;
    @FXML private TableColumn<Muestra, String> colMarca;
    @FXML private TableColumn<Muestra, String> colReferencia;
    @FXML private TableColumn<Muestra, Integer> colCantidad;
    @FXML private TableColumn<Muestra, Estado> colEstado;
    @FXML private TableColumn<Muestra, LocalDate> colFecha;
    @FXML private TableColumn<Muestra, String> colUbicacion;
    @FXML private TableColumn<Muestra, String> colEstante;
    @FXML private TableColumn<Muestra, Usuario> colTecnico;

    @FXML private ImageView imgDetalle;
    @FXML private Label lblDetalleCodigoInterno;
    @FXML private Label lblDetalleDescripcion;
    @FXML private Label lblDetalleRotulo;
    @FXML private Label lblDetalleMarca;
    @FXML private Label lblDetalleReferencia;
    @FXML private Label lblDetalleCantidad;
    @FXML private Label lblDetalleEstado;
    @FXML private Label lblDetalleFecha;
    @FXML private Label lblDetalleUbicacion;
    @FXML private Label lblDetalleEstante;
    @FXML private Label lblDetalleTecnico;
    @FXML private Label lblDetalleNumeroInforme;
    @FXML private Label lblDetalleNumeroCotizacion;
    @FXML private Label lblDetalleObservacionAlmacenamiento;

    private ObservableList<Muestra> listaMuestras = FXCollections.observableArrayList();

    private Usuario usuario;

    @FXML
    public void initialize() {
        // Configurar columnas de la tabla
        colCodigoInterno.setCellValueFactory(new PropertyValueFactory<>("codigoInterno"));
        colDescripcion.setCellValueFactory(new PropertyValueFactory<>("descripcion"));
        colRotulo.setCellValueFactory(new PropertyValueFactory<>("rotuloCliente"));
        colMarca.setCellValueFactory(new PropertyValueFactory<>("marca"));
        colReferencia.setCellValueFactory(new PropertyValueFactory<>("referencia"));
        colCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        colFecha.setCellValueFactory(new PropertyValueFactory<>("fechaRecepcion"));
        colUbicacion.setCellValueFactory(new PropertyValueFactory<>("ubicacion"));
        colEstante.setCellValueFactory(new PropertyValueFactory<>("estante"));
        colTecnico.setCellValueFactory(new PropertyValueFactory<>("tecnico"));

        tblResultados.setItems(listaMuestras);
        txtBusquedaGeneral.textProperty().addListener((obs, textoAnterior, textoNuevo) -> buscarMuestras());

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
                     "SELECT m.*, t.id AS tecnico_id, t.nombre AS tecnico_nombre, " +
                             "t.rol AS tecnico_rol, t.rutaFoto AS tecnico_rutaFoto " +
                             "FROM muestras m LEFT JOIN usuarios t ON t.id = m.tecnicoId WHERE " +
                             "LOWER(COALESCE(m.codigoInterno, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.descripcion, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.rotuloCliente, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.marca, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.referencia, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.estado, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.ubicacion, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.estante, '')) LIKE ? OR " +
                             "LOWER(COALESCE(t.nombre, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.numeroInforme, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.numeroCotizacion, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.observacionAlmacenamiento, '')) LIKE ? OR " +
                             "CAST(m.cantidad AS VARCHAR) LIKE ? OR " +
                             "CAST(m.fechaRecepcion AS VARCHAR) LIKE ?")) {

            String busqueda = "%" + txtBusquedaGeneral.getText().trim().toLowerCase() + "%";
            for (int i = 1; i <= 14; i++) {
                ps.setString(i, busqueda);
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Muestra m = new Muestra();
                m.setId(rs.getInt("id"));
                m.setCodigoInterno(rs.getString("codigoInterno"));
                m.setDescripcion(rs.getString("descripcion"));
                m.setRotuloCliente(rs.getString("rotuloCliente"));
                m.setMarca(rs.getString("marca"));
                m.setReferencia(rs.getString("referencia"));
                m.setCantidad(rs.getInt("cantidad"));
                m.setEstado(Estado.valueOf(rs.getString("estado")));
                java.sql.Date fecha = rs.getDate("fechaRecepcion");
                m.setFechaRecepcion(fecha == null ? null : fecha.toLocalDate());
                m.setUbicacion(rs.getString("ubicacion"));
                m.setEstante(rs.getString("estante"));
                m.setObservacionAlmacenamiento(rs.getString("observacionAlmacenamiento"));
                m.setNumeroInforme(rs.getString("numeroInforme"));
                m.setNumeroCotizacion(rs.getString("numeroCotizacion"));
                int tecnicoId = rs.getInt("tecnico_id");
                if (!rs.wasNull()) {
                    m.setTecnico(new Usuario(
                            tecnicoId,
                            rs.getString("tecnico_nombre"),
                            domain.Rol.valueOf(rs.getString("tecnico_rol").toUpperCase()),
                            rs.getString("tecnico_rutaFoto")
                    ));
                }
                m.setRutaFoto(rs.getString("rutaFoto"));
                listaMuestras.add(m);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mostrarDetalle(Muestra muestra) {
        lblDetalleCodigoInterno.setText(textoSeguro(muestra.getCodigoInterno()));
        lblDetalleDescripcion.setText(textoSeguro(muestra.getDescripcion()));
        lblDetalleRotulo.setText(textoSeguro(muestra.getRotuloCliente()));
        lblDetalleMarca.setText(textoSeguro(muestra.getMarca()));
        lblDetalleReferencia.setText(textoSeguro(muestra.getReferencia()));
        lblDetalleCantidad.setText(String.valueOf(muestra.getCantidad()));
        lblDetalleEstado.setText(muestra.getEstado().name());
        lblDetalleFecha.setText(muestra.getFechaRecepcion() == null ? "" : muestra.getFechaRecepcion().toString());
        lblDetalleUbicacion.setText(textoSeguro(muestra.getUbicacion()));
        lblDetalleEstante.setText(textoSeguro(muestra.getEstante()));
        lblDetalleTecnico.setText(muestra.getTecnico() == null ? "" : muestra.getTecnico().getNombre());
        lblDetalleNumeroInforme.setText(textoSeguro(muestra.getNumeroInforme()));
        lblDetalleNumeroCotizacion.setText(textoSeguro(muestra.getNumeroCotizacion()));
        lblDetalleObservacionAlmacenamiento.setText(textoSeguro(muestra.getObservacionAlmacenamiento()));

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
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
        UsuarioSesion.setUsuario(usuario);
    }

    @FXML
    void editarInformacion() {
        Muestra muestra = obtenerMuestraSeleccionada();
        if (muestra == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/EditarMuestra.fxml"));
            Parent root = loader.load();
            EditarMuestraController controller = loader.getController();
            controller.setUsuario(usuario);
            controller.setAlActualizar(this::buscarMuestras);
            controller.editarMuestra(muestra);

            Stage stage = new Stage();
            stage.setTitle("Editar Muestra");
            stage.initOwner(tblResultados.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta(Alert.AlertType.ERROR, "Editar muestra", "No se pudo abrir la ventana de edicion");
        }
    }

    @FXML
    void asignarTecnico() {
        Muestra muestra = obtenerMuestraSeleccionada();
        if (muestra == null) return;

        List<Usuario> tecnicos = UsuarioSesion.obtenerUsuariosAsignables();
        if (tecnicos.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Asignar tecnico", "No hay usuarios disponibles para asignar");
            return;
        }

        ComboBox<Usuario> comboTecnico = new ComboBox<>(FXCollections.observableArrayList(tecnicos));
        comboTecnico.setMaxWidth(Double.MAX_VALUE);
        comboTecnico.setPromptText("Seleccione un tecnico");
        if (muestra.getTecnico() != null) {
            tecnicos.stream()
                    .filter(tecnico -> tecnico.getId() == muestra.getTecnico().getId())
                    .findFirst()
                    .ifPresent(comboTecnico::setValue);
        }

        Dialog<ButtonType> dialogo = crearDialogo("Asignar tecnico");
        GridPane contenido = crearFormulario();
        contenido.add(new Label("Tecnico"), 0, 0);
        contenido.add(comboTecnico, 1, 0);
        dialogo.getDialogPane().setContent(contenido);

        Optional<ButtonType> resultado = dialogo.showAndWait();
        if (resultado.isEmpty() || resultado.get() != ButtonType.OK) return;
        if (comboTecnico.getValue() == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Asignar tecnico", "Seleccione un tecnico");
            return;
        }

        if (new MuestraService().asignarTecnico(muestra.getId(), comboTecnico.getValue())) {
            buscarMuestras();
            mostrarAlerta(Alert.AlertType.INFORMATION, "Asignar tecnico", "Tecnico asignado correctamente");
        } else {
            mostrarAlerta(Alert.AlertType.ERROR, "Asignar tecnico", "No se pudo guardar la asignacion");
        }
    }

    @FXML
    void almacenarMuestra() {
        Muestra muestra = obtenerMuestraSeleccionada();
        if (muestra == null) return;

        TextField txtUbicacion = new TextField(textoSeguro(muestra.getUbicacion()));
        TextField txtEstante = new TextField(textoSeguro(muestra.getEstante()));
        TextArea txtObservaciones = new TextArea(textoSeguro(muestra.getObservacionAlmacenamiento()));
        txtObservaciones.setPrefRowCount(3);

        Dialog<ButtonType> dialogo = crearDialogo("Almacenar muestra");
        GridPane contenido = crearFormulario();
        contenido.add(new Label("Ubicacion"), 0, 0);
        contenido.add(txtUbicacion, 1, 0);
        contenido.add(new Label("Estante"), 0, 1);
        contenido.add(txtEstante, 1, 1);
        contenido.add(new Label("Observaciones"), 0, 2);
        contenido.add(txtObservaciones, 1, 2);
        dialogo.getDialogPane().setContent(contenido);

        Optional<ButtonType> resultado = dialogo.showAndWait();
        if (resultado.isEmpty() || resultado.get() != ButtonType.OK) return;
        if (txtUbicacion.getText().isBlank() || txtEstante.getText().isBlank()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Almacenar muestra", "Complete ubicacion y estante");
            return;
        }

        if (new MuestraService().almacenarMuestra(
                muestra.getId(),
                txtUbicacion.getText(),
                txtEstante.getText(),
                txtObservaciones.getText(),
                UsuarioSesion.getUsuario())) {
            buscarMuestras();
            mostrarAlerta(Alert.AlertType.INFORMATION, "Almacenar muestra", "Muestra almacenada correctamente");
        } else {
            mostrarAlerta(Alert.AlertType.ERROR, "Almacenar muestra", "No se pudo almacenar la muestra");
        }
    }

    private Muestra obtenerMuestraSeleccionada() {
        Muestra muestra = tblResultados.getSelectionModel().getSelectedItem();
        if (muestra == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Muestras", "Seleccione una muestra");
        }
        return muestra;
    }

    private Dialog<ButtonType> crearDialogo(String titulo) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(titulo);
        dialogo.initOwner(tblResultados.getScene().getWindow());
        dialogo.initModality(Modality.WINDOW_MODAL);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        return dialogo;
    }

    private GridPane crearFormulario() {
        GridPane formulario = new GridPane();
        formulario.setHgap(10);
        formulario.setVgap(10);
        formulario.setPadding(new Insets(12));
        return formulario;
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alerta = new Alert(tipo);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.showAndWait();
    }

    private String textoSeguro(String texto) {
        return texto == null ? "" : texto;
    }
}
