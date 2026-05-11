package domain;

import java.time.LocalDate;

public class Muestra {
    private int id;
    private String codigoInterno;
    private String rotuloCliente;
    private String descripcion;
    private int cantidad;
    private Estado estado;
    private String ubicacion; // A1, B2, etc.
    private Usuario custodio;
    private LocalDate fechaRecepcion;
    private String rutaFoto;

    public Muestra() {
        this.id = id;
        this.codigoInterno = codigoInterno;
        this.rotuloCliente = rotuloCliente;
        this.descripcion = descripcion;
        this.cantidad = cantidad;
        this.estado = estado;
        this.ubicacion = ubicacion;
        this.custodio = custodio;
        this.fechaRecepcion = fechaRecepcion;
        this.rutaFoto = rutaFoto;
    }

    // Getters y setters metodo Muestra

    public void setId(int id) {
        this.id = id;
    }

    public void setCodigoInterno(String codigoInterno) {
        this.codigoInterno = codigoInterno;
    }

    public String getRotuloCliente() {
        return rotuloCliente;
    }

    public void setRotuloCliente(String rotuloCliente) {
        this.rotuloCliente = rotuloCliente;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public LocalDate getFechaRecepcion() {
        return fechaRecepcion;
    }

    public void setFechaRecepcion(LocalDate fechaRecepcion) {
        this.fechaRecepcion = fechaRecepcion;
    }

    public String getRutaFoto() {
        return rutaFoto;
    }

    public void setRutaFoto(String rutaFoto) {
        this.rutaFoto = rutaFoto;
    }

    // Getters y setters
    public int getId() { return id; }
    public String getCodigoInterno() { return codigoInterno; }
    public Estado getEstado() { return estado; }
    public String getUbicacion() { return ubicacion; }
    public Usuario getCustodio() { return custodio; }

    public void setEstado(Estado estado) { this.estado = estado; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }
    public void setCustodio(Usuario custodio) { this.custodio = custodio; }


}

