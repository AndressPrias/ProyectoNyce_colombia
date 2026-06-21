package utilities;

import db.Database;
import domain.Rol;
import domain.Usuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Mantiene el usuario logueado para pantallas incluidas (p. ej. menú lateral) que no
 * reciben {@code setUsuario} del controlador padre.
 */
public final class UsuarioSesion {

    private static Usuario usuarioActual;

    private UsuarioSesion() {}

    public static void setUsuario(Usuario usuario) {
        usuarioActual = usuario;
    }

    public static Usuario getUsuario() {
        return usuarioActual;
    }

    public static List<Usuario> obtenerUsuariosAsignables() {
        List<Usuario> usuarios = new ArrayList<>();
        String sql = "SELECT id, nombre, rol, rutaFoto, controlMuestras, controlTotal " +
                "FROM usuarios WHERE UPPER(rol) <> ? ORDER BY nombre";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, Rol.ADMIN.name());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                usuarios.add(new Usuario(
                        rs.getInt("id"),
                        rs.getString("nombre"),
                        Rol.valueOf(rs.getString("rol").toUpperCase()),
                        rs.getString("rutaFoto"),
                        rs.getBoolean("controlMuestras"),
                        rs.getBoolean("controlTotal")
                ));
            }
        } catch (SQLException | IllegalArgumentException e) {
            System.err.println("No se pudieron cargar los usuarios asignables: " + e.getMessage());
        }
        return usuarios;
    }

    public static List<Usuario> obtenerTodosUsuarios() {
        List<Usuario> usuarios = new ArrayList<>();
        String sql = "SELECT id, nombre, rol, rutaFoto, controlMuestras, controlTotal FROM usuarios ORDER BY nombre";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                usuarios.add(new Usuario(
                        rs.getInt("id"),
                        rs.getString("nombre"),
                        Rol.valueOf(rs.getString("rol").toUpperCase()),
                        rs.getString("rutaFoto"),
                        rs.getBoolean("controlMuestras"),
                        rs.getBoolean("controlTotal")
                ));
            }
        } catch (SQLException | IllegalArgumentException e) {
            System.err.println("No se pudieron cargar los usuarios: " + e.getMessage());
        }
        return usuarios;
    }

    public static void clear() {
        usuarioActual = null;
    }
}
