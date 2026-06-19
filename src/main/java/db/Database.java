package db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Database {

    private static final String DB_FOLDER = "./data";
    private static final String JDBC_URL = "jdbc:h2:" + DB_FOLDER + "/lencdb";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    public static Connection getConnection() throws SQLException {
        try { Class.forName("org.h2.Driver"); } catch (ClassNotFoundException e) { e.printStackTrace(); }
        File folder = new File(DB_FOLDER);
        if (!folder.exists()) folder.mkdirs();
        return DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
    }

    public static void init() {
        try (Connection conn = getConnection()) {

            // Crear tablas
            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS usuarios (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "nombre VARCHAR(100) NOT NULL," +
                            "rol VARCHAR(20) NOT NULL," +
                            "password VARCHAR(100)," +
                            "rutaFoto VARCHAR(255)" +
                            ");"
            );
            conn.createStatement().execute(
                    "ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS password VARCHAR(100);"
            );
            conn.createStatement().execute(
                    "ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS rutaFoto VARCHAR(255);"
            );
            conn.createStatement().execute(
                    "UPDATE usuarios SET password = nombre WHERE password IS NULL;"
            );

            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS muestras (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "codigoInterno VARCHAR(20) NOT NULL," +
                            "rotuloCliente VARCHAR(100)," +
                            "nombreCliente VARCHAR(150)," +
                            "descripcion VARCHAR(255)," +
                            "marca VARCHAR(100)," +
                            "referencia VARCHAR(100)," +
                            "estado VARCHAR(40) NOT NULL," +
                            "ubicacion VARCHAR(10)," +
                            "estante VARCHAR(50)," +
                            "observacionAlmacenamiento VARCHAR(255)," +
                            "custodioId INT," +
                            "tecnicoId INT," +
                            "fechaRecepcion DATE," +
                            "rutaFoto VARCHAR(255)," +
                            "numeroInforme VARCHAR(100)," +
                            "numeroCotizacion VARCHAR(100)," +
                            "FOREIGN KEY (custodioId) REFERENCES usuarios(id)," +
                            "FOREIGN KEY (tecnicoId) REFERENCES usuarios(id)" +
                            ");"
            );
            conn.createStatement().execute(
                    "ALTER TABLE muestras ADD COLUMN IF NOT EXISTS nombreCliente VARCHAR(150);"
            );
            conn.createStatement().execute(
                    "ALTER TABLE muestras ADD COLUMN IF NOT EXISTS marca VARCHAR(100);"
            );
            conn.createStatement().execute(
                    "ALTER TABLE muestras ADD COLUMN IF NOT EXISTS referencia VARCHAR(100);"
            );
            conn.createStatement().execute(
                    "ALTER TABLE muestras ADD COLUMN IF NOT EXISTS estante VARCHAR(50);"
            );
            conn.createStatement().execute(
                    "ALTER TABLE muestras ADD COLUMN IF NOT EXISTS observacionAlmacenamiento VARCHAR(255);"
            );
            conn.createStatement().execute(
                    "ALTER TABLE muestras ADD COLUMN IF NOT EXISTS tecnicoId INT;"
            );
            conn.createStatement().execute(
                    "ALTER TABLE muestras ADD COLUMN IF NOT EXISTS numeroInforme VARCHAR(100);"
            );
            conn.createStatement().execute(
                    "ALTER TABLE muestras ADD COLUMN IF NOT EXISTS numeroCotizacion VARCHAR(100);"
            );
            conn.createStatement().execute(
                    "ALTER TABLE muestras DROP COLUMN IF EXISTS cantidad;"
            );

            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS movimientos (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "muestraId INT NOT NULL," +
                            "usuarioId INT NOT NULL," +
                            "estadoAnterior VARCHAR(40)," +
                            "estadoNuevo VARCHAR(40)," +
                            "ubicacionAnterior VARCHAR(10)," +
                            "ubicacionNueva VARCHAR(10)," +
                            "fechaHora TIMESTAMP," +
                            "observacion VARCHAR(255)," +
                            "FOREIGN KEY (muestraId) REFERENCES muestras(id)," +
                            "FOREIGN KEY (usuarioId) REFERENCES usuarios(id)" +
                            ");"
            );

            migrarEstados(conn);

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO usuarios (nombre, rol, password) " +
                            "SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM usuarios WHERE LOWER(nombre) = LOWER(?))")) {
                ps.setString(1, "admin");
                ps.setString(2, "ADMIN");
                ps.setString(3, "admin");
                ps.setString(4, "admin");
                ps.executeUpdate();
            }

            System.out.println("Base de datos inicializada con usuarios de prueba.");

        } catch (SQLException e) { e.printStackTrace(); }
    }

    private static void migrarEstados(Connection conn) throws SQLException {
        conn.createStatement().execute(
                "ALTER TABLE muestras ALTER COLUMN estado VARCHAR(40) NOT NULL;"
        );
        conn.createStatement().execute(
                "ALTER TABLE movimientos ALTER COLUMN estadoAnterior VARCHAR(40);"
        );
        conn.createStatement().execute(
                "ALTER TABLE movimientos ALTER COLUMN estadoNuevo VARCHAR(40);"
        );

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
