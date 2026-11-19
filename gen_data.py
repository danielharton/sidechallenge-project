"""
Synthetic Romanian training data for the red-flag model.

    python gen_data.py --n 4000 --out data/train.jsonl --seed 1
    python gen_data.py --n 600  --out data/dev.jsonl   --seed 2

Each line = one scenario in the official JSON format plus labels:
  messages[i]["flags"] = red flags that message adds (sender messages only)
  "scam_type" = official type.
Built from snippet pools; ~40% are genuine scenarios (hard negatives) because
over-alarming genuine messages costs points.
Add your own snippets to the pools: more variety = better model.
"""
import argparse
import json
import random

R = random.Random(1)

BANKS = ["Banca Dunărea", "Banca Dunarea"]
FAKE_TLDS = [".com", ".net", ".info", ".online", ".site", ".xyz", ".top", "-ro.com", ".ro-secure.com"]


def fake_link(base):
    suf = R.choice(["securitate", "verificare", "plati", "livrare", "ajutor", "cont", "deblocare", "online",
                    "rambursari", "formular", "suport", "premii", "login", "confirmare"])
    return R.choice([f"{base}-{suf}{R.choice(FAKE_TLDS)}", f"{suf}-{base}{R.choice(FAKE_TLDS)}",
                     f"{base}{R.choice(FAKE_TLDS)}/{suf}", f"bit.ly/{base[:3]}{R.randint(10, 99)}x",
                     f"www.{base}-{suf}.com"])


def phone_unofficial():
    return R.choice(["07", "+40 7"]) + f"{R.randint(20, 89)} {R.randint(100, 999)} {R.randint(100, 999)}"


def money():
    return R.choice(["1.850", "2.500", "750", "3.000", "1.200", "499", "4.300", "900", "15.000", "2,99", "5,49", "12"])


NAMES_M = ["Andrei", "Mihai", "Alex", "Radu", "Vlad", "Ionuț", "Cristi", "Bogdan"]
NAMES_F = ["Ioana", "Maria", "Elena", "Ana", "Cristina", "Diana", "Raluca", "Andreea"]
REL = ["mami", "mama", "tati", "bunico", "mamă"]


def F(*flags):
    return set(flags)


# --------------------------------------------------------------- flag snippets
def s_impersonation(org):
    return R.choice([
        f"{org}: Stimate client,",
        f"Bună ziua, vă sun de la {org}, departamentul de securitate.",
        f"Bună ziua, sunt {R.choice(NAMES_F)} de la {org}.",
        f"Vă contactăm din partea {org}.",
        f"Mesaj oficial {org}:",
        f"Aici serviciul clienți {org}.",
    ])


AUTHS = ["ANAF", "Poliția Română", "Casa de Pensii", "Ministerul Finanțelor", "Primăria Sectorului 3", "DNSC"]

