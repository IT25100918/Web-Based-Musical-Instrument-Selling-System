package com.instrumentstore.model;

import java.sql.Date;

public class Delivery {
    private int deliveryId;
    private int orderId;
    private String deliveryAddress;
    private Date scheduledDate;
    private String status; // ASSIGNED, IN_TRANSIT, DELIVERED, FAILED
    private String notes;
    private boolean addressVerified;
    private String deliveryPerson;

    public boolean isAddressVerified() { return addressVerified; }
    public void setAddressVerified(boolean addressVerified) { this.addressVerified = addressVerified; }

    public int getDeliveryId() { return deliveryId; }
    public void setDeliveryId(int deliveryId) { this.deliveryId = deliveryId; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }

    public Date getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(Date scheduledDate) { this.scheduledDate = scheduledDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getDeliveryPerson() { return deliveryPerson; }
    public void setDeliveryPerson(String deliveryPerson) { this.deliveryPerson = deliveryPerson; }
}
