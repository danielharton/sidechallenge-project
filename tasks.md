# Scam Shield: team README

R Systems side challenge, EESTEC LC Bucharest. **Code freeze: 17:00.**

Our app reads a message or a conversation, finds the official red flags, computes the risk score, verdict, scam type and first alert, and shows a calm warning in Romanian. **Everything runs on our laptop. No message is ever sent to a cloud service.**

---

## 1. What is already done

| Part | File | Status |
|---|---|---|
| Scoring engine (official formula, verdict bands, first alert, re-score after every message) | `engine.py` | ✅ done |
| Rules for all 13 red flags, scam type, links/phones checked against the official list | `engine.py` | ✅ done |
| Warnings in Romanian + English (max 3 sentences, only official contacts, never repeats links or numbers from the message) | `warnings_ro.py` | ✅ first version |
| Self-check against the answer key, using the jury's accuracy points | `evaluate.py` | ✅ done |
| Web app: paste box, open file, one big button, coloured result with icon + words, read-aloud, "trusted person" call button, RO/EN toggle | `app.py`, `static/index.html` | ✅ first version |
| Dashboard: load a whole folder, summary line, one row per scenario, click a row for the detail view (first-alert message highlighted), CSV export | `static/index.html` | ✅ first version |
| Synthetic Romanian training data generator (with genuine "looks dangerous but safe" messages) | `gen_data.py` | ✅ done, needs more variety |
| Fine-tuning script (Romanian BERT, 13 flags, per-flag threshold calibration) | `train.py`, `model.py` | ✅ tested, **not trained yet** (Andrei is setting up the GPU) |

**Current result:** rules only → **63/63 on the Validation Set (30/30)**.

⚠️ **Be honest about that number.** The rules were tuned on those same 11 scenarios, so the secret Test Set will be harder. **Our own new test scenarios are the real test** (see Person B).

---

## 2. How the app works

It's a **local web app**: a small Python server runs on the laptop and does all the detection; the browser is just the screen.

```
Browser page (static/index.html)
      │  sends the message to localhost:8000 (never leaves the laptop)
      ▼
Python server (app.py)
      ├─ rules + link/phone checks vs official list   (engine.py)
      ├─ fine-tuned Romanian BERT, calibrated thresholds (model.py) ← once trained
      ├─ official score → verdict → scam type → first alert
      └─ warning text                                  (warnings_ro.py)
```

A phone on the same Wi-Fi can open it at `http://<laptop-IP>:8000` (find the IP with `ipconfig`).

---

## 3. Setup (everyone)

On Windows, use `python`, not `python3`.

```
pip install fastapi uvicorn
cd scamshield
python evaluate.py data/validation_set data/validation_expected_results.csv --no-model
```
The last line must say `Accuracy points: 63/63`.

Start the app (rules only, no model needed):
```
python app.py --no-model
```
Open http://localhost:8000 → **Dashboard** → "Încarcă un dosar" → choose `data/validation_set`. You should see 11 rows.

---

## 4. Who does what

**Ownership rule:** only one person edits each file, so nobody overwrites anyone else in git.
- `engine.py` → **Person B**
- `static/index.html`, `warnings_ro.py` → **Person C**
- `gen_data.py`, `train.py`, `model.py` → **Andrei**

### Andrei (Person A): the model
1. Install torch with GPU support (RTX 3050, 4 GB) and check `torch.cuda.is_available()` → `True`.
2. Generate data:
   ```
   python gen_data.py --n 4000 --out data/train.jsonl --seed 1
   python gen_data.py --n 800  --out data/dev.jsonl   --seed 2
   ```
3. Train: `python train.py --bs 8` → creates `model_out/` (not committed to git).
4. Check: `python evaluate.py data/validation_set data/validation_expected_results.csv` must still be 63/63. The `flags +[...]` column shows extra flags the model added. If one breaks a row, raise that flag's threshold in `model_out/thresholds.json`.
5. While it trains: add more varied snippets to `gen_data.py`, especially **genuine messages that look dangerous**.

### Person B: rules and testing
Goal: find the cases where we're wrong before the jury does.
1. Create `data/my_tests/` with **10–15 new scenarios** in the official JSON format (copy a validation file and change it), plus `data/my_tests_expected.csv` with the same columns as the validation CSV. Cover:
   - genuine messages that look dangerous (real bank warning, real courier notice on the official site, a known relative asking for a lift)
   - messages without diacritics, all lowercase, with typos
   - **long threads (8–10 messages) where the hook comes late**
   - types not in the validation set: INVESTMENT, JOB_OFFER, ROMANCE, plus a police or "your account was hacked" call
2. Run `python evaluate.py data/my_tests data/my_tests_expected.csv --no-model`, look at the `XX` rows and fix the patterns in `engine.py`.
3. **After every change:** re-run the Validation Set, which must stay at 63/63.
4. Try to break it: empty message, emoji only, a huge paste, a broken JSON file. It must never crash.

### Person C: GUI, warnings, pitch
This covers ease of use (20 points), warning quality (25) and pitch (15).
1. Open the app on a phone and make sure everything is readable. Text must stay ≥ 18 px, and colour must never be the only signal (there's always an icon + a word).
2. Go through the brief's must-haves **M1–M9** and polish anything rough.
3. **Read the warnings out loud to someone non-technical** (call a parent). Simplify anything they don't understand instantly. Rules: max 3 sentences, no jargon, one action through an official channel, never a link or number from the message.
4. Nice addition: in the detail view, show which flags came from the **model** and which from the **rules**. It helps the jury see the model actually contributes.
5. From about 14:00: write the **3-minute pitch**: problem → live demo → how it works → honest limits. **All three of us speak.**

---

## 5. Approach tier (bonus up to +10%)

We're aiming for **tier 6**: model + rules + link/number checks + calibration. We only qualify **once the trained model is running in the app**. Rules alone are not in the tier table.

At the presentation:
- Start with `python app.py` (**with** the model). It prints "Model loaded".
- Have `model_out/dev_report.txt` ready (per-flag calibration).
- Answer to the architect's question: *"Rules and link/number checks against the official list, combined with a Romanian BERT we fine-tuned for the 13 flags, with a calibrated threshold per flag. Everything runs on this laptop; no message leaves it."*

🚫 **Never paste a Test Set message into ChatGPT, Claude or any cloud AI.** That means disqualification.

---

## 6. Test Set procedure (10 minutes before our slot)

1. Copy the Test Set folder onto the laptop.
2. App already running **with the model** → Dashboard → "Încarcă un dosar" → select the folder.
3. Check: 17 rows, the summary line is correct, and clicking a row opens the detail view.
4. Backup: `python evaluate.py <test_folder> --out test_results.csv` (gives the same results).
5. Live re-run: the jury picks one file → open it in **Verifică** → the result must match the dashboard row.

---

## 7. Timeline

| By | Milestone |
|---|---|
| 12:30 | Model trained; Person B has the first 10 test scenarios |
| 13:30 | Model + rules together still 63/63 on validation; lunch |
| 15:30 | GUI and dashboard final; warnings checked with a real person |
| 16:30 | Pitch rehearsed once; full test run with a fresh folder |
| 17:00 | **Code freeze.** Commit, then only run the app |
