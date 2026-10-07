package com.instrumentstore.controller;

import com.instrumentstore.dao.DeliveryDAO;
import com.instrumentstore.dao.OrderDAO;
import com.instrumentstore.model.Delivery;
import com.instrumentstore.model.Order;
import com.instrumentstore.patterns.observer.delivery.DeliveryStatusSubject;
import com.instrumentstore.service.StatusRules;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.Date;
import java.util.Set;

/**
 * Delivery Management: this is its own function - assign a delivery
 * person/date to an order, track a delivery through its lifecycle
 * (ASSIGNED/IN_TRANSIT/DELIVERED/FAILED), and notify the customer by
 * email as status changes.
 *
 * Observer pattern: after a change this controller only calls
 * {@link DeliveryStatusSubject#deliveryChanged}; the subject tells its observers
 * (customer email, console log) - the controller no longer knows who is listening.
 */
@Controller
@RequestMapping("/admin/deliveries")
public class AdminDeliveryController {

    private static final Set<String> VALID_STATUSES = Set.of("ASSIGNED", "IN_TRANSIT", "DELIVERED", "FAILED");

    private final DeliveryDAO deliveryDAO;
    private final OrderDAO orderDAO;
    private final DeliveryStatusSubject deliveryStatusSubject;

    public AdminDeliveryController(DeliveryDAO deliveryDAO, OrderDAO orderDAO, DeliveryStatusSubject deliveryStatusSubject) {
        this.deliveryDAO = deliveryDAO;
        this.orderDAO = orderDAO;
        this.deliveryStatusSubject = deliveryStatusSubject;
    }

    // Parses the yyyy-MM-dd value of an <input type="date">; blank -> null. Throws IllegalArgumentException if malformed.
    private Date parseDate(String value) {
        return (value != null && !value.isBlank()) ? Date.valueOf(value.trim()) : null;
    }

    // GET /admin/deliveries -> list all assigned deliveries
    @GetMapping
    public String listDeliveries(Model model) {
        model.addAttribute("deliveries", deliveryDAO.getAllDeliveries());
        return "admin-deliveries";
    }

    // GET /admin/deliveries/new -> pick an order awaiting delivery assignment
    @GetMapping("/new")
    public String showAssignForm(Model model) {
        model.addAttribute("orders", deliveryDAO.getOrdersAwaitingDelivery());
        return "delivery-form";
    }

    // POST /admin/deliveries -> assign a delivery (and delivery person) to an order
    @PostMapping
    public String assignDelivery(@RequestParam int orderId,
                                  @RequestParam String deliveryAddress,
                                  @RequestParam(required = false) String scheduledDate,
                                  @RequestParam(required = false) String deliveryPerson,
                                  RedirectAttributes redirectAttributes) {
        Date date;
        try {
            date = parseDate(scheduledDate);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "Please enter a valid scheduled date.");
            return "redirect:/admin/deliveries/new";
        }
        if (date != null && date.toLocalDate().isBefore(java.time.LocalDate.now())) {
            redirectAttributes.addFlashAttribute("error", "The scheduled date can't be in the past.");
            return "redirect:/admin/deliveries/new";
        }

        deliveryAddress = deliveryAddress.trim();
        Order order = orderDAO.getOrderById(orderId);
        if (order == null) {
            redirectAttributes.addFlashAttribute("error", "Order not found.");
            return "redirect:/admin/deliveries/new";
        }
        if ("CANCELLED".equals(order.getStatus()) || "PENDING".equals(order.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "Only paid, non-cancelled orders can be assigned for delivery.");
            return "redirect:/admin/deliveries/new";
        }
        if (deliveryAddress.isEmpty() || deliveryAddress.length() > 255) {
            redirectAttributes.addFlashAttribute("error", "Delivery address is required (max 255 characters).");
            return "redirect:/admin/deliveries/new";
        }
        if (deliveryPerson != null && deliveryPerson.length() > 100) {
            redirectAttributes.addFlashAttribute("error", "Delivery person name is too long (max 100 characters).");
            return "redirect:/admin/deliveries/new";
        }
        // One delivery per order (the "awaiting" list already hides assigned orders, but a stale form or
        // a hand-made request could still create a duplicate)
        if (deliveryDAO.getByOrderId(orderId) != null) {
            redirectAttributes.addFlashAttribute("error", "Order #" + orderId + " already has a delivery assigned.");
            return "redirect:/admin/deliveries/new";
        }

        deliveryDAO.createDelivery(orderId, deliveryAddress, date, deliveryPerson);

        deliveryStatusSubject.deliveryChanged(orderId, "Your order has been assigned for delivery",
                "Your order #" + orderId + " has been assigned for delivery to: " + deliveryAddress +
                (date != null ? "\nScheduled date: " + date : "") +
                (deliveryPerson != null && !deliveryPerson.isBlank() ? "\nDelivery person: " + deliveryPerson : ""));

