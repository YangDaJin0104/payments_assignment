package featurelabs.creev.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private Integer maxPurchaseQuantity;

    protected Product() {
    }

    private Product(
            String name,
            Long price,
            Integer maxPurchaseQuantity
    ) {
        this.name = name;
        this.price = price;
        this.maxPurchaseQuantity = maxPurchaseQuantity;
    }

    public static Product create(String name, Long price, Integer maxPurchaseQuantity) {
        return new Product(name, price, maxPurchaseQuantity);
    }

    public boolean exceedsMaxPurchaseQuantity(int quantity) {
        return quantity > maxPurchaseQuantity;
    }

    public Long calculateAmount(int quantity) {
        return price * quantity;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getPrice() {
        return price;
    }

    public Integer getMaxPurchaseQuantity() {
        return maxPurchaseQuantity;
    }
}
