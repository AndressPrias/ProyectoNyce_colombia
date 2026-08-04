package domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Muestra {
    private int id;
    private String codigoInterno;
    private String rotuloCliente;
    private String nombreCliente;
    private String referencia;
    private String descripcion;
    private int cantidad = 1;
    private String marca;
    private Estado estado;
    private String ubicacion;
    private String observacionAlmacenamiento;
    private Usuario custodio;
    private Usuario tecnico;
    private Usuario responsableAlmacenamiento;
    private LocalDate fechaRecepcion;
    private String rutaFoto;
    private String idCarga;
    private List<ReferenciaDocumento> informes = new ArrayList<>();
    private List<ReferenciaDocumento> cotizaciones = new ArrayList<>();
    private String remision;

    public String getNombreCliente() {
        return nombreCliente;
    }

    public void setNombreCliente(String nombreCliente) {
        this.nombreCliente = nombreCliente;
    }

    public Muestra() {
        this.id = id;
        this.codigoInterno = codigoInterno;
        this.rotuloCliente = rotuloCliente;
        this.nombreCliente = nombreCliente;
        this.descripcion = descripcion;
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
        this.cantidad = Math.max(1, cantidad);
    }

    public String getMarca() {
        return marca;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    public String getReferencia() {
        return referencia;
    }

    public void setReferencia(String referencia) {
        this.referencia = referencia;
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
    public String getObservacionAlmacenamiento() { return observacionAlmacenamiento; }
    public Usuario getCustodio() { return custodio; }
    public Usuario getTecnico() { return tecnico; }
    public Usuario getResponsableAlmacenamiento() { return responsableAlmacenamiento; }
    public String getIdCarga() { return idCarga; }
    public List<ReferenciaDocumento> getInformes() { return List.copyOf(informes); }
    public List<ReferenciaDocumento> getCotizaciones() { return List.copyOf(cotizaciones); }
    public String getInformesTexto() { return unirReferencias(informes); }
    public String getCotizacionesTexto() { return unirReferencias(cotizaciones); }
    public String getRemision() { return remision; }

    public void setEstado(Estado estado) { this.estado = estado; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }
    public void setObservacionAlmacenamiento(String observacionAlmacenamiento) { this.observacionAlmacenamiento = observacionAlmacenamiento; }
    public void setCustodio(Usuario custodio) { this.custodio = custodio; }
    public void setTecnico(Usuario tecnico) { this.tecnico = tecnico; }
    public void setResponsableAlmacenamiento(Usuario responsableAlmacenamiento) { this.responsableAlmacenamiento = responsableAlmacenamiento; }
    public void setIdCarga(String idCarga) { this.idCarga = idCarga; }
    public void setInformes(List<ReferenciaDocumento> informes) {
        this.informes = informes == null ? new ArrayList<>() : new ArrayList<>(informes);
    }
    public void setCotizaciones(List<ReferenciaDocumento> cotizaciones) {
        this.cotizaciones = cotizaciones == null ? new ArrayList<>() : new ArrayList<>(cotizaciones);
    }
    public void setRemision(String remision) { this.remision = remision; }

    private String unirReferencias(List<ReferenciaDocumento> referencias) {
        return referencias.stream().map(ReferenciaDocumento::numero).collect(Collectors.joining(" / "));
    }



}

