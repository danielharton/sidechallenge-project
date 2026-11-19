
"""
Scam Shield detection engine.

analyze(scenario) -> dict with verdict, scam_type, risk_score, first_alert,
red_flags, per-message details and a Romanian warning.

Detection = rules (always on) + optional fine-tuned model (model.py).
Scoring = the official formula from official_catalog.json.
Deterministic: same input -> same output.
"""

import json
import os
import re
import unicodedata
from urllib.parse import urlsplit

HERE = os.path.dirname(os.path.abspath(__file__))
CATALOG_PATH = os.path.join(HERE, "data", "official_catalog.json")

with open(CATALOG_PATH, encoding="utf-8") as f:
    CATALOG = json.load(f)

POINTS = CATALOG["red_flag_points"]
FLAG_ORDER = list(POINTS.keys())
OFFICIAL = CATALOG["official_contacts"]


# ---------------------------------------------------------------- helpers

def norm(text: str) -> str:
    """
    Normalize ordinary message text for keyword detection.

    This intentionally removes diacritics to support Romanian keyword
    matching. Never use this normalized representation to validate URLs.
    """
    if not text:
        return ""

    t = text.lower()
    t = t.replace("ş", "s").replace("ţ", "t")
    t = t.replace("ș", "s").replace("ț", "t")

    t = unicodedata.normalize("NFKD", t)
    t = "".join(c for c in t if not unicodedata.combining(c))

    t = t.replace("’", "'").replace("‘", "'")

    return re.sub(r"\s+", " ", t).strip()


def digits(s: str) -> str:
    return re.sub(r"\D", "", s or "")


# ---------------------------------------------------------------- URL handling

OFFICIAL_DOMAINS = [
    o["website"].strip().lower().rstrip(".")
    for o in OFFICIAL
    if o.get("website")
]

OFFICIAL_PHONES = {
    digits(o["phone"])
    for o in OFFICIAL
    if o.get("phone")
}

OFFICIAL_IDS = {
    norm(o["organisation"]).replace(" ", "")
    for o in OFFICIAL
}

URL_RE = re.compile(
    r"""(?:
        https?://[^\s<>"']+
        |
        www\.[^\s<>"']+
        |
        (?<![\w@])
        [a-z0-9\u0080-\uffff]
        (?:[a-z0-9\u0080-\uffff-]{0,61}[a-z0-9\u0080-\uffff])?
        (?:\.[a-z0-9\u0080-\uffff-]+)+
        (?:/[^\s<>"']*)?
    )""",
    re.IGNORECASE | re.VERBOSE
)

URL_TRAILING_CHARS = ".,!?;:)]}»”’"

PHONE_RE = re.compile(
    r"(?:\+?40|\b0)[\s.-]?\d{2,3}[\s.-]?\d{3}[\s.-]?\d{2,4}\b"
)


def canonical_hostname(host: str) -> str:
    """
    Convert a hostname to lowercase ASCII IDNA form.

    Returns an empty string for invalid hostnames.
    """
    if not host:
        return ""

    host = host.strip().rstrip(".").lower()

    if any(c.isspace() for c in host):
        return ""

    if any(c in host for c in "/\\@:#"):
        return ""

    try:
        return host.encode("idna").decode("ascii").lower()
    except (UnicodeError, ValueError):
        return ""


def is_official_domain(host: str) -> bool:
    """
    Trust exact official domains and explicitly allowed www aliases.

    Arbitrary subdomains are NOT trusted.
    """
    host = canonical_hostname(host)

    if not host:
        return False

    if host.startswith("www."):
        host = host[4:]

    official = {
        canonical_hostname(domain)
        for domain in OFFICIAL_DOMAINS
    }

    return host in official


def is_official_phone(p: str) -> bool:
    d = digits(p)

    if d.startswith("40"):
        d = "0" + d[2:]

    return d in OFFICIAL_PHONES


def extract_links(text: str):
    """
    Extract hostnames from original text.

    URL extraction must happen before accent-insensitive normalization.
    """
    hosts = []

    for match in URL_RE.finditer(text or ""):
        candidate = match.group(0).rstrip(URL_TRAILING_CHARS)

        if not re.match(r"(?i)^https?://", candidate):
            candidate = "https://" + candidate

        try:
            host = urlsplit(candidate).hostname
        except ValueError:
            host = None

        if host:
            if host and re.search(r"[^\W\d_]", host.rsplit(".", 1)[-1]):
                hosts.append(host)

    return hosts


