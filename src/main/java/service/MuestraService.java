package service;


import domain.Estado;
import domain.Usuario;
import db.Database;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MuestraService {

    public String generarCodigoInterno() {
        String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int consecutivo = 1;

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM muestras WHERE codigoInterno LIKE ?")) {

            ps.setString(1, fecha + "%");
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                consecutivo = rs.getInt(1) + 1;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return String.format("%s-%02d", fecha, consecutivo);
    }

    public void registrarMuestra(String rotuloCliente, String descripcion, int cantidad,
                                 String ubicacion, Usuario custodio, String rutaFoto) {

        String codigo = generarCodigoInterno();
        Estado estado = Estado.RECIBIDA;
        LocalDate fecha = LocalDate.now();

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO muestras (codigoInterno, rotuloCliente, descripcion, cantidad, estado, ubicacion, custodioId, fechaRecepcion, rutaFoto) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, codigo);
            ps.setString(2, rotuloCliente);
            ps.setString(3, descripcion);
            ps.setInt(4, cantidad);
            ps.setString(5, estado.name());
            ps.setString(6, ubicacion);
            ps.setInt(7, custodio.getId());
            ps.setObject(8, fecha);
            ps.setString(9, rutaFoto);

            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            int muestraId = -1;
            if (rs.next()) {
                muestraId = rs.getInt(1);
            }

            System.out.println("Muestra registrada: " + codigo);

            if (muestraId != -1) {
                registrarMovimientoInicial(muestraId, custodio, estado, ubicacion);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void registrarMovimientoInicial(int muestraId, Usuario usuario, Estado estado, String ubicacion) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO movimientos (muestraId, usuarioId, estadoAnterior, estadoNuevo, ubicacionAnterior, ubicacionNueva, fechaHora, observacion) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {

            ps.setInt(1, muestraId);
            ps.setInt(2, usuario.getId());
            ps.setString(3, null);
            ps.setString(4, estado.name());
            ps.setString(5, null);
            ps.setString(6, ubicacion);
            ps.setObject(7, LocalDateTime.now());
            ps.setString(8, "Registro inicial de la muestra");

            ps.executeUpdate();
            System.out.println("Movimiento inicial registrado para la muestra ID " + muestraId);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}