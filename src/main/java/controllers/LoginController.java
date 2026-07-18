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
import javafx.scene.image.Image;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import utilities.AppWindow;
import utilities.Paths;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginController {

    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblMensaje;
    /*@FXML private Circle circuloLogoIniciarSesion; // esto es un ejemplo de como importar un circulo*/

    @FXML
    public void initialize() {
        lblMensaje.setVisible(false);

        //ejemplo de como iniciar una imagen dentro de un circulo
        /*
        Image img = new Image(
                getClass().getResource("/icons/cerrar.png")
                        .toExternalForm()
                );
        circuloLogoIniciarSesion.setFill(
                new ImagePattern(img)
        );
         */

    }

    @FXML
    void iniciarSesion(ActionEvent event) {
        String nombre = txtUsuario.getText() == null ? "" : txtUsuario.getText().trim();
        String password = txtPassword.getText() == null ? "" : txtPassword.getText().trim();

        if (nombre.isEmpty() || password.isEmpty()) {
            lblMensaje.setText("Debe completar todos los campos");
            lblMensaje.setVisible(true);
            return;
        }

        Usuario usuarioLogueado = consultarUsuario(nombre, password);

        if (usuarioLogueado != null) {
            lblMensaje.setText("Inicio de sesión correcto");
            lblMensaje.setVisible(true);

            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(Paths.APP_SHELL));
                Parent root = loader.load();

                ControladorBaseController shell = loader.getController();
                shell.iniciarSesion(usuarioLogueado);

                Stage stage = (Stage) txtUsuario.getScene().getWindow();
                stage.setScene(new Scene(root));
                stage.setTitle("Sistema NYCE");
                AppWindow.ocuparAreaVisible(stage);

            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            lblMensaje.setText("Usuario o contraseña incorrectos");
            lblMensaje.setVisible(true);
        }
    }

    private Usuario consultarUsuario(String nombre, String password) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, nombre, rol, rutaFoto, controlMuestras, controlTotal " +
                             "FROM usuarios WHERE LOWER(nombre) = LOWER(?) AND password = ?")) {

            ps.setString(1, nombre);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int id = rs.getInt("id");
                String rolStr = rs.getString("rol");
                String rutaFoto = rs.getString("rutaFoto");
                Rol rol = Rol.valueOf(rolStr.toUpperCase());
                return new Usuario(
                        id,
                        nombre,
                        rol,
                        rutaFoto,
                        rs.getBoolean("controlMuestras"),
                        rs.getBoolean("controlTotal")
                );
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
