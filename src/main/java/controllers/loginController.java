package controllers;

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

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class loginController {

    @FXML
    private Label lblMensaje;

    @FXML
    void click(ActionEvent event) {

    }

    @FXML
    private PasswordField txtPassword;

    @FXML
    private TextField txtUsuario;

    @FXML
    void iniciarSesion(ActionEvent event) {

        String nombre = txtUsuario.getText();
        String password = txtPassword.getText(); // si tu BD tuviera password

        if (nombre.isEmpty() || password.isEmpty()) {
            lblMensaje.setText("Debe completar todos los campos");
            return;
        }

        // Consultar usuario en la BD
        Usuario usuarioLogueado = consultarUsuario(nombre);

        if (usuarioLogueado != null) {
            lblMensaje.setText("Inicio de sesión correcto");

            // Abrir ventana de registro de muestras
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/resources/CrudMuestra.fxml"));
                Parent root = loader.load();

                // Pasar usuario logueado al controlador de registro
                controllers.CrudMuestraController controller = loader.getController();
                controller.setUsuario(usuarioLogueado);

                Stage stage = new Stage();
                stage.setTitle("Registro de Muestras");
                stage.setScene(new Scene(root));
                stage.show();

                // Cerrar ventana de login
                ((Stage) txtUsuario.getScene().getWindow()).close();

            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            lblMensaje.setText("Usuario o contraseña incorrectos");
        }
    }

    // Método para buscar usuario en H2
    private Usuario consultarUsuario(String nombre) {
        try (Connection conn = db.Database.getConnection();
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