def extract_phones(text: str):
    return [
        m.group(0).strip()
        for m in PHONE_RE.finditer(text or "")
    ]


def strip_links(text: str) -> str:
    """Remove URLs before ordinary keyword detection."""
    return URL_RE.sub(" ", text or "")


def any_re(patterns, t):
    return any(re.search(p, t) for p in patterns)


# ---------------------------------------------------------------- rule patterns

ORG_CLAIM = [
    r"\bbanc(a|ii|ilor)\b", r"\bbank\b", r"bancadunarea|banca dunarea",
    r"\bcurier", r"\bcolet", r"\bfan ?courier|\bsameday|\bposta romana",
    r"\banaf\b", r"\bpolitia\b|\bpolitie\b|\bpolitist", r"casa de pensii|\bcnpp\b",
    r"\bministerul\b", r"\bprimaria\b", r"\bguvern", r"\bdnsc\b|\bparchet",
    r"suport(ul)? tehnic", r"\bmicrosoft\b|\bwindows\b|\bapple\b",
    r"departamentul (de )?(securitate|fraude|antifrauda)", r"serviciul (clienti|antifrauda)",
    r"\bmegashop\b", r"\bpiatamea\b|\bpiata mea\b", r"\bcurierexpres\b",
    r"\b(va|te) (sun|contactez|contactam) de la\b",
]

# Explicit statements that the sender represents an organization.
# Merely mentioning an organization is not sufficient.
EXPLICIT_ORG_CLAIM = [
    r"\bva sun(am)? de la\b",
    r"\bva contact(am|eaza) din partea\b",
    r"\bva contactez in numele\b",
    r"\bsunt reprezentant(ul|ii)?\b",
    r"\bsuntem reprezentant(ii|ele)?\b",
    r"\bsunt angajat(ul|ii)?\b",
    r"\bsuntem angajat(ii|ele)?\b",
    r"\bfac parte din\b",
    r"\bdepartamentul nostru\b",
    r"\bserviciul nostru\b",
    r"\bnoi suntem de la\b",
    r"\bva contactam in calitate de\b",
]

CODE_WORD = (
    r"\bcod(?:ul|uri|urile)?\b(?!\s*pin)"
    r"|\botp\b"
    r"|\b(?:sms|text message|mesaj)\s+(?:verification|verificare)\s+code\b"
    r"|\bverification code\b"
    r"|\bcod(?:ul)?\s+(?:de\s+)?verificare\b"
    r"|\bcod(?:ul)?\s+(?:primit|din sms|din mesaj)\b"
    r"|\bcod(?:ul)?\s+de\s+autentificare\b"
)

CODE_ASK = [
    r"\bmi-?l\b",
    r"\bmi l\b",
    r"\btrimite(?:ti)?(?:-mi)?\b",
    r"\btrimiti\b",
    r"\bspune(?:ti)?(?:-mi)?\b",
    r"\bcit(?:iti|esti|este|i)\b",
    r"\bdicta\b",
    r"\bcomunica(?:ti)?(?:-mi)?\b",
    r"\btransmite\b",
    r"\bda(?:ti)?-mi\b",
    r"\binapoi\b",
    r"\bsend(?:\s+me)?\b",
    r"\bshare(?:\s+with\s+me)?\b",
    r"\bforward(?:\s+me)?\b",
    r"\bprovide(?:\s+me)?\b",
    r"\btell(?:\s+me)?\b"
]

PROTECTIVE_NEG = (
    r"\bnu (\w+ ){0,2}(comunica|comunicati|spune|spuneti|da|dati|"
    r"trimite|trimiteti|divulga|divulgati|oferi|oferiti|cere|cerem|"
    r"solicita|solicitam|vom cere)\b|nicio(data)?\b.{0,40}"
    r"\b(cerem|solicitam|cere|solicita)"
)

REMOTE = [
    r"anydesk|any desk|teamviewer|team viewer|quicksupport|quick support|rustdesk|ultraviewer|supremo|airdroid",
    r"(acces|control|ajuta\w*) (de )?la distanta",
    r"instal\w* .{0,30}(aplicati|program).{0,40}(distanta|control)"
]

