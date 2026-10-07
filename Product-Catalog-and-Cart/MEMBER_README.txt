Member3 - Product-Catalog-and-Cart
==========================================

Files in this zip (paths match the project, so copy them straight into the repo):

  src/main/java/com/instrumentstore/controller/HomeController.java
  src/main/java/com/instrumentstore/controller/ProductCatalogShoppingCartController.java
  src/main/java/com/instrumentstore/controller/WishlistController.java
  src/main/java/com/instrumentstore/dao/CartDAO.java
  src/main/java/com/instrumentstore/dao/CategoryDAO.java
  src/main/java/com/instrumentstore/dao/ProductDAO.java
  src/main/java/com/instrumentstore/dao/ProductImageDAO.java
  src/main/java/com/instrumentstore/dao/WishlistDAO.java
  src/main/java/com/instrumentstore/model/CartItem.java
  src/main/java/com/instrumentstore/model/Category.java
  src/main/java/com/instrumentstore/model/Product.java
  src/main/java/com/instrumentstore/model/ProductImage.java
  src/main/java/com/instrumentstore/model/WishlistItem.java
  src/main/java/com/instrumentstore/patterns/decorator/PriceComponent.java
  src/main/java/com/instrumentstore/patterns/decorator/PriceDecorator.java
  src/main/java/com/instrumentstore/patterns/decorator/PromotionDecorator.java
  src/main/java/com/instrumentstore/patterns/decorator/RegularPrice.java
  src/main/java/com/instrumentstore/service/ImageStorageService.java
  src/main/resources/templates/admin-categories.html
  src/main/resources/templates/admin-products.html
  src/main/resources/templates/cart.html
  src/main/resources/templates/category-form.html
  src/main/resources/templates/home.html
  src/main/resources/templates/product-detail.html
  src/main/resources/templates/product-form.html
  src/main/resources/templates/products.html
  src/main/resources/templates/wishlist.html
  src/test/java/com/instrumentstore/dao/ProductDAOTest.java

Notes: Decorator pattern (promotion price). You own home.html and HomeController's '/' page; the /admin/dashboard method is shared. You own product-detail.html - Member 2 edits only the reviews section.

Setup: get Shared_Base-Project.zip from your team lead first (pom.xml, config, CSS, database.sql, etc.).
Copy secrets.properties.example to secrets.properties and fill in your own values. NEVER git add secrets.properties.

Git:
  git clone <repo-url>
  git checkout -b feature/product-catalog
  git add <only your files>
  git commit -m "<Function>: <what you did>"
  git push -u origin feature/product-catalog   # then open a Pull Request
