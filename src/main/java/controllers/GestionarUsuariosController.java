package controllers;

import db.Database;
import domain.Rol;
import domain.Usuario;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import utilities.ImageStorage;
import utilities.Navegacion;
import utilities.PasswordSecurity;
import utilities.UsuarioSesion;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

public class GestionarUsuariosController {

    @FXML private TableView<Usuario> tblUsuarios;
    @FXML private TableColumn<Usuario, Integer> colId;
    @FXML private TableColumn<Usuario, String> colNombre;
    @FXML private TableColumn<Usuario, String> colRol;
    @FXML private TableColumn<Usuario, String> colControlMuestras;
    @FXML private TableColumn<Usuario, String> colControlTotal;
    @FXML private TextField txtNombre;
    @FXML private ComboBox<String> comboRol;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblMensaje;
    @FXML private Label lblModoFormulario;
    @FXML private Label lblTituloFormulario;
    @FXML private Label lblDescripcionFormulario;
    @FXML private Label lblAyudaPassword;
    @FXML private Label lblTituloPagina;
    @FXML private Label lblSubtituloPagina;
    @FXML private Label lblCamposObligatorios;
    @FXML private ImageView imgFotoPerfil;
    @FXML private VBox pnlListadoUsuarios;
    @FXML private VBox pnlPermisosAdicionales;
    @FXML private Button btnSeleccionarImagen;
    @FXML private Button btnGuardar;
    @FXML private Button btnNuevo;
    @FXML private Button btnEliminar;
    @FXML private Button btnCancelarEdicion;
    @FXML private Button btnRestablecerPassword;
    @FXML private CheckBox chkControlMuestras;
    @FXML private CheckBox chkControlTotal;

    private final ObservableList<Usuario> usuarios = FXCollections.observableArrayList();
    private String rutaFotoSeleccionada = "";
    private Usuario usuarioSeleccionado;
    private Usuario usuario;

    @FXML
    public void initialize() {
        comboRol.setItems(FXCollections.observableArrayList("AUXILIAR", "TECNICO", "SUPERVISOR", "ADMIN", "LIDER"));
        imgFotoPerfil.setClip(new Circle(55, 55, 55));

        colId.setCellValueFactory(celda -> new ReadOnlyObjectWrapper<>(celda.getValue().getId()));
        colNombre.setCellValueFactory(celda -> new ReadOnlyStringWrapper(celda.getValue().getNombre()));
        colRol.setCellValueFactory(celda -> new ReadOnlyStringWrapper(rolVisible(celda.getValue().getRol())));
        colControlMuestras.setCellValueFactory(celda -> new ReadOnlyStringWrapper(
                celda.getValue().isControlMuestrasEfectivo() ? "Sí" : "No"));
        colControlTotal.setCellValueFactory(celda -> new ReadOnlyStringWrapper(
                celda.getValue().isControlTotalEfectivo() ? "Sí" : "No"));
        chkControlTotal.selectedProperty().addListener((obs, anterior, seleccionado) -> {
            if (seleccionado) {
                chkControlMuestras.setSelected(true);
            }
        });
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

        File carpetaInicial = ImageStorage.getUserAvatarsInitialDirectory(rutaFotoSeleccionada);
        if (carpetaInicial != null) {
            fileChooser.setInitialDirectory(carpetaInicial);
        }

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imagenes", "*.png", "*.jpg", "*.jpeg")
        );

        File selectedFile = fileChooser.showOpenDialog(btnSeleccionarImagen.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }

