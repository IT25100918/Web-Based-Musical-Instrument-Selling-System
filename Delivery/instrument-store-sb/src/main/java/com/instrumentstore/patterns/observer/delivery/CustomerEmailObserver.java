package com.instrumentstore.patterns.observer.delivery;

import com.instrumentstore.dao.OrderDAO;
import com.instrumentstore.dao.UserDAO;
import com.instrumentstore.model.Order;
import com.instrumentstore.model.User;
import com.instrumentstore.service.NotificationService;
import com.instrumentstore.patterns.observer.Observer;
import org.springframework.stereotype.Component;

/** ConcreteObserver: emails the customer who owns the order (this used to be a private method in the controller). */
@Component
public class CustomerEmailObserver implements Observer<DeliveryEvent> {

    private final OrderDAO orderDAO;
    private final UserDAO userDAO;
    private final NotificationService notificationService;

    public CustomerEmailObserver(OrderDAO orderDAO, UserDAO userDAO, NotificationService notificationService) {
        this.orderDAO = orderDAO;
        this.userDAO = userDAO;
        this.notificationService = notificationService;
    }

    @Override
    public void update(DeliveryEvent event) {
        Order order = orderDAO.getOrderById(event.orderId());
        if (order == null) return;

        User customer = userDAO.findById(order.getUserId());
        if (customer == null || customer.getEmail() == null) return;

        notificationService.sendEmail(customer.getEmail(), event.subject(), event.body());
    }
}
