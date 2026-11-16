"""Übersetzungsdienst für die Dolmetscher-App: Claude über das Claude-Code-Abo (Agent SDK, kein API-Schlüssel).

Die App schickt je erkanntem Satz `{"sitzung", "ziel", "text"}`. Je Sitzung bleibt eine Claude-Sitzung offen,
damit Claude den Gesprächsverlauf kennt und fehlerhafte Umschriften aus dem Zusammenhang deuten kann (gemessen:
1.3 bis 2 Sekunden je Satz, `docs/schweizerdeutsch-erkennung.md`). Unbenutzte Sitzungen schliessen nach
30 Minuten. Zugang nur mit dem Token aus `DOLMETSCHER_TOKEN`.

Start: `uv run uvicorn dienst:app --host 127.0.0.1 --port 8090`
"""
import asyncio
import hmac
import os
import time

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, field_validator

SPRACHEN = {"sr": "Serbian", "de": "German", "hr": "Croatian", "bs": "Bosnian", "en": "English",
            "fr": "French", "it": "Italian"}
LEERLAUF_S = 30 * 60


def anweisung(ziel: str) -> str:
    sprache = SPRACHEN.get(ziel, ziel)
    schrift = " (Latin script)" if ziel == "sr" else ""
    return (
        "You are an interpreter in a Swiss care home. Every message is an automatic, often faulty transcript of "
        "spoken Swiss German or German by residents or carers. Work out what was most likely meant, using the "
        f"earlier messages of this conversation as context, and translate it into {sprache}{schrift}. "
        f"Output only the translation, in {sprache}: no explanation, no notes, no alternatives, no other language."
    )


class ClaudeFehler(RuntimeError):
    """Claude hat statt einer Übersetzung einen Fehler gemeldet (etwa abgelaufener Abo-Login)."""


class Anfrage(BaseModel):
    sitzung: str
    ziel: str
    text: str

    @field_validator("text")
    @classmethod
    def nicht_leer(cls, v: str) -> str:
        if not v.strip():
            raise ValueError("leer")
        return v.strip()


class ClaudeAbo:
    """Hält je Sitzung einen `ClaudeSDKClient` offen (Modell Haiku, ohne Werkzeuge)."""

    def __init__(self):
        self.sitzungen: dict[str, tuple[object, asyncio.Lock, float]] = {}

    async def _client(self, sitzung: str, ziel: str):
        from claude_agent_sdk import ClaudeAgentOptions, ClaudeSDKClient

        await self._aufraeumen()
        if sitzung not in self.sitzungen:
            modell = os.environ.get("DOLMETSCHER_MODELL", "haiku")
            opt = ClaudeAgentOptions(model=modell, system_prompt=anweisung(ziel), tools=[], setting_sources=[],
                                     max_turns=1, include_partial_messages=True)
            c = ClaudeSDKClient(options=opt)
            await c.connect()
            self.sitzungen[sitzung] = (c, asyncio.Lock(), time.time())
        c, lock, _ = self.sitzungen[sitzung]
        self.sitzungen[sitzung] = (c, lock, time.time())
        return c, lock

    async def _aufraeumen(self):
        jetzt = time.time()
        for s, (c, _, zuletzt) in list(self.sitzungen.items()):
            if jetzt - zuletzt > LEERLAUF_S:
                del self.sitzungen[s]
                try:
                    await c.disconnect()
                except Exception:
                    pass

    async def vorwaermen(self, sitzung: str, ziel: str):
        """Claude-Sitzung anlegen und einmal antworten lassen, bevor der erste Satz kommt: Erst nach der ersten
        Antwort ist die Sitzung schnell (gemessen mit Opus: erster Satz 3.2 s ohne, unter 1 s mit Anwärmen)."""
        c, lock = await self._client(sitzung, ziel)
        async with lock:
            await c.query("Guten Tag.")
            async for _ in c.receive_response():
                pass

    async def strom(self, sitzung: str, ziel: str, text: str):
        """Übersetzung Stück für Stück, sobald Claude sie schreibt (Teilantworten des Agent SDK)."""
        from claude_agent_sdk import ResultMessage, StreamEvent

        c, lock = await self._client(sitzung, ziel)
        async with lock:
            fertig = False
            try:
                await c.query(text)
                async for m in c.receive_response():
                    if isinstance(m, StreamEvent):
                        d = m.event.get("delta", {}) if m.event.get("type") == "content_block_delta" else {}
                        if d.get("type") == "text_delta" and d.get("text"):
                            yield d["text"]
                    elif isinstance(m, ResultMessage) and m.is_error:
                        raise ClaudeFehler(str(m.result or m.subtype)[:300])
                fertig = True
            finally:
                # Abgebrochen (App weg) oder Fehler: halb gelesene Antwort würde den nächsten Satz
                # verderben, also Sitzung verwerfen.
                if not fertig:
                    self.sitzungen.pop(sitzung, None)
                    try:
                        await c.disconnect()
                    except Exception:
                        pass

    async def uebersetze(self, sitzung: str, ziel: str, text: str) -> str:
        from claude_agent_sdk import AssistantMessage, ResultMessage, TextBlock

        c, lock = await self._client(sitzung, ziel)
        async with lock:
            await c.query(text)
            out, fehler = "", None
            async for m in c.receive_response():
                if isinstance(m, AssistantMessage):
                    out += "".join(b.text for b in m.content if isinstance(b, TextBlock))
                if isinstance(m, ResultMessage) and m.is_error:
                    fehler = m.result or m.subtype
        if fehler:
            # Kaputte Sitzung nicht weiterverwenden
            self.sitzungen.pop(sitzung, None)
            raise ClaudeFehler(f"{fehler}: {out.strip()}"[:300])
        return out.strip()


