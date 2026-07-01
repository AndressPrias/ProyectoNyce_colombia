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
import javafx.geometry.VPos;
import javafx.print.PrintColor;
import javafx.print.PrinterJob;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.scene.layout.VBox;
import service.RemisionService;
import utilities.UsuarioSesion;
import utilities.PdfRemisionWriter;

import java.time.LocalDate;
import java.nio.file.Path;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RemisionMuestrasController {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String OBSERVACION_PREDETERMINADA =
            "Las muestras fueron sometidas a ensayos destructivos, por lo cual no se encuentran en buen estado.";

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
                texto(muestra.getNumeroInforme(), ""),
                texto(muestra.getNumeroCotizacion(), ""),
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
            mostrarAlerta(Alert.AlertType.WARNING, "Remisión incompleta", "Complete la fecha, el cliente y el tipo de salida.");
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
                        crearImagenParaImpresion(), codigoRemision);
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

        WritableImage imagenDocumento = crearImagenParaImpresion();
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null || !job.showPrintDialog(documentoRemision.getScene().getWindow())) return;

        job.getJobSettings().setPrintColor(PrintColor.COLOR);
        double anchoImprimible = job.getJobSettings().getPageLayout().getPrintableWidth();
        double altoImprimible = job.getJobSettings().getPageLayout().getPrintableHeight();
        ImageView copiaSuperior = crearCopiaImprimible(imagenDocumento, anchoImprimible, altoImprimible);
        ImageView copiaInferior = crearCopiaImprimible(imagenDocumento, anchoImprimible, altoImprimible);
        VBox hojaConDosCopias = new VBox(8, copiaSuperior, copiaInferior);
        hojaConDosCopias.setPrefSize(anchoImprimible, altoImprimible);
        hojaConDosCopias.applyCss();
        hojaConDosCopias.layout();
        boolean impreso = job.printPage(hojaConDosCopias);

        if (impreso) job.endJob();
    }

    private WritableImage crearImagenParaImpresion() {
        double ancho = 1180;
        // 6x sobre el lienzo lógico equivale aproximadamente a 900 DPI
        // al ubicar cada copia en media hoja carta.
        double escalaResolucion = 6.0;
        double altoFila = Math.max(28, Math.min(42, 170.0 / Math.max(1, muestrasSeleccionadas.size())));
        double alto = 517 + altoFila * Math.max(2, muestrasSeleccionadas.size());
        Canvas lienzo = new Canvas(ancho * escalaResolucion, alto * escalaResolucion);
        GraphicsContext gc = lienzo.getGraphicsContext2D();
        gc.scale(escalaResolucion, escalaResolucion);
        Color verde = Color.web("#009486");
        Color gris = Color.web("#8a8b8b");
        Color borde = Color.web("#444444");
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, ancho, alto);

        Image logo = new Image(getClass().getResource("/icons/logoNyceColombia.jpg").toExternalForm());
        gc.drawImage(logo, 16, 10, 150, 64);
        dibujarCelda(gc, 180, 15, 980, 36, verde, verde, "REMISIÓN DE MUESTRAS", Color.WHITE, 17, true);
        dibujarCelda(gc, 540, 66, 145, 42, verde, verde, "FECHA DE\nELABORACIÓN", Color.WHITE, 11, true);
        dibujarCelda(gc, 685, 66, 210, 42, Color.WHITE, verde, formatearFecha(dpFechaElaboracion.getValue()), Color.BLACK, 14, false);
        dibujarCelda(gc, 895, 66, 145, 42, verde, verde, "CONSECUTIVO", Color.WHITE, 11, true);
        dibujarCelda(gc, 1040, 66, 120, 42, Color.WHITE, verde, lblConsecutivo.getText(), Color.BLACK, 25, true);
        dibujarCelda(gc, 16, 116, 1144, 28, gris, gris, "LABORATORIO DE ENSAYOS  NYCE COLOMBIA S.A.S", Color.WHITE, 11, true);
        dibujarCelda(gc, 16, 152, 140, 36, verde, verde, "CLIENTE", Color.WHITE, 11, true);
        dibujarCelda(gc, 156, 152, 360, 36, Color.WHITE, verde, txtCliente.getText(), Color.BLACK, 12, false);
        dibujarCelda(gc, 640, 152, 150, 36, verde, verde, "TIPO DE SALIDA", Color.WHITE, 11, true);
        dibujarCelda(gc, 790, 152, 370, 36, Color.WHITE, verde, cmbTipoSalida.getValue(), Color.BLACK, 12, false);

        double yTabla = 198;
        double[] anchos = {205, 135, 255, 135, 205, 105, 104};
        String[] titulos = {"IDENTIFICACIÓN\nINTERNA", "IDENTIFICACIÓN\nEXTERNA", "DESCRIPCIÓN",
                "FABRICANTE / MARCA", "OBSERVACIONES", "FECHA DE\nRECEPCIÓN", "FECHA DE\nENTREGA"};
        double x = 16;
        for (int i = 0; i < titulos.length; i++) {
            dibujarCelda(gc, x, yTabla, anchos[i], 44, verde, Color.WHITE, titulos[i], Color.WHITE, 9, true);
            x += anchos[i];
        }
        double y = yTabla + 44;
        for (Muestra muestra : muestrasSeleccionadas) {
            String[] valores = {identificacionInterna(muestra), texto(muestra.getRotuloCliente(), ""),
                    texto(muestra.getDescripcion(), ""), texto(muestra.getMarca(), ""),
                    texto(muestra.getObservacionAlmacenamiento(), "Ninguna"),
                    formatearFecha(muestra.getFechaRecepcion()), formatearFecha(dpFechaElaboracion.getValue())};
            x = 16;
            for (int i = 0; i < valores.length; i++) {
                dibujarCelda(gc, x, y, anchos[i], altoFila, Color.WHITE, borde, valores[i], Color.BLACK, 10, false);
                x += anchos[i];
            }
            y += altoFila;
        }
        if (muestrasSeleccionadas.size() < 2) {
            x = 16;
            for (double anchoColumna : anchos) {
                dibujarCelda(gc, x, y, anchoColumna, altoFila, Color.WHITE, borde, "", Color.BLACK, 10, false);
                x += anchoColumna;
            }
            y += altoFila;
        }

        y += 12;
        dibujarCelda(gc, 16, y, 140, 52, verde, verde, "TOTAL MUESTRAS", Color.WHITE, 10, true);
        dibujarCelda(gc, 156, y, 100, 52, Color.WHITE, verde, String.valueOf(muestrasSeleccionadas.size()), Color.BLACK, 13, false);
        dibujarCelda(gc, 256, y, 160, 52, verde, verde, "NÚMERO DE EMPAQUES", Color.WHITE, 10, true);
        dibujarCelda(gc, 416, y, 100, 52, Color.WHITE, verde, String.valueOf(spnEmpaques.getValue()), Color.BLACK, 13, false);
        dibujarCelda(gc, 516, y, 245, 52, verde, verde, "OBSERVACIÓN FINAL DE LAS MUESTRAS", Color.WHITE, 9, true);
        dibujarCelda(gc, 761, y, 399, 52, Color.WHITE, verde, txtObservacionFinal.getText(), Color.BLACK, 10, false);

        y += 64;
        dibujarCelda(gc, 130, y, 470, 32, verde, verde, "ENTREGADO POR:", Color.WHITE, 15, true);
        dibujarCelda(gc, 600, y, 450, 32, verde, verde, "ENTREGADO A:", Color.WHITE, 15, true);
        dibujarCelda(gc, 130, y + 32, 470, 50, Color.WHITE, verde, lblEntregadoPor.getText(), Color.BLACK, 12, true);
        dibujarCelda(gc, 600, y + 32, 450, 25, Color.WHITE, verde, "FIRMA: " + txtFirma.getText(), Color.GRAY, 10, false);
        dibujarCelda(gc, 600, y + 57, 450, 25, Color.WHITE, verde, "CÉDULA: " + txtCedula.getText(), Color.GRAY, 10, false);
        dibujarCelda(gc, 130, y + 82, 470, 25, Color.WHITE, verde, lblCargoEntregadoPor.getText(), Color.BLACK, 10, false);
        dibujarCelda(gc, 600, y + 82, 450, 25, Color.WHITE, verde, "NOMBRE Y PLACA: " + txtNombrePlaca.getText(), Color.GRAY, 10, false);

        double yPie = alto - 74;
        gc.setFill(Color.web("#555555"));
        gc.setFont(Font.font("Arial", 9));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.BASELINE);
        gc.fillText("ENTRADA EN VIGOR 2023-08-30", 16, yPie - 8);
        gc.setFill(gris);
        gc.fillRect(16, yPie, 1144, 62);
        gc.setFill(Color.WHITE);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFont(Font.font("Arial", 10));
        gc.fillText("Se prohíbe la reproducción total o parcial sin previa autorización", ancho / 2, yPie + 20);
        gc.fillText("NYCE Colombia S.A.S. | Calle 30 No. 17 - 52 | Teusaquillo - Bogotá D.C. Colombia | Tel. +571 756 84 85 ext. 129", ancho / 2, yPie + 44);

        SnapshotParameters parametros = new SnapshotParameters();
        parametros.setFill(Color.WHITE);
        return lienzo.snapshot(parametros, null);
    }

    private void dibujarCelda(GraphicsContext gc, double x, double y, double ancho, double alto,
                              Color fondo, Color borde, String texto, Color colorTexto,
                              double tamanoFuente, boolean negrita) {
        gc.setFill(fondo);
        gc.fillRect(x, y, ancho, alto);
        gc.setStroke(borde);
        gc.setLineWidth(1);
        gc.strokeRect(x, y, ancho, alto);
        gc.setFill(colorTexto);
        gc.setFont(Font.font("Arial", negrita ? FontWeight.BOLD : FontWeight.NORMAL, tamanoFuente));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        String valor = texto == null ? "" : texto;
        String[] lineas = ajustarLineas(valor, Math.max(8, (int) ((ancho - 12) / (tamanoFuente * 0.55))));
        double salto = tamanoFuente + 2;
        double inicio = y + alto / 2 - (lineas.length - 1) * salto / 2;
        for (int i = 0; i < lineas.length; i++) {
            gc.fillText(lineas[i], x + ancho / 2, inicio + i * salto, Math.max(10, ancho - 10));
        }
    }

    private String[] ajustarLineas(String texto, int maximoCaracteres) {
        List<String> lineas = new java.util.ArrayList<>();
        for (String parrafo : texto.split("\\n", -1)) {
            if (parrafo.length() <= maximoCaracteres) {
                lineas.add(parrafo);
                continue;
            }
            StringBuilder linea = new StringBuilder();
            for (String palabra : parrafo.split("\\s+")) {
                if (!linea.isEmpty() && linea.length() + palabra.length() + 1 > maximoCaracteres) {
                    lineas.add(linea.toString());
                    linea.setLength(0);
                }
                if (!linea.isEmpty()) linea.append(' ');
                linea.append(palabra);
            }
            lineas.add(linea.toString());
        }
        return lineas.toArray(String[]::new);
    }

    private ImageView crearCopiaImprimible(WritableImage imagen, double ancho, double altoPagina) {
        ImageView copia = new ImageView(imagen);
        copia.setPreserveRatio(true);
        copia.setFitWidth(ancho);
        copia.setFitHeight((altoPagina - 8) / 2);
        return copia;
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
        if (muestra.getNumeroInforme() != null && !muestra.getNumeroInforme().isBlank()) {
            int anio = muestra.getFechaRecepcion() == null ? Year.now().getValue() : muestra.getFechaRecepcion().getYear();
            informe = "LENC - " + String.format("%02d", anio % 100) + " - I " + muestra.getNumeroInforme().trim();
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
