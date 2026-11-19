"""
Fine-tuned red-flag model (multi-label, 13 flags) - inference side.

The engine combines it with the rules:  final flags = rules  OR  (model prob >= threshold[flag]).
Thresholds are calibrated per flag on the dev set by train.py (thresholds.json).
Runs fully offline from the local folder MODEL_DIR. Deterministic (eval mode, no sampling).
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.environ.get("SCAMSHIELD_MODEL", os.path.join(HERE, "model_out"))

FLAGS = ["RF_CODE_REQUEST", "RF_REMOTE_ACCESS", "RF_DATA_REQUEST", "RF_PAYMENT_REQUEST", "RF_SUSPICIOUS_CONTACT",
         "RF_NEW_NUMBER", "RF_SECRECY", "RF_TOO_GOOD", "RF_IMPERSONATION", "RF_URGENCY", "RF_THREAT",
         "RF_EMOTIONAL_PRESSURE", "RF_CHANNEL_SWITCH"]

# flags the rules handle better (exact list lookups) -> model never adds them
RULE_ONLY = {"RF_SUSPICIOUS_CONTACT"}


def build_inputs(scenario):
    """One (context, current) text pair per message; None for reader messages.
    Shared by training and inference so the format is identical."""
    s = scenario.get("sender") or {}
    meta = f"[{scenario.get('channel', 'sms')}] [{'contact cunoscut' if s.get('known_contact') else 'necunoscut'}] [{s.get('name') or ''} | {s.get('address') or ''}]"
    hist = []
    out = []
    for m in scenario.get("messages") or []:
        text = (m.get("text") or "")[:600].replace("ş", "ș").replace("ţ", "ț").replace("Ş", "Ș").replace("Ţ", "Ț")
        if m.get("from", "sender") == "sender":
            ctx = meta + " " + " ".join(hist[-4:])
            out.append((ctx[-700:], text))
        else:
            out.append(None)
        hist.append(("Eu: " if m.get("from") == "reader" else "") + text)
    return out


class FlagModel:
    def __init__(self, model_dir=MODEL_DIR):
        import torch
        from transformers import AutoModelForSequenceClassification, AutoTokenizer
        self.torch = torch
        self.tok = AutoTokenizer.from_pretrained(model_dir, local_files_only=True)
        self.model = AutoModelForSequenceClassification.from_pretrained(model_dir, local_files_only=True)
        self.model.eval()
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.model.to(self.device)
        th_path = os.path.join(model_dir, "thresholds.json")
        self.th = json.load(open(th_path)) if os.path.exists(th_path) else {f: 0.5 for f in FLAGS}

    def probs(self, pairs):
        if not pairs:
            return []
        enc = self.tok([p[0] for p in pairs], [p[1] for p in pairs], truncation="only_first",
                       max_length=256, padding=True, return_tensors="pt").to(self.device)
        with self.torch.no_grad():
            logits = self.model(**enc).logits
        return self.torch.sigmoid(logits).cpu().tolist()

    def predict_thread(self, scenario):
        """list aligned with messages: {flag: True} for flags above threshold, None for reader msgs."""
        inputs = build_inputs(scenario)
        idx = [i for i, x in enumerate(inputs) if x is not None]
        probs = self.probs([inputs[i] for i in idx])
        out = [None] * len(inputs)
        for i, p in zip(idx, probs):
            out[i] = {f: True for f, v in zip(FLAGS, p) if v >= self.th.get(f, 0.5) and f not in RULE_ONLY}
        return out


def load(model_dir=MODEL_DIR):
    if not os.path.isdir(model_dir):
        raise FileNotFoundError(f"no trained model in {model_dir}")
    return FlagModel(model_dir)
