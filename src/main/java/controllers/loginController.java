package controllers;

import db.Database;
import domain.Rol;
import domain.Usuario;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import utilities.Paths;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class loginController {

    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblMensaje; // oculto por defecto en FXML

    @FXML
    public void initialize() {
        lblMensaje.setVisible(false); // Label no visible al inicio
    }

    @FXML
    void iniciarSesion(ActionEvent event) {
        String nombre = txtUsuario.getText();
        String password = txtPassword.getText(); // opcional si quieres validar

        if (nombre.isEmpty() || password.isEmpty()) {
            lblMensaje.setText("Debe completar todos los campos");
            lblMensaje.setVisible(true);
            return;
        }

        Usuario usuarioLogueado = consultarUsuario(nombre);

        if (usuarioLogueado != null) {
            lblMensaje.setText("Inicio de sesión correcto");
            lblMensaje.setVisible(true);

            try {
                // Cargar Menu Principal
                FXMLLoader loader = new FXMLLoader(getClass().getResource(Paths.MENU_PRINCIPAL));
                Parent root = loader.load();

                // Pasar usuario al menú
                controllers.MenuPrincipalController controller = loader.getController();

                controller.setUsuario(usuarioLogueado);

                Stage stage = new Stage();
                stage.setTitle("Menú Principal");
                stage.setScene(new Scene(root));
                stage.show();

                // Cerrar ventana de login
                ((Stage) txtUsuario.getScene().getWindow()).close();

            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            lblMensaje.setText("Usuario o contraseña incorrectos");
            lblMensaje.setVisible(true);
        }
    }

    // Método para buscar usuario en H2
    private Usuario consultarUsuario(String nombre) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, nombre, rol FROM usuarios WHERE nombre = ?")) {

            ps.setString(1, nombre);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int id = rs.getInt("id");
                String rolStr = rs.getString("rol");
                Rol rol = Rol.valueOf(rolStr);
                return new Usuario(id, nombre, rol);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}