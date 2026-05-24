package controllers;

import db.Database;
import domain.Rol;
import domain.Usuario;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import utilities.Navegacion;
import utilities.UsuarioSesion;

import java.io.File;
import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class RegistrarUsuarioController {

    @FXML private ComboBox<Usuario> comboUsuarioEditar;
    @FXML private TextField txtNombre;
    @FXML private ComboBox<String> comboRol;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblMensaje;
    @FXML private ImageView imgFotoPerfil;
    @FXML private Button btnSeleccionarImagen;
    @FXML private Button btnRegistrar;

    private String rutaFotoSeleccionada = "";
    private Usuario usuarioSeleccionado;
    private Usuario usuario;


    @FXML
    public void initialize() {
        comboRol.setItems(FXCollections.observableArrayList("AUXILIAR", "TECNICO", "SUPERVISOR"));
        cargarUsuarios();
        comboUsuarioEditar.getSelectionModel().selectedItemProperty().addListener((obs, anterior, seleccionado) -> {
            if (seleccionado != null) {
                cargarUsuarioEnFormulario(seleccionado);
            }
        });
    }

    @FXML
    void seleccionarImagen(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar foto de perfil");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imagenes", "*.png", "*.jpg", "*.jpeg")
        );

        File selectedFile = fileChooser.showOpenDialog(btnSeleccionarImagen.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }

        rutaFotoSeleccionada = selectedFile.getAbsolutePath();
        try {
            imgFotoPerfil.setImage(new Image(selectedFile.toURI().toURL().toExternalForm()));
        } catch (MalformedURLException e) {
            imgFotoPerfil.setImage(null);
        }
    }

    @FXML
    void registrarUsuario() {
        // Validar rol del usuario logueado
        if (!puedeGestionarUsuarios()) {
            mostrarMensaje("Solo el Supervisor o Admin puede registrar usuarios");
            return;
        }

        // Obtener datos de los campos
        String nombre = txtNombre.getText();
        String rolStr = comboRol.getValue();

        if(nombre.isEmpty() || rolStr == null){
            mostrarMensaje("Debe completar todos los campos");
            return;
        }

        try (Connection conn = Database.getConnection()) {
            if (usuarioSeleccionado == null) {
                registrarNuevoUsuario(conn, nombre, rolStr);
                lblMensaje.setText("Usuario registrado correctamente");
            } else {
                actualizarUsuario(conn, nombre, rolStr);
                lblMensaje.setText("Usuario actualizado correctamente");
            }
            lblMensaje.setVisible(true);
            limpiarFormulario();
            cargarUsuarios();

        } catch (SQLException e) {
            e.printStackTrace();
            mostrarMensaje("Error al guardar usuario");
        }
    }

    @FXML
    void nuevoUsuario() {
        limpiarFormulario();
        lblMensaje.setVisible(false);
    }

    @FXML
    void eliminarUsuario() {
        if (!puedeGestionarUsuarios()) {
            mostrarMensaje("Solo el Supervisor o Admin puede eliminar usuarios");
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

    private void registrarNuevoUsuario(Connection conn, String nombre, String rolStr) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO usuarios (nombre, rol, rutaFoto) VALUES (?, ?, ?)")) {
            ps.setString(1, nombre);
            ps.setString(2, rolStr);
            ps.setString(3, rutaFotoSeleccionada.isBlank() ? null : rutaFotoSeleccionada);
            ps.executeUpdate();
        }
    }

    private void actualizarUsuario(Connection conn, String nombre, String rolStr) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE usuarios SET nombre = ?, rol = ?, rutaFoto = ? WHERE id = ?")) {
            ps.setString(1, nombre);
            ps.setString(2, rolStr);
            ps.setString(3, rutaFotoSeleccionada.isBlank() ? null : rutaFotoSeleccionada);
            ps.setInt(4, usuarioSeleccionado.getId());
            ps.executeUpdate();
        }
    }

    private void cargarUsuarios() {
        ObservableList<Usuario> usuarios = FXCollections.observableArrayList();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, nombre, rol, rutaFoto FROM usuarios ORDER BY nombre");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                usuarios.add(new Usuario(
                        rs.getInt("id"),
                        rs.getString("nombre"),
                        leerRol(rs.getString("rol")),
                        rs.getString("rutaFoto")
                ));
            }
            comboUsuarioEditar.setItems(usuarios);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void cargarUsuarioEnFormulario(Usuario usuarioEditar) {
        usuarioSeleccionado = usuarioEditar;
        txtNombre.setText(usuarioEditar.getNombre());
        comboRol.setValue(usuarioEditar.getRol().name());
        rutaFotoSeleccionada = usuarioEditar.getRutaFoto() == null ? "" : usuarioEditar.getRutaFoto();
        cargarImagen(rutaFotoSeleccionada);
        btnRegistrar.setText("Actualizar");
    }

    private void limpiarFormulario() {
        usuarioSeleccionado = null;
        comboUsuarioEditar.getSelectionModel().clearSelection();
        txtNombre.clear();
        comboRol.getSelectionModel().clearSelection();
        txtPassword.clear();
        rutaFotoSeleccionada = "";
        imgFotoPerfil.setImage(null);
        btnRegistrar.setText("Registrar");
    }

    private void cargarImagen(String rutaFoto) {
        if (rutaFoto == null || rutaFoto.isBlank()) {
            imgFotoPerfil.setImage(null);
            return;
        }

        try {
            imgFotoPerfil.setImage(new Image(new File(rutaFoto).toURI().toURL().toExternalForm()));
        } catch (MalformedURLException e) {
            imgFotoPerfil.setImage(null);
        }
    }

    private Rol leerRol(String valor) {
        return Rol.valueOf(valor.toUpperCase());
    }

    private boolean puedeGestionarUsuarios() {
        return usuario != null && (usuario.getRol() == Rol.SUPERVISOR || usuario.getRol() == Rol.ADMIN);
    }

    private void mostrarMensaje(String mensaje) {
        lblMensaje.setText(mensaje);
        lblMensaje.setVisible(true);
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
        UsuarioSesion.setUsuario(usuario);
    }

    @FXML
    private Button btnSalir;

    @FXML
    void salir() {
        Navegacion.irInicio();
    }
}
