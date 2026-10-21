# Akivnah Services

Spring Boot 3.5 · Java 17 · Spring Security + JWT · Thymeleaf · Spring Data JPA (H2 by default)

## Run
```bash
mvn spring-boot:run
```
Open http://localhost:8080

First admin (created on first start; change it via env vars):

| Variable | Default |
|---|---|
| `ADMIN_USERNAME` | `admin` |
| `ADMIN_PASSWORD` | `Admin@12345` |
| `JWT_SECRET` | dev value, **set your own (32+ chars)** |
| `COOKIE_SECURE` | `false` (set `true` behind HTTPS) |

## How auth works
- **Web pages:** `POST /login` checks credentials, creates a signed JWT and stores it in an HttpOnly, SameSite=Lax cookie (`AKV_TOKEN`). `JwtAuthFilter` reads it on every request. CSRF protection is on for forms.
- **API:** `POST /api/auth/login` returns a token; send it as `Authorization: Bearer <token>`.
- **Roles:** `USER` (public registration), `EMPLOYEE` (created by HR), `HR` and `ADMIN` (`/hr/**`, `/admin/**`).

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@12345"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl localhost:8080/api/services -H "Authorization: Bearer $TOKEN"
```

## Employee portal
- **HR console** (`/hr/employees`, roles HR or ADMIN): add employees (this also creates their login: username = Employee ID plus an initial password), then generate **offer letters**, **payslips** and **increment letters** for each employee.
- **Employee portal** (`/portal`, role EMPLOYEE): each employee sees only their own payslips, offer letters and increment letters.
- **Printable documents** (`/documents/offer|payslip|increment/{id}`) use the Akivnah letterhead with watermark. Use "Print / Save as PDF" (A4, margins "None"). HR/Admin can open any document; an employee only their own.
- Saving an increment letter updates the employee's current CTC (and designation, if given).
- To make an HR user, set a user's role to `HR` in the database (or log in as `admin`, which has HR access too).
- If you ran an earlier version, delete the `data/` folder once: the role list changed and the old H2 file has the old role constraint.

## Logout
Logout deletes the cookie, **revokes the JWT** (in-memory deny-list until it expires; use Redis/DB for several servers) and asks the browser to clear cached pages. Pages also reload themselves when reached with the browser Back button, so private pages are never shown after logout.

## Website
The public Akivnah website is part of the app (Thymeleaf templates, shared header/footer in `templates/fragments.html`, styles in `static/css/app.css`, AH logo files in `static/images/`).

| Page | URL |
|---|---|
| Home | `/` |
| About / Services / Work / Careers / Insights | `/about` `/services` `/work` `/careers` `/insights` |
| Contact form (saved to the database) | `/contact`, admins read messages at `/admin/messages` |
| Service catalog with prices (admin-managed) | `/catalog` |

The page copy (Work, Careers, Insights, About) is sample text: edit the matching file in `src/main/resources/templates/`.

## Mail (`/mail`)
Webmail for staff (roles EMPLOYEE, HR, ADMIN). Folders: **Inbox, Starred, Sent, Outbox, Drafts, Trash**. Compose, reply, reply all, forward, star, mark unread, search, restore from Trash.
- **Between colleagues** it works out of the box: address a person by Employee ID or by their email, and the message lands in their Inbox at once.
- **To outside addresses (send):** set SMTP, e.g. `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT=587`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, optionally `MAIL_FROM`. Such mail waits in the **Outbox** and a background job sends it (retries 5 times, then shows Failed with a Retry button). Replies go to the sender's own address (Reply-To).
- **From outside (receive):** set `IMAP_ENABLED=true`, `IMAP_HOST`, `IMAP_PORT=993`, `IMAP_USERNAME`, `IMAP_PASSWORD`. Every minute unread mail in that one mailbox is filed into the Inbox of the portal user whose email matches the recipient (otherwise the admin).
- **Attachments:** up to 10 files, 5 MB each and 10 MB per message, stored in the database. Programs and scripts (.exe, .bat, .js, ...) are refused, and downloads are always forced as files. Received attachments over the limit are skipped and noted in the message. Forwarding does not copy attachments.
- **Signature:** set it under *Settings & signature*; it is added to new messages, replies and forwards.
- **Website enquiries:** each contact-form message is also delivered to every admin's Inbox.
- Plain text only (no HTML mail yet). Passwords belong in environment variables, never in the file.

## Pages
`/` home · `/services` catalog · `/login` · `/register` · `/dashboard` (logged in) · `/admin/services` (admin CRUD)

## Production notes
Use PostgreSQL/MySQL (change `spring.datasource.*`, `ddl-auto=validate` + Flyway), keep `JWT_SECRET` in a secret store, serve over HTTPS with `COOKIE_SECURE=true`.

## Where employees are stored (H2)
HR Console → *Add employee* saves the employee **and** their portal login in one transaction into the H2 file database `./data/akivnah.mv.db` (it survives restarts).
To look at the tables, start the app with `H2_CONSOLE=true`, sign in as admin and open `/h2-console` (JDBC URL `jdbc:h2:file:./data/akivnah;AUTO_SERVER=TRUE`, user `sa`, empty password). Leave it off in production.

### Managing employees (HR Console)
* **Add** – choose the portal role (Employee, or HR if you are an administrator). 
* **Edit details** – everything except the Employee ID; the portal login email is kept in sync.
* **Delete employee** – removes the employee, their offer letters, payslips and increment letters, their portal login and their mailbox, in one transaction. You cannot delete your own login or an admin; only an administrator can delete an HR user.

### Password reset
* **Forgot password** (`/forgot-password`): one-time link, 30 minutes, only a hash is stored, 3 requests per hour per account, same answer whether or not the account exists. Needs SMTP (`SPRING_MAIL_*`) and `PUBLIC_URL`; without SMTP the link is written to the console (development only).
* **Change password** (`/account/password`) and **HR/admin reset** (temporary password, user must change it at next sign-in).
* Every password change or reset signs out all older sessions.