POOL = {
    "RF_THREAT": [
        "Cardul dvs. a fost blocat temporar din motive de securitate.",
        "Contul dvs. a fost suspendat.", 
        "Am detectat o tranzacție suspectă, contul va fi blocat.",
        "Aveți o amendă neplătită și riscați executare silită.", 
        "Calculatorul dvs. este infectat cu viruși.",
        "Pe numele dvs. a fost deschis un dosar penal.", 
        "Contul dumneavoastră a fost compromis.",
        "Dacă nu răspundeți, veți pierde accesul la cont.", 
        "Telefonul dvs. a fost accesat de persoane neautorizate. Apăsați pe următorul link pentru a-l recupera",
    ],
    "RF_URGENCY": [
        "Aveți la dispoziție 24 de ore.", "Este urgent!", "Accesați în 24h:", "Grăbiți-vă, linkul expiră într-o oră.",
        "Oferta este valabilă doar azi.", "Trebuie rezolvat imediat.", "Aveți timp până la 30.11.",
        "Mai aveți doar 2 ore.", "Ultima zi pentru confirmare!", "Cât mai repede, vă rog!",
    ],
    "RF_PAYMENT_REQUEST": [
        "Achitați taxa de reprogramare de {m} lei:", "Poți să-mi faci un transfer de {m} lei?",
        "Trebuie să plătiți o taxă de {m} lei pentru eliberare.", "Trimite-mi te rog {m} lei, ți-i dau înapoi mâine.",
        "Virați suma de {m} lei în contul indicat.", "Depuneți un avans de {m} lei ca garanție.",
        "Mă împrumuți cu {m} lei până luni?", "Pentru deblocare se percepe un comision de {m} lei.",
        "Vine un curier să ridice banii, pregătiți {m} lei cash.",
    ],
    "RF_DATA_REQUEST": [
        "Completați datele cardului pe:", "Introduceți datele cardului aici:", "Am nevoie de CNP-ul dvs. și seria de buletin.",
        "Spuneți-mi numărul cardului și data expirării.", "Trimiteți o poză cu buletinul.",
        "Confirmați numele de utilizator și parola contului.", "Acum intrați în contul de bancă, să verificăm.",
        "Spuneți-mi codul PIN, ca să verificăm cardul.", "Trebuie să vă confirmăm datele personale și bancare.",
        "Scrieți-mi codul CVV de pe spatele cardului.",
    ],
    "RF_CODE_REQUEST": [
        "Trimite-mi codul primit prin SMS.", "Vă trimit un SMS cu un cod, pe care mi-l citiți.",
        "Ți-am trimis din greșeală un cod de 6 cifre. Mi-l poți trimite înapoi?",
        "Spuneți-mi codul de confirmare pe care tocmai l-ați primit.", "Dictați-mi codul din SMS, vă rog.",
        "Ai primit un cod pe WhatsApp, dă-mi-l și mie te rog.",
    ],
    "RF_REMOTE_ACCESS": [
        "Instalați programul AnyDesk, ca să vă putem ajuta de la distanță.", "Descărcați TeamViewer și dați-mi numărul afișat.",
        "Instalați aplicația QuickSupport din magazin.", "Trebuie să ne dați acces la distanță la calculator.",
        "Descărcați RustDesk, e gratuit, ca să rezolvăm problema.",
    ],
    "RF_NEW_NUMBER": [
        "Bună {rel}, mi s-a stricat telefonul, ăsta e numărul nou. Salvează-l", "{rel}, am schimbat numărul, ăsta e cel nou.",
        "Salut {rel}, mi-am pierdut telefonul, scrie-mi aici pe numărul nou.", "Bună, sunt eu, {rel}, am număr nou.",
    ],
    "RF_SECRECY": [
        "Te rog nu-i spune lui tata, se enervează.", "Nu spuneți nimănui, e confidențial.", "Să rămână între noi.",
        "Nu sunați la bancă, ancheta e secretă.", "Nu închideți telefonul și nu vorbiți cu nimeni.",
        "Să nu afle nimeni din familie.",
    ],
    "RF_TOO_GOOD": [
        "Felicitări! Ați câștigat un telefon nou la tombolă.", "Ați fost ales câștigătorul unui premiu de 3.000 €.",
        "Vești bune: pensia dumneavoastră va crește cu 312 lei.", "Profit garantat de 20% pe lună.",
        "Câștigi 300 lei pe zi din like-uri.", "Randament garantat, recomandat de experți.",
        "Ai primit un bonus de 500 lei.", "Bani ușor, lucrezi de acasă.",
        "Persoanele pensionare primesc reduceri garantate.",
        "Ai fost selectat pentru tombola noastră.",
        "Obține credit fără risc adresa următoare.",
        "Îți dublezi banii în câteva zile, fără niciun risc.",
    ],
    "RF_EMOTIONAL_PRESSURE": [
        "Am făcut un accident și sunt la poliție.", "Sunt la spital, am nevoie de o operație.",
        "Sunt disperat, nu am pe nimeni altcineva.", "Te iubesc și am nevoie de tine.", "Plâng de o oră, ajută-mă.",
    ],
    "RF_CHANNEL_SWITCH": [
        "Hai să vorbim pe Telegram.", "Scrie-mi pe Signal, e mai sigur.", "Adaugă-mă pe Telegram la @{h}.",
        "Continuăm pe Viber.",
    ],
}

