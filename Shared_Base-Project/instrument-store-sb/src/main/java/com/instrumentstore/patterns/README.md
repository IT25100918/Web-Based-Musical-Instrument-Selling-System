# Design patterns in this project

Everything that implements a pattern from the lectures is in this folder, one sub-folder per pattern.
The controllers / services only *use* these classes.

| Pattern (type) | Folder | Function it belongs to | Used from |
|---|---|---|---|
| **Strategy** (behavioral) | `strategy/payment/` - `PaymentProcessor` + Card / COD / Bank Transfer | Order & Payment | `service/OrderService.payOrder` |
| **Factory** (creational) | `factory/PaymentProcessorFactory` | Order & Payment | `OrderPaymentManagementController` (POST `/payment/{orderId}`) |
| **Observer** (behavioral) | `observer/` (shared `Subject`, `Observer`, `AbstractSubject`) + `observer/delivery/` | Delivery | `AdminDeliveryController` |
| **Observer** (behavioral) | `observer/` + `observer/inventory/` | Inventory & Supply | `InventorySupplyManagementController`, `service/InventoryService` |
| **Singleton** (creational) | `singleton/AuditLog` | Customer Account & Admin | `CustomerAdminManagementController` (login, logout, suspend, delete) |
| **Decorator** (structural) | `decorator/` - `RegularPrice` wrapped by `PromotionDecorator` | Product Catalog | `ProductCatalogShoppingCartController` (product detail page) |
| **Strategy** (behavioral) | `strategy/review/` - Newest / Highest / Lowest rating + `ReviewSorter` | Customer Relations (reviews) | `ProductCatalogShoppingCartController` (product detail page) |

Adding a new payment method = one new class in `strategy/payment/` (the factory finds it automatically).
Adding a new delivery / stock reaction = one new observer class in `observer/delivery/` or `observer/inventory/`.
Adding a new review sort order = one new class in `strategy/review/`.
