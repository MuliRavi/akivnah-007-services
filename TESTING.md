# Step-by-step test guide (http://localhost:8080)

Start: `mvn spring-boot:run` (Java 17). Default admin: **admin / Admin@12345** (email hr@akivnah.com).
Fresh start? Delete the `data/` folder first. Use a private window per user so logins don't mix.

## 1. Public website (no login)
| # | URL | Expect |
|---|-----|--------|
| 1 | /  | Home page, header, "Start a project" button |
| 2 | /about /services /work /careers /insights | Each page loads, header/footer present |
| 3 | /catalog | Service catalog with 4 seeded services |
| 4 | /contact (also the Contact tab and "Start a project") | Form shows, dropdown has 6 options |
| 5 | Submit /contact empty | Red error messages under the fields |
| 6 | Submit /contact with name, email, message | Redirects to /contact?sent with a green message |
| 7 | /nope | Branded 404 page |

## 2. Login, security, logout
| # | Step | Expect |
|---|------|--------|
| 1 | Open /dashboard while logged out | Redirected to /login |
| 2 | /login with wrong password | /login?error |
| 3 | /login as admin | /dashboard, header shows Account menu |
| 4 | Account → Logout | /login?logout |
| 5 | Press browser Back | Page reloads and asks for login (no private page shown) |
| 6 | /register (new user) | Creates a USER with no portal access; /portal and /mail show 403 |

## 3. Admin (login as admin)
| # | URL | Expect |
|---|-----|--------|
| 1 | /admin/messages | The enquiry from step 1.6 is listed |
| 2 | /admin/services | List; add a service, edit it (/admin/services/{id}/edit), delete it; it appears/disappears at /catalog |
| 3 | /mail | Admin Inbox also holds "Website enquiry..." mail from the contact form |

## 4. HR Console: employees stored in H2
| # | Step | Expect |
|---|------|--------|
| 1 | /hr/employees → Add employee (ID `AKV001`, name, email `anil@akivnah.com`, designation, department, joining date, CTC, initial password `Welcome@123`, role Employee) | Redirect to the employee page with a green "saved" message |
| 2 | Submit again with the same ID or email | Red "already exists" errors, nothing duplicated |
| 3 | Password shorter than 8 characters | Red password error |
| 4 | Employee list | AKV001 appears with Role EMPLOYEE |
| 5 | Edit details (/hr/employees/{id}/edit) | Change designation → "updated" message; Employee ID is read-only |
| 6 | Restart the app, open /hr/employees | Employee still there (stored in `./data/akivnah.mv.db`) |
| 7 | Optional: start with `H2_CONSOLE=true`, open /h2-console | JDBC URL `jdbc:h2:file:./data/akivnah;AUTO_SERVER=TRUE`, user `sa`; tables EMPLOYEE and USERS hold the rows |

## 5. HR documents
For employee AKV001 (/hr/employees/{id}):
1. **New offer letter** (/hr/employees/{id}/offers/new) → save → listed with ref AKV/OFR/<year>/<n> → *View / Print* (/documents/offer/{id}) shows the letterhead.
2. **New payslip** (/payslips/new) → save → /documents/payslip/{id}. Saving the same month twice is rejected. Days paid above working days is rejected.
3. **New increment letter** (/increments/new) → save → /documents/increment/{id}; the employee's CTC and designation update.
4. In the browser, Print → Save as PDF to check the page layout.

## 6. Employee self-service
1. Log out. Log in as **AKV001 / Welcome@123**.
2. /portal lists only AKV001's own offer letter, payslip and increment letter; each opens.
3. Try /hr/employees → 403. Try /admin/services → 403.
4. Open a document id that belongs to another employee → 403; an id that does not exist (e.g. /documents/offer/999) → 404. Never someone else's data.

## 7. Mail between users
(Create a second employee AKV002 with email `sita@akivnah.com` first.)
1. As AKV001: /mail → Compose (/mail/compose) → To `sita@akivnah.com`, subject, body, attach a small file → Send.
2. It appears in AKV001's Sent (/mail/folder/sent).
3. Log in as AKV002: Inbox (/mail/folder/inbox) shows it unread; open it, download the attachment, Reply, Forward, Star, Mark unread.
4. Save a draft → /mail/folder/drafts → reopen and send.
5. Delete a message → Trash (/mail/folder/trash) → Restore; then delete again and use Empty trash.
6. /mail/settings → set a signature → it is prefilled in Compose.
7. A mail to an outside address stays in Outbox until SMTP is configured (SPRING_MAIL_HOST etc.).

## 8. Delete employee
As admin or HR: employee page → **Delete employee** → confirm.
Expect: back on /hr/employees?deleted, AKV002 gone, and logging in as AKV002 now fails.
Deleting your own login or an admin is refused.

## 9. REST API (JWT)
```
curl -s -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"Admin@12345"}'          # returns a token
curl -s localhost:8080/api/services -H "Authorization: Bearer <token>"
curl -i localhost:8080/api/services                                 # 401 without a token
```

## Troubleshooting: "Create employee" fails
* **500 error page / "Check constraint violation" in the console** – the H2 file was made by an older version. This version repairs this automatically at start-up (look for "Dropped old CHECK constraint" in the log). Or stop the app and delete the `data/` folder once (this erases all stored data).
* **Page reloads with red messages** – read them: Employee ID 2-20 letters/numbers/dash/underscore; valid email; every field except PAN and bank account is required; password at least 8 characters; ID or email already used.
* **403** – you are not signed in as HR or admin. Log out and back in.
* **403 "Access denied" when you press Send/Create on any form** – the console line says `MissingCsrfTokenException`. The old XSRF-TOKEN cookie never reached the server, so the app now uses a cookie-free CSRF token (`SignedCsrfTokenRepository`, an HMAC of your login cookie). Restart, reload the form page, and submit.
* **Still 403?** Look at the console where Spring runs: it now prints a line like `403 POST /hr/employees -> InvalidCsrfTokenException ...` or `... AccessDeniedException`. The first means a missing/old CSRF cookie (clear cookies, reload the form page, resubmit); the second means your role is not allowed there. The public Contact form no longer needs the CSRF token at all.


## 10. Password reset
| # | Step | Expect |
|---|------|--------|
| 1 | /login → "Forgot your password?" (/forgot-password) → enter `AKV001` or its email | Always the same message ("If that account exists..."), also for a name that does not exist |
| 2 | No SMTP set: read the Spring console | A WARN line with the link `http://localhost:8080/reset-password?token=...` (valid 30 minutes). With SMTP set (SPRING_MAIL_*), the link arrives by email. |
| 3 | Open the link, choose a new password (8-72 characters, both fields equal) | Redirect to /login?reset; the new password works, the old one does not |
| 4 | Open the same link again | "invalid, already used, or expired" |
| 5 | Ask for 4 resets within an hour | The 4th sends nothing (limit 3 per hour) |
| 6 | Signed in as AKV001, Account → Change password (/account/password) | Wrong current password is refused; success signs you out everywhere (/login?pwchanged) |
| 7 | HR/admin: employee page → Reset password | A temporary password is shown once. The employee is signed out everywhere; at next sign-in they are sent to /account/password?required and cannot use other pages until they choose a new password. |
| 8 | New employees created in the HR Console | Also must choose their own password at first sign-in |
| 9 | Any session started before a reset/change | Rejected on its next request (you are sent to /login) |

Set `PUBLIC_URL` (for example https://www.akivnah.com) so the emailed link points at your real site.
