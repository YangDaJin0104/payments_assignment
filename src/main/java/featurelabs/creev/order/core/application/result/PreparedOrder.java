package featurelabs.creev.order.core.application.result;

import featurelabs.creev.order.core.domain.Order;

public record PreparedOrder(
        Order order,
        boolean newOrder
) {

    public static PreparedOrder newOrder(Order order) {
        return new PreparedOrder(order, true);
    }

    public static PreparedOrder existingOrder(Order order) {
        return new PreparedOrder(order, false);
    }

    public Long orderId() {
        return order.getId();
    }

    public Long amount() {
        return order.getAmount();
    }
}
