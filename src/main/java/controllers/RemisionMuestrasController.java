package controllers;

import domain.Muestra;
import domain.Usuario;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.VBox;
import service.RemisionService;
import utilities.UsuarioSesion;
import utilities.PdfRemisionWriter;

import java.time.LocalDate;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RemisionMuestrasController {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String OBSERVACION_PREDETERMINADA =
            "Las muestras fueron sometidas a ensayos de carácter destructivo; en consecuencia, " +
            "su integridad física y sus condiciones originales se encuentran alteradas, " +
            "por lo que no son aptas para su reutilización.";

    @FXML private TableView<Muestra> tblDisponibles;
    @FXML private TextField txtBuscarMuestra;
    @FXML private TableColumn<Muestra, Boolean> colSeleccion;
    @FXML private TableColumn<Muestra, String> colDisponibleInterna;
    @FXML private TableColumn<Muestra, String> colDisponibleExterna;
    @FXML private TableColumn<Muestra, String> colDisponibleDescripcion;
    @FXML private TableColumn<Muestra, String> colDisponibleCliente;
    @FXML private TableColumn<Muestra, String> colDisponibleEstado;

    @FXML private DatePicker dpFechaElaboracion;
    @FXML private Label lblConsecutivo;
    @FXML private TextField txtConsecutivo;
    @FXML private TextField txtCliente;
    @FXML private ComboBox<String> cmbTipoSalida;
    @FXML private Spinner<Integer> spnEmpaques;
    @FXML private TextArea txtObservacionFinal;
    @FXML private TextField txtFirma;
    @FXML private TextField txtCedula;
    @FXML private TextField txtNombrePlaca;

    @FXML private TableView<Muestra> tblRemision;
    @FXML private TableColumn<Muestra, String> colInterna;
    @FXML private TableColumn<Muestra, String> colExterna;
    @FXML private TableColumn<Muestra, String> colDescripcion;
    @FXML private TableColumn<Muestra, String> colMarca;
    @FXML private TableColumn<Muestra, String> colObservaciones;
    @FXML private TableColumn<Muestra, String> colRecepcion;
    @FXML private TableColumn<Muestra, String> colEntrega;

    @FXML private Label lblTotalMuestras;
    @FXML private Label lblEmpaques;
    @FXML private Label lblFechaDocumento;
    @FXML private Label lblClienteDocumento;
    @FXML private Label lblTipoSalidaDocumento;
    @FXML private Label lblObservacionDocumento;
    @FXML private Label lblEntregadoPor;
    @FXML private Label lblCargoEntregadoPor;
    @FXML private VBox documentoRemision;

    private final RemisionService remisionService = new RemisionService();
    private final ObservableList<Muestra> muestrasSeleccionadas = FXCollections.observableArrayList();
    private final ObservableList<Muestra> muestrasDisponibles = FXCollections.observableArrayList();
    private final FilteredList<Muestra> muestrasFiltradas = new FilteredList<>(muestrasDisponibles, muestra -> true);
    private final Map<Integer, BooleanProperty> seleccionPorMuestra = new LinkedHashMap<>();
    private Usuario usuario;

    @FXML
    public void initialize() {
        configurarTablas();
        dpFechaElaboracion.setValue(LocalDate.now());
        cmbTipoSalida.setItems(FXCollections.observableArrayList(
                "Solicitado por el cliente", "Envío a laboratorio externo", "Disposición final", "Otro"));
        cmbTipoSalida.getSelectionModel().selectFirst();
        txtObservacionFinal.setText(OBSERVACION_PREDETERMINADA);
        txtConsecutivo.setText(formatearConsecutivo(remisionService.siguienteConsecutivo()));
        txtConsecutivo.setTextFormatter(new TextFormatter<>(cambio ->
                cambio.getControlNewText().matches("\\d{0,9}") ? cambio : null));
        lblConsecutivo.setText(txtConsecutivo.getText());

        tblDisponibles.setEditable(true);
        tblDisponibles.setItems(muestrasFiltradas);
        txtBuscarMuestra.textProperty().addListener((obs, anterior, actual) -> filtrarMuestras(actual));

        dpFechaElaboracion.valueProperty().addListener((obs, anterior, actual) -> actualizarDocumento());
        txtCliente.textProperty().addListener((obs, anterior, actual) -> actualizarDocumento());
        cmbTipoSalida.valueProperty().addListener((obs, anterior, actual) -> actualizarDocumento());
        spnEmpaques.valueProperty().addListener((obs, anterior, actual) -> actualizarDocumento());
        txtObservacionFinal.textProperty().addListener((obs, anterior, actual) -> actualizarDocumento());
        txtConsecutivo.textProperty().addListener((obs, anterior, actual) -> actualizarDocumento());

        cargarMuestrasDisponibles();
        actualizarDocumento();
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
        UsuarioSesion.setUsuario(usuario);
        lblEntregadoPor.setText(texto(usuario == null ? null : usuario.getNombre(), "Sin asignar"));
        lblCargoEntregadoPor.setText(usuario == null ? "" : cargoVisible(usuario));
    }

    private void configurarTablas() {
        colSeleccion.setCellValueFactory(c -> propiedadSeleccion(c.getValue()));
        colSeleccion.setCellFactory(CheckBoxTableCell.forTableColumn(colSeleccion));
        colDisponibleInterna.setCellValueFactory(c -> new ReadOnlyStringWrapper(identificacionInterna(c.getValue())));
        colDisponibleExterna.setCellValueFactory(c -> new ReadOnlyStringWrapper(texto(c.getValue().getRotuloCliente(), "")));
        colDisponibleDescripcion.setCellValueFactory(c -> new ReadOnlyStringWrapper(texto(c.getValue().getDescripcion(), "")));
        colDisponibleCliente.setCellValueFactory(c -> new ReadOnlyStringWrapper(texto(c.getValue().getNombreCliente(), "")));
        colDisponibleEstado.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().getEstado() == null ? "" : c.getValue().getEstado().toString()));

        colInterna.setCellValueFactory(c -> new ReadOnlyStringWrapper(identificacionInterna(c.getValue())));
        colExterna.setCellValueFactory(c -> new ReadOnlyStringWrapper(texto(c.getValue().getRotuloCliente(), "")));
        colDescripcion.setCellValueFactory(c -> new ReadOnlyStringWrapper(texto(c.getValue().getDescripcion(), "")));
        colMarca.setCellValueFactory(c -> new ReadOnlyStringWrapper(texto(c.getValue().getMarca(), "")));
        colObservaciones.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                texto(c.getValue().getObservacionAlmacenamiento(), "Ninguna")));
        colRecepcion.setCellValueFactory(c -> new ReadOnlyStringWrapper(formatearFecha(c.getValue().getFechaRecepcion())));
        colEntrega.setCellValueFactory(c -> new ReadOnlyStringWrapper(formatearFecha(dpFechaElaboracion.getValue())));
        tblRemision.setItems(muestrasSeleccionadas);
    }

    private void cargarMuestrasDisponibles() {
        try {
            muestrasDisponibles.setAll(remisionService.obtenerMuestrasDisponibles());
            seleccionPorMuestra.keySet().removeIf(id -> muestrasDisponibles.stream().noneMatch(m -> m.getId() == id));
            filtrarMuestras(txtBuscarMuestra == null ? "" : txtBuscarMuestra.getText());
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Remisión de muestras", e.getMessage());
        }
    }

    private BooleanProperty propiedadSeleccion(Muestra muestra) {
        return seleccionPorMuestra.computeIfAbsent(muestra.getId(), id -> {
            BooleanProperty seleccionada = new SimpleBooleanProperty(false);
            seleccionada.addListener((obs, anterior, actual) -> sincronizarSeleccion());
            return seleccionada;
        });
    }

    private void sincronizarSeleccion() {
        muestrasSeleccionadas.setAll(muestrasDisponibles.stream()
                .filter(muestra -> propiedadSeleccion(muestra).get())
                .toList());
        autocompletarCliente();
        actualizarDocumento();
    }

    private void filtrarMuestras(String busqueda) {
        String filtro = normalizar(busqueda);
        muestrasFiltradas.setPredicate(muestra -> filtro.isBlank() || parametrosBusqueda(muestra).contains(filtro));
    }

    private String parametrosBusqueda(Muestra muestra) {
        return normalizar(String.join(" ",
                texto(muestra.getCodigoInterno(), ""),
                texto(muestra.getRotuloCliente(), ""),
                texto(muestra.getNombreCliente(), ""),
                texto(muestra.getDescripcion(), ""),
                texto(muestra.getMarca(), ""),
                texto(muestra.getReferencia(), ""),
                texto(muestra.getUbicacion(), ""),
                texto(muestra.getInformesTexto(), ""),
                texto(muestra.getCotizacionesTexto(), ""),
                muestra.getEstado() == null ? "" : muestra.getEstado().toString(),
                formatearFecha(muestra.getFechaRecepcion()),
                muestra.getFechaRecepcion() == null ? "" : muestra.getFechaRecepcion().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
    }

    private String normalizar(String valor) {
        if (valor == null) return "";
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private void autocompletarCliente() {
        List<String> clientes = muestrasSeleccionadas.stream()
                .map(Muestra::getNombreCliente)
                .filter(valor -> valor != null && !valor.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        txtCliente.setText(clientes.size() == 1 ? clientes.get(0) : clientes.size() > 1 ? "VARIOS" : "");
    }

    private void actualizarDocumento() {
        LocalDate fecha = dpFechaElaboracion.getValue();
        lblFechaDocumento.setText(formatearFecha(fecha));
        lblConsecutivo.setText(formatearConsecutivo(leerConsecutivoActual()));
        lblClienteDocumento.setText(texto(txtCliente.getText(), "—"));
        lblTipoSalidaDocumento.setText(texto(cmbTipoSalida.getValue(), "—"));
        lblTotalMuestras.setText(String.valueOf(muestrasSeleccionadas.size()));
        lblEmpaques.setText(String.valueOf(spnEmpaques.getValue()));
        lblObservacionDocumento.setText(texto(txtObservacionFinal.getText(), "Ninguna"));
        tblRemision.refresh();
    }

    @FXML
    private void confirmarRemision() {
        Usuario actual = usuario != null ? usuario : UsuarioSesion.getUsuario();
        if (actual == null || !actual.puedeControlarMuestras()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Permiso requerido", "No tiene permiso para generar remisiones.");
            return;
        }
        if (muestrasSeleccionadas.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Remisión incompleta", "Seleccione al menos una muestra.");
            return;
        }
        if (dpFechaElaboracion.getValue() == null || txtCliente.getText().isBlank() || cmbTipoSalida.getValue() == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Remisión incompleta",
                    "Complete la fecha, el nombre del cliente y el tipo de salida.");
            return;
        }

        try {
            int consecutivo = leerConsecutivoActual();
            if (consecutivo < 1) {
                mostrarAlerta(Alert.AlertType.WARNING, "Remisión incompleta", "Ingrese un consecutivo de remisión válido.");
                return;
            }
            if (!remisionService.consecutivoDisponible(consecutivo)) {
                mostrarAlerta(Alert.AlertType.WARNING, "Consecutivo no disponible",
                        "La remisión R" + formatearConsecutivo(consecutivo) + " ya existe. Ingrese otro consecutivo.");
                return;
            }

            consecutivo = remisionService.registrarRemision(
                    dpFechaElaboracion.getValue(), txtCliente.getText().trim(), cmbTipoSalida.getValue(),
                    spnEmpaques.getValue(), txtObservacionFinal.getText().trim(), actual,
                    txtFirma.getText().trim(), txtCedula.getText().trim(), txtNombrePlaca.getText().trim(),
                    List.copyOf(muestrasSeleccionadas), consecutivo);

            String codigoRemision = "R" + formatearConsecutivo(consecutivo);
            lblConsecutivo.setText(formatearConsecutivo(consecutivo));
            Path archivoPdf;
            try {
                archivoPdf = PdfRemisionWriter.guardarDosCopias(
                        crearDatosPdf(), codigoRemision);
            } catch (Exception errorArchivo) {
                mostrarAlerta(Alert.AlertType.ERROR, "Remisión registrada sin archivo",
                        "La remisión " + codigoRemision + " quedó registrada, pero no fue posible guardar el PDF.\n\n" +
                                mensajeRaiz(errorArchivo));
                limpiarFormulario();
                return;
            }
            remisionService.registrarRutaArchivo(consecutivo, archivoPdf.toString());

            mostrarAlerta(Alert.AlertType.INFORMATION, "Remisión registrada",
                    "La remisión " + codigoRemision + " fue guardada y las muestras quedaron como enviadas.\n\n" +
                            "Archivo: " + archivoPdf);
            limpiarFormulario();
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo registrar", mensajeRaiz(e));
        }
    }

    @FXML
    private void imprimirRemision() {
        if (muestrasSeleccionadas.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Imprimir remisión", "Seleccione al menos una muestra.");
            return;
        }
        try {
            PdfRemisionWriter.imprimir(crearDatosPdf());
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo imprimir", mensajeRaiz(e));
        }
    }

    private PdfRemisionWriter.Datos crearDatosPdf() {
        List<PdfRemisionWriter.Fila> filas = muestrasSeleccionadas.stream()
                .map(muestra -> new PdfRemisionWriter.Fila(
                        identificacionInterna(muestra),
                        texto(muestra.getRotuloCliente(), ""),
                        texto(muestra.getDescripcion(), ""),
                        texto(muestra.getMarca(), ""),
                        texto(muestra.getObservacionAlmacenamiento(), "Ninguna"),
                        formatearFecha(muestra.getFechaRecepcion()),
                        formatearFecha(dpFechaElaboracion.getValue())))
                .toList();
        return new PdfRemisionWriter.Datos(
                formatearFecha(dpFechaElaboracion.getValue()),
                lblConsecutivo.getText(),
                txtCliente.getText(),
                cmbTipoSalida.getValue(),
                filas,
                spnEmpaques.getValue(),
                txtObservacionFinal.getText(),
                lblEntregadoPor.getText(),
                lblCargoEntregadoPor.getText(),
                txtFirma.getText(),
                txtCedula.getText(),
                txtNombrePlaca.getText());
    }

    private void limpiarFormulario() {
        seleccionPorMuestra.values().forEach(propiedad -> propiedad.set(false));
        muestrasSeleccionadas.clear();
        txtFirma.clear();
        txtCedula.clear();
        txtNombrePlaca.clear();
        txtCliente.clear();
        txtConsecutivo.setText(formatearConsecutivo(remisionService.siguienteConsecutivo()));
        lblConsecutivo.setText(txtConsecutivo.getText());
        cargarMuestrasDisponibles();
        actualizarDocumento();
    }

    private String identificacionInterna(Muestra muestra) {
        String informe = "Sin informe";
        if (!muestra.getInformes().isEmpty()) {
            int anio = muestra.getFechaRecepcion() == null
                    ? java.time.Year.now().getValue() : muestra.getFechaRecepcion().getYear();
            informe = "LENC - " + String.format("%02d", anio % 100) + " - I " + muestra.getInformesTexto();
        }
        return informe + "\nID: " + texto(muestra.getCodigoInterno(), "Sin ID");
    }

    private String formatearConsecutivo(int consecutivo) {
        return String.format("%04d", consecutivo);
    }

    private int leerConsecutivoActual() {
        if (txtConsecutivo == null || txtConsecutivo.getText() == null || txtConsecutivo.getText().isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(txtConsecutivo.getText().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String formatearFecha(LocalDate fecha) {
        return fecha == null ? "—" : fecha.format(FECHA);
    }

    private String texto(String valor, String defecto) {
        return valor == null || valor.isBlank() ? defecto : valor.trim();
    }

    private String cargoVisible(Usuario usuario) {
        return switch (usuario.getRol()) {
            case AUXILIAR -> "Auxiliar de laboratorio";
            case TECNICO -> "Técnico de laboratorio";
            case SUPERVISOR -> "Supervisor de laboratorio";
            case ADMIN -> "Administrador";
            case LIDER -> "Líder de laboratorio";
        };
    }

    private String mensajeRaiz(Throwable error) {
        Throwable actual = error;
        while (actual.getCause() != null) actual = actual.getCause();
        return texto(actual.getMessage(), "Ocurrió un error inesperado.");
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alerta = new Alert(tipo);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.showAndWait();
    }
}
