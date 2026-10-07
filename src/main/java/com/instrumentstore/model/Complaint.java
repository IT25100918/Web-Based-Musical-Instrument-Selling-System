package com.instrumentstore.model;

import java.sql.Timestamp;

/**
 * Represents a row in the `complaints` table - a customer complaint, return
 * request, or support query, optionally linked to an order. Part of Customer
 * Relations Management (PBI-04, PBI-08).
 */
public class Complaint {
    private int complaintId;
    private int userId;
    private String customerName; // joined from users.full_name, for admin display
    private Integer orderId;     // nullable - not every complaint is order-specific
    private String subject;
    private String message;
    private String status; // OPEN, IN_PROGRESS, RESOLVED
    private String adminResponse;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public int getComplaintId() { return complaintId; }
    public void setComplaintId(int complaintId) { this.complaintId = complaintId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAdminResponse() { return adminResponse; }
    public void setAdminResponse(String adminResponse) { this.adminResponse = adminResponse; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
