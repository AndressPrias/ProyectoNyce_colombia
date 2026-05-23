package utilities;

import domain.Usuario;

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

    public static void clear() {
        usuarioActual = null;
    }
}
