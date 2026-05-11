package featurelabs.creev.product.infrastructure;

import featurelabs.creev.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

}
