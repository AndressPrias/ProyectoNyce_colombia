package domain;

public record ReferenciaDocumento(String numero, int anio) {
    public ReferenciaDocumento {
        numero = numero == null ? "" : numero.trim();
        if (numero.isEmpty()) {
            throw new IllegalArgumentException("El número no puede estar vacío");
        }
        if (anio < 2000 || anio > 9999) {
            throw new IllegalArgumentException("El año debe contener 4 dígitos");
        }
    }
}