BENIGN_SENDER = [
    "Bună ziua!", "Ce mai faceți?", "Eu, {n}, cine să fie 😅", "Mulțumesc frumos.", "Bine, înțeleg.",
    "Bună ziua, mai este disponibilă bicicleta?", "Facem o actualizare a dosarelor.", "Vă mulțumim pentru răbdare.",
    "Cum vă simțiți azi?", "Bună, ce faci? Mi-a fost dor de tine.",
]
READER = ["Cine e?", "Da, bună ziua.", "Cum adică? Ce trebuie să fac?", "Bine, l-am instalat.", "Ce date?", "Da, este.",
          "A, bine, puiule.", "Nu înțeleg.", "Ok.", "Și ce trebuie să fac?", "Sigur?", "Bună!"]


def fill(text, ctx):
    return (text.replace("{m}", money()).replace("{rel}", ctx.get("rel", "mami"))
            .replace("{n}", ctx.get("name", "Andrei")).replace("{h}", f"user{R.randint(100, 999)}"))


def msg(parts, ctx):
    """parts = list of (text, flags)."""
    text = " ".join(fill(t, ctx) for t, _ in parts)
    flags = set()
    for _, f in parts:
        flags |= f
    return {"from": "sender", "text": text, "flags": sorted(flags)}


def pick(flag):
    return (R.choice(POOL[flag]), F(flag))


def link_part(base):
    return (fake_link(base), F("RF_SUSPICIOUS_CONTACT"))


# --------------------------------------------------------------- scam builders
def scam_bank(ctx):
    ch = R.choice(["sms", "call_transcript", "email", "whatsapp"])
    org = R.choice(BANKS)
    if ch in ("sms", "email"):
        parts = [(f"{org}:", F("RF_IMPERSONATION")), pick("RF_THREAT")]
        if R.random() < .6: parts.append(pick("RF_URGENCY"))
        if R.random() < .5: parts.append(pick("RF_DATA_REQUEST"))
        parts.append(link_part("bancadunarea"))
        sender = {"name": R.choice(["BancaDunarea", None]), "address": R.choice(["BancaDunarea", phone_unofficial()]),
                  "known_contact": False}
        if ch == "email":
            sender = {"name": "Banca Dunărea", "address": f"alerta@{fake_link('bancadunarea').split('/')[0]}", "known_contact": False}
        return ch, sender, [msg(parts, ctx)], "BANK_IMPERSONATION"
    msgs = [msg([(s_impersonation(org), F("RF_IMPERSONATION"))], ctx), {"from": "reader", "text": R.choice(READER)},
            msg([pick("RF_THREAT")], ctx), {"from": "reader", "text": R.choice(READER)},
            msg([R.choice([pick("RF_CODE_REQUEST"), pick("RF_DATA_REQUEST"), pick("RF_PAYMENT_REQUEST")])]
                + ([pick("RF_SECRECY")] if R.random() < .4 else []), ctx)]
    return "call_transcript", {"name": None, "address": phone_unofficial(), "known_contact": False}, msgs, "BANK_IMPERSONATION"


def scam_courier(ctx):
    parts = [(R.choice(["CurierExpres:", "FAN Courier:", "Coletul dvs.", "CurierExpres: Coletul dvs."]), F("RF_IMPERSONATION")),
             (R.choice(["nu a putut fi livrat.", "este reținut în depozit.", "așteaptă confirmarea adresei."]), F()),
             pick("RF_PAYMENT_REQUEST"), link_part(R.choice(["curierexpres", "colet", "livrare"]))]
    if R.random() < .4: parts.insert(2, pick("RF_URGENCY"))
    return "sms", {"name": None, "address": phone_unofficial(), "known_contact": False}, [msg(parts, ctx)], "COURIER_FEE"


def scam_authority(ctx):
    auth = R.choice(AUTHS)
    if R.random() < .5:
        parts = [("Stimate contribuabil, aveți de primit o rambursare de {m} lei." if auth == "ANAF" else f"{auth}: aveți o amendă neplătită.",
                  F("RF_IMPERSONATION") | (F() if auth == "ANAF" else F("RF_THREAT"))),
                 pick("RF_DATA_REQUEST") if R.random() < .6 else pick("RF_PAYMENT_REQUEST"), pick("RF_URGENCY"),
                 link_part(R.choice(["anaf", "ghiseul", "politia", "amenzi"]))]
        sender = {"name": f"{auth} Notificări", "address": f"notificari@{fake_link('anaf').split('/')[0]}", "known_contact": False}
        return "email", sender, [msg(parts, ctx)], "AUTHORITY_IMPERSONATION"
    msgs = [msg([(f"Bună ziua, doamnă. Sunt {R.choice(NAMES_F)}, de la {auth}.", F("RF_IMPERSONATION")),
                 ("Facem o actualizare a dosarelor.", F())], ctx),
            {"from": "reader", "text": R.choice(READER)},
            msg([pick("RF_TOO_GOOD") if R.random() < .5 else pick("RF_THREAT")], ctx),
            {"from": "reader", "text": "Ce date?"},
            msg([pick("RF_DATA_REQUEST")] + ([pick("RF_CODE_REQUEST")] if R.random() < .5 else []), ctx)]
    return "call_transcript", {"name": None, "address": phone_unofficial(), "known_contact": False}, msgs, "AUTHORITY_IMPERSONATION"


