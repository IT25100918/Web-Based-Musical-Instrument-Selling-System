# Web-based Musical Instruments Selling System (Spring Boot version)

## Why this is easier to run than the Servlet/JSP version

Spring Boot has an **embedded Tomcat server built in**. You don't install or configure
Tomcat separately — running the app starts its own server automatically on port 8080.

## What's built

Customer accounts (register / login / verify email / forgot & change password / profile),
product catalogue (categories, products, image galleries, promotions, reviews, wishlist),
cart & orders, payments (simulated gateway), inventory / suppliers / purchase orders,
deliveries (with email notifications), complaints, and sales / inventory / financial reports.
Staff logins are role-restricted (see `AdminAuthInterceptor`).

## The pattern every member should follow

1. **Model** — plain class in `model/` matching your DB table (copy `Product.java`)
2. **DAO** — `@Repository` class in `dao/` with a `JdbcTemplate` field, one method per CRUD
   operation (copy `ProductDAO.java`)
3. **Controller** — `@Controller` class in `controller/` with `@GetMapping`/`@PostMapping`
   methods that call your DAO and return a template name (copy `ProductCatalogShoppingCartController.java`)
4. **Templates** — `.html` files in `resources/templates/`, using `th:each`, `th:if`,
   `th:text` etc. (copy `admin-products.html` / `product-form.html`)

No `web.xml`, no `@WebServlet` annotations, no manual `Connection`/`PreparedStatement`
open-and-close code — Spring handles the plumbing, you just write the SQL and the logic.

## How to run this in IntelliJ

1. **File > Open** and select this `instrument-store-sb` folder (the one with `pom.xml`).
   IntelliJ auto-detects it as a Maven project.
2. Wait for Maven to finish downloading dependencies (progress bar bottom-right).
3. Copy `secrets.properties.example` to `secrets.properties` (same folder as `pom.xml`) and put your
   database / email passwords in it. It is listed in `.gitignore`, so it is never committed.
   Create the database: run `database.sql` once in SSMS / sqlcmd (SQL Server). It also creates the
   `instrument_app` login used by `src/main/resources/application.properties` - check the URL,
   username and password there match your machine. Set `app.base-url` to your public URL.
4. Find `InstrumentStoreApplication.java` in the project tree. Right-click it and choose
   **Run 'InstrumentStoreApplication'** — or click the green ▶ arrow next to the `main`
   method inside the file.
5. Wait for the console to show `Started InstrumentStoreApplication` — that means the
   embedded server is running.
6. Open your browser to **http://localhost:8080/**

No Tomcat download, no WAR file, no manual deployment step.

Demo logins (seeded by `database.sql` for demonstration only - these are well-known passwords, so change
them before any real deployment):

| Role | Email | Password |
|---|---|---|
| Admin | `admin@instrumentstore.com` | `admin123` |
| Customer Relations | `crm@instrumentstore.com` | `Manager@123` |
| Catalog | `catalog@instrumentstore.com` | `Manager@123` |
| Order & Payment | `orders@instrumentstore.com` | `Manager@123` |
| Inventory | `inventory@instrumentstore.com` | `Manager@123` |
| Delivery | `delivery@instrumentstore.com` | `Manager@123` |

## Design patterns already demonstrated

All design-pattern classes live together in `src/main/java/com/instrumentstore/patterns/` (see the `README.md` there for the map).