        return "redirect:/admin/deliveries";
    }

    // POST /admin/deliveries/{id}/assign -> (re)assign the delivery person for an existing delivery
    @PostMapping("/{id}/assign")
    public String assignPerson(@PathVariable int id, @RequestParam String deliveryPerson,
                                RedirectAttributes redirectAttributes) {
        deliveryPerson = deliveryPerson.trim();
        if (deliveryPerson.isEmpty() || deliveryPerson.length() > 100) {
            redirectAttributes.addFlashAttribute("error", "Delivery person name is required (max 100 characters).");
            return "redirect:/admin/deliveries";
        }
        Delivery delivery = deliveryDAO.getById(id);
        if (delivery == null) {
            redirectAttributes.addFlashAttribute("error", "Delivery not found.");
            return "redirect:/admin/deliveries";
        }
        if ("DELIVERED".equals(delivery.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "A completed delivery can't be reassigned.");
            return "redirect:/admin/deliveries";
        }
        deliveryDAO.assignDeliveryPerson(id, deliveryPerson);
        return "redirect:/admin/deliveries";
    }

    // POST /admin/deliveries/{id}/status -> update delivery status/schedule (send notification, confirm, reschedule, or mark failed)
    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable int id,
                                @RequestParam String status,
                                @RequestParam(required = false) String notes,
                                @RequestParam(required = false) String scheduledDate,
                                RedirectAttributes redirectAttributes) {
        Delivery delivery = deliveryDAO.getById(id);
        if (delivery == null || !VALID_STATUSES.contains(status)) {
            redirectAttributes.addFlashAttribute("error", "Unknown delivery or status.");
            return "redirect:/admin/deliveries";
        }
        // Only the changes listed in StatusRules (e.g. DELIVERED is final, FAILED can only be re-assigned).
        // Saving the SAME status is still allowed so notes / the scheduled date can be edited.
        boolean sameStatus = status.equals(delivery.getStatus());
        if (sameStatus ? "DELIVERED".equals(status) : !StatusRules.canChangeDelivery(delivery.getStatus(), status)) {
            redirectAttributes.addFlashAttribute("error",
                    "A delivery can't change from " + delivery.getStatus() + " to " + status + ".");
            return "redirect:/admin/deliveries";
        }
        if (notes != null && notes.length() > 255) {
            redirectAttributes.addFlashAttribute("error", "Notes are too long (max 255 characters).");
            return "redirect:/admin/deliveries";
        }
        Date date;
        try {
            date = parseDate(scheduledDate);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "Please enter a valid scheduled date.");
            return "redirect:/admin/deliveries";
        }

        deliveryDAO.updateStatusAndSchedule(id, status, notes, date);

        // The order to sync/notify is taken from the delivery record itself. It used to come from a
        // hidden form field, so a tampered request could mark ANY order shipped/delivered.
        int orderId = delivery.getOrderId();
        Order order = orderDAO.getOrderById(orderId);

        // Keep the order's own status in sync with delivery outcomes (never touching cancelled orders)
        // (and only along the allowed order changes, so e.g. an unpaid order is never marked shipped)
        if (order != null) {
            String current = order.getStatus();
            // Delivered straight from Assigned: the order is still PROCESSING, so it passes through SHIPPED first
            if ("DELIVERED".equals(status) && "PROCESSING".equals(current)
                    && orderDAO.transitionStatus(orderId, "PROCESSING", "SHIPPED")) {
                current = "SHIPPED";
            }
            String target = "DELIVERED".equals(status) ? "DELIVERED" : "IN_TRANSIT".equals(status) ? "SHIPPED" : null;
            if (target != null && StatusRules.canChangeOrder(current, target)) {
                orderDAO.transitionStatus(orderId, current, target);
            }
        }

        String body = "Your order #" + orderId + " delivery status is now: " + status +
                (notes != null && !notes.isBlank() ? "\nNote: " + notes : "");
        deliveryStatusSubject.deliveryChanged(orderId, "Delivery update for order #" + orderId, body);

        return "redirect:/admin/deliveries";
    }

    // POST /admin/deliveries/delete/{id} -> remove a delivery record
    @PostMapping("/delete/{id}")
    public String deleteDelivery(@PathVariable int id, RedirectAttributes redirectAttributes) {
        Delivery delivery = deliveryDAO.getById(id);
        if (delivery == null) {
            return "redirect:/admin/deliveries";
        }
        // A completed delivery is a record that must stay
        if ("DELIVERED".equals(delivery.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "A completed delivery can't be deleted.");
            return "redirect:/admin/deliveries";
        }
        deliveryDAO.deleteDelivery(id);
        // The order was marked SHIPPED when the delivery went IN_TRANSIT; with no delivery any more it goes
        // back to PROCESSING so it shows up again in "orders awaiting delivery".
        orderDAO.transitionStatus(delivery.getOrderId(), "SHIPPED", "PROCESSING");
        return "redirect:/admin/deliveries";
    }

    // POST /admin/deliveries/{id}/verify-address -> admin manually confirms the address is correct
    @PostMapping("/{id}/verify-address")
    public String verifyAddress(@PathVariable int id) {
        deliveryDAO.setAddressVerified(id, true);
        return "redirect:/admin/deliveries";
    }
}
