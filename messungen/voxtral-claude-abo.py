# /// script
# requires-python = ">=3.11"
# dependencies = ["claude-agent-sdk==0.2.182"]
# ///
"""Antwortzeit einer dauernd offenen Claude-Sitzung über das Claude-Code-Abo (Agent SDK, kein API-Schlüssel).

Schickt Voxtral-Umschriften nacheinander als Gespräch und misst die Zeit bis zur fertigen Übersetzung.
"""
import asyncio, json, sys, time
from pathlib import Path
from claude_agent_sdk import AssistantMessage, ClaudeAgentOptions, ClaudeSDKClient, ResultMessage, TextBlock

daten = json.loads(Path(sys.argv[1]).read_text())
SYSTEM = ("Du bist Dolmetscher in einem Schweizer Altersheim. Jede Nachricht ist eine automatische, oft fehlerhafte "
          "Umschrift von gesprochenem Schweizerdeutsch (Pflegende oder Bewohner). Erschliesse aus dem Gesprächsverlauf, "
          "was gemeint war, und gib nur die serbische Übersetzung aus (lateinische Schrift), ohne Erklärung.")

async def main():
    opt = ClaudeAgentOptions(model="haiku", system_prompt=SYSTEM, tools=[], setting_sources=[], max_turns=1)
    t0 = time.time()
    async with ClaudeSDKClient(options=opt) as client:
        print(f"Sitzung offen nach {time.time() - t0:.1f} s", flush=True)
        for z in daten:
            t = time.time()
            await client.query(z["Voxtral"])
            text = ""
            async for m in client.receive_response():
                if isinstance(m, AssistantMessage):
                    text += "".join(b.text for b in m.content if isinstance(b, TextBlock))
            print(f"{time.time() - t:4.1f} s | {z['Voxtral']} -> {text.strip()} | gemeint: {z['vorlage']}", flush=True)

asyncio.run(main())
