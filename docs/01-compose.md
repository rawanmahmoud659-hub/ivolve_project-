# 1. Docker Compose: Node.js and MySQL

The Compose stage comes from Docker Lab 8. The app listens on port 3000, serves `frontend/index.html`, and checks database connectivity through `/health` and `/ready`. MySQL runs in a separate container. Compose DNS resolves `db` to the database container.

## Start

```bash
cd compose
cp .env.example .env
# Replace the two example passwords in .env.
docker compose config
docker compose up -d --build
docker compose ps
```

The database health check gates app startup. The app then retries MySQL connection before opening its HTTP listener.

## Verify

```bash
curl -f http://localhost:3000/
curl -f http://localhost:3000/health
curl -f http://localhost:3000/ready
docker compose logs --tail=50 app
docker compose logs --tail=50 db
docker compose exec db mysql -uivolveuser -p ivolve -e 'SHOW TABLES;'
docker compose ps
docker volume ls
```

Enter `MYSQL_APP_PASSWORD` at the MySQL password prompt. The frontend is a static page; this version of the app does not create a products table automatically. The old `db.js` from Lab 8 contains product initialization logic but is not called by the inspected `server.js`, so this project does not claim that product data is populated.

`db_data` persists `/var/lib/mysql`. `app_logs` persists `/app/logs`. `docker compose down` stops the stack; adding `-v` would also remove the named volumes and their data.
