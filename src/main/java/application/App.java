package application;

import db.Database;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import utilities.AppConfig;
import utilities.Paths;
import utilities.WebDatabaseSyncService;

public class App extends Application {

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) throws Exception {
        // Seleccionar la carpeta compartida en el primer arranque y luego inicializar SQLite.
        AppConfig.ensureStorageFolderSelected(stage);
        Database.init();
        WebDatabaseSyncService.start();

        FXMLLoader loader = new FXMLLoader(getClass().getResource(Paths.LOGIN));
        Parent root = loader.load();
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("Login NYCE");
        stage.setResizable(false);
        stage.show();
        stage.centerOnScreen();
    }

    @Override
    public void stop() {
        WebDatabaseSyncService.stop();
    }
}
