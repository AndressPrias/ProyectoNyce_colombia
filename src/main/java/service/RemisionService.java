package service;

import db.Database;
import domain.Estado;
import domain.Muestra;
import domain.Usuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class RemisionService {

    public List<Muestra> obtenerMuestrasDisponibles() {
        List<Muestra> muestras = new ArrayList<>();
        String sql = "SELECT * FROM muestras WHERE estado NOT IN (?, ?) ORDER BY fechaRecepcion, codigoInterno";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Estado.ENVIADO.name());
            ps.setString(2, Estado.DESTRUCCION.name());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Muestra muestra = new Muestra();
                    muestra.setId(rs.getInt("id"));
                    muestra.setCodigoInterno(rs.getString("codigoInterno"));
                    muestra.setRotuloCliente(rs.getString("rotuloCliente"));
                    muestra.setNombreCliente(rs.getString("nombreCliente"));
                    muestra.setDescripcion(rs.getString("descripcion"));
                    muestra.setMarca(rs.getString("marca"));
                    muestra.setReferencia(rs.getString("referencia"));
                    muestra.setEstado(Estado.desdeTexto(rs.getString("estado")));
                    muestra.setUbicacion(rs.getString("ubicacion"));
                    muestra.setObservacionAlmacenamiento(rs.getString("observacionAlmacenamiento"));
                    muestra.setNumeroInforme(rs.getString("numeroInforme"));
                    muestra.setNumeroCotizacion(rs.getString("numeroCotizacion"));
                    muestra.setRemision(rs.getString("remision"));
                    muestra.setFechaRecepcion(leerFechaSeguro(rs, "fechaRecepcion"));
                    muestras.add(muestra);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron cargar las muestras para remisión", e);
        }
        return muestras;
    }

    public int siguienteConsecutivo() {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlSiguienteConsecutivo());
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) + 1 : 1;
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo calcular el consecutivo de la remisión", e);
        }
    }

    public boolean consecutivoDisponible(int consecutivo) {
        try (Connection conn = Database.getConnection()) {
            return consecutivoDisponible(conn, consecutivo);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo validar el consecutivo de la remisión", e);
        }
    }

    public int registrarRemision(LocalDate fechaElaboracion, String cliente, String tipoSalida,
                                 int numeroEmpaques, String observacionFinal, Usuario entregadoPor,
                                 String recibidoFirma, String recibidoCedula, String recibidoNombrePlaca,
                                 List<Muestra> muestras) {
        return registrarRemision(fechaElaboracion, cliente, tipoSalida, numeroEmpaques, observacionFinal,
                entregadoPor, recibidoFirma, recibidoCedula, recibidoNombrePlaca, muestras, null);
    }

    public int registrarRemision(LocalDate fechaElaboracion, String cliente, String tipoSalida,
                                 int numeroEmpaques, String observacionFinal, Usuario entregadoPor,
                                 String recibidoFirma, String recibidoCedula, String recibidoNombrePlaca,
                                 List<Muestra> muestras, Integer consecutivoSolicitado) {
        if (entregadoPor == null || !entregadoPor.puedeControlarMuestras()) {
            throw new IllegalArgumentException("No tiene permiso para generar remisiones");
        }
        if (muestras == null || muestras.isEmpty()) {
            throw new IllegalArgumentException("Seleccione al menos una muestra");
        }

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int consecutivo = consecutivoSolicitado == null ? siguienteConsecutivo(conn) : consecutivoSolicitado;
                if (consecutivo < 1) {
                    throw new IllegalArgumentException("El consecutivo debe ser mayor a cero");
                }
                if (!consecutivoDisponible(conn, consecutivo)) {
                    throw new IllegalArgumentException("La remisión R" + String.format("%04d", consecutivo) + " ya existe");
                }
                int remisionId = insertarRemision(conn, consecutivo, fechaElaboracion, cliente, tipoSalida,
                        numeroEmpaques, observacionFinal, entregadoPor, recibidoFirma,
                        recibidoCedula, recibidoNombrePlaca);

                String codigoRemision = String.format("R%04d", consecutivo);
                for (Muestra muestra : muestras) {
                    asociarMuestra(conn, remisionId, muestra, fechaElaboracion);
                    marcarComoEnviada(conn, muestra, entregadoPor, codigoRemision);
                }

                conn.commit();
                return consecutivo;
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof IllegalArgumentException illegalArgumentException) {
                    throw illegalArgumentException;
                }
                throw new IllegalStateException("No se pudo registrar la remisión", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo registrar la remisión", e);
        }
    }

    public boolean registrarRutaArchivo(int consecutivo, String rutaArchivo) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE remisiones SET rutaArchivo=? WHERE consecutivo=?")) {
            ps.setString(1, rutaArchivo);
            ps.setInt(2, consecutivo);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            System.err.println("No se pudo asociar el archivo de la remisión: " + e.getMessage());
            return false;
        }
    }

    public String obtenerRutaArchivo(int consecutivo) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT rutaArchivo FROM remisiones WHERE consecutivo=?")) {
            ps.setInt(1, consecutivo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("rutaArchivo") : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo consultar el PDF de la remision", e);
        }
    }

    private int siguienteConsecutivo(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sqlSiguienteConsecutivo());
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) + 1 : 1;
        }
    }

    private String sqlSiguienteConsecutivo() {
        return "SELECT MAX(valor) FROM (" +
                "SELECT COALESCE(MAX(consecutivo), 0) AS valor FROM remisiones " +
                "UNION ALL " +
                "SELECT COALESCE(MAX(CAST(SUBSTR(remision, 2) AS INTEGER)), 0) AS valor " +
                "FROM muestras WHERE remision GLOB 'R[0-9][0-9][0-9][0-9]*'" +
                ")";
    }

    private boolean consecutivoDisponible(Connection conn, int consecutivo) throws SQLException {
        String codigo = String.format("R%04d", consecutivo);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM remisiones WHERE consecutivo=? " +
                        "UNION ALL SELECT 1 FROM muestras WHERE remision=? LIMIT 1")) {
            ps.setInt(1, consecutivo);
            ps.setString(2, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                return !rs.next();
            }
        }
    }

    private int insertarRemision(Connection conn, int consecutivo, LocalDate fechaElaboracion,
                                 String cliente, String tipoSalida, int numeroEmpaques,
                                 String observacionFinal, Usuario entregadoPor, String recibidoFirma,
                                 String recibidoCedula, String recibidoNombrePlaca) throws SQLException {
        String sql = "INSERT INTO remisiones (consecutivo, fechaElaboracion, cliente, tipoSalida, " +
                "numeroEmpaques, observacionFinal, entregadoPorId, recibidoFirma, recibidoCedula, " +
                "recibidoNombrePlaca) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, consecutivo);
            ps.setString(2, fechaElaboracion == null ? null : fechaElaboracion.toString());
            ps.setString(3, cliente);
            ps.setString(4, tipoSalida);
            ps.setInt(5, numeroEmpaques);
            ps.setString(6, observacionFinal);
            ps.setInt(7, entregadoPor.getId());
            ps.setString(8, recibidoFirma);
            ps.setString(9, recibidoCedula);
            ps.setString(10, recibidoNombrePlaca);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("La base de datos no retornó el id de la remisión");
    }

    private void asociarMuestra(Connection conn, int remisionId, Muestra muestra,
                                LocalDate fechaEntrega) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO remision_muestras (remisionId, muestraId, fechaEntrega, observaciones) VALUES (?, ?, ?, ?)")) {
            ps.setInt(1, remisionId);
            ps.setInt(2, muestra.getId());
            ps.setString(3, fechaEntrega == null ? null : fechaEntrega.toString());
            ps.setString(4, textoOValor(muestra.getObservacionAlmacenamiento(), "Ninguna"));
            ps.executeUpdate();
        }
    }

    private void marcarComoEnviada(Connection conn, Muestra muestra, Usuario usuario,
                                   String codigoRemision) throws SQLException {
        Estado anterior = muestra.getEstado();
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE muestras SET estado=?, remision=? WHERE id=? AND estado NOT IN (?, ?)")) {
            ps.setString(1, Estado.ENVIADO.name());
            ps.setString(2, codigoRemision);
            ps.setInt(3, muestra.getId());
            ps.setString(4, Estado.ENVIADO.name());
            ps.setString(5, Estado.DESTRUCCION.name());
            if (ps.executeUpdate() != 1) {
                throw new SQLException("La muestra " + muestra.getCodigoInterno() + " ya no está disponible");
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO movimientos (muestraId, usuarioId, estadoAnterior, estadoNuevo, " +
                        "ubicacionAnterior, ubicacionNueva, fechaHora, observacion) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, muestra.getId());
            ps.setInt(2, usuario.getId());
            ps.setString(3, anterior == null ? null : anterior.name());
            ps.setString(4, Estado.ENVIADO.name());
            ps.setString(5, muestra.getUbicacion());
            ps.setString(6, "Fuera del laboratorio");
            ps.setString(7, LocalDateTime.now().toString());
            ps.setString(8, "Salida registrada mediante remisión de muestras");
            ps.executeUpdate();
        }
    }

    private LocalDate leerFechaSeguro(ResultSet rs, String columna) {
        try {
            String valor = rs.getString(columna);
            if (valor == null || valor.isBlank()) {
                return null;
            }
            valor = valor.trim();
            int espacio = valor.indexOf(' ');
            if (espacio > 0) {
                valor = valor.substring(0, espacio);
            }
            try {
                return LocalDate.parse(valor);
            } catch (DateTimeParseException ignored) {
                return LocalDate.parse(valor, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
        } catch (Exception e) {
            return null;
        }
    }
    private String textoOValor(String texto, String valorDefecto) {
        return texto == null || texto.isBlank() ? valorDefecto : texto.trim();
    }
}
