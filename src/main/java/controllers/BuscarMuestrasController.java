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
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import javafx.stage.Stage;
import javafx.stage.Modality;
import service.MuestraService;
import utilities.UsuarioSesion;

public class BuscarMuestrasController {

    private static final String IMAGEN_PRODUCTO_DEFECTO = "/images/default_image.png";

    @FXML private TextField txtBusquedaGeneral;

    @FXML private TableView<Muestra> tblResultados;
    @FXML private TableColumn<Muestra, String> colCodigoInterno;
    @FXML private TableColumn<Muestra, String> colDescripcion;
    @FXML private TableColumn<Muestra, String> colRotulo;
    @FXML private TableColumn<Muestra, String> colCliente;
    @FXML private TableColumn<Muestra, String> colMarca;
    @FXML private TableColumn<Muestra, String> colReferencia;
    @FXML private TableColumn<Muestra, Estado> colEstado;
    @FXML private TableColumn<Muestra, LocalDate> colFecha;
    @FXML private TableColumn<Muestra, String> colUbicacion;
    @FXML private TableColumn<Muestra, Usuario> colTecnico;

    @FXML private ImageView imgDetalle;
    @FXML private Label lblDetalleEstado;
    @FXML private Label lblDetalleUbicacion;
    @FXML private Label lblDetalleCodigoInterno;
    @FXML private Label lblDetalleDescripcion;
    @FXML private Label lblDetalleCliente;
    @FXML private Label lblDetalleMarca;
    @FXML private Label lblDetalleFecha;
    @FXML private Label lblDetalleTecnico;
    @FXML private Label lblDetalleResponsable;
    @FXML private Label lblDetalleInforme;
    @FXML private Label lblDetalleCotizacion;
    @FXML private Label lblDetalleObservaciones;
    @FXML private Button btnFinalizarEnsayos;
    @FXML private Button btnEditarInformacion;
    @FXML private Button btnAsignarTecnico;
    @FXML private Button btnAlmacenarMuestra;
    @FXML private Button btnEliminarMuestra;


    private ObservableList<Muestra> listaMuestras = FXCollections.observableArrayList();

    private Usuario usuario;

