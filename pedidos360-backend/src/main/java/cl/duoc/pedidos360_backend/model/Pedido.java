package cl.duoc.pedidos360_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad Pedido para el backoffice de Pedidos 360.
 * Mapeada a la tabla 'pedidos' mediante JPA/Hibernate.
 */
@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "producto", nullable = false)
    private String producto;

    /**
     * Mapeado como columna TEXT / longitud amplia (2048 caracteres) para soportar
     * URLs largas provenientes de AWS S3 con prefijos de seguridad o Unsplash.
     */
    @Column(name = "image_url", length = 2048, columnDefinition = "TEXT", nullable = false)
    private String imageUrl;

    @Column(name = "cantidad", nullable = false)
    private int cantidad;

    @Column(name = "stock", nullable = false)
    private int stock;

    @Column(name = "estado", nullable = false, length = 50)
    private String estado;

    // Constructor por defecto requerido por JPA
    public Pedido() {
    }

    // Constructor completo con ID
    public Pedido(Long id, String producto, String imageUrl, int cantidad, int stock, String estado) {
        this.id = id;
        this.producto = producto;
        this.imageUrl = imageUrl;
        this.cantidad = cantidad;
        this.stock = stock;
        this.estado = estado;
    }

    // Constructor de conveniencia sin ID (para creacion de nuevos registros)
    public Pedido(String producto, String imageUrl, int cantidad, int stock, String estado) {
        this.producto = producto;
        this.imageUrl = imageUrl;
        this.cantidad = cantidad;
        this.stock = stock;
        this.estado = estado;
    }

    // Getters y Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProducto() {
        return producto;
    }

    public void setProducto(String producto) {
        this.producto = producto;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    @Override
    public String toString() {
        return "Pedido{" +
                "id=" + id +
                ", producto='" + producto + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", cantidad=" + cantidad +
                ", stock=" + stock +
                ", estado='" + estado + '\'' +
                '}';
    }
}
