package service;


import domain.Estado;
import domain.Muestra;
import domain.ReferenciaDocumento;
import domain.Usuario;
import db.Database;
import utilities.ImageStorage;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

public class MuestraService {

    private static final String UBICACION_EN_ENSAYOS = "En ensayos";

    public String generarCodigoInterno() {
        return generarCodigoInternoParaFecha(LocalDate.now());
    }

    private String generarCodigoInternoParaFecha(LocalDate fechaRecepcion) {
        LocalDate fechaBase = fechaRecepcion != null ? fechaRecepcion : LocalDate.now();
        String prefijo = fechaBase.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int ultimoConsecutivo = 0;

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT codigoInterno FROM muestras WHERE codigoInterno LIKE ?")) {

            ps.setString(1, prefijo + "-%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String codigo = rs.getString("codigoInterno");
                int separador = codigo != null ? codigo.lastIndexOf('-') : -1;
                if (separador >= 0 && separador < codigo.length() - 1) {
                    try {
                        ultimoConsecutivo = Math.max(
                                ultimoConsecutivo,
                                Integer.parseInt(codigo.substring(separador + 1))
                        );
                    } catch (NumberFormatException ignored) {
                        // Ignora codigos antiguos que no tengan el formato esperado.
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return String.format("%s-%02d", prefijo, ultimoConsecutivo + 1);
    }

    public boolean registrarMuestra(String rotuloCliente, String descripcion,
                                 String ubicacion, Usuario custodio, String rutaFoto) {
        return registrarMuestra(rotuloCliente, null, descripcion, null, null, ubicacion, custodio, rutaFoto,
                null, null, List.of(), List.of(), false);
    }

    public boolean registrarMuestra(String rotuloCliente, String nombreCliente, String descripcion, String marca,
                                    String referencia, String ubicacion, Usuario custodio, String rutaFoto,
                                    Estado estadoUI, LocalDate fechaRecepcionUI) {
        return registrarMuestra(
                rotuloCliente, nombreCliente, descripcion, marca, referencia, ubicacion,
                custodio, rutaFoto, estadoUI, fechaRecepcionUI, List.of(), List.of(), false
        );
    }

    public boolean registrarMuestraExterna(String rotuloCliente, String nombreCliente, String descripcion, String marca,
                                           String referencia, String ubicacion, Usuario custodio, String rutaFoto,
                                           Estado estadoUI, LocalDate fechaRecepcionUI) {
        return registrarMuestra(
                rotuloCliente, nombreCliente, descripcion, marca, referencia, ubicacion,
                custodio, rutaFoto, estadoUI, fechaRecepcionUI, List.of(), List.of(), true
        );
    }

    public ResultadoImportacion importarMuestraExterna(String rotuloCliente, String nombreCliente,
                                                        String descripcion, String marca, String referencia,
                                                        String ubicacion, Usuario custodio, String rutaFoto,
                                                        Estado estadoUI, LocalDate fechaRecepcionUI,
                                                        List<ReferenciaDocumento> informes,
                                                        List<ReferenciaDocumento> cotizaciones) {
        if (custodio == null || !custodio.puedeControlarMuestras()) return ResultadoImportacion.ERROR;
        LocalDate fecha = fechaRecepcionUI != null ? fechaRecepcionUI : LocalDate.now();
        Estado estado = estadoUI != null ? estadoUI : Estado.EN_CUSTODIA;

        try (Connection conn = Database.getConnection()) {
            Integer muestraId = buscarMuestraExistente(conn, fecha, rotuloCliente, nombreCliente, descripcion);
            if (muestraId == null) {
                return registrarMuestra(rotuloCliente, nombreCliente, descripcion, marca, referencia, ubicacion,
                        custodio, rutaFoto, estado, fecha, informes, cotizaciones, true)
                        ? ResultadoImportacion.NUEVA : ResultadoImportacion.ERROR;
            }

            conn.setAutoCommit(false);
            try {
                String codigoInterno;
                String rutaFotoActual;
                try (PreparedStatement consulta = conn.prepareStatement(
                        "SELECT codigoInterno, rutaFoto FROM muestras WHERE id=?")) {
                    consulta.setInt(1, muestraId);
                    try (ResultSet rs = consulta.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return ResultadoImportacion.ERROR;
                        }
                        codigoInterno = rs.getString("codigoInterno");
                        rutaFotoActual = rs.getString("rutaFoto");
                    }
                }
                String rutaFinal = rutaFoto == null || rutaFoto.isBlank()
                        ? rutaFotoActual : normalizarFotoMuestra(rutaFoto, codigoInterno);
                try (PreparedStatement actualizar = conn.prepareStatement(
                        "UPDATE muestras SET nombreCliente=?, marca=?, referencia=?, estado=?, ubicacion=?, " +
                                "custodioId=?, rutaFoto=? WHERE id=?")) {
                    actualizar.setString(1, nombreCliente);
                    actualizar.setString(2, marca);
                    actualizar.setString(3, referencia);
                    actualizar.setString(4, estado.name());
                    actualizar.setString(5, ubicacion);
                    actualizar.setInt(6, custodio.getId());
                    actualizar.setString(7, rutaFinal);
                    actualizar.setInt(8, muestraId);
                    actualizar.executeUpdate();
                }

                List<ReferenciaDocumento> informesCombinados = new ArrayList<>(
                        leerReferencias(conn, "muestra_informes", muestraId));
                informesCombinados.addAll(informes == null ? List.of() : informes);
                List<ReferenciaDocumento> cotizacionesCombinadas = new ArrayList<>(
                        leerReferencias(conn, "muestra_cotizaciones", muestraId));
                cotizacionesCombinadas.addAll(cotizaciones == null ? List.of() : cotizaciones);
                reemplazarReferencias(conn, "muestra_informes", muestraId,
                        normalizarReferencias(informesCombinados));
                reemplazarReferencias(conn, "muestra_cotizaciones", muestraId,
                        normalizarReferencias(cotizacionesCombinadas));
                conn.commit();
                return ResultadoImportacion.ACTUALIZADA;
            } catch (Exception e) {
                conn.rollback();
                System.err.println("Error al actualizar muestra desde Excel: " + e.getMessage());
                return ResultadoImportacion.ERROR;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Error al validar muestra existente: " + e.getMessage());
            return ResultadoImportacion.ERROR;
        }
    }

    private Integer buscarMuestraExistente(Connection conn, LocalDate fecha, String rotuloCliente,
                                            String nombreCliente, String descripcion) throws SQLException {
        String sql = "SELECT id FROM muestras WHERE fechaRecepcion=? " +
                "AND LOWER(TRIM(COALESCE(rotuloCliente, ''))) = LOWER(TRIM(COALESCE(?, ''))) " +
                "AND LOWER(TRIM(COALESCE(nombreCliente, ''))) = LOWER(TRIM(COALESCE(?, ''))) " +
                "AND LOWER(TRIM(COALESCE(descripcion, ''))) = LOWER(TRIM(COALESCE(?, ''))) " +
                "ORDER BY id LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fecha.toString());
            ps.setString(2, rotuloCliente);
            ps.setString(3, nombreCliente);
            ps.setString(4, descripcion);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("id") : null;
            }
        }
    }

    public enum ResultadoImportacion {
        NUEVA, ACTUALIZADA, ERROR
    }

    private boolean registrarMuestra(String rotuloCliente, String nombreCliente, String descripcion, String marca,
                                     String referencia, String ubicacion, Usuario custodio, String rutaFoto,
                                     Estado estadoUI, LocalDate fechaRecepcionUI,
                                     List<ReferenciaDocumento> informes, List<ReferenciaDocumento> cotizaciones,
                                     boolean codigoDesdeFechaRecepcion) {

        if (custodio == null) {
            throw new IllegalArgumentException("No hay un usuario autenticado para registrar la muestra");
        }
        if (!custodio.puedeControlarMuestras()) {
            throw new IllegalArgumentException("El usuario no tiene permiso para registrar muestras");
        }

        Estado estado = estadoUI != null ? estadoUI : Estado.EN_CUSTODIA;
        LocalDate fecha = fechaRecepcionUI != null ? fechaRecepcionUI : LocalDate.now();
        String codigo = codigoDesdeFechaRecepcion
                ? generarCodigoInternoParaFecha(fecha)
                : generarCodigoInterno();
        List<ReferenciaDocumento> informesValidos = normalizarReferencias(informes);
        List<ReferenciaDocumento> cotizacionesValidas = normalizarReferencias(cotizaciones);
        String rutaFotoNormalizada = normalizarFotoMuestra(rutaFoto, codigo);

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
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
            ps.setString(10, fecha.toString());
            ps.setString(11, rutaFotoNormalizada);
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            int muestraId = -1;
            if (rs.next()) {
                muestraId = rs.getInt(1);
            }

            System.out.println("Muestra registrada: " + codigo);

            if (muestraId != -1) {
                guardarReferencias(conn, "muestra_informes", muestraId, informesValidos);
                guardarReferencias(conn, "muestra_cotizaciones", muestraId, cotizacionesValidas);
                registrarMovimientoInicial(conn, muestraId, custodio, estado, ubicacion);
            }
            conn.commit();
            return true;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void registrarMovimientoInicial(Connection conn, int muestraId, Usuario usuario,
                                             Estado estado, String ubicacion) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO movimientos (muestraId, usuarioId, estadoAnterior, estadoNuevo, ubicacionAnterior, ubicacionNueva, fechaHora, observacion) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, muestraId);
            ps.setInt(2, usuario.getId());
            ps.setString(3, null);
            ps.setString(4, estado.name());
            ps.setString(5, null);
            ps.setString(6, ubicacion);
            ps.setString(7, LocalDateTime.now().toString());
            ps.setString(8, "Registro inicial de la muestra");
            ps.executeUpdate();
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
                m.setObservacionAlmacenamiento(rs.getString("observacionAlmacenamiento"));
                m.setRemision(rs.getString("remision"));
                m.setFechaRecepcion(leerFechaSeguro(rs, "fechaRecepcion"));
                cargarReferencias(conn, m);

                m.setRutaFoto(rs.getString("rutaFoto"));
                lista.add(m);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
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
    public boolean actualizarMuestra(Muestra muestra, Usuario usuarioAccion) {
        if (usuarioAccion == null || !usuarioAccion.puedeControlarMuestras()) {
            return false;
        }
        String rutaFoto = normalizarFotoMuestra(
                muestra.getRutaFoto(),
                muestra.getCodigoInterno() == null || muestra.getCodigoInterno().isBlank()
                        ? String.valueOf(muestra.getId())
                        : muestra.getCodigoInterno()
        );
        String sql = "UPDATE muestras SET descripcion=?, rotuloCliente=?, nombreCliente=?, marca=?, referencia=?, estado=?, " +
                "fechaRecepcion=?, ubicacion=?, tecnicoId=?, rutaFoto=? WHERE id=?";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, muestra.getDescripcion());
            ps.setString(2, muestra.getRotuloCliente());
            ps.setString(3, muestra.getNombreCliente());
            ps.setString(4, muestra.getMarca());
            ps.setString(5, muestra.getReferencia());
            ps.setString(6, muestra.getEstado().name());
            ps.setString(7, muestra.getFechaRecepcion() == null ? null : muestra.getFechaRecepcion().toString());
            ps.setString(8, muestra.getUbicacion());
            setUsuarioIdNullable(ps, 9, muestra.getTecnico());
            ps.setString(10, rutaFoto);
            ps.setInt(11, muestra.getId());

            return ps.executeUpdate() == 1;

        } catch (SQLException e) {
            System.err.println("Error al actualizar muestra " + muestra.getId() + ": " + e.getMessage());
            return false;
        }
    }

    public boolean reemplazarInformesCotizaciones(int muestraId, List<ReferenciaDocumento> informes,
                                                   List<ReferenciaDocumento> cotizaciones,
                                                   Usuario usuarioAccion, boolean actualizarInformes,
                                                   boolean actualizarCotizaciones) {
        if (usuarioAccion == null || !usuarioAccion.puedeControlarMuestras()
                || (!actualizarInformes && !actualizarCotizaciones)) return false;
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (actualizarInformes) {
                    reemplazarReferencias(conn, "muestra_informes", muestraId,
                            normalizarReferencias(informes));
                }
                if (actualizarCotizaciones) {
                    reemplazarReferencias(conn, "muestra_cotizaciones", muestraId,
                            normalizarReferencias(cotizaciones));
                }
                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                System.err.println("Error al actualizar informes/cotizaciones: " + e.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public void cargarReferencias(Connection conn, Muestra muestra) throws SQLException {
        muestra.setInformes(leerReferencias(conn, "muestra_informes", muestra.getId()));
        muestra.setCotizaciones(leerReferencias(conn, "muestra_cotizaciones", muestra.getId()));
    }

    private List<ReferenciaDocumento> leerReferencias(Connection conn, String tabla, int muestraId) throws SQLException {
        List<ReferenciaDocumento> resultado = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT numero, anio FROM " + tabla + " WHERE muestraId=? ORDER BY anio, numero")) {
            ps.setInt(1, muestraId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(new ReferenciaDocumento(rs.getString("numero"), rs.getInt("anio")));
                }
            }
        }
        return resultado;
    }

    private void reemplazarReferencias(Connection conn, String tabla, int muestraId,
                                        List<ReferenciaDocumento> referencias) throws SQLException {
        try (PreparedStatement borrar = conn.prepareStatement("DELETE FROM " + tabla + " WHERE muestraId=?")) {
            borrar.setInt(1, muestraId);
            borrar.executeUpdate();
        }
        guardarReferencias(conn, tabla, muestraId, referencias);
    }

    private void guardarReferencias(Connection conn, String tabla, int muestraId,
                                    List<ReferenciaDocumento> referencias) throws SQLException {
        try (PreparedStatement insertar = conn.prepareStatement(
                "INSERT INTO " + tabla + " (muestraId, numero, anio) VALUES (?, ?, ?)")) {
            for (ReferenciaDocumento referencia : referencias) {
                insertar.setInt(1, muestraId);
                insertar.setString(2, referencia.numero());
                insertar.setInt(3, referencia.anio());
                insertar.addBatch();
            }
            insertar.executeBatch();
        }
    }

    private List<ReferenciaDocumento> normalizarReferencias(List<ReferenciaDocumento> referencias) {
        Set<ReferenciaDocumento> unicas = new LinkedHashSet<>(referencias == null ? List.of() : referencias);
        return new ArrayList<>(unicas);
    }

    public boolean asignarTecnico(int muestraId, Usuario tecnico, Usuario usuarioAccion) {
        if (tecnico == null || tecnico.getRol() == domain.Rol.ADMIN
                || usuarioAccion == null || !usuarioAccion.puedeControlarMuestras()) {
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
                        "UPDATE muestras SET tecnicoId=?, estado=?, ubicacion=? WHERE id=?")) {
                    ps.setInt(1, tecnico.getId());
                    ps.setString(2, Estado.EN_CURSO.name());
                    ps.setString(3, UBICACION_EN_ENSAYOS);
                    ps.setInt(4, muestraId);
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
                    movimiento.setString(6, UBICACION_EN_ENSAYOS);
                    movimiento.setString(7, LocalDateTime.now().toString());
                    movimiento.setString(8, "Asignación de técnico: " + tecnico.getNombre());
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

    public boolean finalizarEnsayos(int muestraId, Usuario tecnico) {
        if (tecnico == null) {
            return false;
        }

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Estado estadoAnterior;
                String ubicacionActual;
                Integer tecnicoAsignadoId;
                try (PreparedStatement consulta = conn.prepareStatement(
                        "SELECT tecnicoId, estado, ubicacion FROM muestras WHERE id=?")) {
                    consulta.setInt(1, muestraId);
                    ResultSet rs = consulta.executeQuery();
                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }
                    int tecnicoId = rs.getInt("tecnicoId");
                    tecnicoAsignadoId = rs.wasNull() ? null : tecnicoId;
                    estadoAnterior = Estado.desdeTexto(rs.getString("estado"));
                    ubicacionActual = rs.getString("ubicacion");
                }

                if (!tecnico.puedeFinalizarEnsayos()
                        || tecnicoAsignadoId == null
                        || tecnicoAsignadoId != tecnico.getId()
                        || estadoAnterior != Estado.EN_CURSO) {
                    conn.rollback();
                    return false;
                }

                try (PreparedStatement actualizacion = conn.prepareStatement(
                        "UPDATE muestras SET estado=? WHERE id=?")) {
                    actualizacion.setString(1, Estado.LISTA_PARA_ALMACENAR.name());
                    actualizacion.setInt(2, muestraId);
                    if (actualizacion.executeUpdate() != 1) {
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
                    movimiento.setString(4, Estado.LISTA_PARA_ALMACENAR.name());
                    movimiento.setString(5, ubicacionActual);
                    movimiento.setString(6, ubicacionActual);
                    movimiento.setString(7, LocalDateTime.now().toString());
                    movimiento.setString(8, "Ensayos finalizados. Muestra lista para almacenar");
                    movimiento.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                System.err.println("Error al finalizar ensayos de la muestra " + muestraId + ": " + e.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Error de conexión al finalizar ensayos de la muestra " + muestraId + ": " + e.getMessage());
            return false;
        }
    }

    public boolean eliminarMuestra(int muestraId, Usuario usuarioAccion) {
        if (usuarioAccion == null || !usuarioAccion.puedeControlarMuestras()) {
            return false;
        }
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

    public boolean almacenarMuestra(int muestraId, String ubicacion,
                                    String observacion, Usuario responsable, Usuario usuario) {
        if (usuario == null || !usuario.puedeControlarMuestras()
                || responsable == null || ubicacion == null || ubicacion.isBlank()) {
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
                        "UPDATE muestras SET ubicacion=?, observacionAlmacenamiento=?, responsableId=?, estado=? WHERE id=?")) {
                    actualizacion.setString(1, ubicacion.trim());
                    actualizacion.setString(2, normalizarOpcional(observacion));
                    actualizacion.setInt(3, responsable.getId());
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
                    movimiento.setString(6, ubicacion.trim());
                    movimiento.setString(7, LocalDateTime.now().toString());
                    String detalleResponsable = "Responsable de almacenamiento: " + responsable.getNombre();
                    String observacionNormalizada = normalizarOpcional(observacion);
                    String detalleMovimiento = observacionNormalizada == null
                            ? detalleResponsable
                            : detalleResponsable + ". " + observacionNormalizada;
                    movimiento.setString(8, limitarLongitud(detalleMovimiento, 255));
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

    private String limitarLongitud(String valor, int maximo) {
        return valor != null && valor.length() > maximo ? valor.substring(0, maximo) : valor;
    }

    private String normalizarFotoMuestra(String rutaFoto, String codigoInterno) {
        try {
            return ImageStorage.normalizeSamplePhotoName(rutaFoto, codigoInterno);
        } catch (IOException e) {
            System.err.println("No se pudo normalizar el nombre de la foto de la muestra "
                    + codigoInterno + ": " + e.getMessage());
            return rutaFoto;
        }
    }

}
