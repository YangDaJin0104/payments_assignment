package featurelabs.creev.product.infrastructure;

import featurelabs.creev.product.domain.ProductStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {

    Optional<ProductStock> findByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from ProductStock s
            where s.productId = :productId
            """)
    Optional<ProductStock> findByProductIdForUpdate(
            @Param("productId") Long productId
    );
}
