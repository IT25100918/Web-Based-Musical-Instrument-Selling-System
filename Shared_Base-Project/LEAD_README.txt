Shared Base Project (team lead pushes this FIRST)
================================================

Contains pom.xml, config, CSS/images, database.sql, shared services, observer base classes, .gitignore and README.
Push as the first commit on main; the 6 members then add their files on feature branches.

secrets.properties is intentionally NOT included (real passwords). Copy secrets.properties.example and fill it in locally.

  git init && git add . && git commit -m "Initial shared setup"
  git remote add origin <repo-url> && git push -u origin main
