# LIVE01: RF_CODE_REQUEST lipsește (pentru Persoana B)

Găsit la primul test live. Scorul NU a fost modificat; `engine.py` e al tău.

## Mesajul (lipit în GUI, fără expeditor)

> Bună ziua! Contul dumneavoastră bancar va fi blocat în 30 de minute. Pentru a evita suspendarea, confirmați codul primit prin SMS la acest număr. Nu contactați banca până la finalizarea verificării.

Scenariul în format oficial: `issues/LIVE01.json`.

## Ce a ieșit

`SUSPICIOUS · BANK_IMPERSONATION · 45 · first alert 1 · RF_IMPERSONATION, RF_URGENCY, RF_THREAT`

Reprodus local cu `engine.analyze_text(...)`: același rezultat.

## Ce lipsește

**RF_CODE_REQUEST (35)**: „confirmați codul primit prin SMS la acest număr” cere codul.
Cu el, scorul ar fi 80 → **SCAM**.

## De ce (din ce am văzut, nu am schimbat nimic)

- `CODE_WORD` se potrivește („codul primit”).
- `CODE_ASK` (engine.py, linia ~97) **nu conține „confirmati”**, deci propoziția nu e văzută ca cerere.
- `PROTECTIVE_NEG` nu se declanșează aici, deci nu e problema negației.

Verificat pe propoziția normalizată:
`pentru a evita suspendarea, confirmati codul primit prin sms la acest numar` → ask=False, neg=None.

## De verificat tot aici (doar observație)

„Nu contactați banca până la finalizarea verificării” e, după brief, cerere de a nu verifica cu alții → posibil **RF_SECRECY (20)**. Acum nu e detectat.

## După fix

Rulează din nou validarea: trebuie să rămână 63/63.
