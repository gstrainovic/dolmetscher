# /// script
# requires-python = ">=3.11"
# dependencies = ["claude-agent-sdk==0.2.182"]
# ///
"""Haiku, Sonnet und Opus über das Claude-Code-Abo: fehlerhafte Voxtral-Umschriften deuten und übersetzen.

Gleiche Anweisung wie der Dolmetscher-Dienst, eine offene Sitzung je Modell, Sätze nacheinander als Gespräch.
Gemessen: Zeit bis zum ersten Textstück und bis zur ganzen Übersetzung.
Aufruf: uv run modellvergleich.py suisse-gegen-voxtral.json [modell …] > ergebnis.json
"""
import asyncio, json, sys, time
from pathlib import Path
from claude_agent_sdk import ClaudeAgentOptions, ClaudeSDKClient, StreamEvent

ANWEISUNG = (
    "You are an interpreter in a Swiss care home. Every message is an automatic, often faulty transcript of "
    "spoken Swiss German or German by residents or carers. Work out what was most likely meant, using the "
    "earlier messages of this conversation as context, and translate it into Serbian (Latin script). "
    "Output only the translation, in Serbian: no explanation, no notes, no alternatives, no other language."
)
daten = json.loads(Path(sys.argv[1]).read_text())


async def lauf(modell):
    opt = ClaudeAgentOptions(model=modell, system_prompt=ANWEISUNG, tools=[], setting_sources=[], max_turns=1,
                             include_partial_messages=True)
    zeilen = []
    async with ClaudeSDKClient(options=opt) as c:
        await c.query("Guten Tag.")  # Sitzung anwärmen, wie /vorwaermen
        async for _ in c.receive_response():
            pass
        for z in daten:
            t0 = time.time()
            erstes, text = None, ""
            await c.query(z["Voxtral"])
            async for m in c.receive_response():
                if isinstance(m, StreamEvent) and m.event.get("type") == "content_block_delta":
                    d = m.event.get("delta", {})
                    if d.get("type") == "text_delta":
                        erstes = erstes or time.time() - t0
                        text += d.get("text", "")
            zeilen.append({"modell": modell, "umschrift": z["Voxtral"], "gemeint": z["vorlage"],
                           "uebersetzung": text.strip(), "erstes_s": round(erstes or -1, 2),
                           "ganz_s": round(time.time() - t0, 2)})
            print(modell, zeilen[-1]["erstes_s"], zeilen[-1]["ganz_s"], file=sys.stderr, flush=True)
    return zeilen


async def main():
    alle = []
    for m in sys.argv[2:] or ["haiku", "sonnet", "opus"]:
        alle += await lauf(m)
    print(json.dumps(alle, ensure_ascii=False, indent=1))

asyncio.run(main())
