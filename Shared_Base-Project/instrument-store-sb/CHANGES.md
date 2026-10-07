# Bug-fix round - what changed and why (viva notes)

| # | Problem | Fix | Where |
|---|---|---|---|
| 1 | A suspended customer / staff member who was already logged in kept working until the session ended | `AccountSessionInterceptor` re-checks the account in the DB on every request and logs out suspended / deleted accounts; it also refreshes the role | `config/AccountSessionInterceptor`, `WebConfig` |
| 2 | A manager could suspend the Admin (or themselves); staff could delete their own account | Suspend / unsuspend only work on `CUSTOMER` accounts; `/profile/delete` is customers only | `CustomerAdminManagementController` |
| 3 | Any order status could be set to any other (e.g. unpaid PENDING -> DELIVERED) | `StatusRules` lists the allowed changes; the admin page only offers those; the update is an atomic compare-and-set | `service/StatusRules`, `OrderPaymentManagementController`, `admin-orders.html` |
| 4 | Refund / status change / delete on a payment didn't touch the order or stock | Refund goes through `OrderService.refundPayment` (cancels the order, restocks, refunds). `changePaymentStatus` and `deleteInvalidPayment` enforce consistent rules | `OrderService`, `admin-payments.html` |
| 5 | Customer Relations could POST an order-status change (cancel + refund) | `AdminAuthInterceptor` has a write-only rule: POST to `/admin/orders` needs Order & Payment (or Admin); the page shows "View only" | `AdminAuthInterceptor`, `admin-orders.html` |
| 6 | One user could flag a review many times | New `review_flags` table, primary key (review_id, user_id); cannot flag your own review | `ReviewDAO`, `ReviewController`, `database.sql`, `migration.sql`, `SchemaMigrationRunner` |
| 7 | Payment processor ran before the transaction | `payOrder` now: PENDING->PROCESSING, process, record payment - all in one transaction, so a processor failure rolls back | `OrderService.payOrder` |
| 8 | Saving the product form overwrote stock with a stale value | `updateProduct` no longer writes stock; the field is read-only when editing | `ProductDAO`, `product-form.html` |
| 9a | Delivery status had no rules | `StatusRules.canChangeDelivery`; DELIVERED is final; order sync only follows allowed order changes | `AdminDeliveryController`, `admin-deliveries.html` |
| 9b | Deleting a delivery left the order SHIPPED | Order goes back to PROCESSING; completed deliveries can't be deleted | `AdminDeliveryController` |
| 9c | `%` and `_` in the search box acted as wildcards | Escaped (`ESCAPE '\'`) | `ProductDAO.escapeLike` |
| 9d | Logout was a GET link (another site could log you out) | Logout is POST (all 24 pages use a small form) | `CustomerAdminManagementController`, templates |
| 9e | Password change / reset didn't end other sessions | The session stores a fingerprint of the password hash; a mismatch ends the session. The session that changed the password stays logged in | `AccountSessionInterceptor`, `CustomerAdminManagementController` |

## Tidy-ups
- `pom.xml`: `<n>` was not a valid Maven element, now `<name>`.
- Passwords moved out of `application.properties` into `secrets.properties` (git-ignored); `.idea/` and `target/` removed from the project and ignored.
- `BusinessException` is now its own class (it used to be an inner class of `OrderService` that `InventoryService` borrowed).
- `NotificationService.sendStatusUpdate` renamed `sendEmail` (it also sends verification and reset emails).
- README no longer mentions a file that doesn't exist or a to-do that was already done.

## Behaviour you should know about
- A DELIVERED order can still be cancelled (treated as a return: restock + refund). Everything else follows forward-only rules.
- `review_flags.user_id` has no ON DELETE CASCADE on purpose: SQL Server rejects a second cascade path (users -> reviews -> review_flags). `UserDAO.deleteAccount` removes the user's flags first.
- Revenue in the sales report (orders) and the financial report (payments) now agree, because refunds always go through the order.

## Delivery, complaints and Customer Relations update
- **Delivered option**: a delivery can now be set to DELIVERED straight from ASSIGNED as well as from IN_TRANSIT (`StatusRules`, `admin-deliveries.html`). If the order is still PROCESSING it passes through SHIPPED first, so the order status stays valid (`AdminDeliveryController`).
- **Customer complaints**: "Report a problem" on the order page and a "Complain" button in the orders list open the complaint form with that order pre-selected; a confirmation message is shown after submitting.
- **Customer Relations - customers**: the Customers page now lists all customers with Add, Edit (name, email, phone, address) and Delete. Delete is refused when the customer has orders (suspend instead).
- **Customer Relations - complaints**: add a complaint on a customer's behalf, edit subject/message, delete (existing respond/status stays).

## Logo and admin theme
- New logo (`static/images/logo-wordmark.png` light, `logo-wordmark-dark.png` for white headers, `logo.png`, `favicon.png`). It replaces the note-icon badge on the home page and the text brand in every page header (`theme.css`); a favicon was added to every page.
- Admin pages now load `static/css/admin.css`: deep teal + coral (the home page palette), dark teal header with the light logo, recoloured dashboard banner, pills and focus states.

## Whole project follows the home page theme
- `theme.css` (shared by every page) now uses the home page palette: deep teal `#08272B` / `#0F4048`, coral `#FF7A4D`, mint `#E6F2EE`, and the home page fonts (Fraunces headings, Inter body). Yellow/black values that were hard-coded in pills, alerts, hover states and the footer were replaced too.
- Nav bar, buttons and search bar share the logo colours on all pages; instrument placeholder illustrations were recoloured to match.
- `admin.css` still gives the admin console its dark teal header.

## Product cards and image URL length
- Product card photos use `object-fit: contain`, so images are shown in full instead of being cropped; the price and "Add to Cart" no longer wrap awkwardly (`theme.css`).
- Product / extra-image URLs can now be 1000 characters (`database.sql`, `ProductCatalogShoppingCartController`, `SchemaMigrationRunner` widens existing columns on startup).

## Team names on the dashboard banner
- The admin dashboard banner shows the team member for each area: Customer Relations - THINARA, Order & Payment - SHEHARA, Delivery - GAGANI, Admin - THIRANDI, Inventory & Supply - THARINDU, Catalogue - AVANI (`HomeController`, `admin-dashboard.html`).

## Validation round
- New `service/validation/InputValidator` (email, phone, password 6-72, required-text helpers) with `InputValidatorTest`.
- Account: email/phone format on register, admin add/edit and profile; password max 72 and "must differ from current" on change; max 72 on reset.
- Customer Relations: contact form is now validated server-side (it had none); complaint message / admin response max 2000; unknown complaint status or missing complaint now shows an error instead of silently doing nothing; review comment max 1000.
- Catalog/Cart: promo price must be lower than the price (it used to be silently dropped); description max 5000; editing a product that no longer exists is reported; cart quantity 1-99.
- Inventory: stock update shows an error for out-of-range values / unknown products (it used to ignore them silently); supplier email/phone format; purchase-order quantity max 100,000.
- Delivery: scheduled date can't be in the past; delivery person required and delivery must exist and not be DELIVERED.
- Order & Payment: `payOrder` rejects a zero, negative or missing amount.
- Forms: matching `maxlength` / `max` / `pattern` attributes added so the browser warns before the server has to.

## Patterns folder
- All design-pattern classes moved into `patterns/` (one sub-folder per pattern: `singleton`, `observer`, `strategy`, `factory`, `decorator`). Only package names / imports changed; behaviour is the same.
