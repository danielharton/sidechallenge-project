"""
Run the detector on a folder and score it like the jury does.

    python evaluate.py data/validation_set data/validation_expected_results.csv
    python evaluate.py some_folder              # no answer key -> just prints the dashboard rows
Options: --no-model   rules only
         --out results.csv
"""
import argparse
import csv
import glob
import os

import engine

# accepted first-alert windows for long threads (from the brief)
WINDOWS = {"V11": (3, 5)}


def run_folder(folder):
    rows = []
    for path in sorted(glob.glob(os.path.join(folder, "*.json"))):
        sc = engine.load_scenario(path)
        r = engine.analyze(sc)
        r["_n_messages"] = len(sc.get("messages") or [])
        rows.append(r)
    rows.sort(key=lambda r: r["id"])
    return rows


def score_row(r, exp):
    pts, mx = 0, 5
    pts += 3 if r["verdict"] == exp["verdict"] else 0
    pts += 2 if r["scam_type"] == exp["scam_type"] else 0
    if exp["verdict"] != "SAFE" and r["_n_messages"] > 1:
        mx += 2
        e = int(exp["first_alert"]) if exp["first_alert"] else None
        lo, hi = WINDOWS.get(r["id"], (e, e))
        got = r["first_alert"]
        if got is not None and lo <= got <= hi:
            pts += 2
        elif got is not None and got == hi + 1:
            pts += 1
    if exp["verdict"] != "SAFE" and r["verdict"] == "SAFE":
        pts -= 3
    return pts, mx


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("folder")
    ap.add_argument("answers", nargs="?")
    ap.add_argument("--no-model", action="store_true")
    ap.add_argument("--out")
    a = ap.parse_args()

    if not a.no_model:
        try:
            import model
            engine.set_model(model.load())
            print("[model loaded]")
        except Exception as e:  # rules-only fallback
            print(f"[rules only: {e}]")

    rows = run_folder(a.folder)
    exp = {}
    if a.answers:
        with open(a.answers, encoding="utf-8-sig") as f:
            exp = {row["id"]: row for row in csv.DictReader(f)}

    total, total_max = 0, 0
    print(f"{'ID':5} {'VERDICT':11} {'TYPE':24} {'SC':>3} {'FA':>3}  FLAGS")
    for r in rows:
        line = f"{r['id']:5} {r['verdict']:11} {r['scam_type']:24} {r['risk_score']:3} {str(r['first_alert'] or ''):>3}  {';'.join(r['red_flags'])}"
        if r["id"] in exp:
            e = exp[r["id"]]
            p, m = score_row(r, e)
            total += p
            total_max += m
            ef = set(filter(None, e["red_flags"].split(";")))
            gf = set(r["red_flags"])
            diff = ""
            if ef != gf:
                diff = f"   flags +{sorted(gf - ef)} -{sorted(ef - gf)}"
            mark = "OK " if p == m else "XX "
            line = mark + line + f"   [{p}/{m}]"
            if p != m:
                line += f"  expected {e['verdict']} {e['scam_type']} fa={e['first_alert']}"
            line += diff
        print(line)
    if exp:
        print(f"\nAccuracy points: {total}/{total_max}  -> {30 * max(total, 0) / total_max:.1f} / 30")

    if a.out:
        with open(a.out, "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(["id", "verdict", "scam_type", "risk_score", "first_alert", "red_flags"])
            for r in rows:
                w.writerow([r["id"], r["verdict"], r["scam_type"], r["risk_score"],
                            r["first_alert"] or "", ";".join(r["red_flags"])])


if __name__ == "__main__":
    main()
