package controllers;

import db.Database;
import domain.Estado;
import domain.Muestra;
import domain.Usuario;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.prefs.Preferences;

import javafx.stage.Stage;
import javafx.stage.Modality;
import service.MuestraService;
import service.RemisionService;
import utilities.AppWindow;
import utilities.ImageStorage;
import utilities.UsuarioSesion;

public class BuscarMuestrasController {

    private static final String IMAGEN_PRODUCTO_DEFECTO = "/images/default_image.png";
    private static final double ANCHO_MINIMO_COLUMNA = 45.0;
    private static final double MARGEN_TEXTO_COLUMNA = 30.0;
    private static final String PREF_ORDER = "buscarMuestras.columnOrder";
    private static final String PREF_WIDTH_PREFIX = "buscarMuestras.columnWidth.";
    private static final List<String> ESTILOS_ESTADO_FILA = List.of(
            "estado-almacenado",
            "estado-disposicion",
            "estado-custodia",
            "estado-curso",
            "estado-lista",
            "estado-externo",
            "estado-enviado",
            "estado-destruccion"
    );

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
    @FXML private TableColumn<Muestra, String> colInforme;
    @FXML private TableColumn<Muestra, String> colCotizacion;
    @FXML private TableColumn<Muestra, String> colRemision;

    @FXML private ImageView imgDetalle;
    @FXML private Label lblDetalleEstado;
    @FXML private Label lblDetalleUbicacion;
    @FXML private Label lblTituloDetalleUbicacion;
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
    @FXML private Button btnAsignarInformeCotizacion;
    @FXML private Button btnAsignarTecnico;
    @FXML private Button btnAlmacenarMuestra;
    @FXML private Button btnEliminarMuestra;


    private ObservableList<Muestra> listaMuestras = FXCollections.observableArrayList();
    private final Preferences preferencias = Preferences.userNodeForPackage(BuscarMuestrasController.class);
    private final Map<String, TableColumn<Muestra, ?>> columnasPorId = new LinkedHashMap<>();
    private final RemisionService remisionService = new RemisionService();
    private boolean restaurandoDisposicionTabla;
    private boolean usuarioConfiguroTabla;
    private boolean tieneDisposicionGuardada;

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
        colInforme.setCellValueFactory(new PropertyValueFactory<>("numeroInforme"));
        colCotizacion.setCellValueFactory(new PropertyValueFactory<>("numeroCotizacion"));
        colRemision.setCellValueFactory(new PropertyValueFactory<>("remision"));
        configurarColumnaRemision();
        configurarPersistenciaColumnas();

        tblResultados.setItems(listaMuestras);
        tblResultados.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        txtBusquedaGeneral.textProperty().addListener((obs, textoAnterior, textoNuevo) -> buscarMuestras());

