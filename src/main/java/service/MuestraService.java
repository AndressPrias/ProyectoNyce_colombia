package service;


import domain.Estado;
import domain.Muestra;
import domain.Usuario;
import db.Database;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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

    public boolean registrarMuestra(String rotuloCliente, String descripcion,
                                 String ubicacion, Usuario custodio, String rutaFoto) {
        return registrarMuestra(rotuloCliente, null, descripcion, null, null, ubicacion, custodio, rutaFoto, null, null);
    }

    public boolean registrarMuestra(String rotuloCliente, String nombreCliente, String descripcion, String marca,
                                    String referencia, String ubicacion, Usuario custodio, String rutaFoto,
                                    Estado estadoUI, LocalDate fechaRecepcionUI) {

        if (custodio == null) {
            throw new IllegalArgumentException("No hay un usuario autenticado para registrar la muestra");
        }

        String codigo = generarCodigoInterno();
        Estado estado = estadoUI != null ? estadoUI : Estado.EN_CUSTODIA;
        LocalDate fecha = fechaRecepcionUI != null ? fechaRecepcionUI : LocalDate.now();

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO muestras (codigoInterno, rotuloCliente, nombreCliente, descripcion, marca, referencia, estado, ubicacion, custodioId, fechaRecepcion, rutaFoto) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, codigo);
            ps.setString(2, rotuloCliente);
            ps.setString(3, nombreCliente);
            ps.setString(4, descripcion);
            ps.setString(5, marca);
            ps.setString(6, referencia);
            ps.setString(7, estado.name());
            ps.setString(8, ubicacion);
            ps.setInt(9, custodio.getId());
            ps.setObject(10, fecha);
            ps.setString(11, rutaFoto);

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
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
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

    public List<Muestra> obtenerTodasMuestras() {
        List<Muestra> lista = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM muestras");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Muestra m = new Muestra();
                m.setId(rs.getInt("id"));
                m.setCodigoInterno(rs.getString("codigoInterno"));
                m.setRotuloCliente(rs.getString("rotuloCliente"));
                m.setNombreCliente(rs.getString("nombreCliente"));
                m.setDescripcion(rs.getString("descripcion"));
                m.setMarca(rs.getString("marca"));
                m.setReferencia(rs.getString("referencia"));
                // Convertir String a Enum Estado
                String estadoStr = rs.getString("estado");
                if (estadoStr != null) {
                    try {
                        m.setEstado(Estado.desdeTexto(estadoStr));
                    } catch (IllegalArgumentException e) {
                        m.setEstado(Estado.EN_CUSTODIA);
                    }
                } else {
                    m.setEstado(Estado.EN_CUSTODIA);
                }

                m.setUbicacion(rs.getString("ubicacion"));
                m.setEstante(rs.getString("estante"));
                m.setObservacionAlmacenamiento(rs.getString("observacionAlmacenamiento"));
                m.setNumeroInforme(rs.getString("numeroInforme"));
                m.setNumeroCotizacion(rs.getString("numeroCotizacion"));

                // Fecha segura
                java.sql.Date sqlDate = rs.getDate("fechaRecepcion");
                if (sqlDate != null) {
                    m.setFechaRecepcion(sqlDate.toLocalDate());
                } else {
                    m.setFechaRecepcion(null); // o LocalDate.now() si prefieres
                }

                m.setRutaFoto(rs.getString("rutaFoto"));
                lista.add(m);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    public boolean actualizarMuestra(Muestra muestra) {
        String sql = "UPDATE muestras SET descripcion=?, rotuloCliente=?, nombreCliente=?, marca=?, referencia=?, estado=?, " +
                "fechaRecepcion=?, ubicacion=?, estante=?, tecnicoId=?, rutaFoto=?, numeroInforme=?, numeroCotizacion=? WHERE id=?";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, muestra.getDescripcion());
            ps.setString(2, muestra.getRotuloCliente());
            ps.setString(3, muestra.getNombreCliente());
            ps.setString(4, muestra.getMarca());
            ps.setString(5, muestra.getReferencia());
            ps.setString(6, muestra.getEstado().name());
            ps.setObject(7, muestra.getFechaRecepcion()); // LocalDate compatible
            ps.setString(8, muestra.getUbicacion());
            ps.setString(9, muestra.getEstante());
            setUsuarioIdNullable(ps, 10, muestra.getTecnico());
            ps.setString(11, muestra.getRutaFoto());
            ps.setString(12, muestra.getNumeroInforme());
            ps.setString(13, muestra.getNumeroCotizacion());
            ps.setInt(14, muestra.getId());

            return ps.executeUpdate() == 1;

        } catch (SQLException e) {
            System.err.println("Error al actualizar muestra " + muestra.getId() + ": " + e.getMessage());
            return false;
        }
    }

    public boolean asignarTecnico(int muestraId, Usuario tecnico) {
        if (tecnico == null || tecnico.getRol() == domain.Rol.ADMIN) {
            return false;
        }

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Estado estadoAnterior;
                String ubicacionActual;
                try (PreparedStatement consulta = conn.prepareStatement(
                        "SELECT estado, ubicacion FROM muestras WHERE id=?")) {
                    consulta.setInt(1, muestraId);
                    ResultSet rs = consulta.executeQuery();
                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }
                    estadoAnterior = Estado.desdeTexto(rs.getString("estado"));
                    ubicacionActual = rs.getString("ubicacion");
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE muestras SET tecnicoId=?, estado=? WHERE id=?")) {
                    ps.setInt(1, tecnico.getId());
                    ps.setString(2, Estado.EN_CURSO.name());
                    ps.setInt(3, muestraId);
                    if (ps.executeUpdate() != 1) {
                        conn.rollback();
                        return false;
                    }
                }

                try (PreparedStatement movimiento = conn.prepareStatement(
                        "INSERT INTO movimientos (muestraId, usuarioId, estadoAnterior, estadoNuevo, ubicacionAnterior, ubicacionNueva, fechaHora, observacion) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    movimiento.setInt(1, muestraId);
                    movimiento.setInt(2, tecnico.getId());
                    movimiento.setString(3, estadoAnterior.name());
                    movimiento.setString(4, Estado.EN_CURSO.name());
                    movimiento.setString(5, ubicacionActual);
                    movimiento.setString(6, ubicacionActual);
                    movimiento.setObject(7, LocalDateTime.now());
                    movimiento.setString(8, "Asignacion de tecnico: " + tecnico.getNombre());
                    movimiento.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                System.err.println("Error al asignar tecnico a muestra " + muestraId + ": " + e.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Error al asignar tecnico a muestra " + muestraId + ": " + e.getMessage());
            return false;
        }
    }

    public boolean eliminarMuestra(int muestraId) {
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement borrarMovimientos = conn.prepareStatement(
                    "DELETE FROM movimientos WHERE muestraId=?");
                 PreparedStatement borrarMuestra = conn.prepareStatement(
                         "DELETE FROM muestras WHERE id=?")) {

                borrarMovimientos.setInt(1, muestraId);
                borrarMovimientos.executeUpdate();

                borrarMuestra.setInt(1, muestraId);
                boolean eliminada = borrarMuestra.executeUpdate() == 1;
                if (!eliminada) {
                    conn.rollback();
                    return false;
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("Error al eliminar muestra " + muestraId + ": " + e.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Error de conexion al eliminar muestra " + muestraId + ": " + e.getMessage());
            return false;
        }
    }

    public boolean almacenarMuestra(int muestraId, String ubicacion, String estante,
                                    String observacion, Usuario usuario) {
        if (usuario == null || ubicacion == null || ubicacion.isBlank()) {
            return false;
        }

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String ubicacionAnterior = null;
                Estado estadoAnterior = null;
                try (PreparedStatement consulta = conn.prepareStatement(
                        "SELECT ubicacion, estado FROM muestras WHERE id=?")) {
                    consulta.setInt(1, muestraId);
                    ResultSet rs = consulta.executeQuery();
                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }
                    ubicacionAnterior = rs.getString("ubicacion");
                    estadoAnterior = Estado.desdeTexto(rs.getString("estado"));
                }

                try (PreparedStatement actualizacion = conn.prepareStatement(
                        "UPDATE muestras SET ubicacion=?, estante=?, observacionAlmacenamiento=?, estado=? WHERE id=?")) {
                    actualizacion.setString(1, ubicacion.trim());
                    actualizacion.setString(2, normalizarOpcional(estante));
                    actualizacion.setString(3, normalizarOpcional(observacion));
                    actualizacion.setString(4, Estado.ALMACENADO.name());
                    actualizacion.setInt(5, muestraId);
                    if (actualizacion.executeUpdate() != 1) {
                        conn.rollback();
                        return false;
                    }
                }

                try (PreparedStatement movimiento = conn.prepareStatement(
                        "INSERT INTO movimientos (muestraId, usuarioId, estadoAnterior, estadoNuevo, ubicacionAnterior, ubicacionNueva, fechaHora, observacion) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    movimiento.setInt(1, muestraId);
                    movimiento.setInt(2, usuario.getId());
                    movimiento.setString(3, estadoAnterior.name());
                    movimiento.setString(4, Estado.ALMACENADO.name());
                    movimiento.setString(5, ubicacionAnterior);
                    String nuevaUbicacion = normalizarOpcional(estante) == null
                            ? ubicacion.trim()
                            : ubicacion.trim() + " / " + estante.trim();
                    movimiento.setString(6, nuevaUbicacion);
                    movimiento.setObject(7, LocalDateTime.now());
                    movimiento.setString(8, normalizarOpcional(observacion));
                    movimiento.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                System.err.println("Error al almacenar muestra " + muestraId + ": " + e.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Error de conexion al almacenar muestra " + muestraId + ": " + e.getMessage());
            return false;
        }
    }

    private void setUsuarioIdNullable(PreparedStatement ps, int indice, Usuario usuario) throws SQLException {
        if (usuario == null) {
            ps.setNull(indice, Types.INTEGER);
        } else {
            ps.setInt(indice, usuario.getId());
        }
    }

    private String normalizarOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    public boolean almacenarMuestra(int id, String text, String text1, Usuario usuario) {
        return almacenarMuestra(id, text, null, text1, usuario);
    }
}
