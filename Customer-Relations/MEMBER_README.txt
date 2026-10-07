Member2 - Customer-Relations
==========================================

Files in this zip (paths match the project, so copy them straight into the repo):

  src/main/java/com/instrumentstore/controller/ReviewController.java
  src/main/java/com/instrumentstore/controller/StaticPageController.java
  src/main/java/com/instrumentstore/dao/ComplaintDAO.java
  src/main/java/com/instrumentstore/dao/ReviewDAO.java
  src/main/java/com/instrumentstore/model/Complaint.java
  src/main/java/com/instrumentstore/model/Review.java
  src/main/java/com/instrumentstore/patterns/strategy/review/HighestRatedStrategy.java
  src/main/java/com/instrumentstore/patterns/strategy/review/LowestRatedStrategy.java
  src/main/java/com/instrumentstore/patterns/strategy/review/NewestFirstStrategy.java
  src/main/java/com/instrumentstore/patterns/strategy/review/ReviewSortStrategy.java
  src/main/java/com/instrumentstore/patterns/strategy/review/ReviewSorter.java
  src/main/resources/templates/admin-complaints.html
  src/main/resources/templates/admin-reviews.html
  src/main/resources/templates/complaints.html
  src/main/resources/templates/contact.html
  src/main/resources/templates/privacy.html
  src/main/resources/templates/terms.html

Notes: Strategy pattern (review sorting). Complaint endpoints (/complaints, /admin/complaints/...) currently live in CustomerAdminManagementController.java lines ~520-655 (Member 1's file). Move them into a new ComplaintController.java that you own. product-detail.html (Member 3) has the review section - edit only that part.

Setup: get Shared_Base-Project.zip from your team lead first (pom.xml, config, CSS, database.sql, etc.).
Copy secrets.properties.example to secrets.properties and fill in your own values. NEVER git add secrets.properties.

Git:
  git clone <repo-url>
  git checkout -b feature/customer-relations
  git add <only your files>
  git commit -m "<Function>: <what you did>"
  git push -u origin feature/customer-relations   # then open a Pull Request
