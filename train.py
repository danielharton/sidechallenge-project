"""
Fine-tune a pretrained Romanian encoder for multi-label red-flag detection, then
calibrate one threshold per flag on the dev set.

    python train.py                                   # defaults below
    python train.py --base xlm-roberta-base --epochs 3
    python train.py --freeze 6                        # freeze the first 6 layers (faster, still tier 4)

Output: model_out/ (model + tokenizer + thresholds.json + dev_report.txt)
GPU: ~3-6 min.  CPU: ~20-40 min with defaults -> use --max_train 3000 --epochs 2 if short on time.
"""
import argparse
import json
import os
import random
import time

import numpy as np
import torch
from torch.utils.data import DataLoader
from transformers import AutoModelForSequenceClassification, AutoTokenizer, get_linear_schedule_with_warmup

from model import FLAGS, build_inputs


def load_examples(path, limit=None):
    ex = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            sc = json.loads(line)
            for pair, m in zip(build_inputs(sc), sc["messages"]):
                if pair is None:
                    continue
                y = [1.0 if fl in m.get("flags", []) else 0.0 for fl in FLAGS]
                ex.append((pair[0], pair[1], y))
    random.Random(0).shuffle(ex)
    return ex[:limit] if limit else ex


def batches(ex, tok, bs, shuffle):
    def collate(b):
        enc = tok([x[0] for x in b], [x[1] for x in b], truncation="only_first", max_length=256,
                  padding=True, return_tensors="pt")
        enc["labels"] = torch.tensor([x[2] for x in b])
        return enc
    return DataLoader(ex, batch_size=bs, shuffle=shuffle, collate_fn=collate)


def predict(model, loader, device):
    model.eval()
    P, Y = [], []
    with torch.no_grad():
        for b in loader:
            y = b.pop("labels")
            b = {k: v.to(device) for k, v in b.items()}
            P.append(torch.sigmoid(model(**b).logits).cpu().numpy())
            Y.append(y.numpy())
    return np.concatenate(P), np.concatenate(Y)


def f_beta(p, y, th, beta=0.7):
    pred = p >= th
    tp = (pred & (y == 1)).sum(); fp = (pred & (y == 0)).sum(); fn = (~pred & (y == 1)).sum()
    if tp == 0:
        return 0.0, 0, 0, 0
    prec, rec = tp / (tp + fp), tp / (tp + fn)
    return (1 + beta**2) * prec * rec / (beta**2 * prec + rec), prec, rec, tp


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="dumitrescustefan/bert-base-romanian-cased-v1")
    ap.add_argument("--train", default="data/train.jsonl")
    ap.add_argument("--dev", default="data/dev.jsonl")
    ap.add_argument("--out", default="model_out")
    ap.add_argument("--epochs", type=int, default=3)
    ap.add_argument("--bs", type=int, default=16)
    ap.add_argument("--lr", type=float, default=3e-5)
    ap.add_argument("--freeze", type=int, default=0, help="freeze embeddings + first N encoder layers")
    ap.add_argument("--max_train", type=int, default=None)
    a = ap.parse_args()

    torch.manual_seed(0); random.seed(0); np.random.seed(0)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    print("device:", device)

    tok = AutoTokenizer.from_pretrained(a.base)
    model = AutoModelForSequenceClassification.from_pretrained(
        a.base, num_labels=len(FLAGS), problem_type="multi_label_classification",
        id2label=dict(enumerate(FLAGS)), label2id={f: i for i, f in enumerate(FLAGS)}, ignore_mismatched_sizes=True)

    if a.freeze:
        base = getattr(model, model.base_model_prefix)
        for p in base.embeddings.parameters():
            p.requires_grad = False
        for layer in base.encoder.layer[:a.freeze]:
            for p in layer.parameters():
                p.requires_grad = False
        print(f"froze embeddings + {a.freeze} layers")
    model.to(device)

    tr = load_examples(a.train, a.max_train)
    dv = load_examples(a.dev)
    print(f"train {len(tr)} messages, dev {len(dv)} messages")
    tl = batches(tr, tok, a.bs, True)
    dl = batches(dv, tok, 64, False)

    opt = torch.optim.AdamW([p for p in model.parameters() if p.requires_grad], lr=a.lr, weight_decay=0.01)
    steps = len(tl) * a.epochs
    sch = get_linear_schedule_with_warmup(opt, int(0.06 * steps), steps)

    t0 = time.time()
    for ep in range(a.epochs):
        model.train()
        tot = 0
        for i, b in enumerate(tl):
            b = {k: v.to(device) for k, v in b.items()}
            loss = model(**b).loss
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            opt.step(); sch.step(); opt.zero_grad()
            tot += loss.item()
            if i % 50 == 0:
                print(f"ep {ep} step {i}/{len(tl)} loss {tot / (i + 1):.4f}  {time.time() - t0:.0f}s", flush=True)

    # ---- calibrate thresholds per flag on dev (precision-leaning F-beta, since model is OR-ed with rules)
    P, Y = predict(model, dl, device)
    th, report = {}, []
    for j, fl in enumerate(FLAGS):
        scores = [(round(float(t), 2),) + f_beta(P[:, j], Y[:, j], t)[:3] for t in np.arange(0.3, 0.96, 0.05)]
        top = max(sc[1] for sc in scores)
        tied = [sc for sc in scores if sc[1] >= top - 0.002]
        best = tied[len(tied) // 2]   # middle of the best range, not its lowest edge
    print("\n".join(report))

    os.makedirs(a.out, exist_ok=True)
    model.save_pretrained(a.out)
    tok.save_pretrained(a.out)
    json.dump(th, open(os.path.join(a.out, "thresholds.json"), "w"), indent=1)
    open(os.path.join(a.out, "dev_report.txt"), "w").write("\n".join(report))
    print("saved to", a.out, f"in {time.time() - t0:.0f}s")


if __name__ == "__main__":
    main()
