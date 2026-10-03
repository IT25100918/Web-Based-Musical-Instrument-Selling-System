package com.instrumentstore.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Guards every /admin/** route AND enforces per-module access: each of the
 * five management personas (Customer Relations, Product Catalog, Order &
 * Payment, Inventory, Delivery) only reaches the section of the admin panel
 * that matches their role - not the whole /admin/** tree. A plain "ADMIN"
 * login is the sixth (Admin Management) persona and a super-role that can
 * reach everything, for whoever administers the store overall.
 *
 * Rules are checked by URL prefix, longest/most specific first. If a path
 * isn't listed here (e.g. /admin/dashboard) it just requires *some*
 * logged-in staff role, and the page itself decides what to show.
 */
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final Map<String, Set<String>> SECTION_ROLES = new LinkedHashMap<>();
    static {
        SECTION_ROLES.put("/admin/customers", Set.of("CUSTOMER_RELATIONS_MANAGER"));
        SECTION_ROLES.put("/admin/complaints", Set.of("CUSTOMER_RELATIONS_MANAGER"));
        // No dedicated "review manager" role exists in CK_users_role - the 6 admin roles
        // map to the other 5 functions plus overall Admin. Review moderation is given to
        // CUSTOMER_RELATIONS_MANAGER since that role already owns trust/complaints handling.
        SECTION_ROLES.put("/admin/reviews", Set.of("CUSTOMER_RELATIONS_MANAGER"));
        SECTION_ROLES.put("/admin/products", Set.of("CATALOG_MANAGER"));
        SECTION_ROLES.put("/admin/categories", Set.of("CATALOG_MANAGER"));
        SECTION_ROLES.put("/admin/orders", Set.of("ORDER_PAYMENT_MANAGER", "CUSTOMER_RELATIONS_MANAGER"));
        SECTION_ROLES.put("/admin/reports/sales", Set.of("ORDER_PAYMENT_MANAGER"));
        SECTION_ROLES.put("/admin/inventory", Set.of("INVENTORY_MANAGER"));
        SECTION_ROLES.put("/admin/suppliers", Set.of("INVENTORY_MANAGER"));
        SECTION_ROLES.put("/admin/purchase-orders", Set.of("INVENTORY_MANAGER"));
        SECTION_ROLES.put("/admin/reports/inventory", Set.of("INVENTORY_MANAGER"));
        SECTION_ROLES.put("/admin/deliveries", Set.of("DELIVERY_MANAGER"));
        SECTION_ROLES.put("/admin/payments", Set.of("ORDER_PAYMENT_MANAGER"));
        SECTION_ROLES.put("/admin/reports/financial", Set.of("ORDER_PAYMENT_MANAGER"));
    }

    // Every role above is allowed to at least open /admin/dashboard and see
    // the links relevant to them; ADMIN is the only role with no restrictions.
    private static final Set<String> ALL_STAFF_ROLES = Set.of(
            "ADMIN", "CUSTOMER_RELATIONS_MANAGER", "CATALOG_MANAGER",
            "ORDER_PAYMENT_MANAGER", "INVENTORY_MANAGER", "DELIVERY_MANAGER"
    );

    // Decodes %-escapes, strips ;jsessionid=... path params and the context path, i.e. the same
    // path Spring MVC uses to pick a controller. getRequestURI() alone is the RAW path, so
    // e.g. /admin/%70roducts would reach ProductCatalogShoppingCartController while slipping past the
    // prefix check below.
    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        Object roleAttr = (session != null) ? session.getAttribute("role") : null;
        String role = (roleAttr != null) ? roleAttr.toString() : null;

        if (role == null || !ALL_STAFF_ROLES.contains(role)) {
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }

        // ADMIN bypasses all section-level checks - full access.
        if ("ADMIN".equals(role)) {
            return true;
        }

        String path = PATH_HELPER.getPathWithinApplication(request);
        String matchedPrefix = null;
        for (String prefix : SECTION_ROLES.keySet()) {
            boolean matches = path.equals(prefix) || path.startsWith(prefix + "/");
            if (matches && (matchedPrefix == null || prefix.length() > matchedPrefix.length())) {
                matchedPrefix = prefix;
            }
        }

        // Not a section we lock down (e.g. /admin/dashboard) - any staff role can view it.
        if (matchedPrefix == null) {
            return true;
        }

        if (SECTION_ROLES.get(matchedPrefix).contains(role)) {
            return true;
        }

        // Logged in, but not the right role for this section - send them back
        // to their own dashboard rather than the generic login page.
        response.sendRedirect(request.getContextPath() + "/admin/dashboard");
        return false;
    }
}
