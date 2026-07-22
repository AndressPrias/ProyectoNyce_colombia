package utilities;

import domain.Estado;
import domain.Muestra;
import domain.ReferenciaDocumento;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExcelHelper {

    private static final String[] ENCABEZADOS = {
            "rotuloCliente *",
            "nombreCliente",
            "descripcion *",
            "marca",
            "referencia",
            "estado",
            "fechaRecepcion",
            "ubicacion",
            "numeroInforme",
            "numeroCotizacion",
            "rutaFoto"
    };

    private static final DateTimeFormatter FECHA_DIA_MES_ANIO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ExcelHelper() {
    }

    public static void crearPlantilla(File destino) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet datos = workbook.createSheet("Datos");
            Sheet instrucciones = workbook.createSheet("Instrucciones");
            Sheet catalogos = workbook.createSheet("Catalogos");

            crearEncabezados(workbook, datos);
            crearInstrucciones(workbook, instrucciones);
            crearCatalogoEstados(catalogos);
            agregarValidacionEstados(workbook, datos);

            workbook.setSheetHidden(workbook.getSheetIndex(catalogos), true);
            workbook.setActiveSheet(workbook.getSheetIndex(datos));

            try (FileOutputStream salida = new FileOutputStream(destino)) {
                workbook.write(salida);
            }
        }
    }

    public static ResultadoLectura leerExcel(File archivo) {
        List<Muestra> muestras = new ArrayList<>();
        List<String> errores = new ArrayList<>();

        try (FileInputStream entrada = new FileInputStream(archivo);
             Workbook workbook = new XSSFWorkbook(entrada)) {

            Sheet sheet = workbook.getSheet("Datos");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            Row encabezado = sheet.getRow(0);
            if (encabezado == null) {
                errores.add("El archivo no contiene una fila de encabezados.");
                return new ResultadoLectura(muestras, errores);
            }

            Map<String, Integer> columnas = obtenerColumnas(encabezado);
            validarEncabezados(columnas, errores);
            if (!errores.isEmpty()) {
                return new ResultadoLectura(muestras, errores);
            }

            DataFormatter formatter = new DataFormatter(new Locale("es", "CO"));
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            for (int indice = 1; indice <= sheet.getLastRowNum(); indice++) {
                Row row = sheet.getRow(indice);
                if (row == null || filaVacia(row, formatter, evaluator)) {
                    continue;
                }

                int numeroFila = indice + 1;
                List<String> erroresFila = new ArrayList<>();
                Muestra muestra = leerMuestra(row, columnas, formatter, evaluator, numeroFila, erroresFila);
                if (erroresFila.isEmpty()) {
                    muestras.add(muestra);
                } else {
                    errores.addAll(erroresFila);
                }
            }

            Map<String, Muestra> muestrasPorIdCarga = new HashMap<>();
            for (Muestra muestra : muestras) {
                if (muestrasPorIdCarga.putIfAbsent(muestra.getIdCarga(), muestra) != null) {
                    errores.add("El idCarga '" + muestra.getIdCarga() + "' está repetido en la hoja Datos.");
                }
            }
            leerRelaciones(workbook.getSheet("Informes"), "Informes", muestrasPorIdCarga, true,
                    formatter, evaluator, errores);
            leerRelaciones(workbook.getSheet("Cotizaciones"), "Cotizaciones", muestrasPorIdCarga, false,
                    formatter, evaluator, errores);

            if (muestras.isEmpty() && errores.isEmpty()) {
                errores.add("La plantilla no contiene filas de datos.");
            }
        } catch (Exception e) {
            errores.add("No se pudo leer el archivo: " + e.getMessage());
        }

        return new ResultadoLectura(muestras, errores);
    }

    private static void crearEncabezados(Workbook workbook, Sheet sheet) {
        Row row = sheet.createRow(0);
        CellStyle estilo = workbook.createCellStyle();
        estilo.setFillForegroundColor(IndexedColors.TEAL.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setAlignment(HorizontalAlignment.CENTER);

        Font fuente = workbook.createFont();
        fuente.setBold(true);
        fuente.setColor(IndexedColors.WHITE.getIndex());
        estilo.setFont(fuente);

        for (int i = 0; i < ENCABEZADOS.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(ENCABEZADOS[i]);
            cell.setCellStyle(estilo);
            sheet.setColumnWidth(i, switch (i) {
                case 1, 2, 7, 10 -> 28 * 256;
                case 8, 9 -> 24 * 256;
                default -> 20 * 256;
            });
        }

        CellStyle texto = workbook.createCellStyle();
        texto.setDataFormat(workbook.createDataFormat().getFormat("@"));
        sheet.setDefaultColumnStyle(8, texto);
        sheet.setDefaultColumnStyle(9, texto);
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, ENCABEZADOS.length - 1));
    }

    private static void crearInstrucciones(Workbook workbook, Sheet sheet) {
        CellStyle titulo = workbook.createCellStyle();
        Font fuenteTitulo = workbook.createFont();
        fuenteTitulo.setBold(true);
        fuenteTitulo.setFontHeightInPoints((short) 16);
        fuenteTitulo.setColor(IndexedColors.TEAL.getIndex());
        titulo.setFont(fuenteTitulo);

        Row encabezado = sheet.createRow(0);
        encabezado.createCell(0).setCellValue("Plantilla para cargar muestras");
        encabezado.getCell(0).setCellStyle(titulo);

        String[] lineas = {
                "Complete una muestra por fila en la hoja Datos.",
                "No cambie los nombres de los encabezados.",
                "Campos obligatorios: rotuloCliente * y descripcion *.",
                "estado: seleccione un valor de la lista disponible.",
                "fechaRecepcion: use una fecha de Excel o el formato dd/MM/yyyy.",
                "numeroInforme y numeroCotizacion son opcionales.",
                "Para asociar varios, escriba códigos de 4 dígitos separados por /, por ejemplo: 0001 / 0002 / 0003.",
                "El año se toma automáticamente de la fecha de recepción de la muestra.",
                "rutaFoto es opcional y debe contener la ruta completa de una imagen existente.",
                "El código interno y el custodio se asignan automáticamente al importar."
        };

        for (int i = 0; i < lineas.length; i++) {
            sheet.createRow(i + 2).createCell(0).setCellValue(lineas[i]);
        }
        sheet.setColumnWidth(0, 90 * 256);
    }

    private static void crearCatalogoEstados(Sheet sheet) {
        sheet.createRow(0).createCell(0).setCellValue("Estados");
        Estado[] estados = Estado.values();
        for (int i = 0; i < estados.length; i++) {
            sheet.createRow(i + 1).createCell(0).setCellValue(estados[i].toString());
        }
    }

    private static void agregarValidacionEstados(Workbook workbook, Sheet datos) {
        int columnaEstado = 5;
        int ultimaFilaCatalogo = Estado.values().length + 1;
        Name rangoEstados = workbook.createName();
        rangoEstados.setNameName("EstadosValidos");
        rangoEstados.setRefersToFormula("Catalogos!$A$2:$A$" + ultimaFilaCatalogo);

        DataValidationHelper helper = datos.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createFormulaListConstraint("EstadosValidos");
        CellRangeAddressList rango = new CellRangeAddressList(1, 2000, columnaEstado, columnaEstado);
        DataValidation validation = helper.createValidation(constraint, rango);
        validation.setShowErrorBox(true);
        validation.createErrorBox("Estado no valido", "Seleccione un estado de la lista.");
        datos.addValidationData(validation);
    }

    private static Map<String, Integer> obtenerColumnas(Row encabezado) {
        Map<String, Integer> columnas = new HashMap<>();
        for (Cell cell : encabezado) {
            String nombre = normalizar(cell.getStringCellValue());
            if (!nombre.isBlank()) {
                columnas.put(nombre, cell.getColumnIndex());
            }
        }
        return columnas;
    }

    private static void validarEncabezados(Map<String, Integer> columnas, List<String> errores) {
        if (!columnas.containsKey("rotulocliente")) {
            errores.add("Falta el encabezado obligatorio rotuloCliente *.");
        }
        if (!columnas.containsKey("descripcion")) {
            errores.add("Falta el encabezado obligatorio descripcion *.");
        }
    }

    private static Muestra leerMuestra(Row row, Map<String, Integer> columnas,
                                       DataFormatter formatter, FormulaEvaluator evaluator,
                                       int numeroFila, List<String> errores) {
        Muestra muestra = new Muestra();
        boolean plantillaNueva = columnas.containsKey("idcarga");
        muestra.setIdCarga(plantillaNueva
                ? valor(row, columnas, "idcarga", formatter, evaluator)
                : String.format("FILA-%04d", numeroFila));
        muestra.setRotuloCliente(valor(row, columnas, "rotulocliente", formatter, evaluator));
        muestra.setNombreCliente(valor(row, columnas, "nombrecliente", formatter, evaluator));
        muestra.setDescripcion(valor(row, columnas, "descripcion", formatter, evaluator));
        muestra.setMarca(valor(row, columnas, "marca", formatter, evaluator));
        muestra.setReferencia(valor(row, columnas, "referencia", formatter, evaluator));
        muestra.setUbicacion(valor(row, columnas, "ubicacion", formatter, evaluator));
        muestra.setRutaFoto(valor(row, columnas, "rutafoto", formatter, evaluator));

        if (plantillaNueva && muestra.getIdCarga().isBlank()) {
            errores.add("Fila " + numeroFila + ": idCarga es obligatorio.");
        }

        if (muestra.getRotuloCliente().isBlank()) {
            errores.add("Fila " + numeroFila + ": rotuloCliente es obligatorio.");
        }
        if (muestra.getDescripcion().isBlank()) {
            errores.add("Fila " + numeroFila + ": descripcion es obligatoria.");
        }

        String estado = valor(row, columnas, "estado", formatter, evaluator);
        if (estado.isBlank()) {
            muestra.setEstado(Estado.EN_CUSTODIA);
        } else {
            try {
                muestra.setEstado(Estado.desdeTexto(estado));
            } catch (IllegalArgumentException e) {
                errores.add("Fila " + numeroFila + ": estado '" + estado + "' no es valido.");
            }
        }

        Cell celdaFecha = celda(row, columnas, "fecharecepcion");
        try {
            muestra.setFechaRecepcion(leerFecha(celdaFecha, formatter, evaluator));
        } catch (IllegalArgumentException e) {
            errores.add("Fila " + numeroFila + ": " + e.getMessage());
        }

        int anioPredeterminado = muestra.getFechaRecepcion() != null
                && muestra.getFechaRecepcion().getYear() >= 2000
                ? muestra.getFechaRecepcion().getYear() : LocalDate.now().getYear();
        muestra.setInformes(leerCodigosLegacy(row, columnas, "numeroinforme", "numeroInforme",
                anioPredeterminado, formatter, evaluator, numeroFila, errores));
        muestra.setCotizaciones(leerCodigosLegacy(row, columnas, "numerocotizacion", "numeroCotizacion",
                anioPredeterminado, formatter, evaluator, numeroFila, errores));

        return muestra;
    }

    private static void leerRelaciones(Sheet sheet, String nombreHoja, Map<String, Muestra> muestrasPorIdCarga,
                                       boolean informes, DataFormatter formatter, FormulaEvaluator evaluator,
                                       List<String> errores) {
        if (sheet == null) {
            return;
        }
        Row encabezado = sheet.getRow(0);
        if (encabezado == null) {
            errores.add("La hoja " + nombreHoja + " no contiene encabezados.");
            return;
        }
        Map<String, Integer> columnas = obtenerColumnas(encabezado);
        if (!columnas.keySet().containsAll(List.of("idcargamuestra", "numero"))) {
            errores.add("La hoja " + nombreHoja + " debe contener idCargaMuestra * y numero *.");
            return;
        }
        Map<String, List<ReferenciaDocumento>> referencias = new HashMap<>();
        for (int indice = 1; indice <= sheet.getLastRowNum(); indice++) {
            Row row = sheet.getRow(indice);
            if (row == null || filaVacia(row, formatter, evaluator)) continue;
            int numeroFila = indice + 1;
            String idCarga = valor(row, columnas, "idcargamuestra", formatter, evaluator);
            String numero = valor(row, columnas, "numero", formatter, evaluator);
            if (!muestrasPorIdCarga.containsKey(idCarga)) {
                errores.add(nombreHoja + " fila " + numeroFila + ": idCargaMuestra '" + idCarga + "' no existe en Datos.");
                continue;
            }
            if (!numero.matches("\\d{4}")) {
                errores.add(nombreHoja + " fila " + numeroFila + ": numero debe contener exactamente 4 dígitos.");
                continue;
            }
            try {
                Muestra muestra = muestrasPorIdCarga.get(idCarga);
                int anio = muestra.getFechaRecepcion() == null
                        ? LocalDate.now().getYear() : muestra.getFechaRecepcion().getYear();
                ReferenciaDocumento referencia = new ReferenciaDocumento(numero, anio);
                List<ReferenciaDocumento> lista = referencias.computeIfAbsent(idCarga, clave -> new ArrayList<>());
                if (lista.contains(referencia)) {
                    errores.add(nombreHoja + " fila " + numeroFila + ": la relación está duplicada.");
                } else {
                    lista.add(referencia);
                }
            } catch (IllegalArgumentException e) {
                errores.add(nombreHoja + " fila " + numeroFila + ": " + e.getMessage());
            }
        }
        referencias.forEach((id, lista) -> {
            if (informes) muestrasPorIdCarga.get(id).setInformes(lista);
            else muestrasPorIdCarga.get(id).setCotizaciones(lista);
        });
    }

    private static List<ReferenciaDocumento> leerCodigosLegacy(Row row, Map<String, Integer> columnas,
                                                                String nombreColumna, String etiqueta, int anio,
                                                                DataFormatter formatter, FormulaEvaluator evaluator,
                                                                int numeroFila, List<String> errores) {
        String valor = valor(row, columnas, nombreColumna, formatter, evaluator);
        if (valor.isBlank()) return List.of();
        List<ReferenciaDocumento> referencias = new ArrayList<>();
        Matcher matcher = Pattern.compile("(?<!\\d)\\d{4}(?!\\d)").matcher(valor);
        while (matcher.find()) {
            ReferenciaDocumento referencia = new ReferenciaDocumento(matcher.group(), anio);
            if (!referencias.contains(referencia)) referencias.add(referencia);
        }
        if (referencias.isEmpty()) {
            errores.add("Fila " + numeroFila + ": " + etiqueta + " debe contener códigos de exactamente 4 dígitos.");
        }
        return referencias;
    }

    private static LocalDate leerFecha(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null || formatter.formatCellValue(cell, evaluator).isBlank()) {
            return LocalDate.now();
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }

        String texto = formatter.formatCellValue(cell, evaluator).trim();
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(texto, FECHA_DIA_MES_ANIO);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("fechaRecepcion debe usar dd/MM/yyyy.");
            }
        }
    }

    private static String valor(Row row, Map<String, Integer> columnas, String nombre,
                                DataFormatter formatter, FormulaEvaluator evaluator) {
        Cell cell = celda(row, columnas, nombre);
        return cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim();
    }

    private static Cell celda(Row row, Map<String, Integer> columnas, String nombre) {
        Integer indice = columnas.get(nombre);
        return indice == null ? null : row.getCell(indice);
    }

    private static boolean filaVacia(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell, evaluator).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        String sinAcentos = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String normalizarEstado(String valor) {
        String sinAcentos = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    }

    public static final class ResultadoLectura {
        private final List<Muestra> muestras;
        private final List<String> errores;

        public ResultadoLectura(List<Muestra> muestras, List<String> errores) {
            this.muestras = List.copyOf(muestras);
            this.errores = List.copyOf(errores);
        }

        public List<Muestra> getMuestras() {
            return muestras;
        }

        public List<String> getErrores() {
            return errores;
        }

        public boolean esValido() {
            return errores.isEmpty() && !muestras.isEmpty();
        }
    }
}