- **MVC** — Controller → Model → Thymeleaf view
- **DAO Pattern** — SQL isolated in `*DAO.java` classes
- **Dependency Injection** — Spring creates and hands you `JdbcTemplate` and DAO instances
  automatically (this itself is worth mentioning as a design pattern discussion point —
  it's a form of the Inversion of Control principle)

- **Factory / Strategy** (`patterns/strategy/payment/` and `patterns/factory/`) — `PaymentProcessor` is the Strategy interface (Card, COD, Bank Transfer
  implementations). `PaymentProcessorFactory` is the Factory: Spring injects every processor as a list and the
  factory turns it into a lookup by method name. `OrderPaymentManagementController` asks the factory for the
  processor and passes it to `OrderService.payOrder`, so the service never needs to know which payment methods
  exist. Adding a payment method means adding one class. Note: the processors are *simulated* (always succeed,
  fake reference) - no real payment gateway is connected.
- **Observer** (Delivery + Inventory) — `patterns/observer/` holds the generic `Subject`/`Observer`/`AbstractSubject`.
  `DeliveryStatusSubject` notifies `CustomerEmailObserver` and `DeliveryLogObserver` when a delivery is assigned or
  its status changes; `StockLevelSubject` notifies `LowStockAlertObserver` and `StockChangeLogObserver` when stock
  changes (manual update or purchase order received). Spring injects every observer, so a new reaction = one new class.
- **Singleton** (Customer Account & Admin Management) — `patterns/singleton/AuditLog` is hand-written as in the lecture
  (private constructor, static instance, synchronized `getInstance()`); login/logout/suspend/delete actions are recorded in it.
- **Decorator** (Product Catalog) — `patterns/decorator/`: `RegularPrice` (component) wrapped by `PromotionDecorator` on the
  product detail page, which also reports the customer's saving.
- **Strategy** (Customer Review Management) — `patterns/strategy/review/`: `ReviewSortStrategy` with Newest / Highest / Lowest
  rating strategies, applied by `ReviewSorter`; the product page has a "Sort reviews" drop-down (`?sort=`).

- **Interceptor (cross-cutting checks)** — `AdminAuthInterceptor` (role per admin section) and
  `AccountSessionInterceptor` (suspended / password-changed sessions) run before controllers.

## Validation by function

Every form is checked **twice**: in the browser (`required`, `maxlength`, `pattern`, `min`/`max` - quick feedback) and again
on the server in the controller/service (the browser checks can be bypassed with a hand-made request, so the server ones are
the real protection). The shared rules (email format, phone format, password length 6-72) live in
`service/validation/InputValidator`; limits match the column sizes in `database.sql`.

| Function | What is validated |
|---|---|
| Customer Account & Admin | Name/email/phone/address required + max lengths; email format; phone format (7-15 digits); password 6-72 chars (BCrypt reads only 72); duplicate email; new password must differ from the current one; suspend/delete only on CUSTOMER accounts |
| Customer Relations (complaints, contact, reviews) | Complaint subject (max 150) and message (max 2000); order must belong to the customer; status must be OPEN / IN_PROGRESS / RESOLVED; contact form name/email/subject/message; review rating 1-5, comment max 1000, only buyers can review |
| Product Catalog & Cart | Name (max 150), price 0-99,999,999.99, promo price lower than the regular price, stock not negative, category exists, image type/size, description max 5000; cart quantity 1-99 and never above stock |
| Inventory & Supply | Manual stock 0-1,000,000 and product must exist; supplier name/email format/phone format/lengths; purchase-order quantity 1-100,000, supplier + product exist, status rules (RECEIVED/CANCELLED are final) |
| Delivery | Order must be paid and not cancelled; address required (max 255); scheduled date valid and not in the past; delivery person required (max 100) and not changeable once DELIVERED; one delivery per order; status changes follow `StatusRules` |
| Order & Payment | Payment method must be one the factory knows; amount must be > 0; order must be PENDING to be paid; status/payment changes follow `StatusRules` and `OrderService` rules |

## Email

Email sending is switched off (`app.mail.enabled=false` in `application.properties`) until SMTP is set up, so nothing is
sent and no "Authentication failed" errors appear. Verification/reset links are printed in the console instead. To turn
it on, put a Gmail address and app password in `secrets.properties` and set `app.mail.enabled=true`.

## Secrets

`secrets.properties` (database and email passwords) is included in the full project zip so the app runs straight away
on your machine. It is git-ignored: **never `git add` it or push it to GitHub.** Teammates copy
`secrets.properties.example` to `secrets.properties` and fill in their own values.

## Reminder on the "Use of AI Tools" rule in your spec

This is a starting template and teaching pattern. Each member should write their own
function's code using this as a reference (not copy-paste), and be ready to explain every
line in the viva.


## Tests

`src/test/java` has unit tests (JUnit 5 + Mockito) for the order/payment rules, status rules, the
account-session interceptor and search escaping. Run them with `mvn test` or right-click `src/test` in IntelliJ.
See `CHANGES.md` for what was fixed and why.
