package db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Database {

    private static final String DB_FOLDER = "./data";
    private static final String JDBC_URL = "jdbc:h2:" + DB_FOLDER + "/lencdb"; // corregido
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.h2.Driver"); // registrar driver
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        // Crear carpeta data si no existe
        File folder = new File(DB_FOLDER);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        return DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
    }

    public static void init() {
        try (Connection conn = getConnection()) {

            // 1️⃣ Crear tablas
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

            System.out.println("Base de datos inicializada correctamente.");

            // 2️⃣ Insertar usuarios de prueba
            try {
                String sqlInsert = "INSERT INTO usuarios (nombre, rol) VALUES (?, ?)";
                PreparedStatement ps = conn.prepareStatement(sqlInsert);

                ps.setString(1, "admin");
                ps.setString(2, "AUXILIAR");
                ps.executeUpdate();

                ps.setString(1, "carlos");
                ps.setString(2, "TECNICO");
                ps.executeUpdate();

                ps.setString(1, "laura");
                ps.setString(2, "SUPERVISOR");
                ps.executeUpdate();

                System.out.println("Usuarios de prueba creados: admin, carlos, laura");

            } catch (SQLException e) {
                System.out.println("Usuarios de prueba ya existen o error en la creación.");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}