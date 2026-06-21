package domain;

public class Usuario {
    private int id;
    private String nombre;
    private Rol rol;
    private String rutaFoto;
    private boolean controlMuestras;
    private boolean controlTotal;

    public Usuario(int id, String nombre, Rol rol) {
        this(id, nombre, rol, null);
    }

    public Usuario(int id, String nombre, Rol rol, String rutaFoto) {
        this(id, nombre, rol, rutaFoto, false, false);
    }

    public Usuario(int id, String nombre, Rol rol, String rutaFoto,
                   boolean controlMuestras, boolean controlTotal) {
        this.id = id;
        this.nombre = nombre;
        this.rol = rol;
        this.rutaFoto = rutaFoto;
        this.controlMuestras = controlMuestras;
        this.controlTotal = controlTotal;
    }

    // Getters y setters
    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public Rol getRol() { return rol; }
    public String getRutaFoto() { return rutaFoto; }
    public boolean isControlMuestras() { return controlMuestras; }
    public boolean isControlTotal() { return controlTotal; }

    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setRol(Rol rol) { this.rol = rol; }
    public void setRutaFoto(String rutaFoto) { this.rutaFoto = rutaFoto; }
    public void setControlMuestras(boolean controlMuestras) { this.controlMuestras = controlMuestras; }
    public void setControlTotal(boolean controlTotal) { this.controlTotal = controlTotal; }

    public boolean tieneControlTotal() {
        return rol == Rol.ADMIN || rol == Rol.SUPERVISOR || controlTotal;
    }

    public boolean puedeControlarMuestras() {
        return tieneControlTotal() || controlMuestras;
    }

    public boolean puedeAdministrarUsuarios() {
        return rol == Rol.ADMIN || rol == Rol.SUPERVISOR;
    }

    public boolean puedeFinalizarEnsayos() {
        return rol == Rol.TECNICO || rol == Rol.AUXILIAR || puedeControlarMuestras();
    }

    public boolean isControlMuestrasEfectivo() {
        return puedeControlarMuestras();
    }

    public boolean isControlTotalEfectivo() {
        return tieneControlTotal();
    }

    @Override
    public String toString() {
        return nombre;
    }
}
