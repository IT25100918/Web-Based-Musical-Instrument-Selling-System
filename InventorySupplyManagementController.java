package com.instrumentstore.controller;

import com.instrumentstore.dao.ProductDAO;
import com.instrumentstore.dao.PurchaseOrderDAO;
import com.instrumentstore.dao.SupplierDAO;
import com.instrumentstore.model.Product;
import com.instrumentstore.model.Supplier;
import com.instrumentstore.service.InventoryService;
import com.instrumentstore.service.BusinessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Inventory and Supply Management: this is ONE function covering stock
 * levels, the suppliers stock is restocked from, and the purchase orders
 * that connect the two. It has three sides:
 *
 *  - Inventory monitoring: view all stock levels, flag low-stock items
 *    (hardcoded threshold of 5 units for simplicity), manually correct a
 *    stock count, and the inventory/stock-movement report (PBI-24).
 *  - Suppliers: list/add/edit/delete suppliers and see their performance.
 *  - Purchase orders: request restock from a supplier, track a PO through
 *    its lifecycle - when marked RECEIVED, stock is automatically added
 *    (once only) via InventoryService.
 */
@Controller
public class InventorySupplyManagementController {

    private static final int LOW_STOCK_THRESHOLD = 5;

    private final ProductDAO productDAO;
    private final SupplierDAO supplierDAO;
    private final PurchaseOrderDAO purchaseOrderDAO;
    private final InventoryService inventoryService;

    public InventorySupplyManagementController(ProductDAO productDAO, SupplierDAO supplierDAO,
                                                 PurchaseOrderDAO purchaseOrderDAO, InventoryService inventoryService) {
        this.productDAO = productDAO;
        this.supplierDAO = supplierDAO;
        this.purchaseOrderDAO = purchaseOrderDAO;
        this.inventoryService = inventoryService;
    }

    // ============================================================
    // Inventory monitoring
    // ============================================================

    // GET /admin/inventory -> monitor all stock levels, flag low-stock items
    @GetMapping("/admin/inventory")
    public String monitorInventory(Model model) {
        List<Product> products = productDAO.getAllProducts();
        model.addAttribute("products", products);
        model.addAttribute("threshold", LOW_STOCK_THRESHOLD);
        return "admin-inventory";
    }

    // POST /admin/inventory/update-stock -> manually correct a stock count
    @PostMapping("/admin/inventory/update-stock")
    public String updateStock(@RequestParam int productId, @RequestParam int newQuantity) {
        if (newQuantity >= 0) { // negative stock makes no sense (and the form's min="0" can be bypassed)
            productDAO.updateStock(productId, newQuantity);
        }
        return "redirect:/admin/inventory";
    }

    // GET /admin/reports/inventory -> stock movement / inventory report (PBI-24)
    @GetMapping("/admin/reports/inventory")
    public String inventoryReport(Model model) {
        model.addAttribute("inventoryRows", productDAO.getInventoryReport());
        return "admin-inventory-report";
    }

    // ============================================================
    // Suppliers
    // ============================================================

    // GET /admin/suppliers -> list all suppliers + performance report
    @GetMapping("/admin/suppliers")
    public String listSuppliers(Model model) {
        model.addAttribute("suppliers", supplierDAO.getAllSuppliers());
        model.addAttribute("performance", supplierDAO.getSupplierPerformance());
        return "admin-suppliers";
    }

    // GET /admin/suppliers/new -> empty add form
    @GetMapping("/admin/suppliers/new")
    public String showAddSupplierForm(Model model) {
        model.addAttribute("supplier", new Supplier());
        return "supplier-form";
    }

    // GET /admin/suppliers/edit/{id} -> pre-filled edit form
    @GetMapping("/admin/suppliers/edit/{id}")
    public String showEditSupplierForm(@PathVariable int id, Model model) {
        Supplier supplier = supplierDAO.getSupplierById(id);
        if (supplier == null) {
            return "redirect:/admin/suppliers";
        }
        model.addAttribute("supplier", supplier);
        return "supplier-form";
    }

