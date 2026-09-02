package db;

import java.nio.file.Files;
import java.nio.file.Path;
import utilities.AppConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

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
            migrarInformesTextoLibre(conn);
            migrarColumnas(conn);
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
                    "controlTotal INTEGER DEFAULT 0," +
                    "cambioPasswordObligatorio INTEGER DEFAULT 0" +
                    ");");

            st.execute("CREATE TABLE IF NOT EXISTS restablecimientos_password (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "usuarioId INTEGER," +
                    "administradorId INTEGER," +
                    "usuarioNombre TEXT NOT NULL," +
                    "administradorNombre TEXT NOT NULL," +
                    "fechaHora TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY (usuarioId) REFERENCES usuarios(id) ON DELETE SET NULL," +
                    "FOREIGN KEY (administradorId) REFERENCES usuarios(id) ON DELETE SET NULL" +
                    ");");

            st.execute("CREATE TABLE IF NOT EXISTS muestras (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "codigoInterno TEXT NOT NULL," +
                    "rotuloCliente TEXT," +
                    "nombreCliente TEXT," +
                    "descripcion TEXT," +
                    "cantidad INTEGER NOT NULL DEFAULT 1 CHECK(cantidad > 0)," +
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

            st.execute("CREATE TABLE IF NOT EXISTS muestra_informes (" +
                    "muestraId INTEGER NOT NULL," +
                    "numero TEXT NOT NULL CHECK(length(TRIM(numero)) > 0)," +
                    "anio INTEGER NOT NULL CHECK(anio BETWEEN 2000 AND 9999)," +
                    "PRIMARY KEY (muestraId, numero, anio)," +
                    "FOREIGN KEY (muestraId) REFERENCES muestras(id) ON DELETE CASCADE" +
                    ");");

            st.execute("CREATE TABLE IF NOT EXISTS muestra_cotizaciones (" +
                    "muestraId INTEGER NOT NULL," +
                    "numero TEXT NOT NULL CHECK(length(numero) = 4 AND numero NOT GLOB '*[^0-9]*')," +
                    "anio INTEGER NOT NULL CHECK(anio BETWEEN 2000 AND 9999)," +
                    "PRIMARY KEY (muestraId, numero, anio)," +
                    "FOREIGN KEY (muestraId) REFERENCES muestras(id) ON DELETE CASCADE" +
                    ");");
        }
    }

    private static void migrarInformesTextoLibre(Connection conn) throws SQLException {
        String definicion = null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT sql FROM sqlite_master WHERE type='table' AND name='muestra_informes'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) definicion = rs.getString("sql");
        }

        if (definicion == null || !definicion.contains("numero NOT GLOB")) return;

        boolean autoCommitAnterior = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE muestra_informes RENAME TO muestra_informes_anterior");
            st.execute("CREATE TABLE muestra_informes (" +
                    "muestraId INTEGER NOT NULL," +
                    "numero TEXT NOT NULL CHECK(length(TRIM(numero)) > 0)," +
                    "anio INTEGER NOT NULL CHECK(anio BETWEEN 2000 AND 9999)," +
                    "PRIMARY KEY (muestraId, numero, anio)," +
                    "FOREIGN KEY (muestraId) REFERENCES muestras(id) ON DELETE CASCADE" +
                    ")");
            st.execute("INSERT INTO muestra_informes (muestraId, numero, anio) " +
                    "SELECT muestraId, numero, anio FROM muestra_informes_anterior");
            st.execute("DROP TABLE muestra_informes_anterior");
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommitAnterior);
        }
    }

    private static void migrarColumnas(Connection conn) throws SQLException {
        agregarColumnaSiNoExiste(conn, "usuarios", "password", "TEXT");
        agregarColumnaSiNoExiste(conn, "usuarios", "rutaFoto", "TEXT");
        agregarColumnaSiNoExiste(conn, "usuarios", "controlMuestras", "INTEGER DEFAULT 0");
        agregarColumnaSiNoExiste(conn, "usuarios", "controlTotal", "INTEGER DEFAULT 0");
        agregarColumnaSiNoExiste(conn, "usuarios", "cambioPasswordObligatorio", "INTEGER DEFAULT 0");

        agregarColumnaSiNoExiste(conn, "muestras", "nombreCliente", "TEXT");
        agregarColumnaSiNoExiste(conn, "muestras", "cantidad",
                "INTEGER NOT NULL DEFAULT 1 CHECK(cantidad > 0)");
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
            st.execute("INSERT OR IGNORE INTO muestra_informes (muestraId, numero, anio) " +
                    "SELECT id, TRIM(numeroInforme), CAST(substr(fechaRecepcion, 1, 4) AS INTEGER) FROM muestras " +
                    "WHERE TRIM(COALESCE(numeroInforme, '')) GLOB '[0-9][0-9][0-9][0-9]' " +
                    "AND CAST(substr(fechaRecepcion, 1, 4) AS INTEGER) BETWEEN 2000 AND 9999");
            st.execute("INSERT OR IGNORE INTO muestra_cotizaciones (muestraId, numero, anio) " +
                    "SELECT id, TRIM(numeroCotizacion), CAST(substr(fechaRecepcion, 1, 4) AS INTEGER) FROM muestras " +
                    "WHERE TRIM(COALESCE(numeroCotizacion, '')) GLOB '[0-9][0-9][0-9][0-9]' " +
                    "AND CAST(substr(fechaRecepcion, 1, 4) AS INTEGER) BETWEEN 2000 AND 9999");
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