def scam_family(ctx):
    ctx["name"] = R.choice(NAMES_M + NAMES_F)
    ctx["rel"] = R.choice(REL)
    msgs = [msg([pick("RF_NEW_NUMBER")], ctx), {"from": "reader", "text": "Cine e?"},
            msg([(R.choice(BENIGN_SENDER[2:3]), F())], ctx), {"from": "reader", "text": R.choice(["A, bine, puiule.", "Ok, salvat."])}]
    hook = [pick("RF_PAYMENT_REQUEST")]
    if R.random() < .5: hook.insert(0, pick("RF_EMOTIONAL_PRESSURE"))
    if R.random() < .5: hook.append(pick("RF_URGENCY"))
    msgs.append(msg(hook, ctx))
    if R.random() < .6: msgs.append(msg([pick("RF_SECRECY")], ctx))
    return "whatsapp", {"name": None, "address": phone_unofficial(), "known_contact": False}, msgs, "FAMILY_EMERGENCY"


def scam_tech(ctx):
    msgs = [msg([(R.choice(["Bună ziua, vă sun de la suportul tehnic Windows.", "Bună ziua, sunt de la Microsoft."]), F("RF_IMPERSONATION")),
                 pick("RF_THREAT")], ctx),
            {"from": "reader", "text": "Cum adică? Ce trebuie să fac?"}, msg([pick("RF_REMOTE_ACCESS")], ctx),
            {"from": "reader", "text": "Bine, l-am instalat."}, msg([pick("RF_DATA_REQUEST")], ctx)]
    return "call_transcript", {"name": None, "address": phone_unofficial(), "known_contact": False}, msgs, "TECH_SUPPORT"


def scam_marketplace(ctx):
    item = R.choice(["bicicleta", "canapeaua", "telefonul", "căruciorul", "mașina de spălat"])
    msgs = [msg([(f"Bună ziua, mai este disponibil(ă) {item}?", F())], ctx), {"from": "reader", "text": "Da, este."},
            msg([("O iau. Am plătit deja prin Livrare Sigură. Ca să primiți banii,", F()), pick("RF_DATA_REQUEST"),
                 link_part("piatamea")], ctx)]
    if R.random() < .7: msgs.append(msg([pick("RF_URGENCY")], ctx))
    if R.random() < .3: msgs.append(msg([pick("RF_CHANNEL_SWITCH")], ctx))
    return "chat", {"name": "Cumpărător PiațaMea", "address": f"buyer_{R.randint(10000, 99999)}", "known_contact": False}, msgs, "MARKETPLACE"


def scam_takeover(ctx):
    ctx["name"] = R.choice(NAMES_F + NAMES_M)
    parts = [("Bună! Ți-am trimis din greșeală un cod de 6 cifre prin SMS. Mi-l poți trimite înapoi, te rog?", F("RF_CODE_REQUEST"))]
    if R.random() < .7: parts.append(("E urgent!", F("RF_URGENCY")))
    return "whatsapp", {"name": f"{ctx['name']} (vecina)", "address": phone_unofficial(), "known_contact": True}, [msg(parts, ctx)], "ACCOUNT_TAKEOVER"


def scam_prize(ctx):
    parts = [(R.choice(["Felicitări! Ați câștigat", "Ați fost ales câștigătorul", "Felicitări, ați fost extras câștigător"]), F("RF_TOO_GOOD")),
             (R.choice(["unui telefon nou", "unui voucher de 1.000 lei", "unei vacanțe"]) + R.choice([" la tombola MegaShop.", " oferit de MegaShop."]), F("RF_IMPERSONATION")),
             ("Răspundeți cu DA pentru detalii.", F())]
    if R.random() < .5:
        parts += [pick("RF_PAYMENT_REQUEST"), link_part("megashop")]
    return "sms", {"name": None, "address": phone_unofficial(), "known_contact": False}, [msg(parts, ctx)], "PRIZE_LOTTERY"