    // POST /admin/suppliers -> save (add or update)
    @PostMapping("/admin/suppliers")
    public String saveSupplier(@RequestParam(required = false) Integer supplierId,
                                @RequestParam String name,
                                @RequestParam(required = false) String contactEmail,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) String address,
                                Model model) {

        Supplier supplier = new Supplier();
        if (supplierId != null) {
            supplier.setSupplierId(supplierId);
        }
        supplier.setName(name == null ? "" : name.trim());
        supplier.setContactEmail(contactEmail);
        supplier.setPhone(phone);
        supplier.setAddress(address);

        // Column sizes in the suppliers table (see database.sql) - a longer value, or a blank
        // required name, would otherwise fail with a database error (HTTP 500) instead of a message.
        String error = null;
        if (supplier.getName().isEmpty() || supplier.getName().length() > 150) {
            error = "Supplier name is required (max 150 characters).";
        } else if (contactEmail != null && contactEmail.length() > 100) {
            error = "Contact email is too long (max 100 characters).";
        } else if (phone != null && phone.length() > 20) {
            error = "Phone is too long (max 20 characters).";
        } else if (address != null && address.length() > 255) {
            error = "Address is too long (max 255 characters).";
        }
        if (error != null) {
            model.addAttribute("error", error);
            model.addAttribute("supplier", supplier);
            return "supplier-form";
        }

        if (supplierId != null) {
            supplierDAO.updateSupplier(supplier);
        } else {
            supplierDAO.addSupplier(supplier);
        }
        return "redirect:/admin/suppliers";
    }

    // POST /admin/suppliers/delete/{id}
    @PostMapping("/admin/suppliers/delete/{id}")
    public String deleteSupplier(@PathVariable int id, RedirectAttributes redirectAttributes) {
        if (!supplierDAO.deleteSupplier(id)) {
            redirectAttributes.addFlashAttribute("error",
                    "This supplier can't be deleted because it has purchase orders.");
        }
        return "redirect:/admin/suppliers";
    }

    // ============================================================
    // Purchase orders
    // ============================================================

    // GET /admin/purchase-orders -> list all purchase orders
    @GetMapping("/admin/purchase-orders")
    public String listPurchaseOrders(Model model) {
        model.addAttribute("purchaseOrders", purchaseOrderDAO.getAllPurchaseOrders());
        return "admin-purchase-orders";
    }

    // GET /admin/purchase-orders/new -> form to request a new PO, optionally pre-selecting a product
    // (e.g. from the "Request Restock" quick-link on the low-stock inventory view)
    @GetMapping("/admin/purchase-orders/new")
    public String showCreatePurchaseOrderForm(@RequestParam(required = false) Integer productId, Model model) {
        model.addAttribute("suppliers", supplierDAO.getAllSuppliers());
        model.addAttribute("products", productDAO.getAllProducts());
        model.addAttribute("preselectedProductId", productId);
        return "purchase-order-form";
    }

    // POST /admin/purchase-orders -> create a new purchase order request
    @PostMapping("/admin/purchase-orders")
    public String createPurchaseOrder(@RequestParam int supplierId,
                                       @RequestParam int productId,
                                       @RequestParam int quantity,
                                       RedirectAttributes redirectAttributes) {
        if (quantity <= 0) {
            redirectAttributes.addFlashAttribute("error", "Quantity must be at least 1.");
            return "redirect:/admin/purchase-orders";
        }
        if (supplierDAO.getSupplierById(supplierId) == null || productDAO.getProductById(productId) == null) {
            redirectAttributes.addFlashAttribute("error", "Unknown supplier or product.");
            return "redirect:/admin/purchase-orders";
        }
        purchaseOrderDAO.createPurchaseOrder(supplierId, productId, quantity);
        return "redirect:/admin/purchase-orders";
    }

    // POST /admin/purchase-orders/{id}/status -> track a PO through its lifecycle.
    // When marked RECEIVED, the ordered quantity is automatically added to product stock (once only -
    // see InventoryService).
    @PostMapping("/admin/purchase-orders/{id}/status")
    public String updatePurchaseOrderStatus(@PathVariable int id, @RequestParam String status,
                                             RedirectAttributes redirectAttributes) {
        try {
            inventoryService.changePurchaseOrderStatus(id, status);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/purchase-orders";
    }
}
