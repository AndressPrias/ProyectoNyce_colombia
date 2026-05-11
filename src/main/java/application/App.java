package application;

import controllers.loginController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import utilities.Paths;

public class App extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {

        // -------------------------------------
        // 1️⃣ Cargar pantalla de Login primero
        // -------------------------------------
        FXMLLoader loader = new FXMLLoader(getClass().getResource(Paths.LOGIN));
        AnchorPane pane = loader.load();

        Scene scene = new Scene(pane);
        stage.setScene(scene);
        stage.setTitle("Login NYCE");

        // Mostrar ventana de login
        stage.show();

        // -------------------------------------
        // Nota: Cuando el usuario haga login exitoso
        // LoginController abrirá la ventana de registro de muestras
        // y cerrará esta ventana automáticamente
        // -------------------------------------
    }
}