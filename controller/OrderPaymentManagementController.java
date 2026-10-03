package com.instrumentstore.controller;

import com.instrumentstore.dao.DeliveryDAO;
import com.instrumentstore.dao.OrderDAO;
import com.instrumentstore.dao.PaymentDAO;
import com.instrumentstore.dao.PurchaseOrderDAO;
import com.instrumentstore.model.Order;
import com.instrumentstore.model.OrderItem;
import com.instrumentstore.model.Payment;
import com.instrumentstore.model.User;
import com.instrumentstore.service.OrderService;
import com.instrumentstore.service.payment.PaymentProcessor;
import com.instrumentstore.service.payment.PaymentProcessorFactory;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Order and Payment Management: this is ONE function covering an order's
 * whole lifecycle after checkout. It has two sides:
 *
 *  - Customer-facing: view own order history, view/cancel a pending order,
 *    pay for an order (Factory pattern picks the right PaymentProcessor for
 *    the chosen method - see the service.payment package; there's no real
 *    payment gateway wired in), view the receipt, view own payment history.
 *  - Admin/Order & Payment Manager side: view all orders and update status
 *    (processing/shipped/delivered/cancelled), view all payment records
 *    with reconciliation, change payment status, refund, delete.
 */
@Controller
public class OrderPaymentManagementController {

    private static final Set<String> VALID_ORDER_STATUSES = Set.of("PENDING", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED");
    private static final Set<String> VALID_PAYMENT_STATUSES = Set.of("PENDING", "SUCCESS", "FAILED", "REFUNDED");

    // Staff who may open any customer's order page (they work with orders/payments/deliveries/complaints)
    private static final Set<String> STAFF_ROLES_WITH_ORDER_ACCESS = Set.of(
            "ADMIN", "ORDER_PAYMENT_MANAGER", "DELIVERY_MANAGER", "CUSTOMER_RELATIONS_MANAGER");

    private final OrderDAO orderDAO;
    private final DeliveryDAO deliveryDAO;
    private final PaymentDAO paymentDAO;
    private final PurchaseOrderDAO purchaseOrderDAO;
    private final OrderService orderService;
    private final PaymentProcessorFactory paymentProcessorFactory;

    public OrderPaymentManagementController(OrderDAO orderDAO, DeliveryDAO deliveryDAO, PaymentDAO paymentDAO,
                                              PurchaseOrderDAO purchaseOrderDAO, OrderService orderService,
                                              PaymentProcessorFactory paymentProcessorFactory) {
        this.orderDAO = orderDAO;
        this.deliveryDAO = deliveryDAO;
        this.paymentDAO = paymentDAO;
        this.purchaseOrderDAO = purchaseOrderDAO;
        this.orderService = orderService;
        this.paymentProcessorFactory = paymentProcessorFactory;
    }

    private User currentUser(HttpSession session) {
        return (User) session.getAttribute("user");
    }

    // ============================================================
    // Customer-facing: orders
    // ============================================================

    // GET /orders -> customer's own order history
    @GetMapping("/orders")
    public String myOrders(HttpSession session, Model model) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("orders", orderDAO.getOrdersByUser(user.getUserId()));
        return "orders";
    }

    // GET /orders/{id} -> single order detail + status + delivery tracking (also the "order confirmation" page after checkout)
    @GetMapping("/orders/{id}")
    public String orderDetail(@PathVariable int id, HttpSession session, Model model) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        Order order = orderDAO.getOrderById(id);
        // Security check: customers can only view their own orders (staff who handle orders may view any)
        if (order == null || (order.getUserId() != user.getUserId() && !STAFF_ROLES_WITH_ORDER_ACCESS.contains(user.getRole()))) {
            return "redirect:/orders";
        }