        // Listener para actualizar panel de detalle al seleccionar una fila
        tblResultados.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                mostrarDetalle(newSel);
            }
        });
        tblResultados.setRowFactory(tabla -> {
            TableRow<Muestra> fila = new TableRow<>() {
                @Override
                protected void updateItem(Muestra muestra, boolean empty) {
                    super.updateItem(muestra, empty);
                    getStyleClass().removeAll(ESTILOS_ESTADO_FILA);
                    if (!empty && muestra != null) {
                        getStyleClass().add(estiloEstadoFila(muestra.getEstado()));
                    }
                }
            };
            fila.setOnMouseClicked(evento -> {
                if (evento.getButton() == MouseButton.PRIMARY && evento.getClickCount() == 2 && !fila.isEmpty()) {
                    tblResultados.getSelectionModel().select(fila.getItem());
                    editarInformacion();
                }
            });
            return fila;
        });

        cargarImagenDetalle(null);
        // Cargar todos los datos inicialmente
        buscarMuestras();
    }

    private String estiloEstadoFila(Estado estado) {
        if (estado == null) {
            return "estado-custodia";
        }

        return switch (estado) {
            case ALMACENADO -> "estado-almacenado";
            case REALIZAR_DISPOSICION_FINAL -> "estado-disposicion";
            case EN_CUSTODIA -> "estado-custodia";
            case EN_CURSO -> "estado-curso";
            case LISTA_PARA_ALMACENAR -> "estado-lista";
            case LABORATORIO_EXTERNO -> "estado-externo";
            case ENVIADO -> "estado-enviado";
            case DESTRUCCION -> "estado-destruccion";
        };
    }

    private void configurarColumnaRemision() {
        colRemision.setCellFactory(columna -> new TableCell<>() {
            private final Label etiqueta = new Label();
            private final Button boton = crearBotonVerRemision();
            private final HBox contenido = new HBox(4, etiqueta, boton);

            {
                contenido.setAlignment(Pos.CENTER_LEFT);
                etiqueta.getStyleClass().add("table-remision-label");
                etiqueta.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(etiqueta, javafx.scene.layout.Priority.ALWAYS);
                boton.setOnAction(evento -> abrirPdfRemision(getItem()));
            }

            @Override
            protected void updateItem(String remision, boolean empty) {
                super.updateItem(remision, empty);
                if (empty || remision == null || remision.isBlank()) {
                    setGraphic(null);
                    return;
                }
                etiqueta.setText(remision.trim());
                setGraphic(contenido);
            }
        });
    }

    private Button crearBotonVerRemision() {
        Button boton = new Button();
        boton.setMinSize(18, 18);
        boton.setPrefSize(18, 18);
        boton.setMaxSize(18, 18);
        boton.setTooltip(new Tooltip("Ver PDF de remision"));
        boton.getStyleClass().add("table-icon-button");
        SVGPath icono = new SVGPath();
        icono.setContent("M9 17A8 8 0 1 1 9 1A8 8 0 0 1 9 17M15 15L22 22");
        icono.setScaleX(0.72);
        icono.setScaleY(0.72);
        icono.setStyle("-fx-stroke: #063f3b; -fx-stroke-width: 1.8; -fx-fill: transparent;");
        boton.setGraphic(icono);
        return boton;
    }

    private void abrirPdfRemision(String remision) {
        int consecutivo = extraerConsecutivoRemision(remision);
        if (consecutivo < 1) {
            mostrarAlerta(Alert.AlertType.WARNING, "Ver PDF", "La remision seleccionada no tiene un consecutivo valido.");
            return;
        }

        try {
            String rutaArchivo = remisionService.obtenerRutaArchivo(consecutivo);
            if (rutaArchivo == null || rutaArchivo.isBlank()) {
                mostrarAlerta(Alert.AlertType.WARNING, "Ver PDF",
                        "La remision " + remision + " no tiene PDF registrado.");
                return;
            }

            File archivo = new File(rutaArchivo);
            if (!archivo.exists() || !archivo.isFile()) {
                mostrarAlerta(Alert.AlertType.WARNING, "Ver PDF",
                        "No se encontro el PDF en la ruta registrada:\n" + rutaArchivo);
                return;
            }

            if (!Desktop.isDesktopSupported()) {
                mostrarAlerta(Alert.AlertType.ERROR, "Ver PDF",
                        "Windows no permitio abrir el PDF automaticamente.\nRuta: " + rutaArchivo);
                return;
            }

            Desktop.getDesktop().open(archivo);
        } catch (IOException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Ver PDF", "No se pudo abrir el PDF:\n" + mensajeRaiz(e));
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Ver PDF", mensajeRaiz(e));
        }
    }

    private int extraerConsecutivoRemision(String remision) {
        if (remision == null) {
            return 0;
        }
        String digitos = remision.replaceAll("\\D", "");
        if (digitos.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(digitos);
        } catch (NumberFormatException e) {
            return 0;
        }
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
                             "LOWER(COALESCE(m.remision, '')) LIKE ? OR " +
                             "LOWER(COALESCE(m.observacionAlmacenamiento, '')) LIKE ? OR " +
                             "CAST(m.fechaRecepcion AS VARCHAR) LIKE ?")) {

            String textoBusqueda = txtBusquedaGeneral.getText().trim();
            String busqueda = "%" + textoBusqueda.toLowerCase() + "%";
            for (int i = 1; i <= 15; i++) {
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
                m.setEstado(leerEstadoSeguro(rs.getString("estado")));
                m.setFechaRecepcion(leerFechaSeguro(rs, "fechaRecepcion"));
                m.setUbicacion(rs.getString("ubicacion"));
                m.setObservacionAlmacenamiento(rs.getString("observacionAlmacenamiento"));
                m.setNumeroInforme(rs.getString("numeroInforme"));
                m.setNumeroCotizacion(rs.getString("numeroCotizacion"));
                m.setRemision(rs.getString("remision"));
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
            Platform.runLater(tblResultados::refresh);
            ajustarColumnasPorContenidoSiCorresponde();

        } catch (Exception e) {
            e.printStackTrace();
            limpiarDetalle();
            ajustarColumnasPorContenidoSiCorresponde();
        }
    }

    private void configurarPersistenciaColumnas() {
        registrarColumna("codigoInterno", colCodigoInterno);
        registrarColumna("descripcion", colDescripcion);
        registrarColumna("rotuloCliente", colRotulo);
        registrarColumna("cliente", colCliente);
        registrarColumna("marca", colMarca);
        registrarColumna("referencia", colReferencia);
        registrarColumna("estado", colEstado);
        registrarColumna("fecha", colFecha);
        registrarColumna("ubicacion", colUbicacion);
        registrarColumna("tecnico", colTecnico);
        registrarColumna("informe", colInforme);
        registrarColumna("cotizacion", colCotizacion);
        registrarColumna("remision", colRemision);

        tieneDisposicionGuardada = preferencias.get(PREF_ORDER, null) != null;
        restaurarDisposicionColumnas();
        for (TableColumn<Muestra, ?> columna : columnasPorId.values()) {
            columna.widthProperty().addListener((obs, anterior, actual) -> guardarDisposicionColumnas());
        }
        tblResultados.getColumns().addListener((ListChangeListener<TableColumn<Muestra, ?>>) cambio -> {
            while (cambio.next()) {
                if (cambio.wasPermutated() || cambio.wasAdded() || cambio.wasRemoved() || cambio.wasReplaced()) {
                    guardarDisposicionColumnas();
                }
            }
        });
    }

    private void registrarColumna(String id, TableColumn<Muestra, ?> columna) {
        columna.setId(id);
        columnasPorId.put(id, columna);
    }

    private void restaurarDisposicionColumnas() {
        restaurandoDisposicionTabla = true;
        try {
            String ordenGuardado = preferencias.get(PREF_ORDER, "");
            List<TableColumn<Muestra, ?>> columnasOrdenadas = new ArrayList<>();
            if (!ordenGuardado.isBlank()) {
                for (String id : ordenGuardado.split(",")) {
                    TableColumn<Muestra, ?> columna = columnasPorId.get(id);
                    if (columna != null && !columnasOrdenadas.contains(columna)) {
                        columnasOrdenadas.add(columna);
                    }
                }
            }
            for (TableColumn<Muestra, ?> columna : columnasPorId.values()) {
                if (!columnasOrdenadas.contains(columna)) {
                    columnasOrdenadas.add(columna);
                }
            }
            if (!columnasOrdenadas.isEmpty()) {
                tblResultados.getColumns().setAll(columnasOrdenadas);
            }

            for (Map.Entry<String, TableColumn<Muestra, ?>> entrada : columnasPorId.entrySet()) {
                double ancho = preferencias.getDouble(PREF_WIDTH_PREFIX + entrada.getKey(), -1);
                if (ancho >= ANCHO_MINIMO_COLUMNA) {
                    entrada.getValue().setPrefWidth(ancho);
                }
            }
        } finally {
            restaurandoDisposicionTabla = false;
        }
    }

    private void guardarDisposicionColumnas() {
        if (restaurandoDisposicionTabla) {
            return;
        }
        usuarioConfiguroTabla = true;
        tieneDisposicionGuardada = true;

        StringBuilder orden = new StringBuilder();
        for (TableColumn<Muestra, ?> columna : tblResultados.getColumns()) {
            if (columna.getId() == null || columna.getId().isBlank()) {
                continue;
            }
            if (!orden.isEmpty()) {
                orden.append(',');
            }
            orden.append(columna.getId());
            preferencias.putDouble(PREF_WIDTH_PREFIX + columna.getId(), columna.getWidth());
        }
        preferencias.put(PREF_ORDER, orden.toString());
    }

    private void ajustarColumnasPorContenidoSiCorresponde() {
        if (!tieneDisposicionGuardada && !usuarioConfiguroTabla) {
            ajustarColumnasPorContenido();
        }
    }

    private void ajustarColumnasPorContenido() {
        Platform.runLater(() -> {
            if (tieneDisposicionGuardada || usuarioConfiguroTabla) {
                return;
            }
            restaurandoDisposicionTabla = true;
            for (TableColumn<Muestra, ?> columna : tblResultados.getColumns()) {
                double anchoMayor = medirTexto(columna.getText());
                for (Muestra muestra : listaMuestras) {
                    Object valor = columna.getCellData(muestra);
                    anchoMayor = Math.max(anchoMayor, medirTexto(valor == null ? "" : valor.toString()));
                }
                columna.setPrefWidth(Math.ceil(Math.max(ANCHO_MINIMO_COLUMNA, anchoMayor + MARGEN_TEXTO_COLUMNA)));
            }
            restaurandoDisposicionTabla = false;
        });
    }

    private double medirTexto(String texto) {
        Text medidor = new Text(texto == null ? "" : texto);
        return medidor.getLayoutBounds().getWidth();
    }

    private Estado leerEstadoSeguro(String estado) {
        try {
            return Estado.desdeTexto(estado);
        } catch (IllegalArgumentException e) {
            return Estado.EN_CUSTODIA;
        }
    }

    private LocalDate leerFechaSeguro(ResultSet rs, String columna) {
        try {
            String valor = rs.getString(columna);
            if (valor == null || valor.isBlank()) {
                return null;
            }
            valor = valor.trim();
            int espacio = valor.indexOf(' ');
            if (espacio > 0) {
                valor = valor.substring(0, espacio);
            }
            try {
                return LocalDate.parse(valor);
            } catch (DateTimeParseException ignored) {
                return LocalDate.parse(valor, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
        } catch (Exception e) {
            return null;
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
        boolean muestraEnviada = muestra.getEstado() == Estado.ENVIADO;
        lblTituloDetalleUbicacion.setText(muestraEnviada ? "REMISIÓN" : "UBICACIÓN");
        lblDetalleUbicacion.setText(muestraEnviada
                ? textoDetalle(muestra.getRemision())
                : textoDetalle(muestra.getUbicacion()));
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
        for (Button boton : List.of(btnEditarInformacion, btnAsignarInformeCotizacion, btnAsignarTecnico, btnAlmacenarMuestra, btnEliminarMuestra)) {
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
            String url = ImageStorage.resolveImageUrl(rutaFoto);
            if (url == null) {
                imgDetalle.setImage(cargarImagenProductoDefecto());
                return;
            }
            Image img = new Image(url);
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
            ScrollPane scroll = new ScrollPane(root);
            scroll.setFitToWidth(true);
            scroll.setPannable(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scroll.setStyle("-fx-background-color: transparent; -fx-background: #EEF2F2;");
            stage.setScene(new Scene(scroll));
            stage.show();
            AppWindow.ocuparAreaVisible(stage);
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
    void asignarInformeCotizacion() {
        if (!verificarControlMuestras()) return;
        List<Muestra> muestras = obtenerMuestrasSeleccionadas();
        if (muestras.isEmpty()) return;

        TextField txtInforme = new TextField();
        TextField txtCotizacion = new TextField();
        UnaryOperator<TextFormatter.Change> filtroCuatroDigitos = cambio ->
                cambio.getControlNewText().matches("\\d{0,4}") ? cambio : null;
        txtInforme.setTextFormatter(new TextFormatter<>(filtroCuatroDigitos));
        txtCotizacion.setTextFormatter(new TextFormatter<>(filtroCuatroDigitos));
        txtInforme.setPromptText("4 digitos");
        txtCotizacion.setPromptText("4 digitos");

        if (muestras.size() == 1) {
            txtInforme.setText(textoSeguro(muestras.get(0).getNumeroInforme()));
            txtCotizacion.setText(textoSeguro(muestras.get(0).getNumeroCotizacion()));
        }

        Dialog<ButtonType> dialogo = crearDialogo("Asignar informe / cotizacion");
        GridPane contenido = crearFormulario();
        Label ayuda = new Label("Se aplicara a " + muestras.size()
                + " muestra(s). Deje un campo vacio para no modificarlo.");
        ayuda.setWrapText(true);
        contenido.add(ayuda, 0, 0, 2, 1);
        contenido.add(new Label("Informe"), 0, 1);
        contenido.add(txtInforme, 1, 1);
        contenido.add(new Label("Cotizacion"), 0, 2);
        contenido.add(txtCotizacion, 1, 2);
        dialogo.getDialogPane().setContent(contenido);

        Optional<ButtonType> resultado = dialogo.showAndWait();
        if (resultado.isEmpty() || resultado.get() != ButtonType.OK) return;

        String informe = normalizarCodigoAsignacion(txtInforme.getText());
        String cotizacion = normalizarCodigoAsignacion(txtCotizacion.getText());
        boolean actualizarInforme = informe != null;
        boolean actualizarCotizacion = cotizacion != null;
        if (!actualizarInforme && !actualizarCotizacion) {
            mostrarAlerta(Alert.AlertType.WARNING, "Asignar informe / cotizacion",
                    "Digite el informe, la cotizacion o ambos campos");
            return;
        }

        MuestraService service = new MuestraService();
        int actualizadas = 0;
        for (Muestra muestra : muestras) {
            if (service.actualizarInformeCotizacion(
                    muestra.getId(),
                    informe,
                    cotizacion,
                    usuarioActual(),
                    actualizarInforme,
                    actualizarCotizacion)) {
                actualizadas++;
            }
        }

        buscarMuestras();
        if (actualizadas == muestras.size()) {
            mostrarAlerta(Alert.AlertType.INFORMATION, "Asignar informe / cotizacion",
                    "Se actualizaron " + actualizadas + " muestra(s)");
        } else {
            mostrarAlerta(Alert.AlertType.WARNING, "Asignar informe / cotizacion",
                    "Se actualizaron " + actualizadas + " de " + muestras.size() + " muestra(s)");
        }
    }

    @FXML
    void almacenarMuestra() {
        if (!verificarControlMuestras()) return;
        List<Muestra> muestras = obtenerMuestrasSeleccionadas();
        if (muestras.isEmpty()) return;
        Muestra muestraBase = muestras.get(0);

        // Crear los campos de texto para el diálogo
        TextField txtUbicacion = new TextField(muestras.size() == 1 ? textoSeguro(muestraBase.getUbicacion()) : "");
        TextArea txtObservaciones = new TextArea(muestras.size() == 1
                ? textoSeguro(muestraBase.getObservacionAlmacenamiento())
                : "");
        txtObservaciones.setPrefRowCount(3);
        List<Usuario> usuariosResponsables = UsuarioSesion.obtenerTodosUsuarios();
        if (usuariosResponsables.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Almacenar muestra", "No hay usuarios disponibles para seleccionar como responsable");
            return;
        }
        ComboBox<Usuario> comboResponsable = new ComboBox<>(FXCollections.observableArrayList(usuariosResponsables));
        comboResponsable.setMaxWidth(Double.MAX_VALUE);
        comboResponsable.setPromptText("Seleccione un responsable");
        if (muestras.size() == 1 && muestraBase.getResponsableAlmacenamiento() != null) {
            usuariosResponsables.stream()
                    .filter(responsable -> responsable.getId() == muestraBase.getResponsableAlmacenamiento().getId())
                    .findFirst()
                    .ifPresent(comboResponsable::setValue);
        }

        // Crear el diálogo
        Dialog<ButtonType> dialogo = crearDialogo(muestras.size() == 1 ? "Almacenar muestra" : "Almacenar muestras");
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
        MuestraService service = new MuestraService();
        int almacenadas = 0;
        for (Muestra muestra : muestras) {
            if (service.almacenarMuestra(
                    muestra.getId(),
                    txtUbicacion.getText(),
                    txtObservaciones.getText(),
                    comboResponsable.getValue(),
                    UsuarioSesion.getUsuario())) {
                almacenadas++;
            }
        }

        buscarMuestras();
        if (almacenadas == muestras.size()) {
            mostrarAlerta(Alert.AlertType.INFORMATION, "Almacenar muestra",
                    "Se almacenaron " + almacenadas + " muestra(s) correctamente");
        } else {
            mostrarAlerta(Alert.AlertType.WARNING, "Almacenar muestra",
                    "Se almacenaron " + almacenadas + " de " + muestras.size() + " muestra(s)");
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

    private List<Muestra> obtenerMuestrasSeleccionadas() {
        List<Muestra> muestras = new ArrayList<>(tblResultados.getSelectionModel().getSelectedItems());
        if (muestras.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Muestras", "Seleccione una o mas muestras");
        }
        return muestras;
    }

    private String normalizarCodigoAsignacion(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
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

    private String mensajeRaiz(Throwable error) {
        Throwable actual = error;
        while (actual.getCause() != null) {
            actual = actual.getCause();
        }
        return textoSeguro(actual.getMessage()).isBlank() ? "Ocurrio un error inesperado." : actual.getMessage();
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
        lblTituloDetalleUbicacion.setText("UBICACIÓN");
        lblDetalleInforme.setText("");
        lblDetalleCotizacion.setText("");
        lblDetalleObservaciones.setText("");
        btnFinalizarEnsayos.setDisable(true);
        btnFinalizarEnsayos.setVisible(false);
        btnFinalizarEnsayos.setManaged(false);
        btnFinalizarEnsayos.setTooltip(new Tooltip("Seleccione una muestra"));
        for (Button boton : List.of(btnEditarInformacion, btnAsignarInformeCotizacion, btnAsignarTecnico, btnAlmacenarMuestra, btnEliminarMuestra)) {
            boton.setVisible(false);
            boton.setManaged(false);
        }
    }
}
