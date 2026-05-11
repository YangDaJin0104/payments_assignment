package featurelabs.creev.order.infrastructure;

import featurelabs.creev.order.domain.Order;
import featurelabs.creev.order.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByUserIdAndIdempotencyKey(
            Long userId,
            String idempotencyKey
    );

    boolean existsByUserIdAndProductIdAndStatusIn(
            Long userId,
            Long productId,
            Collection<OrderStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from OrderEntity o
            where o.id = :orderId
            """)
    Optional<Order> findByIdForUpdate(
            @Param("orderId") Long orderId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from OrderEntity o
            where o.status = :status
              and o.reservationExpiresAt < :now
            order by o.reservationExpiresAt asc, o.id asc
            """)
    List<Order> findExpiredPendingOrdersForUpdate(
            @Param("status") OrderStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
