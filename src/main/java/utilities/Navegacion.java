package utilities;

import controllers.ControladorBaseController;
import controllers.BuscarMuestrasController;
import controllers.CargarBaseDatosController;
import controllers.GestionarUsuariosController;
import controllers.MenuLateralController;
import controllers.MenuPrincipalController;
import controllers.RegistrarMuestraController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public final class Navegacion {

    private static ControladorBaseController shell;
    private static MenuLateralController menuLateral;

    private Navegacion() {}

    public static void registrarShell(ControladorBaseController controlador) {
        shell = controlador;
    }

    public static void registrarMenuLateral(MenuLateralController controlador) {
        menuLateral = controlador;
    }

    private static void marcarSeccionActiva(SeccionApp seccion) {
        if (menuLateral != null) {
            menuLateral.marcarActivo(seccion);
        }
    }

    public static void irInicio() {
        cargar(Paths.MENU_PRINCIPAL, "Inicio", controller -> {
            if (controller instanceof MenuPrincipalController c) {
                c.setUsuario(UsuarioSesion.getUsuario());
            }
        });
        marcarSeccionActiva(SeccionApp.INICIO);
    }

    public static void irRegistrarMuestra() {
        cargar(Paths.REGISTRAR_MUESTRA, "Registrar Muestra", controller -> {
            if (controller instanceof RegistrarMuestraController c) {
                c.setUsuario(UsuarioSesion.getUsuario());
            }
        });
        marcarSeccionActiva(SeccionApp.REGISTRAR_MUESTRA);
    }

    public static void irRegistrarUsuario() {
        cargar(Paths.GESTIONAR_USUARIOS, "Gestionar Usuarios", controller -> {
            if (controller instanceof GestionarUsuariosController c) {
                c.setUsuario(UsuarioSesion.getUsuario());
            }
        });
        marcarSeccionActiva(SeccionApp.GESTIONAR_USUARIOS);
    }

    public static void irBuscarMuestras() {
        cargar(Paths.BUSCAR_MUESTRAS, "Buscar Muestras", controller -> {
            if (controller instanceof BuscarMuestrasController c) {
                c.setUsuario(UsuarioSesion.getUsuario());
            }
        });
        marcarSeccionActiva(SeccionApp.BUSCAR_MUESTRAS);
    }

    public static void irCargarBaseDatos() {
        cargar(Paths.CARGAR_BASE_DATOS, "Cargar Base de Datos", controller -> {
            if (controller instanceof CargarBaseDatosController c) {
                c.setUsuario(UsuarioSesion.getUsuario());
            }
        });
        marcarSeccionActiva(SeccionApp.CARGAR_BASE_DATOS);
    }

    public static void cargar(String fxmlPath, String tituloVentana, ConfiguradorVista configurador) {
        if (shell == null) {
            System.err.println("AppShell no inicializado. No se puede navegar a: " + fxmlPath);
            return;
        }
        shell.cargarVista(fxmlPath, tituloVentana, configurador);
    }

    public static void cerrarSesion(Stage stage) {
        UsuarioSesion.clear();
        shell = null;
        menuLateral = null;
        try {
            FXMLLoader loader = new FXMLLoader(Navegacion.class.getResource(Paths.LOGIN));
            Parent root = loader.load();
            stage.setScene(new Scene(root));
            stage.setTitle("Login NYCE");
            stage.setMaximized(false);
            stage.sizeToScene();
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FunctionalInterface
    public interface ConfiguradorVista {
        void configurar(Object controller) throws Exception;
    }
}
