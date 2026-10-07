Member1 - Customer-Account-and-Admin
==========================================

Files in this zip (paths match the project, so copy them straight into the repo):

  src/main/java/com/instrumentstore/config/AccountSessionInterceptor.java
  src/main/java/com/instrumentstore/controller/CustomerAdminManagementController.java
  src/main/java/com/instrumentstore/controller/PasswordResetController.java
  src/main/java/com/instrumentstore/dao/EmailVerificationTokenDAO.java
  src/main/java/com/instrumentstore/dao/PasswordResetTokenDAO.java
  src/main/java/com/instrumentstore/dao/UserDAO.java
  src/main/java/com/instrumentstore/model/EmailVerificationToken.java
  src/main/java/com/instrumentstore/model/PasswordResetToken.java
  src/main/java/com/instrumentstore/model/User.java
  src/main/java/com/instrumentstore/patterns/singleton/AuditLog.java
  src/main/java/com/instrumentstore/service/AccountVerificationService.java
  src/main/resources/templates/admin-customers.html
  src/main/resources/templates/change-password.html
  src/main/resources/templates/forgot-password.html
  src/main/resources/templates/login.html
  src/main/resources/templates/profile.html
  src/main/resources/templates/register.html
  src/main/resources/templates/reset-password-invalid.html
  src/main/resources/templates/reset-password.html
  src/test/java/com/instrumentstore/config/AccountSessionInterceptorTest.java

Notes: Singleton pattern: AuditLog. Your controller also contains the complaint methods (lines ~520-655) - these belong to Member 2; agree with Member 2 on moving them into ComplaintController.java.

Setup: get Shared_Base-Project.zip from your team lead first (pom.xml, config, CSS, database.sql, etc.).
Copy secrets.properties.example to secrets.properties and fill in your own values. NEVER git add secrets.properties.

Git:
  git clone <repo-url>
  git checkout -b feature/customer-account
  git add <only your files>
  git commit -m "<Function>: <what you did>"
  git push -u origin feature/customer-account   # then open a Pull Request
