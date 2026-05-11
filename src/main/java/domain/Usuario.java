package domain;

public class Usuario {
    private int id;
    private String nombre;
    private Rol rol;

    public Usuario(int id, String nombre, Rol rol) {
        this.id = id;
        this.nombre = nombre;
        this.rol = rol;
    }

    // Getters y setters
    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public Rol getRol() { return rol; }

    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setRol(Rol rol) { this.rol = rol; }
}