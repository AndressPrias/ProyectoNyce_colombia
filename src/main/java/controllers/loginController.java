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
    @FXML private Label lblMensaje;

    @FXML
    public void initialize() {
        lblMensaje.setVisible(false);
    }

    @FXML
    void iniciarSesion(ActionEvent event) {
        String nombre = txtUsuario.getText();
        String password = txtPassword.getText();

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
                FXMLLoader loader = new FXMLLoader(getClass().getResource(Paths.APP_SHELL));
                Parent root = loader.load();

                AppShellController shell = loader.getController();
                shell.iniciarSesion(usuarioLogueado);

                Stage stage = (Stage) txtUsuario.getScene().getWindow();
                stage.setScene(new Scene(root));
                stage.setTitle("Sistema NYCE");
                stage.setMaximized(true);

            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            lblMensaje.setText("Usuario o contraseña incorrectos");
            lblMensaje.setVisible(true);
        }
    }

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
