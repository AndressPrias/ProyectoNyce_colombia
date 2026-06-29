package application;

/**
 * Punto de entrada para el JAR ejecutable.
 *
 * Esta clase no hereda de JavaFX Application, lo que evita que el lanzador de
 * Java exija un module-path externo cuando JavaFX ya está incluido en el JAR.
 */
public final class Launcher {

    private Launcher() {}

    public static void main(String[] args) {
        App.main(args);
    }
}