        List<OrderItem> items = orderDAO.getOrderItems(id);
        model.addAttribute("order", order);
        model.addAttribute("items", items);
        model.addAttribute("delivery", deliveryDAO.getByOrderId(id));
        return "order-detail";
    }

    // POST /orders/{id}/cancel -> customer cancels their own pending order
    @PostMapping("/orders/{id}/cancel")
    public String cancelOrder(@PathVariable int id, HttpSession session) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        Order order = orderDAO.getOrderById(id);
        if (order != null && order.getUserId() == user.getUserId()) {
            // Only while still unpaid; also puts the reserved stock back
            orderService.cancelOrder(id, true);
        }
        return "redirect:/orders/" + id;
    }

    // ============================================================
    // Admin / Order & Payment Manager side: orders
    // ============================================================

    // GET /admin/orders -> all orders across all customers
    @GetMapping("/admin/orders")
    public String adminOrderList(Model model) {
        model.addAttribute("orders", orderDAO.getAllOrders());
        return "admin-orders";
    }

    // POST /admin/orders/{id}/status -> admin updates an order's status
    @PostMapping("/admin/orders/{id}/status")
    public String updateStatus(@PathVariable int id, @RequestParam String status,
                                RedirectAttributes redirectAttributes) {
        Order order = orderDAO.getOrderById(id);
        if (order == null || !VALID_ORDER_STATUSES.contains(status)) {
            redirectAttributes.addFlashAttribute("error", "Unknown order or status.");
            return "redirect:/admin/orders";
        }
        if (status.equals(order.getStatus())) {
            return "redirect:/admin/orders";
        }
        // A cancelled order has already given its stock back (and been refunded), so it is final.
        if ("CANCELLED".equals(order.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "Order #" + id + " is cancelled and can't be changed.");
            return "redirect:/admin/orders";
        }

        if ("CANCELLED".equals(status)) {
            orderService.cancelOrder(id, false); // restocks + refunds
        } else {
            orderDAO.updateStatus(id, status);
        }
        return "redirect:/admin/orders";
    }

    // ============================================================
    // Customer-facing: payment (simulated gateway - "processing" a
    // payment just simulates success and generates a fake transaction
    // reference; mention this simulation clearly in your report/viva)
    // ============================================================

    // GET /payment/{orderId} -> show payment method selection for a pending order
    @GetMapping("/payment/{orderId}")
    public String showPaymentPage(@PathVariable int orderId, HttpSession session, Model model) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        Order order = orderDAO.getOrderById(orderId);
        if (order == null || order.getUserId() != user.getUserId()) {
            return "redirect:/orders";
        }
        // Only an unpaid order can be paid; otherwise just show it
        if (!"PENDING".equals(order.getStatus())) {
            return "redirect:/orders/" + orderId;
        }

        model.addAttribute("order", order);
        return "payment";
    }

    // POST /payment/{orderId} -> simulate processing the payment
    @PostMapping("/payment/{orderId}")
    public String processPayment(@PathVariable int orderId,
                                  @RequestParam String paymentMethod,
                                  HttpSession session, Model model) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        Order order = orderDAO.getOrderById(orderId);
        if (order == null || order.getUserId() != user.getUserId()) {
            return "redirect:/orders";
        }

        // Factory pattern: look up the processor for this method rather than branching on the
        // string ourselves. An unknown/tampered method name throws instead of silently falling
        // through, so there's no need for a separate VALID_PAYMENT_METHODS check here.
        PaymentProcessor processor;
        try {
            processor = paymentProcessorFactory.getProcessor(paymentMethod);
        } catch (IllegalArgumentException e) {
            model.addAttribute("order", order);
            model.addAttribute("error", "Please choose a valid payment method.");
            return "payment";
        }

        String transactionRef = processor.process(order.getTotalAmount(), orderId);
        try {
            // One transaction: PENDING -> PROCESSING and the payment record. Fails if the order was
            // already paid or has been cancelled (which used to resurrect cancelled orders).
            orderService.payOrder(orderId, order.getTotalAmount(), processor.getMethodName(), transactionRef);
        } catch (OrderService.OrderException e) {
            return "redirect:/orders/" + orderId;
        }

        return "redirect:/payment/" + orderId + "/receipt";
    }

    // GET /payment/{orderId}/receipt -> view the receipt for a completed payment
    @GetMapping("/payment/{orderId}/receipt")
    public String showReceipt(@PathVariable int orderId, HttpSession session, Model model) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        Order order = orderDAO.getOrderById(orderId);
        Payment payment = paymentDAO.getByOrderId(orderId);
        if (order == null || payment == null || order.getUserId() != user.getUserId()) {
            return "redirect:/orders";
        }

        model.addAttribute("order", order);
        model.addAttribute("payment", payment);
        return "receipt";
    }

    // GET /payments -> customer's own payment history
    @GetMapping("/payments")
    public String paymentHistory(HttpSession session, Model model) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("payments", paymentDAO.getPaymentHistoryByUser(user.getUserId()));
        return "payment-history";
    }

    // ============================================================
    // Admin / Order & Payment Manager side: payments
    // ============================================================

    // GET /admin/payments -> all payment records, with reconciliation status against order totals
    @GetMapping("/admin/payments")
    public String adminPaymentList(Model model) {
        model.addAttribute("payments", paymentDAO.getAllPaymentsWithReconciliation());
        return "admin-payments";
    }

    // POST /admin/payments/{id}/status -> generic status change (SUCCESS, FAILED, REFUNDED)
    @PostMapping("/admin/payments/{id}/status")
    public String updatePaymentStatus(@PathVariable int id, @RequestParam String status) {
        if (VALID_PAYMENT_STATUSES.contains(status)) { // an unknown value would violate the DB CHECK constraint (HTTP 500)
            paymentDAO.updateStatus(id, status);
        }
        return "redirect:/admin/payments";
    }

    // POST /admin/payments/{id}/refund
    @PostMapping("/admin/payments/{id}/refund")
    public String refundPayment(@PathVariable int id) {
        paymentDAO.refund(id);
        return "redirect:/admin/payments";
    }

    // POST /admin/payments/{id}/delete -> remove an invalid record
    @PostMapping("/admin/payments/{id}/delete")
    public String deletePayment(@PathVariable int id) {
        paymentDAO.deletePayment(id);
        return "redirect:/admin/payments";
    }

    // ============================================================
    // Admin / Order & Payment Manager side: reporting
    // (the inventory/stock movement report lives with Inventory and
    // Supply Management instead - see InventorySupplyManagementController)
    // ============================================================

    // GET /admin/reports/sales -> order/sales performance report (PBI-20)
    @GetMapping("/admin/reports/sales")
    public String salesReport(Model model) {
        model.addAttribute("totalRevenue", orderDAO.getTotalRevenue());
        model.addAttribute("statusBreakdown", orderDAO.getOrderCountsByStatus());
        model.addAttribute("dailySales", orderDAO.getDailySales(30));
        return "admin-sales-report";
    }

    // GET /admin/reports/financial -> financial statement: revenue, refunds, estimated supplier costs (PBI-28)
    @GetMapping("/admin/reports/financial")
    public String financialStatement(Model model) {
        BigDecimal revenue = paymentDAO.getTotalByStatus("SUCCESS");
        BigDecimal refunds = paymentDAO.getTotalByStatus("REFUNDED");
        BigDecimal goodsReceivedValue = purchaseOrderDAO.getEstimatedReceivedGoodsValue();

        model.addAttribute("revenue", revenue);
        model.addAttribute("refunds", refunds);
        model.addAttribute("goodsReceivedValue", goodsReceivedValue);
        // "revenue" is the sum of payments still in SUCCESS status. A refunded payment is moved to
        // REFUNDED (leaving SUCCESS), so it is ALREADY excluded from revenue - subtracting refunds again
        // (as before) counted every refund twice. "refunds" is shown for information only.
        model.addAttribute("netPosition", revenue.subtract(goodsReceivedValue));
        return "admin-financial-report";
    }
}
