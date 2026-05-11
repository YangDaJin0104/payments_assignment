package featurelabs.creev.product.domain;

import featurelabs.creev.common.error.BusinessException;
import featurelabs.creev.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_stocks")
public class ProductStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(nullable = false)
    private Integer totalStock;

    @Column(nullable = false)
    private Integer reservedStock;

    @Column(nullable = false)
    private Integer soldStock;

    protected ProductStock() {
    }

    private ProductStock(
            Long productId,
            Integer totalStock
    ) {
        this.productId = productId;
        this.totalStock = totalStock;
        this.reservedStock = 0;
        this.soldStock = 0;
    }

    public static ProductStock create(Long productId, Integer totalStock) {
        return new ProductStock(productId, totalStock);
    }

    public int availableStock() {
        return totalStock - reservedStock - soldStock;
    }

    public void reserve(int quantity) {
        validatePositiveQuantity(quantity);

        if (availableStock() < quantity) {
            throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
        }

        this.reservedStock += quantity;
    }

    public void confirm(int quantity) {
        validatePositiveQuantity(quantity);
        validateEnoughReservedStock(quantity);

        this.reservedStock -= quantity;
        this.soldStock += quantity;
    }

    public void release(int quantity) {
        validatePositiveQuantity(quantity);
        validateEnoughReservedStock(quantity);

        this.reservedStock -= quantity;
    }

    private void validatePositiveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_QUANTITY);
        }
    }

    private void validateEnoughReservedStock(int quantity) {
        if (this.reservedStock < quantity) {
            throw new BusinessException(ErrorCode.INVALID_STOCK_STATE);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getTotalStock() {
        return totalStock;
    }

    public Integer getReservedStock() {
        return reservedStock;
    }

    public Integer getSoldStock() {
        return soldStock;
    }
}