DATA = [
    r"(datele|detaliile|numarul|nr\.?) (de pe )?card(ului|ul)?",
    r"\bcnp", r"\bpin(-?ul)?\b", r"\bparol",
    r"\bcvv|\bcvc|codul de pe (spatele )?card",
    r"\bdata expirarii",
    r"(poza|fotografie|copie|copia) (cu |a |la )?(buletin|act|carte)",
    r"\bbuletin(ul)?\b",
    r"cart(e|ea) de identitate|act(ul)? de identitate",
    r"date(le)? (de )?(logare|autentificare|personale|bancare)",
    r"(user|utilizator)\w* si parol",
    r"(intrati|intra|intrà|logati-va|logheaza-te|autentificati-va|deschideti)\b.{0,25}\bcont",
    r"(confirm|verific|actualiz)\w* datele",
    r"completati (datele|formularul)",
    r"\biban\b"
]

PAYMENT = [
    r"\bachit(ati|a|i)\b", r"\bplatiti\b",
    r"\bsa (platiti|platesti)\b",
    r"\b(faci|face|faceti|facut|trimite|trimiteti|trimiti|vireaza|virati|depune|depuneti)\b.{0,25}\b(transfer|bani|banii|suma|lei|euro|ron|eur)\b",
    r"\btransfer\b.{0,25}\d",
    r"\d[\d.,]*\s*(lei|ron|euro|eur|€).{0,25}\b(transfer|trimit|vira|achit|plat)",
    r"\btaxa\b.{0,40}\d",
    r"\b(avans|garantie|depozit|comision de deblocare)\b",
    r"\b(imprumut\w*|ajuti cu|ajutati cu)\b.{0,20}(bani|lei|euro|\d)",
    r"am nevoie de \d[\d.,]*\s*(lei|euro|eur|ron|€)",
    r"\bcomision\b",
    r"\bpregatiti\b.{0,20}(lei|bani|cash)",
    r"(dai|dati|trimite\w*)\b.{0,12}\b(niste |ceva )?bani\b",
    r"ridic\w* (numerar|banii)",
    r"\b(alimentati|incarcati|cumparati) .{0,20}(card|voucher|cupon)"
]

NEW_NUMBER = [
    r"numar(ul)? (meu )?nou",
    r"am schimbat numarul",
    r"telefonul (meu )?nou",
    r"mi s-?a stricat telefonul",
    r"mi-?am (pierdut|spart|stricat) telefonul",
    r"salveaza-?l\b",
    r"noul (meu )?numar"
]

SECRECY = [
    r"nu-?i spune", r"nu ii spune", r"nu le spune",
    r"nu spune(ti)? (nimanui|la nimeni|familiei|copiilor|lui|nimic)",
    r"sa nu afle", r"(ramane|sa ramana) intre noi",
    r"\be secret\b|\bin secret\b|\bconfidential",
    r"nu (sunati|mergeti|va duceti) la (banca|politie)",
    r"nu inchideti", r"nu vorbiti cu nimeni",
    r"nu (verificati|intrebati)"
]

TOO_GOOD = [
    r"\bcastig\w*", r"\bpremi(u|ul|i|ile)\b", r"\btombola",
    r"\bloteri", r"\bextragere", r"vest(i|e) bun",
    r"(va|vor|o sa) creste", r"\bgarantat", r"\bprofit",
    r"\brandament", r"bani usor|venit pasiv|castig(uri)? usor",
    r"\d+\s*(lei|euro|€|eur)\s*(pe|/)\s*(zi|ora|saptamana)",
    r"ati fost (ales|selectat)", r"\bbonus\b",
    r"\bmostenire\b", r"\bdubl\w* banii"
]

URGENCY = [
    r"\burgent", r"\bimediat\b",
    r"in (\d+ ?h|\d+ ?(de )?(ore|ora|minute|min)\b|24h)",
    r"intr-?o ora",
    r"\bexpira (in|azi|astazi|curand|dupa|intr)|linkul expira|expira intr",
    r"grabiti|grabeste|grabeste-te|repede|cat mai (repede|curand)",
    r"ultima (zi|sansa|ocazie|zi)",
    r"pana (la|in) \d{1,2}[./]\d|pana (la|in) (ora )?\d{1,2}(:\d\d)? (azi|diseara)",
    r"doar (azi|astazi|\d+ (ore|ora|minute))",
    r"la dispozitie \d+", r"(mai aveti|mai ai) doar",
    r"valabil\w* doar", r"pana (azi|astazi|diseara|maine)",
    r"trebuie\b.{0,40}\b(azi|astazi|acum|diseara)\b",
    r"\bacum\b.{0,10}(altfel|sau|ca sa nu)",
    r"in cel mai scurt timp", r"\bfara intarziere"
]

