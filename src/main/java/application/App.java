package application;

import db.Database;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import utilities.Paths;

public class App extends Application {

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) throws Exception {
        // Inicializar DB antes de abrir login
        Database.init();

        FXMLLoader loader = new FXMLLoader(getClass().getResource(Paths.LOGIN));
        AnchorPane pane = loader.load();
        Scene scene = new Scene(pane);
        stage.setScene(scene);
        stage.setTitle("Login NYCE");
        stage.show();
    }
}