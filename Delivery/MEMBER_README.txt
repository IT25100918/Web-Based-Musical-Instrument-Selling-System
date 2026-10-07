Member5 - Delivery
==========================================

Files in this zip (paths match the project, so copy them straight into the repo):

  src/main/java/com/instrumentstore/controller/AdminDeliveryController.java
  src/main/java/com/instrumentstore/dao/DeliveryDAO.java
  src/main/java/com/instrumentstore/model/Delivery.java
  src/main/java/com/instrumentstore/patterns/observer/delivery/CustomerEmailObserver.java
  src/main/java/com/instrumentstore/patterns/observer/delivery/DeliveryEvent.java
  src/main/java/com/instrumentstore/patterns/observer/delivery/DeliveryLogObserver.java
  src/main/java/com/instrumentstore/patterns/observer/delivery/DeliveryStatusSubject.java
  src/main/java/com/instrumentstore/service/NotificationService.java
  src/main/resources/templates/admin-deliveries.html
  src/main/resources/templates/delivery-form.html
  src/test/java/com/instrumentstore/service/StatusRulesTest.java

Notes: Observer pattern (delivery status). StatusRules.java is shared with Member 6 - change it via pull request only.

Setup: get Shared_Base-Project.zip from your team lead first (pom.xml, config, CSS, database.sql, etc.).
Copy secrets.properties.example to secrets.properties and fill in your own values. NEVER git add secrets.properties.

Git:
  git clone <repo-url>
  git checkout -b feature/delivery
  git add <only your files>
  git commit -m "<Function>: <what you did>"
  git push -u origin feature/delivery   # then open a Pull Request