THREAT = [
    r"\bblocat|\bblocare\b|\bblocheaza",
    r"\bsuspendat|\bsuspendare", r"\bamend",
    r"\binchisoare", r"dosar penal|\bpenal\b",
    r"\bvirus", r"\bsechestru|executare silita",
    r"\bpoprire",
    r"(pierde|pierdeti|pierdut) (contul|accesul|banii|economiile)",
    r"\barest", r"\bhack\w*|sparg\w* contul",
    r"(contul|cardul) (dvs|dumneavoastra|tau)? ?(a fost )?(compromis|atacat|clonat)"
]

EMOTIONAL = [
    r"\baccident", r"\bspital", r"\boperati",
    r"sunt retinut|\barestat",
    r"\bplang\b|\bplang\w*", r"disperat",
    r"nu am pe nimeni", r"in pericol",
    r"te iubesc|iubirea mea|dragostea mea",
    r"\bmor\b|o sa mor", r"ma bat|m-au batut",
    r"sunt (la|in) (politie|spital)", r"\bajutor!"
]

CHANNEL = [
    r"\btelegram\b", r"\bsignal\b", r"\bviber\b",
    r"(vorbim|scrie-?mi|scrieti-mi|adauga-?ma|contactati-ma) pe\b",
    r"\bpe whatsapp\b"
]

FAMILY = (
    r"\b(mami|mama|mamico|tati|tata|bunico|bunicule|buni|fiule|"
    r"fiica|nepot\w*|puiule|draga mea mama)\b"
)


# ---------------------------------------------------------------- sender helpers

def sender_is_official(sender: dict) -> bool:
    addr = str((sender or {}).get("address") or "").strip()

    if "@" in addr:
        email_domain = addr.rsplit("@", 1)[1]
        return is_official_domain(email_domain)

    a = norm(addr).replace(" ", "")

    if a in OFFICIAL_IDS:
        return True

    if is_official_phone(addr):
        return True

    return False


def sender_claims_org(sender: dict) -> bool:
    """
    Check whether the sender's displayed identity names an organization.

    This is only an identity claim. Whether it is suspicious is decided
    separately in rule_flags().
    """
    name = norm((sender or {}).get("name") or "")

    if not name or re.match(r"(cumparator|vanzator|client)", name):
        return False

    return any_re(ORG_CLAIM, name) or name.replace(" ", "") in OFFICIAL_IDS or bool(
        re.search(r"\banaf\b", name)
    )


# ---------------------------------------------------------------- per message rules

