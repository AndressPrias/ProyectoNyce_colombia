package domain;

public record ReferenciaDocumento(String numero, int anio) {
    public ReferenciaDocumento {
        numero = numero == null ? "" : numero.trim();
        if (!numero.matches("\\d{4}")) {
            throw new IllegalArgumentException("El número debe contener exactamente 4 dígitos");
        }
        if (anio < 2000 || anio > 9999) {
            throw new IllegalArgumentException("El año debe contener 4 dígitos");
        }
    }

    public String formatoEdicion() {
        return numero + "/" + anio;
    }
}
