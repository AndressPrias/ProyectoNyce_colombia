package controllers;

import db.Database;
import domain.Rol;
import domain.Usuario;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import utilities.AppWindow;
import utilities.PasswordSecurity;
import utilities.Paths;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

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
        String password = txtPassword.getText() == null ? "" : txtPassword.getText();

        if (nombre.isEmpty() || password.isEmpty()) {
            lblMensaje.setText("Debe completar todos los campos");
            lblMensaje.setVisible(true);
            return;
        }

        ResultadoAutenticacion autenticacion = consultarUsuario(nombre, password);

        if (autenticacion != null) {
            if (autenticacion.passwordLegacy()) {
                actualizarHashLegacy(autenticacion.usuario().getId(), password);
            }
            if (autenticacion.cambioObligatorio()
                    && !solicitarCambioObligatorio(autenticacion.usuario(), password)) {
                lblMensaje.setText("Debe establecer una contraseña nueva para continuar");
                lblMensaje.setVisible(true);
                return;
            }

            lblMensaje.setText("Inicio de sesión correcto");
            lblMensaje.setVisible(true);

            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(Paths.APP_SHELL));
                Parent root = loader.load();

                ControladorBaseController shell = loader.getController();
                shell.iniciarSesion(autenticacion.usuario());

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

    @FXML
    void mostrarAyudaRecuperacion() {
        Alert alerta = new Alert(Alert.AlertType.INFORMATION);
        alerta.setTitle("Recuperar acceso");
        alerta.setHeaderText("Solicite una contraseña temporal");
        alerta.setContentText("Un administrador o supervisor puede restablecer su contraseña "
                + "desde Gestión de usuarios. Al iniciar sesión con la clave temporal, "
                + "el sistema le pedirá crear una contraseña nueva.");
        alerta.showAndWait();
    }

    private ResultadoAutenticacion consultarUsuario(String nombre, String password) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, nombre, rol, password, rutaFoto, controlMuestras, controlTotal, "
                             + "cambioPasswordObligatorio "
                             + "FROM usuarios WHERE LOWER(nombre) = LOWER(?)")) {

            ps.setString(1, nombre);
            ResultSet rs = ps.executeQuery();

            if (rs.next() && PasswordSecurity.verify(password, rs.getString("password"))) {
                int id = rs.getInt("id");
                String rolStr = rs.getString("rol");
                String rutaFoto = rs.getString("rutaFoto");
                Rol rol = Rol.valueOf(rolStr.toUpperCase());
                Usuario encontrado = new Usuario(
                        id,
                        rs.getString("nombre"),
                        rol,
                        rutaFoto,
                        rs.getBoolean("controlMuestras"),
                        rs.getBoolean("controlTotal")
                );
                return new ResultadoAutenticacion(
                        encontrado,
                        rs.getBoolean("cambioPasswordObligatorio"),
                        PasswordSecurity.needsUpgrade(rs.getString("password"))
                );
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean solicitarCambioObligatorio(Usuario usuario, String passwordTemporal) {
        Dialog<String> dialogo = new Dialog<>();
        dialogo.setTitle("Crear contraseña nueva");
        dialogo.setHeaderText("La contraseña temporal debe reemplazarse antes de continuar");
        dialogo.initOwner(txtPassword.getScene().getWindow());

        PasswordField nueva = new PasswordField();
        nueva.setPromptText("Mínimo 8 caracteres");
        PasswordField confirmacion = new PasswordField();
        confirmacion.setPromptText("Repita la contraseña");
        Label error = new Label();
        error.setStyle("-fx-text-fill: #c62828;");
        error.setWrapText(true);

        GridPane contenido = new GridPane();
        contenido.setHgap(12);
        contenido.setVgap(10);
        contenido.add(new Label("Contraseña nueva:"), 0, 0);
        contenido.add(nueva, 1, 0);
        contenido.add(new Label("Confirmar contraseña:"), 0, 1);
        contenido.add(confirmacion, 1, 1);
        contenido.add(error, 0, 2, 2, 1);

        ButtonType confirmar = new ButtonType("Cambiar y continuar", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(confirmar, ButtonType.CANCEL);
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().lookupButton(confirmar).addEventFilter(ActionEvent.ACTION, evento -> {
            String valor = nueva.getText() == null ? "" : nueva.getText();
            if (valor.length() < 8) {
                error.setText("La contraseña debe tener al menos 8 caracteres.");
                evento.consume();
            } else if (!valor.equals(confirmacion.getText())) {
                error.setText("Las contraseñas no coinciden.");
                evento.consume();
            } else if (valor.equals(passwordTemporal)) {
                error.setText("La contraseña nueva debe ser diferente a la temporal.");
                evento.consume();
            }
        });
        dialogo.setResultConverter(boton -> boton == confirmar ? nueva.getText() : null);

        Optional<String> resultado = dialogo.showAndWait();
        if (resultado.isEmpty()) {
            return false;
        }

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE usuarios SET password=?, cambioPasswordObligatorio=0 WHERE id=?")) {
            ps.setString(1, PasswordSecurity.hash(resultado.get()));
            ps.setInt(2, usuario.getId());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            e.printStackTrace();
            lblMensaje.setText("No se pudo guardar la contraseña nueva");
            lblMensaje.setVisible(true);
            return false;
        }
    }

    private void actualizarHashLegacy(int usuarioId, String password) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE usuarios SET password=? WHERE id=?")) {
            ps.setString(1, PasswordSecurity.hash(password));
            ps.setInt(2, usuarioId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private record ResultadoAutenticacion(
            Usuario usuario,
            boolean cambioObligatorio,
            boolean passwordLegacy
    ) {}
}
