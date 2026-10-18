package com.instrumentstore.service;

import com.instrumentstore.dao.ProductDAO;
import com.instrumentstore.dao.PurchaseOrderDAO;
import com.instrumentstore.model.Product;
import com.instrumentstore.model.PurchaseOrder;
import com.instrumentstore.patterns.observer.inventory.StockLevelSubject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Purchase-order lifecycle. Receiving a purchase order adds stock and changes the PO's status; those
 * two writes are done in one transaction so they can't get out of step.
 */
@Service
public class InventoryService {

    private static final Set<String> VALID_STATUSES = Set.of("REQUESTED", "APPROVED", "RECEIVED", "CANCELLED");

    private final PurchaseOrderDAO purchaseOrderDAO;
    private final ProductDAO productDAO;
    private final StockLevelSubject stockLevelSubject; // Observer pattern: told whenever stock changes

    public InventoryService(PurchaseOrderDAO purchaseOrderDAO, ProductDAO productDAO, StockLevelSubject stockLevelSubject) {
        this.purchaseOrderDAO = purchaseOrderDAO;
        this.productDAO = productDAO;
        this.stockLevelSubject = stockLevelSubject;
    }

    /**
     * @throws BusinessException with a user-facing message if the change isn't allowed
     */
    @Transactional
    public void changePurchaseOrderStatus(int purchaseOrderId, String newStatus) {
        if (newStatus == null || !VALID_STATUSES.contains(newStatus)) {
            throw new BusinessException("Unknown purchase order status.");
        }
        PurchaseOrder po = purchaseOrderDAO.getById(purchaseOrderId);
        if (po == null) {
            throw new BusinessException("Purchase order not found.");
        }
        String current = po.getStatus();
        if (current.equals(newStatus)) {
            return;
        }
        // RECEIVED and CANCELLED are final. Without this, RECEIVED -> APPROVED -> RECEIVED added the
        // stock a second time, and RECEIVED -> CANCELLED left stock that was never really delivered.
        if ("RECEIVED".equals(current) || "CANCELLED".equals(current)) {
            throw new BusinessException("Purchase order #" + purchaseOrderId + " is already " + current + " and can't be changed.");
        }
        if ("RECEIVED".equals(newStatus)) {
            productDAO.increaseStock(po.getProductId(), po.getQuantity());
            Product updated = productDAO.getProductById(po.getProductId());
            if (updated != null) {
                stockLevelSubject.stockChanged(updated.getProductId(), updated.getName(), updated.getStockQuantity());
            }
        }
        purchaseOrderDAO.updateStatus(purchaseOrderId, newStatus);
    }
}
