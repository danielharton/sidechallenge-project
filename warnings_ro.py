"""
Plain-language warnings. Template based on purpose:
 - never repeats a link or phone number from the message
 - only official contacts from official_catalog.json (+ generic safe actions)
 - max 3 short sentences: headline, what/why, one action
"""
import re
from engine import norm  # noqa: circular-safe (only used at call time)

HEAD = {
    "SCAM": (
        "Atenție, acest mesaj poate fi o înșelătorie!",
        "Warning, this message may be a scam!"
    ),
    "SUSPICIOUS": (
        "Atenție, verificați înainte să răspundeți!",
        "Careful, check before you reply!"
    ),
}

# why-phrases, in priority order (RO, EN)
WHY = [
    ("RF_REMOTE_ACCESS", "vrea să instalați un program ca să vă controleze calculatorul",
     "it wants you to install a program that controls your device"),
    ("RF_CODE_REQUEST", "vă cere codul primit prin SMS", "it asks for the code you got by SMS"),
    ("RF_DATA_REQUEST", "vă cere datele cardului sau date personale", "it asks for card or personal details"),
    ("RF_SUSPICIOUS_CONTACT", "adresa sau numărul din mesaj sunt false", "the address or number in it is fake"),
    ("RF_PAYMENT_REQUEST", "vă cere bani", "it asks for money"),
    ("RF_SECRECY", "vă cere să păstrați secretul", "it asks you to keep it secret"),
    ("RF_TOO_GOOD", "vă promite câștiguri prea frumoase", "it promises gains that are too good"),
    ("RF_THREAT", "vă sperie cu amenințări", "it scares you with threats"),
    ("RF_URGENCY", "vă grăbește", "it rushes you"),
    ("RF_EMOTIONAL_PRESSURE", "se folosește de emoții", "it plays on your emotions"),
    ("RF_CHANNEL_SWITCH", "vrea să mutați discuția în altă aplicație", "it wants to move to another app"),
]

WHAT = {
    "BANK_IMPERSONATION": ("Mesajul se dă drept banca dumneavoastră", "The message pretends to be your bank"),
    "COURIER_FEE": ("Mesajul se dă drept firma de curierat", "The message pretends to be a courier"),
    "AUTHORITY_IMPERSONATION": ("Cineva se dă drept o instituție a statului", "Someone pretends to be a state authority"),
    "FAMILY_EMERGENCY": ("Cineva {nr}se dă drept {rel}", "Someone {nr_en}pretends to be {rel_en}"),
    "TECH_SUPPORT": ("Cineva se dă drept suport tehnic", "Someone pretends to be tech support"),
    "MARKETPLACE": ("Un fals cumpărător vrea să vă păcălească", "A fake buyer is trying to trick you"),
    "ACCOUNT_TAKEOVER": ("Contul acestei persoane pare să fi fost furat", "This person's account seems to have been stolen"),
    "INVESTMENT": ("Vi se promite profit sigur, dar nimeni nu poate garanta asta", "You are promised sure profit, which nobody can guarantee"),
    "ROMANCE": ("O persoană cunoscută doar pe internet vă cere ajutor", "Someone you only know online is asking for help"),
    "JOB_OFFER": ("Vi se oferă bani ușor pentru o muncă simplă", "You are offered easy money for simple tasks"),
    "PRIZE_LOTTERY": ("Vi se spune că ați câștigat un premiu", "You are told you won a prize"),
    "OTHER": ("Mesajul pare făcut ca să vă păcălească", "The message looks designed to trick you"),
}


def _org_in(text_n):
    if "banca dunarea" in text_n or "bancadunarea" in text_n:
        return "bank"
    if "curierexpres" in text_n or "curier expres" in text_n:
        return "courier"
    if "piatamea" in text_n or "piata mea" in text_n:
        return "piatamea"
    if "megashop" in text_n:
        return "megashop"
    if "anaf" in text_n:
        return "anaf"
    return None


def _name(scenario):
    for m in scenario.get("messages") or []:
        if m.get("from") != "sender":
            continue
        mm = re.search(r"\b(?:Eu|eu|sunt|Sunt)\s*,?\s+([A-ZĂÂÎȘȚ][a-zăâîșț]{2,})", m.get("text") or "")
        if mm and mm.group(1) not in ("Bine", "Doar", "Aici", "Acasa", "Acasă"):
            return mm.group(1)
    nm = ((scenario.get("sender") or {}).get("name") or "").strip()
    if nm and not re.search(r"\d|@", nm):
        return re.sub(r"\s*\(.*\)", "", nm)
    return None