def scam_investment(ctx):
    msgs = [msg([("Bună ziua, vă prezentăm o platformă de investiții crypto.", F()), pick("RF_TOO_GOOD")], ctx),
            {"from": "reader", "text": "Și ce trebuie să fac?"},
            msg([("Depuneți doar 1.000 lei ca să începeți.", F("RF_PAYMENT_REQUEST")), pick("RF_URGENCY")]
                + ([pick("RF_CHANNEL_SWITCH")] if R.random() < .5 else []), ctx)]
    return "whatsapp", {"name": None, "address": phone_unofficial(), "known_contact": False}, msgs, "INVESTMENT"


def scam_job(ctx):
    msgs = [msg([("Bună! Avem un job de acasă:", F()), ("Câștigi 300 lei pe zi din like-uri.", F("RF_TOO_GOOD"))], ctx),
            {"from": "reader", "text": "Sună bine."},
            msg([("Ca să intri în grupul VIP plătești o garanție de 200 lei.", F("RF_PAYMENT_REQUEST"))]
                + ([pick("RF_CHANNEL_SWITCH")] if R.random() < .6 else []), ctx)]
    return "whatsapp", {"name": None, "address": phone_unofficial(), "known_contact": False}, msgs, "JOB_OFFER"


def scam_romance(ctx):
    msgs = [msg([("Bună, ce faci? Mi-a fost dor de tine.", F())], ctx), {"from": "reader", "text": "Bine, tu?"},
            msg([pick("RF_EMOTIONAL_PRESSURE")], ctx), {"from": "reader", "text": "Ce s-a întâmplat?"},
            msg([("Am nevoie de 2.000 euro pentru biletul de avion ca să vin la tine.", F("RF_PAYMENT_REQUEST"))]
                + ([pick("RF_SECRECY")] if R.random() < .5 else []), ctx)]
    return "chat", {"name": R.choice(["John", "Michael Army", "Dr. Robert"]), "address": f"user_{R.randint(1000, 9999)}", "known_contact": False}, msgs, "ROMANCE"


SCAMS = [scam_bank, scam_courier, scam_authority, scam_family, scam_tech, scam_marketplace, scam_takeover,
         scam_prize, scam_investment, scam_job, scam_romance]


# --------------------------------------------------------------- genuine (hard negatives)
GENUINE_SINGLE = [
    ("sms", "BancaDunarea", "Banca Dunărea: Codul de confirmare este {c}. Nu comunicați acest cod nimănui, nici angajaților băncii."),
    ("sms", "BancaDunarea", "Banca Dunărea: Plata de {m} lei la MegaShop a fost efectuată cu cardul ****{d4}."),
    ("sms", "BancaDunarea", "Banca Dunărea nu vă cere niciodată PIN-ul, parola sau codurile primite prin SMS."),
    ("sms", "BancaDunarea", "Banca Dunărea: Cardul dvs. expiră luna viitoare. Cardul nou poate fi ridicat de la agenție."),
    ("sms", "CurierExpres", "CurierExpres: Coletul AWB {c} va fi livrat azi, între 12:00 și 16:00. Ramburs: 0 lei. Urmărire: curierexpres.ro/awb"),
    ("sms", "CurierExpres", "CurierExpres: Coletul dvs. a fost livrat. Mulțumim! Detalii pe curierexpres.ro"),
    ("sms", "MegaShop", "MegaShop: Comanda dvs. #{c} a fost expediată. Detalii pe megashop.ro."),
    ("sms", "MegaShop", "MegaShop: -20% la electrocasnice în acest weekend, doar pe megashop.ro."),
    ("email", "notificari@anaf.ro", "ANAF: Declarația unică poate fi depusă online pe anaf.ro până la 25 mai."),
    ("sms", "BancaDunarea", "Banca Dunărea: Codul dvs. de autentificare în aplicație este {c}. Valabil 5 minute."),
    ("whatsapp", None, "La mulți ani! Multă sănătate și bucurii!"),
    ("whatsapp", None, "Mama, ajung diseară pe la 8, pregătește ceva de mâncare 😊"),
    ("whatsapp", None, "Am plătit eu factura la curent, nu-ți face griji."),
    ("whatsapp", None, "Bunico, ți-am trimis pozele cu copiii, te pup!"),
    ("whatsapp", None, "Ne vedem mâine la 10 la piață?"),
    ("sms", None, "Programarea dvs. la medicul de familie este mâine la ora 9:30."),
    ("whatsapp", None, "Urgent: nu uita să iei pâine când vii acasă!"),
    ("whatsapp", None, "Am ajuns cu bine, mulțumesc pentru tot."),
    ("chat", None, "Bună ziua, mai este disponibilă bicicleta? Pot veni mâine s-o văd și plătesc cash la predare."),
    ("sms", None, "Vă reamintim că factura la gaz de {m} lei poate fi plătită la ghișeu sau online până la 15 ale lunii."),
]

