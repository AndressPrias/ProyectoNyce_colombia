package utilities;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Optional;

public final class AppDialog {

    private AppDialog() {
    }

    public static void showInformation(String title, String header, String message) {
        Dialog<ButtonType> dialog = create(title, header, message, DialogKind.INFO,
                new ButtonType("Entendido", ButtonBar.ButtonData.OK_DONE));
        dialog.showAndWait();
    }

    public static void showSuccess(String title, String header, String message) {
        Dialog<ButtonType> dialog = create(title, header, message, DialogKind.SUCCESS,
                new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE));
        dialog.showAndWait();
    }

    public static void showWarning(String title, String header, String message) {
        Dialog<ButtonType> dialog = create(title, header, message, DialogKind.WARNING,
                new ButtonType("Entendido", ButtonBar.ButtonData.OK_DONE));
        dialog.showAndWait();
    }

    public static void showError(String title, String header, String message) {
        Dialog<ButtonType> dialog = create(title, header, message, DialogKind.ERROR,
                new ButtonType("Entendido", ButtonBar.ButtonData.OK_DONE));
        dialog.showAndWait();
    }

    public static boolean confirmUpdate(String title, String header, String message) {
        ButtonType updateButton = new ButtonType("Actualizar ahora", ButtonBar.ButtonData.OK_DONE);
        ButtonType laterButton = new ButtonType("Después", ButtonBar.ButtonData.CANCEL_CLOSE);
        Dialog<ButtonType> dialog = create(title, header, message, DialogKind.UPDATE, updateButton, laterButton);
        Optional<ButtonType> result = dialog.showAndWait();
        return result.filter(updateButton::equals).isPresent();
    }

    private static Dialog<ButtonType> create(String title, String header, String message,
                                             DialogKind kind, ButtonType... buttons) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(buttons);
        pane.getStyleClass().addAll("app-dialog", kind.styleClass);
        applyStylesheet(pane);
        pane.setContent(createContent(header, message, kind));
        pane.setMinWidth(430);
        pane.setPrefWidth(460);

        for (ButtonType buttonType : buttons) {
            Node node = pane.lookupButton(buttonType);
            if (node instanceof Button button) {
                button.getStyleClass().add(buttonType.getButtonData().isCancelButton()
                        ? "app-dialog-secondary-button"
                        : "app-dialog-primary-button");
            }
        }

        configureIcon(dialog);
        return dialog;
    }

    private static VBox createContent(String header, String message, DialogKind kind) {
        VBox content = new VBox(14);
        content.setFillWidth(true);
        content.getStyleClass().add("app-dialog-content");

        HBox heading = new HBox(12);
        heading.setAlignment(Pos.CENTER_LEFT);

        Label symbol = new Label(kind.symbol);
        symbol.getStyleClass().add("app-dialog-symbol");

        VBox texts = new VBox(4);
        HBox.setHgrow(texts, Priority.ALWAYS);

        Label titleLabel = new Label(header);
        titleLabel.setWrapText(true);
        titleLabel.getStyleClass().add("app-dialog-title");

        Label subtitle = new Label(kind.subtitle);
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("app-dialog-subtitle");

        texts.getChildren().addAll(titleLabel, subtitle);

        ImageView logo = createLogo();
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        heading.getChildren().addAll(symbol, texts, spacer);
        if (logo != null) {
            heading.getChildren().add(logo);
        }

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(Double.MAX_VALUE);
        messageLabel.getStyleClass().add("app-dialog-message");
        VBox.setMargin(messageLabel, new Insets(2, 0, 0, 0));

        content.getChildren().addAll(heading, messageLabel);
        return content;
    }

    private static ImageView createLogo() {
        try {
            String resource = AppDialog.class.getResource("/icons/logoNycePng.png").toExternalForm();
            ImageView logo = new ImageView(new Image(resource));
            logo.setFitWidth(54);
            logo.setFitHeight(38);
            logo.setPreserveRatio(true);
            logo.getStyleClass().add("app-dialog-logo");
            return logo;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void applyStylesheet(DialogPane pane) {
        try {
            String css = AppDialog.class.getResource("/css/dialogs.css").toExternalForm();
            pane.getStylesheets().add(css);
        } catch (Exception ignored) {
            // The dialog remains usable if the stylesheet is unavailable.
        }
    }

    private static void configureIcon(Dialog<ButtonType> dialog) {
        try {
            String resource = AppDialog.class.getResource("/icons/logoNycePng.png").toExternalForm();
            Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
            stage.getIcons().add(new Image(resource));
        } catch (Exception ignored) {
            // Window icon is cosmetic only.
        }
    }

    private enum DialogKind {
        INFO("info-dialog", "i", "Notificación del sistema"),
        SUCCESS("success-dialog", "✓", "Proceso completado"),
        WARNING("warning-dialog", "!", "Proceso completado con observaciones"),
        UPDATE("update-dialog", "UP", "Nueva versión disponible"),
        ERROR("error-dialog", "!", "Se requiere revisión");

        private final String styleClass;
        private final String symbol;
        private final String subtitle;

        DialogKind(String styleClass, String symbol, String subtitle) {
            this.styleClass = styleClass;
            this.symbol = symbol;
            this.subtitle = subtitle;
        }
    }
}