def _action(t, text_n, scenario):
    org = _org_in(text_n + " " + norm((scenario.get("sender") or {}).get("name") or ""))
    name = _name(scenario)
    if t == "BANK_IMPERSONATION":
        return ("Nu apăsați pe nimic și nu dați niciun cod; sunați la numărul de pe spatele cardului.",
                "Do not click anything; call the number on the back of your card.")
    if t == "COURIER_FEE":
        if org == "courier":
            return ("Nu plătiți nimic; verificați coletul doar pe curierexpres.ro sau la 0800 300 400.",
                    "Do not pay; check the parcel only on curierexpres.ro or at 0800 300 400.")
        return ("Nu plătiți nimic; verificați coletul doar pe site-ul oficial al firmei, scris de dumneavoastră.",
                "Do not pay; check the parcel only on the courier's official website.")
    if t == "AUTHORITY_IMPERSONATION":
        if org == "anaf":
            return ("Nu dați nicio informație; sunați chiar dumneavoastră la ANAF, la 031 403 91 60.",
                    "Do not share anything; call ANAF yourself at 031 403 91 60.")
        if "pensi" in text_n:
            return ("Închideți și sunați la Casa de Pensii, la numărul scris pe decizia dumneavoastră de pensie.",
                    "Hang up and call the pension office using the number on your pension papers.")
        if "politi" in text_n:
            return ("Închideți; poliția nu cere bani la telefon, iar dacă sunteți în pericol sunați la 112.",
                    "Hang up; the police never ask for money by phone. If in danger, call 112.")
        return ("Nu dați nicio informație; mergeți sau sunați la instituție, la numărul de pe site-ul ei oficial.",
                "Share nothing; contact the institution using its official website.")
    if t == "FAMILY_EMERGENCY":
        who = name or "persoana"
        return (f"Nu trimiteți bani: sunați-l pe {who} la numărul vechi, pe care îl cunoașteți."
                if name else "Nu trimiteți bani: sunați-vă copilul sau nepotul la numărul vechi, pe care îl cunoașteți.",
                f"Do not send money: call {who} on the old number you know.")
    if t == "TECH_SUPPORT":
        return ("Închideți, nu instalați nimic și, dacă ați intrat în bancă, sunați la numărul de pe spatele cardului.",
                "Hang up, install nothing, and if you opened your bank, call the number on your card.")
    if t == "MARKETPLACE":
        return ("Nu introduceți datele cardului: ca să primiți bani nu trebuie; vorbiți doar în aplicația PiațaMea.",
                "Never enter card details to receive money; talk only inside the PiațaMea app.")
    if t == "ACCOUNT_TAKEOVER":
        who = name or "persoana"
        return (f"Nu trimiteți codul; sunați-o pe {who} la numărul pe care îl știți, ca să verificați.",
                f"Do not send the code; call {who} on the number you know to check.")
    if t == "PRIZE_LOTTERY":
        if org == "megashop":
            return ("Nu răspundeți; dacă vreți să verificați, sunați la MegaShop, la 0800 500 600.",
                    "Do not reply; to check, call MegaShop at 0800 500 600.")
        return ("Nu răspundeți și nu dați date; un premiu adevărat nu vă cere bani sau coduri.",
                "Do not reply or share details; a real prize never asks for money or codes.")
    if t in ("INVESTMENT", "ROMANCE", "JOB_OFFER"):
        return ("Nu trimiteți bani și nu dați datele cardului; vorbiți întâi cu cineva apropiat din familie.",
                "Do not send money or card details; talk to someone in your family first.")
    return ("Nu răspundeți, nu trimiteți bani sau coduri și cereți părerea unei persoane de încredere.",
            "Do not reply or send money or codes; ask someone you trust.")


def make_warning(result, scenario):
    verdict = result["verdict"]
    flags = set(result["red_flags"])
    sender_text = " ".join(m.get("text") or "" for m in scenario.get("messages") or [] if m.get("from") == "sender")
    text_n = norm(sender_text)

    if verdict == "SAFE":
        return ("Mesajul nu vă cere bani, coduri sau date personale. "
                "Dacă aveți totuși dubii, sunați la numărul oficial al  instituției, nu la cel din mesaj.",
                "The message does not ask for money, codes or personal data. "
                "If in doubt, call the official number, not one from the message.")

    t = result["scam_type"]
    rel = "copilul dumneavoastră"
    rel_en = "your child"
    if re.search(r"\b(bunico|bunicule|buni|bunicuto)\b", text_n):
        rel, rel_en = "nepotul dumneavoastră", "your grandchild"
    what_ro, what_en = WHAT.get(t, WHAT["OTHER"])
    newnr = "RF_NEW_NUMBER" in flags
    what_ro = what_ro.format(rel=rel, nr="de pe un număr nou " if newnr else "")
    what_en = what_en.format(rel_en=rel_en, nr_en="on a new number " if newnr else "")

    whys = [(ro, en) for f, ro, en in WHY if f in flags]
    # family: "cere bani urgent, în secret" style already covered by first two whys
    whys = whys[:2]
    if whys:
        conj_ro = " și "
        why_ro = conj_ro.join(w[0] for w in whys)
        why_en = " and ".join(w[1] for w in whys)
        sep = ", dar " if whys[0][0].startswith("adresa") else ": "
        s2_ro = f"{what_ro}{sep}{why_ro}."
        s2_en = f"{what_en}, and {why_en}."
    else:
        s2_ro, s2_en = what_ro + ".", what_en + "."

    act_ro, act_en = _action(t, text_n, scenario)
    return f"{s2_ro} {act_ro}", f"{s2_en} {act_en}"
