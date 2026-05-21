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
                            "rol VARCHAR(20) NOT NULL" +
                            ");"
            );

            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS muestras (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "codigoInterno VARCHAR(20) NOT NULL," +
                            "rotuloCliente VARCHAR(100)," +
                            "descripcion VARCHAR(255)," +
                            "cantidad INT," +
                            "estado VARCHAR(20) NOT NULL," +
                            "ubicacion VARCHAR(10)," +
                            "custodioId INT," +
                            "fechaRecepcion DATE," +
                            "rutaFoto VARCHAR(255)," +
                            "FOREIGN KEY (custodioId) REFERENCES usuarios(id)" +
                            ");"
            );

            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS movimientos (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "muestraId INT NOT NULL," +
                            "usuarioId INT NOT NULL," +
                            "estadoAnterior VARCHAR(20)," +
                            "estadoNuevo VARCHAR(20)," +
                            "ubicacionAnterior VARCHAR(10)," +
                            "ubicacionNueva VARCHAR(10)," +
                            "fechaHora TIMESTAMP," +
                            "observacion VARCHAR(255)," +
                            "FOREIGN KEY (muestraId) REFERENCES muestras(id)," +
                            "FOREIGN KEY (usuarioId) REFERENCES usuarios(id)" +
                            ");"
            );

            // Usuarios de prueba
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO usuarios (nombre, rol) VALUES (?, ?)")) {
                ps.setString(1, "Admin"); ps.setString(2, "Admin"); ps.executeUpdate();
                ps.setString(1, "carlos"); ps.setString(2, "TECNICO"); ps.executeUpdate();
                ps.setString(1, "Miguel Orozco"); ps.setString(2, "SUPERVISOR"); ps.executeUpdate();
            } catch (SQLException e) {
                System.out.println("Usuarios de prueba ya existen.");
            }

            System.out.println("Base de datos inicializada con usuarios de prueba.");

        } catch (SQLException e) { e.printStackTrace(); }
    }
}