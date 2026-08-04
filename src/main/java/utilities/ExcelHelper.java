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
            "FECHA DE INGRESO",
            "NOMBRE DEL CLIENTE",
            "DESCRIPCIÓN MUESTRA",
            "CANTIDAD",
            "MARCA",
            "REFERENCIA",
            "REFERENCIA EXTERNA",
            "ID",
            "INFORME",
            "COTIZACIÓN",
            "UBICACIÓN",
            "REMISIÓN",
            "ESTADO",
            "OBSERVACIONES"
    };

    private static final DateTimeFormatter FECHA_DIA_MES_ANIO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ExcelHelper() {
    }

    public static void crearPlantilla(File destino) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            crearEstructuraPlantilla(workbook);

            try (FileOutputStream salida = new FileOutputStream(destino)) {
                workbook.write(salida);
            }
        }
    }

    public static void exportarMuestras(File destino, List<Muestra> muestras) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet datos = crearEstructuraPlantilla(workbook);
            CellStyle fecha = workbook.createCellStyle();
            fecha.setDataFormat(workbook.createDataFormat().getFormat("dd/MM/yyyy"));
            CellStyle texto = workbook.createCellStyle();
            texto.setDataFormat(workbook.createDataFormat().getFormat("@"));
            texto.setAlignment(HorizontalAlignment.LEFT);

            int indiceFila = 1;
            for (Muestra muestra : muestras) {
                Row fila = datos.createRow(indiceFila++);
                Cell celdaFecha = fila.createCell(0);
                if (muestra.getFechaRecepcion() != null) {
                    celdaFecha.setCellValue(muestra.getFechaRecepcion());
                    celdaFecha.setCellStyle(fecha);
                }
                escribirTexto(fila, 1, muestra.getNombreCliente(), null);
                escribirTexto(fila, 2, muestra.getDescripcion(), null);
                fila.createCell(3).setCellValue(muestra.getCantidad());
                escribirTexto(fila, 4, muestra.getMarca(), null);
                escribirTexto(fila, 5, muestra.getReferencia(), null);
                escribirTexto(fila, 6, muestra.getRotuloCliente(), null);
                escribirTexto(fila, 7, muestra.getCodigoInterno(), texto);
                escribirTexto(fila, 8, formatoReferenciasCompletas(muestra.getInformes(), "I"), texto);
                escribirTexto(fila, 9, formatoReferenciasCompletas(muestra.getCotizaciones(), "C"), texto);
                escribirTexto(fila, 10, muestra.getUbicacion(), null);
                escribirTexto(fila, 11, muestra.getRemision(), texto);
                escribirTexto(fila, 12, muestra.getEstado() == null ? "" : muestra.getEstado().toString(), null);
                escribirTexto(fila, 13, muestra.getObservacionAlmacenamiento(), null);
            }

            if (!muestras.isEmpty()) {
                datos.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                        0, muestras.size(), 0, ENCABEZADOS.length - 1));
            }
            try (FileOutputStream salida = new FileOutputStream(destino)) {
                workbook.write(salida);
            }
        }
    }

    private static Sheet crearEstructuraPlantilla(Workbook workbook) {
        Sheet datos = workbook.createSheet("Datos");
        Sheet instrucciones = workbook.createSheet("Instrucciones");
        Sheet catalogos = workbook.createSheet("Catalogos");

        crearEncabezados(workbook, datos);
        crearInstrucciones(workbook, instrucciones);
        crearCatalogoEstados(catalogos);
        agregarValidacionEstados(workbook, datos);

        workbook.setSheetHidden(workbook.getSheetIndex(catalogos), true);
        workbook.setActiveSheet(workbook.getSheetIndex(datos));
        return datos;
    }

    private static void escribirTexto(Row fila, int columna, String valor, CellStyle estilo) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        Cell celda = fila.createCell(columna);
        celda.setCellValue(valor);
        if (estilo != null) {
            celda.setCellStyle(estilo);
        }
    }

    private static String formatoReferenciasCompletas(List<ReferenciaDocumento> referencias, String tipo) {
        if (referencias == null || referencias.isEmpty()) {
            return "";
        }
        int anio = referencias.getFirst().anio();
        boolean mismoAnio = referencias.stream().allMatch(referencia -> referencia.anio() == anio);
        if (mismoAnio) {
            String numeros = referencias.stream()
                    .map(ReferenciaDocumento::numero)
                    .reduce((primero, siguiente) -> primero + " / " + siguiente)
                    .orElse("");
            return "LENC - " + String.format("%02d", anio % 100) + " - " + tipo + " " + numeros;
        }
        return referencias.stream()
                .map(referencia -> "LENC - " + String.format("%02d", referencia.anio() % 100)
                        + " - " + tipo + " " + referencia.numero())
                .reduce((primero, siguiente) -> primero + " / " + siguiente)
                .orElse("");
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
                case 1, 2, 13 -> 30 * 256;
                case 5, 6, 10, 11 -> 24 * 256;
                case 8, 9 -> 34 * 256;
                case 3 -> 12 * 256;
                default -> 20 * 256;
            });
        }

        CellStyle texto = workbook.createCellStyle();
        texto.setDataFormat(workbook.createDataFormat().getFormat("@"));
        texto.setAlignment(HorizontalAlignment.LEFT);
        for (int columna : new int[]{7, 8, 9, 11}) {
            sheet.setDefaultColumnStyle(columna, texto);
        }
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
                "Campos obligatorios: REFERENCIA EXTERNA, DESCRIPCIÓN MUESTRA y CANTIDAD.",
                "CANTIDAD: indique un número entero mayor que cero.",
                "REFERENCIA: dato opcional correspondiente a la referencia del producto.",
                "ESTADO: seleccione un valor de la lista disponible.",
                "Fecha de ingreso: use una fecha de Excel o el formato dd/MM/yyyy.",
                "INFORME y COTIZACIÓN son opcionales.",
                "Para asociar varios, escriba códigos de 4 dígitos separados por /, por ejemplo: 0001 / 0002 / 0003.",
                "El año se toma automáticamente de la fecha de ingreso de la muestra.",
                "ID es opcional: permite actualizar una muestra existente o conservar su identificación histórica.",
                "Si ID está vacío, el sistema genera uno automáticamente. El custodio se asigna al importar."
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
        int columnaEstado = 12;
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
        if (!contieneAlguna(columnas, "referenciaexterna", "rotulocliente")) {
            errores.add("Falta el encabezado obligatorio REFERENCIA EXTERNA.");
        }
        if (!contieneAlguna(columnas, "descripcionmuestra", "descripcion")) {
            errores.add("Falta el encabezado obligatorio DESCRIPCIÓN MUESTRA.");
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
        muestra.setRotuloCliente(valorConAlias(row, columnas, formatter, evaluator,
                "referenciaexterna", "rotulocliente"));
        muestra.setNombreCliente(valorConAlias(row, columnas, formatter, evaluator,
                "nombredelcliente", "nombrecliente"));
        muestra.setDescripcion(valorConAlias(row, columnas, formatter, evaluator,
                "descripcionmuestra", "descripcion"));
        String cantidad = valor(row, columnas, "cantidad", formatter, evaluator);
        if (cantidad.isBlank()) {
            muestra.setCantidad(1);
        } else {
            try {
                if (!cantidad.matches("\\d+")) {
                    throw new NumberFormatException();
                }
                int valorCantidad = Integer.parseInt(cantidad);
                if (valorCantidad < 1) {
                    throw new NumberFormatException();
                }
                muestra.setCantidad(valorCantidad);
            } catch (NumberFormatException e) {
                errores.add("Fila " + numeroFila + ": CANTIDAD debe ser un número entero mayor que cero.");
            }
        }
        muestra.setMarca(valor(row, columnas, "marca", formatter, evaluator));
        muestra.setReferencia(columnas.containsKey("referencia")
                ? valor(row, columnas, "referencia", formatter, evaluator) : null);
        muestra.setUbicacion(valor(row, columnas, "ubicacion", formatter, evaluator));
        muestra.setRutaFoto(columnas.containsKey("rutafoto")
                ? valor(row, columnas, "rutafoto", formatter, evaluator) : null);
        muestra.setCodigoInterno(valor(row, columnas, "id", formatter, evaluator));
        muestra.setRemision(columnas.containsKey("remision")
                ? valor(row, columnas, "remision", formatter, evaluator) : null);
        muestra.setObservacionAlmacenamiento(columnas.containsKey("observaciones")
                ? valor(row, columnas, "observaciones", formatter, evaluator) : null);

        if (plantillaNueva && muestra.getIdCarga().isBlank()) {
            errores.add("Fila " + numeroFila + ": idCarga es obligatorio.");
        }

        if (muestra.getRotuloCliente().isBlank()) {
            errores.add("Fila " + numeroFila + ": Referencia Externa es obligatoria.");
        }
        if (muestra.getDescripcion().isBlank()) {
            errores.add("Fila " + numeroFila + ": Descripción Muestra es obligatoria.");
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

        Cell celdaFecha = celdaConAlias(row, columnas, "fechadeingreso", "fecharecepcion");
        try {
            muestra.setFechaRecepcion(leerFecha(celdaFecha, formatter, evaluator));
        } catch (IllegalArgumentException e) {
            errores.add("Fila " + numeroFila + ": " + e.getMessage());
        }

        int anioPredeterminado = muestra.getFechaRecepcion() != null
                && muestra.getFechaRecepcion().getYear() >= 2000
                ? muestra.getFechaRecepcion().getYear() : LocalDate.now().getYear();
        muestra.setInformes(leerCodigos(row, columnas, new String[]{"informe", "numeroinforme"}, "INFORME",
                anioPredeterminado, formatter, evaluator, numeroFila, errores));
        muestra.setCotizaciones(leerCodigos(row, columnas, new String[]{"cotizacion", "numerocotizacion"}, "COTIZACIÓN",
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

    private static List<ReferenciaDocumento> leerCodigos(Row row, Map<String, Integer> columnas,
                                                          String[] nombresColumnas, String etiqueta, int anio,
                                                          DataFormatter formatter, FormulaEvaluator evaluator,
                                                          int numeroFila, List<String> errores) {
        String valor = valorConAlias(row, columnas, formatter, evaluator, nombresColumnas);
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
                throw new IllegalArgumentException("Fecha de ingreso debe usar dd/MM/yyyy.");
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

    private static String valorConAlias(Row row, Map<String, Integer> columnas,
                                        DataFormatter formatter, FormulaEvaluator evaluator,
                                        String... nombres) {
        Cell cell = celdaConAlias(row, columnas, nombres);
        return cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim();
    }

    private static Cell celdaConAlias(Row row, Map<String, Integer> columnas, String... nombres) {
        for (String nombre : nombres) {
            Cell cell = celda(row, columnas, nombre);
            if (cell != null) return cell;
        }
        return null;
    }

    private static boolean contieneAlguna(Map<String, Integer> columnas, String... nombres) {
        for (String nombre : nombres) {
            if (columnas.containsKey(nombre)) return true;
        }
        return false;
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
