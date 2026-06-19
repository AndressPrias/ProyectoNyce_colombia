package domain;

import java.text.Normalizer;
import java.util.Locale;

public enum Estado {
    ALMACENADO("Almacenado"),
    REALIZAR_DISPOSICION_FINAL("Realizar disposición final"),
    EN_CUSTODIA("En custodia"),
    EN_CURSO("En curso"),
    LABORATORIO_EXTERNO("Laboratorio Externo"),
    ENVIADO("Enviado"),
    DESTRUCCION("Destrucción");

    private final String nombreVisible;

    Estado(String nombreVisible) {
        this.nombreVisible = nombreVisible;
    }

    public static Estado desdeTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            return EN_CUSTODIA;
        }

        String normalizado = normalizar(valor);
        for (Estado estado : values()) {
            if (normalizar(estado.name()).equals(normalizado)
                    || normalizar(estado.nombreVisible).equals(normalizado)) {
                return estado;
            }
        }

        return switch (normalizado) {
            case "RECIBIDA" -> EN_CUSTODIA;
            case "EN_ALMACENAMIENTO" -> ALMACENADO;
            case "EN_ENSAYO", "EN_REVISION" -> EN_CURSO;
            case "FINALIZADA" -> REALIZAR_DISPOSICION_FINAL;
            case "DEVUELTA" -> ENVIADO;
            case "DESTRUIDA" -> DESTRUCCION;
            default -> throw new IllegalArgumentException("Estado no valido: " + valor);
        };
    }

    private static String normalizar(String valor) {
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[\\s-]+", "_");
    }

    @Override
    public String toString() {
        return nombreVisible;
    }
}
