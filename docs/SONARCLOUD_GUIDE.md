# SonarQube Cloud guide and metrics plan

## 1. One-time setup (about 15 minutes)

1. **Push to GitHub.** Create an empty repo (e.g. `libratrack`), then from the project folder:
   `git init && git add . && git commit -m "LibraTrack" && git branch -M main && git remote add origin <url> && git push -u origin main`
2. **Sign in at sonarcloud.io with GitHub**, create/import your organization, and choose **Analyze new project** → select the repo.
3. Choose **With GitHub Actions** as the analysis method. SonarQube Cloud shows you two values:
   - the **organization key** and **project key** (also visible under *Project information*)
   - a token: copy it.
4. In the GitHub repo → *Settings → Secrets and variables → Actions*:
   - Secret `SONAR_TOKEN` = the token
   - Variables `SONAR_ORGANIZATION` and `SONAR_PROJECT_KEY` = the keys from step 3
5. Put the same two keys in `pom.xml` (`sonar.organization`, `sonar.projectKey`) so local runs work too.
6. In SonarQube Cloud → *Administration → Analysis Method*, turn **Automatic Analysis off** (CI analysis and automatic analysis cannot both run).
7. Push any commit. The workflow `.github/workflows/sonarcloud.yml` builds, runs the JUnit tests with JaCoCo coverage, and uploads the analysis. The dashboard appears at sonarcloud.io within a couple of minutes.

Local alternative (no Actions): `mvn clean verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.token=<token>`

## 2. Where each of the 12 metrics comes from

| # | Metric | Source | Where to read it |
|---|--------|--------|------------------|
| 1 | LOC | SonarQube Cloud (`ncloc`) | *Measures → Size*; per class under *Code* |
| 2 | Cyclomatic Complexity | SonarQube Cloud (`complexity`) | *Measures → Complexity*; per class/method |
| 3 | Cognitive Complexity | SonarQube Cloud (`cognitive_complexity`) | *Measures → Complexity* |
| 4 | Halstead Volume / Effort | `scripts/metrics_extra.py` (Sonar does not report Halstead) | script output / CSV |
| 5 | Maintainability Index / Technical Debt Ratio | Technical Debt Ratio and Maintainability Rating from Sonar (`sqale_debt_ratio`, `sqale_rating`); MI from `metrics_extra.py` | *Measures → Maintainability* |
| 6 | Code Smells | SonarQube Cloud (`code_smells`) | *Issues* filtered by type Code Smell |
| 7 | Duplicated Lines % | SonarQube Cloud (`duplicated_lines_density`) | *Measures → Duplications* |
| 8 | CBO | CKJM-ext (Sonar does not report CBO) | see section 4 |
| 9 | LCOM | CKJM-ext (Sonar does not report LCOM) | see section 4 |
| 10 | Comment Density % | SonarQube Cloud (`comment_lines_density`) | *Measures → Size/Documentation* |
| 11 | OFRD (custom) | `scripts/metrics_extra.py` | section 3 |
| 12 | RFCI (custom) | Runtime counters in `ReservationManager` | section 3 |

Your proposal already says CBO/LCOM would come from CKJM and the custom metrics are manual, so this matches what you promised.
Take screenshots of the Sonar dashboard (overall + the *Measures* tabs + the file list sorted by complexity) for the report.

## 3. Custom metrics

### OFRD - Overdue Fine Rule Density

```
OFRD = (conditional fine-rule branches in FineCalculationEngine) / (LOC of FineCalculationEngine) x 100
```

Branches counted: `if`, `for`, `while`, `case`, `catch`, `&&`, `||`, ternary `?`, i.e. every point where a fine rule
(grace period, weekend/holiday skip, escalation band, price cap, waiver choice, waiver cap) can change the outcome.

Run `python3 scripts/metrics_extra.py`. The baseline for the current code is **14 branches / 93 LOC = 15.05 per 100 LOC**
(per-class numbers are in `docs/metrics_extra_baseline.csv`; re-run after any change and cite the final value).
Interpretation: above roughly 10 means the class is rule-dense and should be tested heavily; compare before and after refactoring.

### RFCI - Reservation Fulfillment Complexity Index

```
RFCI = (reservations needing multi-condition queue resolution) / (total reservations placed)
```

A reservation counts as multi-condition when, at the time it is placed, the book's active queue has at least two entries
**and** either the queue mixes membership tiers or there are fewer available copies than queued members
(so tier priority, request timestamp and copy availability all decide the order).
The counters live in `ReservationManager`. They are deliberately not shown in the library UI. While the app is running, open `http://localhost:8080/api/rfci` (use your port) to read them as JSON.

Demo scenario to produce a meaningful value (web UI, then open `/api/rfci`):

1. Acting as Priya (BASIC), issue *Dune* (single copy).
2. Acting as Rahul (BASIC), reserve *Dune*. `/api/rfci` shows 1 total, 0 multi-condition.
3. Acting as Anubhav (VIP), reserve *Dune*. Now 2 total, 1 multi-condition, **RFCI = 0.5**.
4. Add a third reservation (e.g. Meena, PREMIUM) for 3 total, 2 multi-condition, RFCI = 0.667.
5. Return Priya's copy and try to issue to Rahul: it is refused because VIP/PREMIUM members rank ahead of him.

Take a screenshot of the Reservations tab and of the `/api/rfci` output for the report.

## 4. CBO and LCOM with CKJM-ext

CKJM-ext works on compiled classes. After `mvn clean package`:

```bash
# download ckjm_ext.jar from https://gromit.iiar.pwr.wroc.pl/p_inf/ckjm/ (check the page for the current link)
java -jar ckjm_ext.jar -s target/classes > ckjm_output.txt
```
Columns include WMC, DIT, NOC, CBO, RFC, LCOM. Report CBO and LCOM per class, highlight the highest few,
and discuss them (expect `LibraryService`, `WebServer` and `ConsoleUI` to have the highest CBO because they coordinate many classes).

## 5. Report outline

1. Project overview and architecture (reuse your Review-1 text)
2. Tool and method (SonarQube Cloud + CKJM + `metrics_extra.py`)
3. Results table for all 12 metrics, project-level, then class-level for the key classes
   (`FineCalculationEngine`, `ReservationManager`, `IssueValidator`, `LibraryService`, `ConsoleUI`, `WebServer`)
4. Custom metrics: definitions, formulas, values, interpretation
5. Analysis: which classes are complex or hard to maintain and why
6. Refactoring proposals, then **re-run the analysis and show before/after values**

## 6. Refactoring ideas that will show up in the metrics

- `IssueValidator.validate`: already split into small checks; the class still has many branches. Move limits into a rules object.
- `ConsoleUI`: the largest cyclomatic complexity from the menu; replace the `switch` with a command map.
- `FineCalculationEngine.totalWaiver`: extract waiver selection into its own `WaiverPolicy` class to lower OFRD and cognitive complexity.
- `Json`: replace the hand-written writer with a library or simplify to reduce complexity.
- Add tests until JaCoCo coverage on the Service layer is above 80%.