    @FXML
    public void initialize() {
        // Configurar columnas de la tabla
        colCodigoInterno.setCellValueFactory(new PropertyValueFactory<>("codigoInterno"));
        colDescripcion.setCellValueFactory(new PropertyValueFactory<>("descripcion"));
        colRotulo.setCellValueFactory(new PropertyValueFactory<>("rotuloCliente"));
        colCliente.setCellValueFactory(new PropertyValueFactory<>("nombreCliente"));
        colMarca.setCellValueFactory(new PropertyValueFactory<>("marca"));
        colReferencia.setCellValueFactory(new PropertyValueFactory<>("referencia"));
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        colFecha.setCellValueFactory(new PropertyValueFactory<>("fechaRecepcion"));
        colUbicacion.setCellValueFactory(new PropertyValueFactory<>("ubicacion"));
        colTecnico.setCellValueFactory(new PropertyValueFactory<>("tecnico"));

        tblResultados.setItems(listaMuestras);
        txtBusquedaGeneral.textProperty().addListener((obs, textoAnterior, textoNuevo) -> buscarMuestras());

        // Listener para actualizar panel de detalle al seleccionar una fila
        tblResultados.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                mostrarDetalle(newSel);
            }
        });

        cargarImagenDetalle(null);
        // Cargar todos los datos inicialmente
        buscarMuestras();
    }

    @FXML
    void buscarMuestras() {
        Muestra seleccionAnterior = tblResultados.getSelectionModel().getSelectedItem();
        Integer idSeleccionado = seleccionAnterior == null ? null : seleccionAnterior.getId();
        listaMuestras.clear();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT m.*, t.id AS tecnico_id, t.nombre AS tecnico_nombre, " +
                             "t.rol AS tecnico_rol, t.rutaFoto AS tecnico_rutaFoto, " +
                             "r.id AS responsable_id, r.nombre AS responsable_nombre, " +
                             "r.rol AS responsable_rol, r.rutaFoto AS responsable_rutaFoto " +
                             "FROM muestras m " +
                             "LEFT JOIN usuarios t ON t.id = m.tecnicoId " +
                             "LEFT JOIN usuarios r ON r.id = m.responsableId WHERE " +
                             "LOWER(COALESCE(m.codigoInterno, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.descripcion, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.rotuloCliente, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.nombreCliente, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.marca, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.referencia, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.estado, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.ubicacion, '')) LIKE ? OR " +
                             "LOWER(COALESCE(t.nombre, '')) LIKE ? OR " +
                             "LOWER(COALESCE(r.nombre, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.numeroInforme, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.numeroCotizacion, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.observacionAlmacenamiento, '')) LIKE ? OR " +
                             "CAST(m.fechaRecepcion AS VARCHAR) LIKE ?")) {

            String textoBusqueda = txtBusquedaGeneral.getText().trim();
            String busqueda = "%" + textoBusqueda.toLowerCase() + "%";
            for (int i = 1; i <= 14; i++) {
                ps.setString(i, busqueda);
            }
            try {
                ps.setString(7, "%" + Estado.desdeTexto(textoBusqueda).name().toLowerCase() + "%");
            } catch (IllegalArgumentException ignored) {
                // Para búsquedas parciales se conserva el texto escrito por el usuario.
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Muestra m = new Muestra();
                m.setId(rs.getInt("id"));
                m.setCodigoInterno(rs.getString("codigoInterno"));
                m.setDescripcion(rs.getString("descripcion"));
                m.setRotuloCliente(rs.getString("rotuloCliente"));
                m.setNombreCliente(rs.getString("nombreCliente"));
                m.setMarca(rs.getString("marca"));
                m.setReferencia(rs.getString("referencia"));
                m.setEstado(Estado.desdeTexto(rs.getString("estado")));
                java.sql.Date fecha = rs.getDate("fechaRecepcion");
                m.setFechaRecepcion(fecha == null ? null : fecha.toLocalDate());
                m.setUbicacion(rs.getString("ubicacion"));
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
                int responsableId = rs.getInt("responsable_id");
                if (!rs.wasNull()) {
                    m.setResponsableAlmacenamiento(new Usuario(
                            responsableId,
                            rs.getString("responsable_nombre"),
                            domain.Rol.valueOf(rs.getString("responsable_rol").toUpperCase()),
                            rs.getString("responsable_rutaFoto")
                    ));
                }
                m.setRutaFoto(rs.getString("rutaFoto"));
                listaMuestras.add(m);
            }

            actualizarSeleccionYDetalle(idSeleccionado);

        } catch (Exception e) {
            e.printStackTrace();
            limpiarDetalle();
        }
    }

    private void actualizarSeleccionYDetalle(Integer idSeleccionado) {
        if (idSeleccionado == null) {
            limpiarDetalle();
            return;
        }

        Muestra muestraActualizada = listaMuestras.stream()
                .filter(muestra -> muestra.getId() == idSeleccionado)
                .findFirst()
                .orElse(null);

        if (muestraActualizada == null) {
            limpiarDetalle();
            return;
        }

        tblResultados.getSelectionModel().select(muestraActualizada);
        tblResultados.scrollTo(muestraActualizada);
        mostrarDetalle(muestraActualizada);
    }

    private void mostrarDetalle(Muestra muestra) {
        lblDetalleCodigoInterno.setText(textoDetalle(muestra.getCodigoInterno()));
        lblDetalleEstado.setText(muestra.getEstado() == null ? "Sin datos" : muestra.getEstado().toString());
        lblDetalleFecha.setText(muestra.getFechaRecepcion() == null
                ? "Sin datos"
                : muestra.getFechaRecepcion().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        lblDetalleTecnico.setText(muestra.getTecnico() == null
                ? "Sin asignar"
                : textoDetalle(muestra.getTecnico().getNombre()));
        lblDetalleResponsable.setText(muestra.getResponsableAlmacenamiento() == null
                ? "Sin asignar"
                : textoDetalle(muestra.getResponsableAlmacenamiento().getNombre()));
        lblDetalleCliente.setText(textoDetalle(muestra.getNombreCliente()));
        lblDetalleMarca.setText(textoDetalle(muestra.getMarca()));
        lblDetalleUbicacion.setText(textoDetalle(muestra.getUbicacion()));
        lblDetalleInforme.setText(formatearInforme(muestra));
        lblDetalleCotizacion.setText(formatearCotizacion(muestra));
        lblDetalleDescripcion.setText(textoDetalle(muestra.getDescripcion()));
        lblDetalleObservaciones.setText(textoDetalle(muestra.getObservacionAlmacenamiento()));
        actualizarAccionesControlMuestras();
        actualizarAccionFinalizarEnsayos(muestra);

        cargarImagenDetalle(muestra.getRutaFoto());
    }

    private void actualizarAccionFinalizarEnsayos(Muestra muestra) {
        Usuario usuarioActual = usuario != null ? usuario : UsuarioSesion.getUsuario();
        boolean tieneTecnico = muestra.getTecnico() != null;
        boolean estaEnCurso = muestra.getEstado() == Estado.EN_CURSO;
        boolean puedeFinalizarPorRol = usuarioActual != null && usuarioActual.puedeFinalizarEnsayos();
        boolean esTecnicoAsignado = usuarioActual != null
                && tieneTecnico
                && muestra.getTecnico().getId() == usuarioActual.getId();
        boolean puedeFinalizar = estaEnCurso
                && esTecnicoAsignado
                && puedeFinalizarPorRol;
        btnFinalizarEnsayos.setDisable(!puedeFinalizar);
        btnFinalizarEnsayos.setVisible(puedeFinalizar);
        btnFinalizarEnsayos.setManaged(puedeFinalizar);

        String ayuda;
        if (!estaEnCurso) {
            ayuda = "La muestra debe estar en estado En curso";
        } else if (usuarioActual == null) {
            ayuda = "No hay un usuario autenticado";
        } else if (!puedeFinalizarPorRol) {
            ayuda = "Su rol no permite finalizar ensayos";
        } else if (!tieneTecnico) {
            ayuda = "La muestra no tiene un técnico asignado";
        } else if (!esTecnicoAsignado) {
            ayuda = "Solo " + muestra.getTecnico().getNombre() + " puede finalizar estos ensayos";
        } else {
            ayuda = "Marcar la muestra como lista para almacenar";
        }
        btnFinalizarEnsayos.setTooltip(new Tooltip(ayuda));
    }

    private void actualizarAccionesControlMuestras() {
        Usuario usuarioActual = usuario != null ? usuario : UsuarioSesion.getUsuario();
        boolean permitido = usuarioActual != null && usuarioActual.puedeControlarMuestras();
        for (Button boton : List.of(btnEditarInformacion, btnAsignarTecnico, btnAlmacenarMuestra, btnEliminarMuestra)) {
            boton.setVisible(permitido);
            boton.setManaged(permitido);
        }
    }

    private void cargarImagenDetalle(String rutaFoto) {
        if (rutaFoto == null || rutaFoto.isBlank()) {
            imgDetalle.setImage(cargarImagenProductoDefecto());
            return;
        }

        try {
            File archivo = new File(rutaFoto);
            Image img = archivo.exists()
                    ? new Image(archivo.toURI().toString())
                    : new Image(rutaFoto);

            imgDetalle.setImage(img.isError() ? cargarImagenProductoDefecto() : img);
        } catch (Exception e) {
            imgDetalle.setImage(cargarImagenProductoDefecto());
        }
    }

    private Image cargarImagenProductoDefecto() {
        URL recurso = getClass().getResource(IMAGEN_PRODUCTO_DEFECTO);
        return recurso == null ? null : new Image(recurso.toExternalForm());
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
        if (!verificarControlMuestras()) return;
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
        if (!verificarControlMuestras()) return;
        Muestra muestra = obtenerMuestraSeleccionada();
        if (muestra == null) return;

        List<Usuario> tecnicos = UsuarioSesion.obtenerUsuariosAsignables();
        if (tecnicos.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Asignar técnico", "No hay usuarios disponibles para asignar");
            return;
        }

        ComboBox<Usuario> comboTecnico = new ComboBox<>(FXCollections.observableArrayList(tecnicos));
        comboTecnico.setMaxWidth(Double.MAX_VALUE);
        comboTecnico.setPromptText("Seleccione un técnico");
        if (muestra.getTecnico() != null) {
            tecnicos.stream()
                    .filter(tecnico -> tecnico.getId() == muestra.getTecnico().getId())
                    .findFirst()
                    .ifPresent(comboTecnico::setValue);
        }

        Dialog<ButtonType> dialogo = crearDialogo("Asignar técnico");
        GridPane contenido = crearFormulario();
        contenido.add(new Label("Tecnico"), 0, 0);
        contenido.add(comboTecnico, 1, 0);
        dialogo.getDialogPane().setContent(contenido);

        Optional<ButtonType> resultado = dialogo.showAndWait();
        if (resultado.isEmpty() || resultado.get() != ButtonType.OK) return;
        if (comboTecnico.getValue() == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Asignar técnico", "Seleccione un técnico");
            return;
        }

        if (new MuestraService().asignarTecnico(muestra.getId(), comboTecnico.getValue(), usuarioActual())) {
            buscarMuestras();
            mostrarAlerta(Alert.AlertType.INFORMATION, "Asignar técnico", "Técnico asignado correctamente");
        } else {
            mostrarAlerta(Alert.AlertType.ERROR, "Asignar técnico", "No se pudo guardar la asignación");
        }
    }

    @FXML
    void almacenarMuestra() {
        if (!verificarControlMuestras()) return;
        Muestra muestra = obtenerMuestraSeleccionada();
        if (muestra == null) return;

        // Crear los campos de texto para el diálogo
        TextField txtUbicacion = new TextField(textoSeguro(muestra.getUbicacion()));
        TextArea txtObservaciones = new TextArea(textoSeguro(muestra.getObservacionAlmacenamiento()));
        txtObservaciones.setPrefRowCount(3);
        List<Usuario> usuariosResponsables = UsuarioSesion.obtenerTodosUsuarios();
        if (usuariosResponsables.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Almacenar muestra", "No hay usuarios disponibles para seleccionar como responsable");
            return;
        }
        ComboBox<Usuario> comboResponsable = new ComboBox<>(FXCollections.observableArrayList(usuariosResponsables));
        comboResponsable.setMaxWidth(Double.MAX_VALUE);
        comboResponsable.setPromptText("Seleccione un responsable");
        if (muestra.getResponsableAlmacenamiento() != null) {
            usuariosResponsables.stream()
                    .filter(responsable -> responsable.getId() == muestra.getResponsableAlmacenamiento().getId())
                    .findFirst()
                    .ifPresent(comboResponsable::setValue);
        }

        // Crear el diálogo
        Dialog<ButtonType> dialogo = crearDialogo("Almacenar muestra");
        GridPane contenido = crearFormulario();
        contenido.add(new Label("Ubicación *"), 0, 0);
        contenido.add(txtUbicacion, 1, 0);
        contenido.add(new Label("Responsable *"), 0, 1);
        contenido.add(comboResponsable, 1, 1);
        contenido.add(new Label("Observaciones"), 0, 2);
        contenido.add(txtObservaciones, 1, 2);

        dialogo.getDialogPane().setContent(contenido);

        // Mostrar diálogo y esperar respuesta
        Optional<ButtonType> resultado = dialogo.showAndWait();
        if (resultado.isEmpty() || resultado.get() != ButtonType.OK) return;

        // Validar campos obligatorios
        if (txtUbicacion.getText().isBlank() || comboResponsable.getValue() == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Almacenar muestra", "Complete la ubicación y seleccione un responsable");
            return;
        }

        // Guardar la información
        if (new MuestraService().almacenarMuestra(
                muestra.getId(),
                txtUbicacion.getText(),
                txtObservaciones.getText(),
                comboResponsable.getValue(),
                UsuarioSesion.getUsuario())) {
            buscarMuestras();
            mostrarAlerta(Alert.AlertType.INFORMATION, "Almacenar muestra", "Muestra almacenada correctamente");
        } else {
            mostrarAlerta(Alert.AlertType.ERROR, "Almacenar muestra", "No se pudo almacenar la muestra");
        }
    }

    @FXML
    void eliminarMuestra() {
        if (!verificarControlMuestras()) return;
        Muestra muestra = obtenerMuestraSeleccionada();
        if (muestra == null) return;

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Eliminar muestra");
        confirmacion.setHeaderText(null);
        confirmacion.setContentText("¿Desea eliminar la muestra " + textoSeguro(muestra.getCodigoInterno()) + "?");

        Optional<ButtonType> resultado = confirmacion.showAndWait();
        if (resultado.isEmpty() || resultado.get() != ButtonType.OK) {
            return;
        }

        if (new MuestraService().eliminarMuestra(muestra.getId(), usuarioActual())) {
            limpiarDetalle();
            buscarMuestras();
            mostrarAlerta(Alert.AlertType.INFORMATION, "Eliminar muestra", "Muestra eliminada correctamente");
        } else {
            mostrarAlerta(Alert.AlertType.ERROR, "Eliminar muestra", "No se pudo eliminar la muestra");
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

    @FXML
    void finalizarEnsayos() {
        Muestra muestra = obtenerMuestraSeleccionada();
        if (muestra == null) return;

        Usuario tecnicoActual = usuario != null ? usuario : UsuarioSesion.getUsuario();
        if (!puedeFinalizarEnsayos(muestra, tecnicoActual)) {
            mostrarAlerta(Alert.AlertType.WARNING, "Finalizar ensayos",
                    "El usuario actual no tiene permiso para finalizar los ensayos de esta muestra");
            return;
        }

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Finalizar ensayos");
        confirmacion.setHeaderText(null);
        confirmacion.setContentText("¿Confirma que los ensayos finalizaron y la muestra está lista para almacenar?");
        Optional<ButtonType> resultado = confirmacion.showAndWait();
        if (resultado.isEmpty() || resultado.get() != ButtonType.OK) {
            return;
        }

        if (new MuestraService().finalizarEnsayos(muestra.getId(), tecnicoActual)) {
            buscarMuestras();
            mostrarAlerta(Alert.AlertType.INFORMATION, "Finalizar ensayos",
                    "La muestra quedó lista para almacenar");
        } else {
            mostrarAlerta(Alert.AlertType.ERROR, "Finalizar ensayos",
                    "No se pudieron finalizar los ensayos de la muestra");
        }
    }

    private String textoDetalle(String texto) {
        return texto == null || texto.isBlank() ? "Sin datos" : texto.trim();
    }

    private boolean puedeFinalizarEnsayos(Muestra muestra, Usuario usuarioActual) {
        if (muestra == null || usuarioActual == null || muestra.getEstado() != Estado.EN_CURSO) {
            return false;
        }
        return usuarioActual.puedeFinalizarEnsayos()
                && muestra.getTecnico() != null
                && muestra.getTecnico().getId() == usuarioActual.getId();
    }

    private Usuario usuarioActual() {
        return usuario != null ? usuario : UsuarioSesion.getUsuario();
    }

    private boolean verificarControlMuestras() {
        Usuario actual = usuarioActual();
        if (actual != null && actual.puedeControlarMuestras()) {
            return true;
        }
        mostrarAlerta(Alert.AlertType.WARNING, "Permiso requerido",
                "No tiene permiso para controlar muestras");
        return false;
    }

    private String formatearInforme(Muestra muestra) {
        String codigo = muestra.getNumeroInforme();
        if (codigo == null || codigo.isBlank()) {
            return "Sin datos";
        }
        return "LENC - " + obtenerAnioCorto(muestra) + " - I " + codigo.trim();
    }

    private String formatearCotizacion(Muestra muestra) {
        String codigo = muestra.getNumeroCotizacion();
        if (codigo == null || codigo.isBlank()) {
            return "Sin datos";
        }
        return "LENC-" + obtenerAnioCorto(muestra) + "-C" + codigo.trim();
    }

    private String obtenerAnioCorto(Muestra muestra) {
        int anio = muestra.getFechaRecepcion() == null
                ? Year.now().getValue()
                : muestra.getFechaRecepcion().getYear();
        return String.format("%02d", anio % 100);
    }

    private void limpiarDetalle() {
        cargarImagenDetalle(null);
        lblDetalleCodigoInterno.setText("");
        lblDetalleEstado.setText("");
        lblDetalleFecha.setText("");
        lblDetalleTecnico.setText("");
        lblDetalleResponsable.setText("");
        lblDetalleCliente.setText("");
        lblDetalleDescripcion.setText("");
        lblDetalleMarca.setText("");
        lblDetalleUbicacion.setText("");
        lblDetalleInforme.setText("");
        lblDetalleCotizacion.setText("");
        lblDetalleObservaciones.setText("");
        btnFinalizarEnsayos.setDisable(true);
        btnFinalizarEnsayos.setVisible(false);
        btnFinalizarEnsayos.setManaged(false);
        btnFinalizarEnsayos.setTooltip(new Tooltip("Seleccione una muestra"));
        for (Button boton : List.of(btnEditarInformacion, btnAsignarTecnico, btnAlmacenarMuestra, btnEliminarMuestra)) {
            boton.setVisible(false);
            boton.setManaged(false);
        }
    }
}
