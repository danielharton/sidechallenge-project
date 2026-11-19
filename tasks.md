# Scam Shield: tasks and progress

R Systems side challenge, EESTEC LC Bucharest, 19 Nov 2026. **Code freeze: 17:00.**
Repo: https://github.com/danielharton/sidechallenge-project
Last updated: 13:25

---

## 1. Progress so far

| Part | Owner | Status |
|---|---|---|
| Scoring engine: official formula, verdict bands, first alert, re-score after every message (`engine.py`) | B | ✅ done |
| Rules for all 13 red flags + link/phone checks against `data/official_catalog.json` (`engine.py`) | B | ✅ done, **not yet tested on new messages** |
| Fine-tuned Romanian BERT, 13 flags (`train.py`, `model.py`) | Andrei | ✅ trained, lives in `model_out/` **on Andrei's laptop only** (not in git) |
| Per-flag thresholds (`model_out/thresholds.json`) | Andrei | ✅ set to 0.6 (dev scores were all 1.0, so they couldn't pick a threshold) |
| Threshold-selection fix in `train.py` | Andrei | ✅ pushed |
| Synthetic training data generator (`gen_data.py`) | Andrei | ✅ works; ⚠️ **delete line 70** (`"Codul de acces pentru contul de ",`, an unfinished sentence with a wrong label) before the next training run |
| Warnings RO/EN (`warnings_ro.py`) | C | ✅ refined by David; wording kept as written |
| Web app redesign (`static/index.html`) | C | ✅ redesigned by David (dashboard, folder load, detail view, read-aloud, call button, CSV export) |
| Validation Set | all | ✅ **63/63** with rules only and with rules + model |
| Approach tier | Andrei | ✅ qualifies for **tier 6** (model + rules + link/number checks + calibration), **only if the app runs with the model** |

⚠️ **The 63/63 is flattering.** The rules were tuned on those 11 scenarios, and `gen_data.py` contains near-copies of them. Person B's new tests are the only honest measurement.

---

## 2. Remaining work

| By | Who | Task |
|---|---|---|
| 14:00 | Andrei | Add own sentences to `gen_data.py` (genuine "looks dangerous" messages first) |
| 14:00 | Andrei | Second training run → `model_out2/`; keep it only if it beats `model_out/` on Person B's tests |
| 14:30 | **B** | **15–20 new test scenarios + expected CSV (section 3)** |
| 14:30 | C | Read-aloud: use only voices installed on the laptop (`v.localService`). Online "Google" voices in Chrome send the text to a server |
| 15:00 | C | Innovation: **risk timeline** (score per message, line at 25, alert marker) and a **"message a trusted person" SMS button** |
| 15:30 | B | Run tests, fix rules; Validation must stay 63/63 |
| 15:30 | Andrei | **Freeze the model choice.** No training after this |
| 16:00 | C | 3-minute pitch drafted; everyone speaks |
| 16:30 | B | Rehearse the Test Set procedure on Andrei's laptop |
| 17:00 | all | **Code freeze.** Commit, then only run the app |

---

## 3. Task for Person B: write independent test scenarios

> This section is written so an AI assistant can work on it directly.

### Goal
Create **15–20 new test scenarios** that check how our detector does on messages it has never seen, plus an answer key. They mimic the secret Test Set (17 scenarios, 9 of them threads, including threads that build trust before the hook and genuine messages that look dangerous).

### Hard rules
1. 🚫 **Never paste any Test Set message into an AI assistant** (ChatGPT, Claude, Copilot...). That disqualifies the team. This task only uses messages **we invent**.
2. **Write new phrasings.** Don't copy or adapt the messages in `data/validation_set/` or the sentences in `gen_data.py`. A test that repeats training phrases proves nothing.
3. **Decide the expected answers from the rules below, by hand.** Never run the app and copy its output into the answer key.
4. **These files must never be used for training** (never added to `gen_data.py`).
5. All organisations, links and numbers are fictional (see the official list below). Messages in Romanian.

### What to cover

| How many | Kind | Example idea |
|---|---|---|
| 5–6 | **Genuine but dangerous-looking** (expected SAFE) | real bank payment notification; courier "pay cash on delivery", tracking on curierexpres.ro; relative from a *known* contact asking for a lift; ANAF deadline reminder pointing to anaf.ro; bank SMS warning "never share your PIN" |
| 4–5 | **Long threads, 6–10 messages, late hook** | friendly chat, then a money/code/data request at message 6–8 |
| 4–5 | **Scam types not in the validation set** | INVESTMENT (crypto, guaranteed profit), JOB_OFFER (paid likes, then a deposit), ROMANCE, police call asking for bail money, "your account was hacked, give me the code" |
| 2–3 | **Messy input** | no diacritics, all lowercase, typos, emoji, a link written as "bancadunarea punct com" |

### File format: one JSON file per scenario, in `data/my_tests/T01.json`, `T02.json`, ...
```json
{
  "id": "T01",
  "channel": "whatsapp",
  "sender": { "name": null, "address": "+40 7xx xxx 123", "known_contact": false },
  "messages": [
    { "n": 1, "from": "sender", "text": "..." },
    { "n": 2, "from": "reader", "text": "..." }
  ]
}
```
- `channel`: `sms`, `whatsapp`, `email`, `chat` or `call_transcript`
- `from`: `sender` (the possible scammer) or `reader` (the person we protect)
- `known_contact`: `true` if the sender is saved in the reader's contacts
- `n`: numbered from 1, **counting the reader's replies too**
- A single message is a thread of length 1.

### Answer key: `data/my_tests_expected.csv`
Same columns as `data/validation_expected_results.csv`:
```
id,verdict,scam_type,risk_score,first_alert,red_flags
T01,SCAM,FAMILY_EMERGENCY,80,5,RF_NEW_NUMBER;RF_PAYMENT_REQUEST;RF_URGENCY;RF_SECRECY
T02,SAFE,NONE,0,,
```
`first_alert` and `red_flags` are empty for SAFE.

### Official rules for computing the expected answers

**Red flags and points** (count each flag once per scenario):

| Code | Meaning | Points |
|---|---|---|
| RF_CODE_REQUEST | asks for an SMS / OTP / app code | 35 |
| RF_REMOTE_ACCESS | asks to install remote-control software (AnyDesk...) | 35 |
| RF_DATA_REQUEST | asks for card data, PIN, password, CNP, ID photo or bank login | 30 |
| RF_PAYMENT_REQUEST | asks for a transfer, fee, deposit or cash pick-up | 25 |
| RF_SUSPICIOUS_CONTACT | link, sender address or phone number **not** on the official list | 25 |
| RF_NEW_NUMBER | unknown number claims to be family or a friend | 20 |
| RF_SECRECY | asks to keep it secret / not to check with others | 20 |
| RF_TOO_GOOD | prize, refund, guaranteed profit, easy money | 20 |
| RF_IMPERSONATION | claims to be a bank, company, authority, police or support | 15 |
| RF_URGENCY | short deadline or pressure to act now | 15 |
| RF_THREAT | account blocked, fine, prison, virus | 15 |
| RF_EMOTIONAL_PRESSURE | fear, love, pity or panic used as a lever | 10 |
| RF_CHANNEL_SWITCH | moves the conversation to another app | 10 |

**Risk score** = sum of points, **capped at 100**.
**Verdict:** 0–24 SAFE · 25–49 SUSPICIOUS · 50–100 SCAM.
**First alert** (threads): the number of the first message after which the score reaches **25 or more**. Empty for SAFE.
**Scam type** (one): BANK_IMPERSONATION, COURIER_FEE, AUTHORITY_IMPERSONATION, FAMILY_EMERGENCY, TECH_SUPPORT, MARKETPLACE, ACCOUNT_TAKEOVER, INVESTMENT, ROMANCE, JOB_OFFER, PRIZE_LOTTERY, OTHER; **NONE** for SAFE.

**How the answer key treats edge cases** (learned from the validation set):
- A real company named **with** its official link or sender and no request → no flags (SAFE).
- A real company named **with** a fake link, an unknown sender or a request → RF_IMPERSONATION.
- "Do not share this code with anyone" (protective advice) is **not** RF_CODE_REQUEST.
- A known contact asking for a code is still a scam (hacked account): RF_CODE_REQUEST, type ACCOUNT_TAKEOVER.

**Official contacts** (fictional; anything else is unofficial):

| Organisation | Website | Phone |
|---|---|---|
| Banca Dunărea | bancadunarea.ro | 0800 100 200 |
| CurierExpres | curierexpres.ro | 0800 300 400 |
| PiațaMea | piatamea.ro | — |
| MegaShop | megashop.ro | 0800 500 600 |
| ANAF | anaf.ro | 031 403 91 60 |

### Then run and report
```
python evaluate.py data/my_tests data/my_tests_expected.csv --no-model
python evaluate.py data/my_tests data/my_tests_expected.csv
```
- `XX` rows are failures.
- If a **rule pattern** in `engine.py` is the cause → fix it, then re-run the Validation Set (must stay **63/63**).
- If the failure appears **only with the model** (second command, not the first) → tell Andrei; he raises that flag's threshold in `model_out/thresholds.json`.
- If the answer key itself looks debatable, say so instead of bending the code to it.

### Crash tests (in the app)
Empty paste, emoji only, a 5,000-word paste, a JSON file with a missing field, a `.txt` file: nothing may crash.

---

## 4. Test Set procedure (10 minutes before our slot, on Andrei's laptop)

1. App running **with the model**: `python app.py` prints "Model loaded".
2. Copy the Test Set folder onto the laptop → Dashboard → load the folder.
3. Check: 17 rows, the summary line is correct, a row opens the detail view.
4. Backup: `python evaluate.py <test_folder> --out test_results.csv`.
5. Live re-run: the jury picks a file → open it alone → it must match its dashboard row.

🚫 The Test Set never goes into any AI assistant.

---

## 5. Answer to the jury's tier question

*"Rules and link/number checks against the official list, combined with a Romanian BERT we fine-tuned for the 13 red flags, with a calibrated threshold per flag. Everything runs on this laptop; no message leaves it."*

Have `model_out/dev_report.txt` ready to show.