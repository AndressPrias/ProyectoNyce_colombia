package domain;

public class Usuario {
    private int id;
    private String nombre;
    private Rol rol;
    private String rutaFoto;

    public Usuario(int id, String nombre, Rol rol) {
        this(id, nombre, rol, null);
    }

    public Usuario(int id, String nombre, Rol rol, String rutaFoto) {
        this.id = id;
        this.nombre = nombre;
        this.rol = rol;
        this.rutaFoto = rutaFoto;
    }

    // Getters y setters
    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public Rol getRol() { return rol; }
    public String getRutaFoto() { return rutaFoto; }

    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setRol(Rol rol) { this.rol = rol; }
    public void setRutaFoto(String rutaFoto) { this.rutaFoto = rutaFoto; }

    @Override
    public String toString() {
        return nombre;
    }
}