def rule_flags(text: str, sender: dict, channel: str, context_flags: set, ctx: dict):
    """Return {flag: evidence_string} for one sender message."""

    extracted_hosts = extract_links(text)

    t_nolink = norm(strip_links(text))
    found = {}

    sentences = re.split(r"(?<=[.!?])\s+", t_nolink)

    # Links / phones.
    for host in extracted_hosts:
        if not is_official_domain(host):
            found["RF_SUSPICIOUS_CONTACT"] = host
            break

    if "RF_SUSPICIOUS_CONTACT" not in found:
        for p in extract_phones(text):
            if not is_official_phone(p):
                found["RF_SUSPICIOUS_CONTACT"] = p
                break

    # Code request.
    has_code_word = bool(re.search(CODE_WORD, t_nolink))

    for s in (sentences if has_code_word else []):
        if any_re(CODE_ASK, s):
            neg = re.search(PROTECTIVE_NEG, s)

            if neg and not re.search(
                r"mi-?l\b|trimite-?mi|spune-?mi|\bcititi\b|\binapoi\b",
                s
            ):
                continue

            found["RF_CODE_REQUEST"] = s[:60]
            break

    if any_re(REMOTE, t_nolink):
        found["RF_REMOTE_ACCESS"] = "remote"

    for s in sentences:
        if any_re(DATA, s) and not re.search(PROTECTIVE_NEG, s):
            found["RF_DATA_REQUEST"] = s[:60]
            break

    for s in sentences:
        if (
            any_re(PAYMENT, s)
            and not re.search(PROTECTIVE_NEG, s)
            and not re.search(
                r"\bam (platit|achitat|trimis|virat)\b|ramburs\w*: ?0|poate fi platit",
                s
            )
        ):
            found["RF_PAYMENT_REQUEST"] = s[:60]
            break

    if not (sender or {}).get("known_contact") and any_re(NEW_NUMBER, t_nolink):
        found["RF_NEW_NUMBER"] = "new number"

    for s in sentences:
        if any_re(SECRECY, s) and not re.search(r"\bcod|\bparol|\bpin\b", s):
            found["RF_SECRECY"] = s[:60]
            break

    if any_re(TOO_GOOD, t_nolink):
        found["RF_TOO_GOOD"] = "too good"

    if any_re(URGENCY, t_nolink):
        found["RF_URGENCY"] = "urgency"

    if any_re(THREAT, t_nolink):
        found["RF_THREAT"] = "threat"

    if any_re(EMOTIONAL, t_nolink):
        found["RF_EMOTIONAL_PRESSURE"] = "emotional"

    if any_re(CHANNEL, t_nolink) and not (
        channel == "whatsapp"
        and "telegram" not in t_nolink
        and "signal" not in t_nolink
        and "viber" not in t_nolink
    ):
        found["RF_CHANNEL_SWITCH"] = "channel"

    # Impersonation: require an explicit representation claim in the
    # message, or an organization name in the sender identity.
    t_claim = re.sub(
        r"(sunt|am ajuns|ma aflu|m-au dus) la (politie|spital)|"
        r"nu (sunati|mergeti) la banca|la banca (mea|ta)",
        " ",
        t_nolink
    )

    explicit_claim = any_re(EXPLICIT_ORG_CLAIM, t_claim) or any_re(ORG_CLAIM, t_claim)
    sender_claim = sender_claims_org(sender)

    if explicit_claim or sender_claim:
        ctx["org_claim"] = True

    if ctx.get("org_claim"):
        # A user-confirmed known contact is not treated as an impersonator
        # solely because its display name mentions an organization.
        known_contact = bool((sender or {}).get("known_contact"))

        bad_context = (
            (
                not sender_is_official(sender)
                and not known_contact
            )
            or bool(
                {
                    "RF_SUSPICIOUS_CONTACT",
                    "RF_DATA_REQUEST",
                    "RF_CODE_REQUEST",
                    "RF_PAYMENT_REQUEST",
                    "RF_REMOTE_ACCESS"
                } & (set(found) | context_flags)
            )
        )

        if bad_context:
            found["RF_IMPERSONATION"] = "claims org"

    return found


# ---------------------------------------------------------------- scam type

def pick_type(all_text_n: str, flags: set, sender: dict) -> str:
    t = all_text_n + " " + norm((sender or {}).get("name") or "")

    if "RF_REMOTE_ACCESS" in flags or re.search(
        r"suport(ul)? tehnic|\bvirus|\bmicrosoft|\bwindows\b",
        t
    ):
        return "TECH_SUPPORT"

    if "RF_CODE_REQUEST" in flags and re.search(
        r"din greseala|gresit|\bmi-?l (poti|puteti) trimite",
        t
    ):
        return "ACCOUNT_TAKEOVER"

    if "RF_NEW_NUMBER" in flags or (
        re.search(FAMILY, t)
        and flags & {"RF_PAYMENT_REQUEST", "RF_EMOTIONAL_PRESSURE"}
    ):
        return "FAMILY_EMERGENCY"

    if re.search(
        r"cumparator|vanzator|\banunt|mai (este|e) disponibil|"
        r"primiti banii|primesti banii|piatamea|livrare sigura",
        t
    ):
        return "MARKETPLACE"

    if re.search(r"\bcurier|\bcolet|\bawb\b|livrar|reprogramare", t):
        return "COURIER_FEE"

    if re.search(
        r"\banaf\b|politi|casa de pensii|\bpensi|minister|primari|"
        r"guvern|amend|impozit|contribuabil|\bfisc|ghiseul|\bdnsc\b",
        t
    ):
        return "AUTHORITY_IMPERSONATION"

    if re.search(r"\bbanc|\bcard|\bcont(ul)? (bancar|dvs)", t):
        return "BANK_IMPERSONATION"

    if re.search(
        r"investi|crypto|cripto|bitcoin|randament|profit|"
        r"actiuni|trading|platforma de",
        t
    ):
        return "INVESTMENT"

    if re.search(
        r"\bjob\b|loc de munca|angaj|like-?uri|\bsarcin|\btask|"
        r"recenzi|lucru de acasa",
        t
    ):
        return "JOB_OFFER"

    if re.search(
        r"iubi|dragul meu|draga mea|dragoste|\bdor\b|sa ne vedem|bilet de avion",
        t
    ):
        return "ROMANCE"

    if re.search(r"tombola|castig|premi|loteri|extragere", t):
        return "PRIZE_LOTTERY"

    if re.search(r"\bcod", t) and "RF_CODE_REQUEST" in flags:
        return "ACCOUNT_TAKEOVER"

    return "OTHER"