        try {
            rutaFotoSeleccionada = ImageStorage.copyUserAvatar(selectedFile);
            cargarImagen(rutaFotoSeleccionada);
        } catch (Exception e) {
            rutaFotoSeleccionada = "";
            imgFotoPerfil.setImage(null);
            mostrarMensaje("No se pudo copiar la foto de perfil a la carpeta compartida");
        }
    }

    @FXML
    void guardarUsuario() {
        if (!puedeModificarSeleccionado() && usuarioSeleccionado != null) {
            mostrarMensaje("Solo puede modificar su propio usuario");
            return;
        }

        if (!puedeGestionarUsuarios()) {
            actualizarPerfilPropio();
            return;
        }

        String nombre = txtNombre.getText() == null ? "" : txtNombre.getText().trim();
        String rolStr = comboRol.getValue();
        String password = txtPassword.getText() == null ? "" : txtPassword.getText();
        boolean controlMuestras = chkControlMuestras.isSelected();
        boolean controlTotal = chkControlTotal.isSelected();

        if (usuarioSeleccionado == null && !puedeGestionarUsuarios()) {
            mostrarMensaje("Solo el supervisor o el administrador pueden crear usuarios");
            return;
        }

        if (nombre.isEmpty() || rolStr == null || (usuarioSeleccionado == null && password.isEmpty())) {
            mostrarMensaje("Debe completar todos los campos");
            return;
        }
        if (!password.isEmpty() && password.length() < PasswordSecurity.MINIMUM_LENGTH) {
            mostrarMensaje("La clave debe tener al menos 4 caracteres");
            return;
        }

        Rol nuevoRol = leerRol(rolStr);
        boolean nuevoControlTotalEfectivo = controlTotal
                || nuevoRol == Rol.ADMIN
                || nuevoRol == Rol.SUPERVISOR;
        if (nuevoControlTotalEfectivo
                && (usuarioSeleccionado == null || !usuarioSeleccionado.tieneControlTotal())
                && !confirmarControlTotal(nombre)) {
            return;
        }

        try (Connection conn = Database.getConnection()) {
            if (usuarioSeleccionado == null) {
                registrarNuevoUsuario(conn, nombre, rolStr, password, controlMuestras, controlTotal);
                mostrarMensaje("Usuario registrado correctamente");
            } else {
                actualizarUsuario(conn, nombre, rolStr, password, controlMuestras, controlTotal);
                actualizarUsuarioActivo(nombre, rolStr, controlMuestras, controlTotal);
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
            mostrarMensaje("Solo el supervisor o el administrador pueden crear usuarios");
            return;
        }
        limpiarFormulario();
        ocultarMensaje();
    }

    @FXML
    void cancelarEdicion() {
        limpiarFormulario();
        ocultarMensaje();
    }

    @FXML
    void restablecerPassword() {
        if (!puedeGestionarUsuarios() || usuarioSeleccionado == null) {
            mostrarMensaje("Seleccione un usuario para restablecer su contraseña");
            return;
        }

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Restablecer contraseña");
        confirmacion.setHeaderText("Se generará una contraseña temporal");
        confirmacion.setContentText("El usuario " + usuarioSeleccionado.getNombre()
                + " tendrá que crear una contraseña nueva en su próximo inicio de sesión.");
        if (confirmacion.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
            return;
        }

        String passwordTemporal = PasswordSecurity.generateTemporaryPassword();
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE usuarios SET password=?, cambioPasswordObligatorio=1 WHERE id=?")) {
                    ps.setString(1, PasswordSecurity.hash(passwordTemporal));
                    ps.setInt(2, usuarioSeleccionado.getId());
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("El usuario seleccionado ya no existe");
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO restablecimientos_password "
                                + "(usuarioId, administradorId, usuarioNombre, administradorNombre) "
                                + "VALUES (?, ?, ?, ?)")) {
                    ps.setInt(1, usuarioSeleccionado.getId());
                    ps.setInt(2, usuario.getId());
                    ps.setString(3, usuarioSeleccionado.getNombre());
                    ps.setString(4, usuario.getNombre());
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            mostrarMensaje("No se pudo restablecer la contraseña");
            return;
        }

        TextField claveTemporal = new TextField(passwordTemporal);
        claveTemporal.setEditable(false);
        claveTemporal.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 16px; -fx-font-weight: bold;");
        Button copiar = new Button("Copiar contraseña");
        copiar.setOnAction(evento -> {
            ClipboardContent contenido = new ClipboardContent();
            contenido.putString(passwordTemporal);
            Clipboard.getSystemClipboard().setContent(contenido);
            copiar.setText("Copiada");
        });

        VBox contenido = new VBox(10,
                new Label("Entregue esta clave temporal al usuario:"),
                claveTemporal,
                copiar,
                new Label("La clave dejará de ser válida cuando el usuario establezca la nueva contraseña.")
        );
        ((Label) contenido.getChildren().get(3)).setWrapText(true);

        Alert resultado = new Alert(Alert.AlertType.INFORMATION);
        resultado.setTitle("Contraseña temporal generada");
        resultado.setHeaderText("Acceso restablecido para " + usuarioSeleccionado.getNombre());
        resultado.getDialogPane().setContent(contenido);
        resultado.showAndWait();
        mostrarMensaje("Contraseña temporal generada correctamente");
    }

    @FXML
    void eliminarUsuario() {
        if (!puedeGestionarUsuarios()) {
            mostrarMensaje("Solo el supervisor o el administrador pueden eliminar usuarios");
            return;
        }

        if (usuarioSeleccionado == null) {
            mostrarMensaje("Seleccione un usuario para eliminar");
            return;
        }

        if (usuario != null && usuario.getId() == usuarioSeleccionado.getId()) {
            mostrarMensaje("No puede eliminar el usuario con la sesión activa");
            return;
        }

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM usuarios WHERE id = ?")) {

            ps.setInt(1, usuarioSeleccionado.getId());
            int filas = ps.executeUpdate();
            mostrarMensaje(filas > 0 ? "Usuario eliminado correctamente" : "No se encontró el usuario");
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
        configurarPermisosFormulario();
        cargarUsuarios();
    }

    private void registrarNuevoUsuario(Connection conn, String nombre, String rolStr, String password,
                                       boolean controlMuestras, boolean controlTotal) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO usuarios (nombre, rol, password, rutaFoto, controlMuestras, controlTotal) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, nombre);
            ps.setString(2, rolStr);
            ps.setString(3, PasswordSecurity.hash(password));
            ps.setString(4, rutaFotoSeleccionada.isBlank() ? null : rutaFotoSeleccionada);
            ps.setBoolean(5, controlMuestras);
            ps.setBoolean(6, controlTotal);
            ps.executeUpdate();
        }
    }

    private void actualizarUsuario(Connection conn, String nombre, String rolStr, String password,
                                   boolean controlMuestras, boolean controlTotal) throws SQLException {
        String rutaFotoAnterior = usuarioSeleccionado.getRutaFoto();
        String rutaFotoNueva = rutaFotoSeleccionada.isBlank() ? null : rutaFotoSeleccionada;
        String sql = password.isEmpty()
                ? "UPDATE usuarios SET nombre=?, rol=?, rutaFoto=?, controlMuestras=?, controlTotal=? WHERE id=?"
                : "UPDATE usuarios SET nombre=?, rol=?, rutaFoto=?, controlMuestras=?, controlTotal=?, "
                + "password=?, cambioPasswordObligatorio=0 WHERE id=?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ps.setString(2, rolStr);
            ps.setString(3, rutaFotoNueva);
            ps.setBoolean(4, controlMuestras);
            ps.setBoolean(5, controlTotal);
            if (password.isEmpty()) {
                ps.setInt(6, usuarioSeleccionado.getId());
            } else {
                ps.setString(6, PasswordSecurity.hash(password));
                ps.setInt(7, usuarioSeleccionado.getId());
            }
            ps.executeUpdate();
        }
        eliminarAvatarAnteriorSiNoSeUsa(conn, rutaFotoAnterior, rutaFotoNueva);
    }

    private void cargarUsuarios() {
        usuarios.clear();
        if (usuario == null) {
            return;
        }

        String sql = puedeGestionarUsuarios()
                ? "SELECT id, nombre, rol, rutaFoto, controlMuestras, controlTotal FROM usuarios ORDER BY nombre"
                : "SELECT id, nombre, rol, rutaFoto, controlMuestras, controlTotal FROM usuarios WHERE id = ? ORDER BY nombre";

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
                        rs.getString("rutaFoto"),
                        rs.getBoolean("controlMuestras"),
                        rs.getBoolean("controlTotal")
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
        ocultarMensaje();
        txtNombre.setText(usuarioEditar.getNombre());
        comboRol.setValue(usuarioEditar.getRol().name());
        chkControlMuestras.setSelected(usuarioEditar.isControlMuestras());
        chkControlTotal.setSelected(usuarioEditar.isControlTotal());
        txtPassword.clear();
        rutaFotoSeleccionada = usuarioEditar.getRutaFoto() == null ? "" : usuarioEditar.getRutaFoto();
        cargarImagen(rutaFotoSeleccionada);
        configurarPermisosFormulario();
    }

    private void limpiarFormulario() {
        usuarioSeleccionado = null;
        tblUsuarios.getSelectionModel().clearSelection();
        txtNombre.clear();
        comboRol.getSelectionModel().clearSelection();
        chkControlMuestras.setSelected(false);
        chkControlTotal.setSelected(false);
        txtPassword.clear();
        rutaFotoSeleccionada = "";
        imgFotoPerfil.setImage(null);
        configurarPermisosFormulario();
    }

    private void cargarImagen(String rutaFoto) {
        String url = ImageStorage.resolveImageUrl(rutaFoto);
        if (url == null || url.isBlank()) {
            imgFotoPerfil.setImage(null);
            return;
        }

        Image image = new Image(url, false);
        imgFotoPerfil.setImage(image.isError() ? null : image);
    }

    private void actualizarUsuarioActivo(String nombre, String rolStr,
                                         boolean controlMuestras, boolean controlTotal) {
        if (usuario == null || usuarioSeleccionado == null || usuario.getId() != usuarioSeleccionado.getId()) {
            return;
        }

        usuario.setNombre(nombre);
        usuario.setRol(leerRol(rolStr));
        usuario.setRutaFoto(rutaFotoSeleccionada.isBlank() ? null : rutaFotoSeleccionada);
        usuario.setControlMuestras(controlMuestras);
        usuario.setControlTotal(controlTotal);
        UsuarioSesion.setUsuario(usuario);
    }

    private Rol leerRol(String valor) {
        return Rol.valueOf(valor.toUpperCase());
    }

    private boolean puedeGestionarUsuarios() {
        return usuario != null && usuario.puedeAdministrarUsuarios();
    }

    private boolean puedeModificarSeleccionado() {
        return puedeGestionarUsuarios()
                || (usuario != null && usuarioSeleccionado != null && usuario.getId() == usuarioSeleccionado.getId());
    }

    private void mostrarMensaje(String mensaje) {
        lblMensaje.setText(mensaje);
        lblMensaje.getStyleClass().removeAll("message-success", "message-error");
        String mensajeNormalizado = mensaje.toLowerCase(Locale.ROOT);
        if (mensajeNormalizado.contains("correctamente")) {
            lblMensaje.getStyleClass().add("message-success");
        } else {
            lblMensaje.getStyleClass().add("message-error");
        }
        lblMensaje.setManaged(true);
        lblMensaje.setVisible(true);
    }

    private void ocultarMensaje() {
        lblMensaje.setVisible(false);
        lblMensaje.setManaged(false);
    }

    private void actualizarPerfilPropio() {
        if (usuario == null || usuarioSeleccionado == null || usuario.getId() != usuarioSeleccionado.getId()) {
            mostrarMensaje("Solo puede modificar su propio perfil");
            return;
        }
        String password = txtPassword.getText();
        boolean cambiarPassword = password != null && !password.isBlank();
        if (cambiarPassword && password.length() < PasswordSecurity.MINIMUM_LENGTH) {
            mostrarMensaje("La clave debe tener al menos 4 caracteres");
            return;
        }
        String rutaFotoAnterior = usuarioSeleccionado.getRutaFoto();
        String rutaFoto = rutaFotoSeleccionada == null || rutaFotoSeleccionada.isBlank()
                ? null
                : rutaFotoSeleccionada;
        String sql = cambiarPassword
                ? "UPDATE usuarios SET rutaFoto=?, password=?, cambioPasswordObligatorio=0 WHERE id=?"
                : "UPDATE usuarios SET rutaFoto=? WHERE id=?";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rutaFoto);
            if (cambiarPassword) {
                ps.setString(2, PasswordSecurity.hash(password));
                ps.setInt(3, usuario.getId());
            } else {
                ps.setInt(2, usuario.getId());
            }
            ps.executeUpdate();
            eliminarAvatarAnteriorSiNoSeUsa(conn, rutaFotoAnterior, rutaFoto);

            usuario.setRutaFoto(rutaFoto);
            usuarioSeleccionado.setRutaFoto(rutaFoto);
            UsuarioSesion.setUsuario(usuario);
            txtPassword.clear();
            tblUsuarios.refresh();
            mostrarMensaje(cambiarPassword
                    ? "Foto y contraseña actualizadas correctamente"
                    : "Foto de perfil actualizada correctamente");
        } catch (SQLException e) {
            e.printStackTrace();
            mostrarMensaje("No se pudo actualizar el perfil");
        }
    }

    private void eliminarAvatarAnteriorSiNoSeUsa(Connection conn, String rutaAnterior, String rutaNueva) throws SQLException {
        if (rutaAnterior == null || rutaAnterior.isBlank() || rutaAnterior.equals(rutaNueva)) {
            return;
        }

        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM usuarios WHERE rutaFoto = ?")) {
            ps.setString(1, rutaAnterior);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    ImageStorage.deleteUserAvatarIfManaged(rutaAnterior);
                }
            }
        }
    }

    private boolean confirmarControlTotal(String nombre) {
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION);
        alerta.setTitle("Confirmar control total");
        alerta.setHeaderText("Está cediendo el control total de la aplicación");
        alerta.setContentText("El usuario " + nombre
                + " podrá controlar todas las muestras y sus procesos, pero no podrá crear, eliminar ni modificar otros usuarios. ¿Desea continuar?");
        return alerta.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void configurarPermisosFormulario() {
        boolean gestor = puedeGestionarUsuarios();
        pnlListadoUsuarios.setVisible(gestor);
        pnlListadoUsuarios.setManaged(gestor);
        pnlPermisosAdicionales.setVisible(gestor);
        pnlPermisosAdicionales.setManaged(gestor);
        lblTituloPagina.setText(gestor ? "Gestión de usuarios" : "Mi perfil");
        lblSubtituloPagina.setText(gestor
                ? "Administra accesos, roles y permisos del equipo de laboratorio."
                : "Consulta tu información y actualiza tu foto o contraseña.");
        lblCamposObligatorios.setVisible(gestor);
        lblCamposObligatorios.setManaged(gestor);

        txtNombre.setDisable(!gestor);
        comboRol.setDisable(!gestor);
        btnSeleccionarImagen.setDisable(false);
        chkControlMuestras.setDisable(!gestor);
        chkControlTotal.setDisable(!gestor);
        actualizarModoFormulario();
    }

    private void actualizarModoFormulario() {
        boolean gestor = puedeGestionarUsuarios();
        boolean editando = usuarioSeleccionado != null;
        boolean perfilPropio = editando && !gestor;

        lblModoFormulario.getStyleClass().removeAll("mode-edit", "mode-profile");
        if (perfilPropio) {
            lblModoFormulario.setText("MI PERFIL");
            lblModoFormulario.getStyleClass().add("mode-profile");
            lblTituloFormulario.setText("Actualizar mi perfil");
            lblDescripcionFormulario.setText("Puedes cambiar tu foto o establecer una nueva contraseña.");
            lblAyudaPassword.setText("Déjala vacía si no deseas cambiarla.");
            btnGuardar.setText("Guardar perfil");
        } else if (editando) {
            lblModoFormulario.setText("EDITANDO");
            lblModoFormulario.getStyleClass().add("mode-edit");
            lblTituloFormulario.setText("Editar usuario");
            lblDescripcionFormulario.setText(
                    "Estás modificando la cuenta de " + usuarioSeleccionado.getNombre() + ".");
            lblAyudaPassword.setText("Déjala vacía para conservar la contraseña actual.");
            btnGuardar.setText("Guardar cambios");
        } else {
            lblModoFormulario.setText("NUEVO USUARIO");
            lblTituloFormulario.setText("Crear usuario");
            lblDescripcionFormulario.setText("Completa los datos para registrar un nuevo acceso.");
            lblAyudaPassword.setText("Obligatoria para crear un usuario nuevo.");
            btnGuardar.setText("Registrar usuario");
        }

        btnNuevo.setVisible(gestor);
        btnNuevo.setManaged(gestor);
        btnNuevo.setDisable(!gestor);

        boolean mostrarAccionesEdicion = gestor && editando;
        btnCancelarEdicion.setVisible(mostrarAccionesEdicion);
        btnCancelarEdicion.setManaged(mostrarAccionesEdicion);
        btnEliminar.setVisible(mostrarAccionesEdicion);
        btnEliminar.setManaged(mostrarAccionesEdicion);
        btnEliminar.setDisable(!mostrarAccionesEdicion
                || (usuario != null && usuario.getId() == usuarioSeleccionado.getId()));
        btnRestablecerPassword.setVisible(mostrarAccionesEdicion);
        btnRestablecerPassword.setManaged(mostrarAccionesEdicion);
        btnRestablecerPassword.setDisable(!mostrarAccionesEdicion);
    }

    private String rolVisible(Rol rol) {
        return switch (rol) {
            case AUXILIAR -> "Auxiliar";
            case TECNICO -> "Técnico";
            case SUPERVISOR -> "Supervisor";
            case ADMIN -> "Administrador";
            case LIDER -> "Líder";
        };
    }
}
