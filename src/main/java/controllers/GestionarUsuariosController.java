package controllers;

import db.Database;
import domain.Rol;
import domain.Usuario;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import utilities.Navegacion;
import utilities.UsuarioSesion;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class GestionarUsuariosController {

    private static final String CARPETA_AVATARES = "src/main/resources/avatarUsuarios";
    private static final String RECURSO_AVATARES = "/avatarUsuarios/";

    @FXML private TableView<Usuario> tblUsuarios;
    @FXML private TableColumn<Usuario, Integer> colId;
    @FXML private TableColumn<Usuario, String> colNombre;
    @FXML private TableColumn<Usuario, Rol> colRol;
    @FXML private TextField txtNombre;
    @FXML private ComboBox<String> comboRol;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblMensaje;
    @FXML private ImageView imgFotoPerfil;
    @FXML private Button btnSeleccionarImagen;
    @FXML private Button btnGuardar;

    private final ObservableList<Usuario> usuarios = FXCollections.observableArrayList();
    private String rutaFotoSeleccionada = "";
    private Usuario usuarioSeleccionado;
    private Usuario usuario;

    @FXML
    public void initialize() {
        comboRol.setItems(FXCollections.observableArrayList("AUXILIAR", "TECNICO", "SUPERVISOR", "ADMIN", "LIDER"));
        imgFotoPerfil.setClip(new Circle(69, 69, 69));

        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        colRol.setCellValueFactory(new PropertyValueFactory<>("rol"));
        tblUsuarios.setItems(usuarios);
        tblUsuarios.getSelectionModel().selectedItemProperty().addListener((obs, anterior, seleccionado) -> {
            if (seleccionado != null) {
                cargarUsuarioEnFormulario(seleccionado);
            }
        });

        cargarUsuarios();
    }

    @FXML
    void seleccionarImagen(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar foto de perfil");
        File carpetaAvatares = new File(CARPETA_AVATARES);
        if (carpetaAvatares.exists()) {
            fileChooser.setInitialDirectory(carpetaAvatares);
        }
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imagenes", "*.png", "*.jpg", "*.jpeg")
        );

        File selectedFile = fileChooser.showOpenDialog(btnSeleccionarImagen.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }

        rutaFotoSeleccionada = obtenerRutaAvatar(selectedFile);
        cargarImagenDesdeArchivo(selectedFile);
    }

    @FXML
    void guardarUsuario() {
        if (!puedeModificarSeleccionado() && usuarioSeleccionado != null) {
            mostrarMensaje("Solo puede modificar su propio usuario");
            return;
        }

        String nombre = txtNombre.getText();
        String rolStr = puedeGestionarUsuarios() ? comboRol.getValue() : usuario.getRol().name();
        String password = txtPassword.getText();

        if (usuarioSeleccionado == null && !puedeGestionarUsuarios()) {
            mostrarMensaje("Solo Supervisor o Admin puede crear usuarios");
            return;
        }

        if (nombre.isEmpty() || rolStr == null || (usuarioSeleccionado == null && password.isEmpty())) {
            mostrarMensaje("Debe completar todos los campos");
            return;
        }

        try (Connection conn = Database.getConnection()) {
            if (usuarioSeleccionado == null) {
                registrarNuevoUsuario(conn, nombre, rolStr, password);
                mostrarMensaje("Usuario registrado correctamente");
            } else {
                actualizarUsuario(conn, nombre, rolStr, password);
                actualizarUsuarioActivo(nombre, rolStr);
                mostrarMensaje("Usuario actualizado correctamente");
            }
            limpiarFormulario();
            cargarUsuarios();
        } catch (SQLException e) {
            e.printStackTrace();
            mostrarMensaje("Error al guardar usuario");
        }
    }

    @FXML
    void nuevoUsuario() {
        if (!puedeGestionarUsuarios()) {
            cargarUsuarioEnFormulario(usuario);
            mostrarMensaje("Solo Supervisor o Admin puede crear usuarios");
            return;
        }
        limpiarFormulario();
        lblMensaje.setVisible(false);
    }

    @FXML
    void eliminarUsuario() {
        if (!puedeGestionarUsuarios()) {
            mostrarMensaje("Solo Supervisor o Admin puede eliminar usuarios");
            return;
        }

        if (usuarioSeleccionado == null) {
            mostrarMensaje("Seleccione un usuario para eliminar");
            return;
        }

        if (usuario != null && usuario.getId() == usuarioSeleccionado.getId()) {
            mostrarMensaje("No puede eliminar el usuario con sesion activa");
            return;
        }

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM usuarios WHERE id = ?")) {

            ps.setInt(1, usuarioSeleccionado.getId());
            int filas = ps.executeUpdate();
            mostrarMensaje(filas > 0 ? "Usuario eliminado correctamente" : "No se encontro el usuario");
            limpiarFormulario();
            cargarUsuarios();
        } catch (SQLException e) {
            e.printStackTrace();
            mostrarMensaje("No se puede eliminar: el usuario tiene registros asociados");
        }
    }

    @FXML
    void salir() {
        Navegacion.irInicio();
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
        UsuarioSesion.setUsuario(usuario);
        cargarUsuarios();
    }

    private void registrarNuevoUsuario(Connection conn, String nombre, String rolStr, String password) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO usuarios (nombre, rol, password, rutaFoto) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, nombre);
            ps.setString(2, rolStr);
            ps.setString(3, password);
            ps.setString(4, rutaFotoSeleccionada.isBlank() ? null : rutaFotoSeleccionada);
            ps.executeUpdate();
        }
    }

    private void actualizarUsuario(Connection conn, String nombre, String rolStr, String password) throws SQLException {
        String sql = password.isEmpty()
                ? "UPDATE usuarios SET nombre = ?, rol = ?, rutaFoto = ? WHERE id = ?"
                : "UPDATE usuarios SET nombre = ?, rol = ?, rutaFoto = ?, password = ? WHERE id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ps.setString(2, rolStr);
            ps.setString(3, rutaFotoSeleccionada.isBlank() ? null : rutaFotoSeleccionada);
            if (password.isEmpty()) {
                ps.setInt(4, usuarioSeleccionado.getId());
            } else {
                ps.setString(4, password);
                ps.setInt(5, usuarioSeleccionado.getId());
            }
            ps.executeUpdate();
        }
    }

    private void cargarUsuarios() {
        usuarios.clear();
        if (usuario == null) {
            return;
        }

        String sql = puedeGestionarUsuarios()
                ? "SELECT id, nombre, rol, rutaFoto FROM usuarios ORDER BY nombre"
                : "SELECT id, nombre, rol, rutaFoto FROM usuarios WHERE id = ? ORDER BY nombre";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (!puedeGestionarUsuarios() && usuario != null) {
                ps.setInt(1, usuario.getId());
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                usuarios.add(new Usuario(
                        rs.getInt("id"),
                        rs.getString("nombre"),
                        leerRol(rs.getString("rol")),
                        rs.getString("rutaFoto")
                ));
            }
            if (!puedeGestionarUsuarios() && !usuarios.isEmpty()) {
                tblUsuarios.getSelectionModel().selectFirst();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            mostrarMensaje("Error al cargar usuarios");
        }
    }

    private void cargarUsuarioEnFormulario(Usuario usuarioEditar) {
        usuarioSeleccionado = usuarioEditar;
        txtNombre.setText(usuarioEditar.getNombre());
        comboRol.setValue(usuarioEditar.getRol().name());
        comboRol.setDisable(!puedeGestionarUsuarios());
        txtPassword.clear();
        rutaFotoSeleccionada = usuarioEditar.getRutaFoto() == null ? "" : usuarioEditar.getRutaFoto();
        cargarImagen(rutaFotoSeleccionada);
        btnGuardar.setText("Actualizar");
    }

    private void limpiarFormulario() {
        usuarioSeleccionado = null;
        tblUsuarios.getSelectionModel().clearSelection();
        txtNombre.clear();
        comboRol.getSelectionModel().clearSelection();
        comboRol.setDisable(false);
        txtPassword.clear();
        rutaFotoSeleccionada = "";
        imgFotoPerfil.setImage(null);
        btnGuardar.setText("Registrar");
    }

    private void cargarImagen(String rutaFoto) {
        if (rutaFoto == null || rutaFoto.isBlank()) {
            imgFotoPerfil.setImage(null);
            return;
        }

        try {
            if (rutaFoto.startsWith("/")) {
                URL recurso = getClass().getResource(rutaFoto);
                if (recurso != null) {
                    imgFotoPerfil.setImage(new Image(recurso.toExternalForm()));
                    return;
                }
                File archivoRecurso = new File("src/main/resources" + rutaFoto);
                imgFotoPerfil.setImage(archivoRecurso.exists() ? new Image(archivoRecurso.toURI().toString()) : null);
            } else {
                imgFotoPerfil.setImage(new Image(new File(rutaFoto).toURI().toURL().toExternalForm()));
            }
        } catch (MalformedURLException e) {
            imgFotoPerfil.setImage(null);
        }
    }

    private void cargarImagenDesdeArchivo(File archivo) {
        try {
            imgFotoPerfil.setImage(new Image(archivo.toURI().toURL().toExternalForm()));
        } catch (MalformedURLException e) {
            imgFotoPerfil.setImage(null);
        }
    }

    private String obtenerRutaAvatar(File archivo) {
        File carpetaAvatares = new File(CARPETA_AVATARES);
        if (carpetaAvatares.exists()) {
            try {
                String carpeta = carpetaAvatares.getCanonicalPath();
                String seleccionado = archivo.getCanonicalPath();
                if (seleccionado.startsWith(carpeta + File.separator)) {
                    return RECURSO_AVATARES + archivo.getName();
                }
            } catch (Exception ignored) {
                // Si no se puede normalizar, se conserva la ruta absoluta.
            }
        }
        return archivo.getAbsolutePath();
    }

    private void actualizarUsuarioActivo(String nombre, String rolStr) {
        if (usuario == null || usuarioSeleccionado == null || usuario.getId() != usuarioSeleccionado.getId()) {
            return;
        }

        usuario.setNombre(nombre);
        usuario.setRol(leerRol(rolStr));
        usuario.setRutaFoto(rutaFotoSeleccionada.isBlank() ? null : rutaFotoSeleccionada);
        UsuarioSesion.setUsuario(usuario);
    }

    private Rol leerRol(String valor) {
        return Rol.valueOf(valor.toUpperCase());
    }

    private boolean puedeGestionarUsuarios() {
        return usuario != null && (usuario.getRol() == Rol.SUPERVISOR || usuario.getRol() == Rol.ADMIN);
    }

    private boolean puedeModificarSeleccionado() {
        return puedeGestionarUsuarios()
                || (usuario != null && usuarioSeleccionado != null && usuario.getId() == usuarioSeleccionado.getId());
    }

    private void mostrarMensaje(String mensaje) {
        lblMensaje.setText(mensaje);
        lblMensaje.setVisible(true);
    }
}
