# LibraTrack - Library Management System

Software Metrics (ISWE402L) project, VIT. Team: Anubhav B (23MIS0457), Joshith S (23MIS0635).

A layered Java 17 application with dynamic overdue-fine calculation, waiver stacking, issue
validation and priority-based reservations. It has two front ends over the same Service layer:
a console UI (as in the proposal) and a web UI.

## Run

```bash
mvn clean verify                                  # compile, test, JaCoCo coverage
java -jar target/libratrack-1.0.0.jar             # web UI at http://localhost:8080
java -jar target/libratrack-1.0.0.jar web 9090    # web UI on another port
java -jar target/libratrack-1.0.0.jar console     # console UI
```

Data is stored in a SQLite file (`libratrack.db` in the working directory; set the `LIBRATRACK_DB` environment variable to change the path, or `:memory:` for a throw-away DB). Tables are created automatically and the demo data is seeded only on first run. Delete the file to reset. The jar is built with the Shade plugin so the sqlite-jdbc driver is bundled.

## Architecture

| Layer   | Package                | Contents |
|---------|------------------------|----------|
| Model   | `com.libratrack.model`   | Book, Member, Transaction, Reservation, Fine, MembershipTier, BookCategory |
| DAO     | `com.libratrack.dao`     | Database (SQLite/JDBC), InMemoryDao write-through base + Book/Member/Transaction/Reservation/Fine DAOs |
| Service | `com.libratrack.service` | FineCalculationEngine, ReservationManager, IssueValidator, MembershipService, LibraryService (facade) |
| UI      | `com.libratrack.ui`, `com.libratrack.web` | ConsoleUI; WebServer (JDK HttpServer) + `static/index.html` |

## Business rules

**Fine** (`FineCalculationEngine`): category daily rate; tier grace days (0/2/4); weekends and holidays
not chargeable; escalation 1.0x (days 1-7), 1.5x (8-14), 2.0x (15+); capped at book price; waivers =
tier (0/20/40%) + larger of first-offense 50% / bulk-return 20% (3+ books returned together), total capped at 75%.

**Issue** (`IssueValidator`): open Mon-Sat 09:00-20:00 (closed Sundays/holidays); per-category issue window
(REFERENCE only 14:00-17:00); borrowing limit by tier (3/6/10); unpaid fines above 200 block issue; no duplicate
loans; remaining copies are held for higher-priority reservations.

**Reservations** (`ReservationManager`): queue ordered by tier priority then request time.

The web UI has a simulated clock (header buttons) so overdue scenarios can be demonstrated instantly.

## Metrics

See [docs/SONARCLOUD_GUIDE.md](docs/SONARCLOUD_GUIDE.md) for SonarQube Cloud setup, where each of the
12 metrics comes from, and the custom OFRD and RFCI definitions.
`python3 scripts/metrics_extra.py` computes Halstead, Maintainability Index, comment density and OFRD locally.
The RFCI counters are readable as JSON at `/api/rfci` while the app runs; the library UI itself contains no metrics screens.