class Protokollzeilen(BaseModel):
    zeilen: list[str]


def erstelle_app(claude) -> FastAPI:
    app = FastAPI(title="Dolmetscher-Dienst")

    def pruefe_token(authorization: str = Header(default="")):
        soll = os.environ.get("DOLMETSCHER_TOKEN", "")
        if not soll or not hmac.compare_digest(authorization, f"Bearer {soll}"):
            raise HTTPException(status_code=401, detail="Token fehlt oder falsch")

    @app.post("/uebersetzen", dependencies=[Depends(pruefe_token)])
    async def uebersetzen(a: Anfrage):
        try:
            return {"uebersetzung": await claude.uebersetze(a.sitzung, a.ziel, a.text)}
        except ClaudeFehler as e:
            raise HTTPException(status_code=502, detail=f"Claude: {e}")

    @app.post("/vorwaermen", dependencies=[Depends(pruefe_token)])
    async def vorwaermen(a: Anfrage):
        await claude.vorwaermen(a.sitzung, a.ziel)
        return {"ok": True}

    @app.post("/uebersetzen/strom", dependencies=[Depends(pruefe_token)])
    async def uebersetzen_strom(a: Anfrage):
        gen = claude.strom(a.sitzung, a.ziel, a.text)
        try:
            erstes = await gen.__anext__()  # Fehler vor dem ersten Stück noch als 502 melden
        except StopAsyncIteration:
            erstes = ""
        except ClaudeFehler as e:
            raise HTTPException(status_code=502, detail=f"Claude: {e}")

        async def rest():
            try:
                yield erstes
                async for s in gen:
                    yield s
            except ClaudeFehler as e:
                yield f"\n[FEHLER] {e}"
            finally:
                await gen.aclose()

        return StreamingResponse(rest(), media_type="text/plain; charset=utf-8")

    @app.post("/protokoll", dependencies=[Depends(pruefe_token)])
    async def protokoll(p: Protokollzeilen):
        """Laufprotokoll der App, damit es ohne adb lesbar ist."""
        datei = os.environ.get("DOLMETSCHER_PROTOKOLL", os.path.expanduser("~/dolmetscher-protokoll.txt"))
        with open(datei, "a", encoding="utf-8") as f:
            f.writelines(z.replace("\n", " ") + "\n" for z in p.zeilen)
        return {"ok": True}

    @app.get("/gesund")
    async def gesund():
        return {"ok": True}

    return app


app = erstelle_app(ClaudeAbo())