GENUINE_THREADS = [
    ["Bună, ce faci? Vii duminică la masă?", "R:Da, sigur. Ce să aduc?", "Nimic, doar pe tine 😊 Te așteptăm la 1."],
    ["Bună ziua, mai este disponibilă canapeaua?", "R:Da, este.", "Super, pot veni sâmbătă s-o iau. Plătesc cash la predare.",
     "R:Perfect, vă aștept.", "Mulțumesc, ne vedem sâmbătă."],
    ["Mami, am luat 10 la examen!", "R:Bravo, puiule! Sunt mândră de tine.", "Vin acasă vineri 😊"],
    ["Bună ziua, sunt de la cabinetul dr. Popescu.", "R:Da, bună ziua.", "Vă confirmăm programarea de joi la ora 10. Vă rugăm să aduceți analizele."],
    ["Salut! Ai văzut meciul aseară?", "R:Da, ce meci!", "Data viitoare îl vedem împreună."],
]


def genuine(ctx):
    if R.random() < .55:
        ch, addr, text = R.choice(GENUINE_SINGLE)
        text = text.replace("{c}", str(R.randint(100000, 9999999))).replace("{m}", money()).replace("{d4}", str(R.randint(1000, 9999)))
        known = addr is None and R.random() < .8
        sender = {"name": addr, "address": addr or phone_unofficial(), "known_contact": known}
        return ch, sender, [{"from": "sender", "text": text, "flags": []}], "NONE"
    th = R.choice(GENUINE_THREADS)
    msgs = [{"from": "reader", "text": t[2:]} if t.startswith("R:") else {"from": "sender", "text": t, "flags": []} for t in th]
    return R.choice(["whatsapp", "chat"]), {"name": R.choice(NAMES_F + NAMES_M + [None]), "address": phone_unofficial(),
                                            "known_contact": R.random() < .7}, msgs, "NONE"


def strip_diacritics(s):
    for a, b in zip("ăâîșțşţĂÂÎȘȚ", "aaistssAAIST"):
        s = s.replace(a, b)
    return s


def make(i):
    ctx = {}
    builder = genuine if R.random() < .4 else R.choice(SCAMS)
    ch, sender, msgs, typ = builder(ctx)
    no_dia = R.random() < .25
    out = []
    for k, m in enumerate(msgs, 1):
        t = strip_diacritics(m["text"]) if no_dia else m["text"]
        if R.random() < .15:
            t = t.lower()
        mm = {"n": k, "from": m["from"], "text": t}
        if m["from"] == "sender":
            mm["flags"] = m.get("flags", [])
        out.append(mm)
    return {"id": f"S{i:05d}", "channel": ch, "sender": sender, "messages": out, "scam_type": typ}


def main():
    global R
    ap = argparse.ArgumentParser()
    ap.add_argument("--n", type=int, default=4000)
    ap.add_argument("--out", default="data/train.jsonl")
    ap.add_argument("--seed", type=int, default=1)
    a = ap.parse_args()
    R = random.Random(a.seed)
    with open(a.out, "w", encoding="utf-8") as f:
        for i in range(a.n):
            f.write(json.dumps(make(i), ensure_ascii=False) + "\n")
    print("wrote", a.n, "scenarios to", a.out)


if __name__ == "__main__":
    main()