def verdict_for(score: int) -> str:
    for v, (lo, hi) in CATALOG["verdict_bands"].items():
        if lo <= score <= hi:
            return v

    return "SCAM"


def score_of(flags) -> int:
    return min(100, sum(POINTS[f] for f in set(flags)))


# ---------------------------------------------------------------- main entry

_MODEL = None


def set_model(model):
    """Plug a trained model (see model.py)."""
    global _MODEL
    _MODEL = model


def analyze(scenario: dict, use_model: bool = True) -> dict:
    sender = scenario.get("sender") or {}
    channel = scenario.get("channel") or "sms"
    messages = scenario.get("messages") or []

    ctx = {}
    flags_so_far = {}
    per_message = []
    first_alert = None

    model_probs = None

    if use_model and _MODEL is not None:
        model_probs = _MODEL.predict_thread(scenario)

    for i, m in enumerate(messages):
        n = m.get("n", i + 1)
        text = m.get("text") or ""
        new = {}

        if m.get("from", "sender") == "sender":
            found = rule_flags(
                text,
                sender,
                channel,
                set(flags_so_far),
                ctx
            )

            if model_probs is not None and model_probs[i]:
                for flag, ok in model_probs[i].items():
                    if ok and flag not in found:
                        found[flag] = "model"

            for f, ev in found.items():
                if f not in flags_so_far:
                    flags_so_far[f] = n
                    new[f] = ev

        score = score_of(flags_so_far)

        if first_alert is None and score >= 25:
            first_alert = n

        per_message.append({
            "n": n,
            "from": m.get("from", "sender"),
            "text": text,
            "new_flags": sorted(new, key=FLAG_ORDER.index),
            "score_after": score
        })

    score = score_of(flags_so_far)
    verdict = verdict_for(score)

    flags_sorted = sorted(
        flags_so_far,
        key=lambda f: (flags_so_far[f], FLAG_ORDER.index(f))
    )

    all_text = norm(
        " ".join(
            m.get("text") or ""
            for m in messages
            if m.get("from", "sender") == "sender"
        )
    )

    scam_type = (
        "NONE"
        if verdict == "SAFE"
        else pick_type(all_text, set(flags_so_far), sender)
    )

    from warnings_ro import make_warning

    result = {
        "id": scenario.get("id", ""),
        "verdict": verdict,
        "scam_type": scam_type,
        "risk_score": score,
        "first_alert": first_alert if verdict != "SAFE" else None,
        "red_flags": flags_sorted,
        "flag_message": flags_so_far,
        "messages": per_message
    }

    result["warning_ro"], result["warning_en"] = make_warning(
        result,
        scenario
    )

    return result


def analyze_text(
    text: str,
    channel: str = "whatsapp",
    known_contact: bool = False
) -> dict:
    """
    For the paste box.

    One message per non-empty line starting with '>' = reader reply;
    otherwise the whole text is one message.
    """
    text = text or ""

    lines = [
        l for l in text.splitlines()
        if l.strip()
    ]

    msgs = []

    if len(lines) > 1 and any(
        l.lstrip().startswith((">", "Eu:", "Reader:"))
        for l in lines
    ):
        for l in lines:
            s = l.strip()

            who = (
                "reader"
                if s.startswith((">", "Eu:", "Reader:"))
                else "sender"
            )

            s = re.sub(r"^(>|Eu:|Reader:)\s*", "", s)

            msgs.append({
                "n": len(msgs) + 1,
                "from": who,
                "text": s
            })

    else:
        msgs = [{
            "n": 1,
            "from": "sender",
            "text": text.strip()
        }]

    return analyze({
        "id": "PASTE",
        "channel": channel,
        "sender": {
            "name": None,
            "address": None,
            "known_contact": known_contact
        },
        "messages": msgs
    })


def load_scenario(path: str) -> dict:
    with open(path, encoding="utf-8-sig") as f:
        return json.load(f)
