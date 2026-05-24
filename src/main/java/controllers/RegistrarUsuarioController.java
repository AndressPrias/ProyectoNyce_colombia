package controllers;

import db.Database;
import domain.Rol;
import domain.Usuario;
import javafx.collections.FXCollections;
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
import java.sql.SQLException;

public class RegistrarUsuarioController {

    @FXML private TextField txtNombre;
    @FXML private ComboBox<String> comboRol;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblMensaje;
    @FXML private ImageView imgFotoPerfil;
    @FXML private Button btnSeleccionarImagen;

    private String rutaFotoSeleccionada = "";
    private Usuario usuario;


    @FXML
    public void initialize() {
        comboRol.setItems(FXCollections.observableArrayList("AUXILIAR", "TECNICO", "SUPERVISOR"));
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
        if (usuario == null || !(usuario.getRol() == Rol.SUPERVISOR || usuario.getRol() == Rol.ADMIN)) {
            lblMensaje.setText("Solo el Supervisor o Admin puede registrar usuarios");
            lblMensaje.setVisible(true);
            return;
        }

        // Obtener datos de los campos
        String nombre = txtNombre.getText();
        String rolStr = comboRol.getValue();

        if(nombre.isEmpty() || rolStr == null){
            lblMensaje.setText("Debe completar todos los campos");
            lblMensaje.setVisible(true);
            return;
        }

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO usuarios (nombre, rol, rutaFoto) VALUES (?, ?, ?)")) {

            ps.setString(1, nombre);
            ps.setString(2, rolStr);
            ps.setString(3, rutaFotoSeleccionada.isBlank() ? null : rutaFotoSeleccionada);
            ps.executeUpdate();

            lblMensaje.setText("Usuario registrado correctamente");
            lblMensaje.setVisible(true);

            // Limpiar campos
            txtNombre.clear();
            comboRol.getSelectionModel().clearSelection();
            txtPassword.clear();
            rutaFotoSeleccionada = "";
            imgFotoPerfil.setImage(null);

        } catch (SQLException e) {
            e.printStackTrace();
            lblMensaje.setText("Error al registrar usuario");
            lblMensaje.setVisible(true);
        }
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
