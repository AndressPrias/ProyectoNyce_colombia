package db;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import utilities.AppConfig;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class Database {

    private static final String LEGACY_DB_FOLDER = "./data";

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("No se encontr\u00f3 el driver SQLite", e);
        }

        Path databasePath = AppConfig.getDatabasePath();
        try {
            Files.createDirectories(databasePath.getParent());
        } catch (Exception e) {
            throw new SQLException("No se pudo crear la carpeta de la base de datos: " + databasePath.getParent(), e);
        }

        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA busy_timeout = 10000");
            st.execute("PRAGMA journal_mode = WAL");
            st.execute("PRAGMA synchronous = NORMAL");
        }
        return conn;
    }

    public static void init() {
        try (Connection conn = getConnection()) {
            crearTablas(conn);
            migrarColumnas(conn);
            migrarDesdeH2SiAplica(conn);
            migrarRemisionesEnMuestras(conn);
            migrarEstados(conn);
            asegurarUsuarioAdmin(conn);

            System.out.println("Base de datos SQLite inicializada.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void crearTablas(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS usuarios (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "nombre TEXT NOT NULL," +
                    "rol TEXT NOT NULL," +
                    "password TEXT," +
                    "rutaFoto TEXT," +
                    "controlMuestras INTEGER DEFAULT 0," +
                    "controlTotal INTEGER DEFAULT 0" +
                    ");");

            st.execute("CREATE TABLE IF NOT EXISTS muestras (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "codigoInterno TEXT NOT NULL," +
                    "rotuloCliente TEXT," +
                    "nombreCliente TEXT," +
                    "descripcion TEXT," +
                    "marca TEXT," +
                    "referencia TEXT," +
                    "estado TEXT NOT NULL," +
                    "ubicacion TEXT," +
                    "observacionAlmacenamiento TEXT," +
                    "custodioId INTEGER," +
                    "tecnicoId INTEGER," +
                    "responsableId INTEGER," +
                    "fechaRecepcion TEXT," +
                    "rutaFoto TEXT," +
                    "numeroInforme TEXT," +
                    "numeroCotizacion TEXT," +
                    "remision TEXT," +
                    "FOREIGN KEY (custodioId) REFERENCES usuarios(id)," +
                    "FOREIGN KEY (tecnicoId) REFERENCES usuarios(id)," +
                    "FOREIGN KEY (responsableId) REFERENCES usuarios(id)" +
                    ");");

            st.execute("CREATE TABLE IF NOT EXISTS movimientos (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "muestraId INTEGER NOT NULL," +
                    "usuarioId INTEGER NOT NULL," +
                    "estadoAnterior TEXT," +
                    "estadoNuevo TEXT," +
                    "ubicacionAnterior TEXT," +
                    "ubicacionNueva TEXT," +
                    "fechaHora TEXT," +
                    "observacion TEXT," +
                    "FOREIGN KEY (muestraId) REFERENCES muestras(id)," +
                    "FOREIGN KEY (usuarioId) REFERENCES usuarios(id)" +
                    ");");

            st.execute("CREATE TABLE IF NOT EXISTS remisiones (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "consecutivo INTEGER NOT NULL UNIQUE," +
                    "fechaElaboracion TEXT NOT NULL," +
                    "cliente TEXT," +
                    "tipoSalida TEXT NOT NULL," +
                    "numeroEmpaques INTEGER NOT NULL," +
                    "observacionFinal TEXT," +
                    "entregadoPorId INTEGER," +
                    "recibidoFirma TEXT," +
                    "recibidoCedula TEXT," +
                    "recibidoNombrePlaca TEXT," +
                    "rutaArchivo TEXT," +
                    "fechaCreacion TEXT DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY (entregadoPorId) REFERENCES usuarios(id)" +
                    ");");

            st.execute("CREATE TABLE IF NOT EXISTS remision_muestras (" +
                    "remisionId INTEGER NOT NULL," +
                    "muestraId INTEGER NOT NULL," +
                    "fechaEntrega TEXT NOT NULL," +
                    "observaciones TEXT," +
                    "PRIMARY KEY (remisionId, muestraId)," +
                    "FOREIGN KEY (remisionId) REFERENCES remisiones(id)," +
                    "FOREIGN KEY (muestraId) REFERENCES muestras(id)" +
                    ");");
        }
    }

    private static void migrarColumnas(Connection conn) throws SQLException {
        agregarColumnaSiNoExiste(conn, "usuarios", "password", "TEXT");
        agregarColumnaSiNoExiste(conn, "usuarios", "rutaFoto", "TEXT");
        agregarColumnaSiNoExiste(conn, "usuarios", "controlMuestras", "INTEGER DEFAULT 0");
        agregarColumnaSiNoExiste(conn, "usuarios", "controlTotal", "INTEGER DEFAULT 0");

        agregarColumnaSiNoExiste(conn, "muestras", "nombreCliente", "TEXT");
        agregarColumnaSiNoExiste(conn, "muestras", "marca", "TEXT");
        agregarColumnaSiNoExiste(conn, "muestras", "referencia", "TEXT");
        agregarColumnaSiNoExiste(conn, "muestras", "observacionAlmacenamiento", "TEXT");
        agregarColumnaSiNoExiste(conn, "muestras", "tecnicoId", "INTEGER");
        agregarColumnaSiNoExiste(conn, "muestras", "responsableId", "INTEGER");
        agregarColumnaSiNoExiste(conn, "muestras", "numeroInforme", "TEXT");
        agregarColumnaSiNoExiste(conn, "muestras", "numeroCotizacion", "TEXT");
        agregarColumnaSiNoExiste(conn, "muestras", "remision", "TEXT");

        agregarColumnaSiNoExiste(conn, "movimientos", "ubicacionAnterior", "TEXT");
        agregarColumnaSiNoExiste(conn, "movimientos", "ubicacionNueva", "TEXT");
        agregarColumnaSiNoExiste(conn, "remisiones", "rutaArchivo", "TEXT");

        try (Statement st = conn.createStatement()) {
            st.execute("UPDATE usuarios SET password = nombre WHERE password IS NULL");
        }
    }

    private static void agregarColumnaSiNoExiste(Connection conn, String tabla, String columna, String definicion) throws SQLException {
        if (existeColumna(conn, tabla, columna)) return;
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE " + tabla + " ADD COLUMN " + columna + " " + definicion);
        }
    }

    private static boolean existeColumna(Connection conn, String tabla, String columna) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(" + tabla + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (columna.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void migrarDesdeH2SiAplica(Connection sqliteConn) {
        File h2File = resolveLegacyH2File();
        if (!h2File.exists()) return;
        if (!tablaVacia(sqliteConn, "usuarios") || !tablaVacia(sqliteConn, "muestras")) return;

        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("Existe una base H2 anterior, pero no est\u00e1 disponible el driver H2 para migrarla.");
            return;
        }

        boolean autoCommitOriginal = true;
        try (Connection h2Conn = DriverManager.getConnection(resolveLegacyH2JdbcUrl(h2File), "sa", "")) {
            autoCommitOriginal = sqliteConn.getAutoCommit();
            sqliteConn.setAutoCommit(false);

            copiarTabla(h2Conn, sqliteConn, "usuarios", List.of(
                    "id", "nombre", "rol", "password", "rutaFoto", "controlMuestras", "controlTotal"));
            copiarTabla(h2Conn, sqliteConn, "muestras", List.of(
                    "id", "codigoInterno", "rotuloCliente", "nombreCliente", "descripcion", "marca", "referencia",
                    "estado", "ubicacion", "observacionAlmacenamiento", "custodioId", "tecnicoId", "responsableId",
                    "fechaRecepcion", "rutaFoto", "numeroInforme", "numeroCotizacion", "remision"));
            copiarTabla(h2Conn, sqliteConn, "movimientos", List.of(
                    "id", "muestraId", "usuarioId", "estadoAnterior", "estadoNuevo", "ubicacionAnterior",
                    "ubicacionNueva", "fechaHora", "observacion"));
            copiarTabla(h2Conn, sqliteConn, "remisiones", List.of(
                    "id", "consecutivo", "fechaElaboracion", "cliente", "tipoSalida", "numeroEmpaques",
                    "observacionFinal", "entregadoPorId", "recibidoFirma", "recibidoCedula",
                    "recibidoNombrePlaca", "rutaArchivo", "fechaCreacion"));
            copiarTabla(h2Conn, sqliteConn, "remision_muestras", List.of(
                    "remisionId", "muestraId", "fechaEntrega", "observaciones"));

            sqliteConn.commit();
            System.out.println("Migraci\u00f3n desde H2 a SQLite completada.");
        } catch (Exception e) {
            try { sqliteConn.rollback(); } catch (SQLException ignored) {}
            System.err.println("No se pudo migrar la base H2 a SQLite: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { sqliteConn.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
        }
    }
    private static File resolveLegacyH2File() {
        Path selectedFolderH2 = AppConfig.getStorageFolder().resolve("lencdb.mv.db");
        if (Files.exists(selectedFolderH2)) {
            return selectedFolderH2.toFile();
        }
        return new File(LEGACY_DB_FOLDER + "/lencdb.mv.db");
    }

    private static String resolveLegacyH2JdbcUrl(File h2File) {
        String absolutePath = h2File.getAbsolutePath();
        String withoutExtension = absolutePath.endsWith(".mv.db")
                ? absolutePath.substring(0, absolutePath.length() - ".mv.db".length())
                : absolutePath;
        return "jdbc:h2:" + withoutExtension;
    }

    private static boolean tablaVacia(Connection conn, String tabla) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tabla)) {
            return rs.next() && rs.getInt(1) == 0;
        } catch (SQLException e) {
            return true;
        }
    }

    private static void copiarTabla(Connection origen, Connection destino, String tabla, List<String> columnasDeseadas) throws SQLException {
        if (!existeTabla(origen, tabla)) return;

        List<String> columnasOrigen = columnasExistentes(origen, tabla, columnasDeseadas);
        List<String> columnasDestino = columnasExistentes(destino, tabla, columnasOrigen);
        if (columnasDestino.isEmpty()) return;

        StringJoiner selectCols = new StringJoiner(", ");
        StringJoiner insertCols = new StringJoiner(", ");
        StringJoiner placeholders = new StringJoiner(", ");
        for (String columna : columnasDestino) {
            selectCols.add(columna);
            insertCols.add(columna);
            placeholders.add("?");
        }

        String selectSql = "SELECT " + selectCols + " FROM " + tabla;
        String insertSql = "INSERT OR IGNORE INTO " + tabla + " (" + insertCols + ") VALUES (" + placeholders + ")";

        try (Statement select = origen.createStatement();
             ResultSet rs = select.executeQuery(selectSql);
             PreparedStatement insert = destino.prepareStatement(insertSql)) {
            ResultSetMetaData md = rs.getMetaData();
            int columnas = md.getColumnCount();
            while (rs.next()) {
                for (int i = 1; i <= columnas; i++) {
                    insert.setObject(i, rs.getObject(i));
                }
                insert.executeUpdate();
            }
        }
    }

    private static boolean existeTabla(Connection conn, String tabla) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getTables(null, null, tabla.toUpperCase(), null)) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = metaData.getTables(null, null, tabla, null)) {
            return rs.next();
        }
    }

    private static List<String> columnasExistentes(Connection conn, String tabla, List<String> columnasDeseadas) throws SQLException {
        List<String> existentes = new ArrayList<>();
        for (String columna : columnasDeseadas) {
            if (esSqlite(conn)) {
                if (existeColumna(conn, tabla, columna)) existentes.add(columna);
            } else if (existeColumnaJdbc(conn, tabla, columna)) {
                existentes.add(columna);
            }
        }
        return existentes;
    }

    private static boolean esSqlite(Connection conn) throws SQLException {
        return conn.getMetaData().getURL().startsWith("jdbc:sqlite:");
    }

    private static boolean existeColumnaJdbc(Connection conn, String tabla, String columna) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getColumns(null, null, tabla.toUpperCase(), columna.toUpperCase())) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = metaData.getColumns(null, null, tabla, columna)) {
            return rs.next();
        }
    }

    private static void migrarRemisionesEnMuestras(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("UPDATE muestras SET remision = (" +
                    "SELECT 'R' || printf('%04d', r.consecutivo) " +
                    "FROM remision_muestras rm " +
                    "JOIN remisiones r ON r.id = rm.remisionId " +
                    "WHERE rm.muestraId = muestras.id" +
                    ") WHERE remision IS NULL AND EXISTS (" +
                    "SELECT 1 FROM remision_muestras rm WHERE rm.muestraId = muestras.id" +
                    ")");
        }
    }

    private static void asegurarUsuarioAdmin(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO usuarios (nombre, rol, password) " +
                        "SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM usuarios WHERE LOWER(nombre) = LOWER(?))")) {
            ps.setString(1, "admin");
            ps.setString(2, "ADMIN");
            ps.setString(3, "admin");
            ps.setString(4, "admin");
            ps.executeUpdate();
        }
    }

    private static void migrarEstados(Connection conn) throws SQLException {
        String[][] equivalencias = {
                {"RECIBIDA", "EN_CUSTODIA"},
                {"EN_ALMACENAMIENTO", "ALMACENADO"},
                {"EN_ENSAYO", "EN_CURSO"},
                {"EN_REVISION", "EN_CURSO"},
                {"FINALIZADA", "REALIZAR_DISPOSICION_FINAL"},
                {"DEVUELTA", "ENVIADO"},
                {"DESTRUIDA", "DESTRUCCION"}
        };

        for (String[] equivalencia : equivalencias) {
            actualizarEstado(conn, "muestras", "estado", equivalencia[0], equivalencia[1]);
            actualizarEstado(conn, "movimientos", "estadoAnterior", equivalencia[0], equivalencia[1]);
            actualizarEstado(conn, "movimientos", "estadoNuevo", equivalencia[0], equivalencia[1]);
        }
    }

    private static void actualizarEstado(Connection conn, String tabla, String columna,
                                         String anterior, String nuevo) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE " + tabla + " SET " + columna + " = ? WHERE " + columna + " = ?")) {
            ps.setString(1, nuevo);
            ps.setString(2, anterior);
            ps.executeUpdate();
        }
    }
}
