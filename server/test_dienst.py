"""Verhalten des Übersetzungsdienstes, mit einem Ersatz für Claude (ohne Abo-Aufruf)."""
import pytest
from fastapi.testclient import TestClient

import dienst


class FalscherClaude:
    """Merkt sich je Sitzung, was ankam, und antwortet mit einer erkennbaren «Übersetzung»."""

    def __init__(self):
        self.verlauf: dict[str, list[str]] = {}

    async def uebersetze(self, sitzung: str, ziel: str, text: str) -> str:
        self.verlauf.setdefault(sitzung, []).append(text)
        return f"[{ziel}] {text}"

    async def strom(self, sitzung: str, ziel: str, text: str):
        self.verlauf.setdefault(sitzung, []).append(text)
        for stueck in [f"[{ziel}] ", "Erster Satz. ", "Zweiter ", "Satz."]:
            yield stueck


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv("DOLMETSCHER_TOKEN", "geheim")
    falsch = FalscherClaude()
    app = dienst.erstelle_app(falsch)
    return TestClient(app), falsch


def test_uebersetzt_mit_richtigem_token(client):
    c, _ = client
    r = c.post("/uebersetzen", json={"sitzung": "a", "ziel": "sr", "text": "Haben Sie Hunger?"},
               headers={"Authorization": "Bearer geheim"})
    assert r.status_code == 200
    assert r.json() == {"uebersetzung": "[sr] Haben Sie Hunger?"}


def test_ohne_oder_mit_falschem_token_abgelehnt(client):
    c, _ = client
    body = {"sitzung": "a", "ziel": "sr", "text": "x"}
    assert c.post("/uebersetzen", json=body).status_code == 401
    assert c.post("/uebersetzen", json=body, headers={"Authorization": "Bearer falsch"}).status_code == 401


def test_jede_sitzung_hat_ihren_eigenen_verlauf(client):
    c, falsch = client
    h = {"Authorization": "Bearer geheim"}
    c.post("/uebersetzen", json={"sitzung": "a", "ziel": "sr", "text": "eins"}, headers=h)
    c.post("/uebersetzen", json={"sitzung": "b", "ziel": "sr", "text": "zwei"}, headers=h)
    c.post("/uebersetzen", json={"sitzung": "a", "ziel": "sr", "text": "drei"}, headers=h)
    assert falsch.verlauf == {"a": ["eins", "drei"], "b": ["zwei"]}


def test_fehler_von_claude_wird_nicht_als_uebersetzung_vorgelesen(monkeypatch):
    # Abgelaufener Abo-Login: Claude antwortet mit einer Fehlermeldung statt einer Übersetzung.
    monkeypatch.setenv("DOLMETSCHER_TOKEN", "geheim")

    class KaputterClaude:
        async def uebersetze(self, sitzung, ziel, text):
            raise dienst.ClaudeFehler("Failed to authenticate: OAuth session expired")

    c = TestClient(dienst.erstelle_app(KaputterClaude()))
    r = c.post("/uebersetzen", json={"sitzung": "a", "ziel": "sr", "text": "Hallo"},
               headers={"Authorization": "Bearer geheim"})
    assert r.status_code == 502
    assert "OAuth" in r.json()["detail"]
    assert "uebersetzung" not in r.json()


def test_strom_liefert_die_uebersetzung_stueckweise(client):
    # Die App spricht den ersten Satz, sobald er da ist, statt auf die ganze Übersetzung zu warten.
    c, falsch = client
    with c.stream("POST", "/uebersetzen/strom", json={"sitzung": "s", "ziel": "sr", "text": "Hallo"},
                  headers={"Authorization": "Bearer geheim"}) as r:
        assert r.status_code == 200
        stuecke = list(r.iter_text())
    assert "".join(stuecke) == "[sr] Erster Satz. Zweiter Satz."
    assert falsch.verlauf == {"s": ["Hallo"]}


def test_strom_ohne_token_abgelehnt(client):
    c, _ = client
    r = c.post("/uebersetzen/strom", json={"sitzung": "s", "ziel": "sr", "text": "Hallo"})
    assert r.status_code == 401


def test_strom_fehler_vor_dem_ersten_stueck_gibt_502(monkeypatch):
    monkeypatch.setenv("DOLMETSCHER_TOKEN", "geheim")

    class KaputterClaude:
        async def strom(self, sitzung, ziel, text):
            raise dienst.ClaudeFehler("Failed to authenticate")
            yield ""  # macht die Funktion zum Generator

    c = TestClient(dienst.erstelle_app(KaputterClaude()))
    r = c.post("/uebersetzen/strom", json={"sitzung": "a", "ziel": "sr", "text": "Hallo"},
               headers={"Authorization": "Bearer geheim"})
    assert r.status_code == 502


def test_abgebrochener_strom_verwirft_die_sitzung():
    # Liest die App nicht zu Ende, darf der Rest der Antwort nicht im nächsten Satz landen.
    import asyncio

    class FalscherClient:
        def __init__(self):
            self.getrennt = False

        async def query(self, text):
            pass

        async def receive_response(self):
            from claude_agent_sdk import StreamEvent
            for t in ["Prvi. ", "Drugi."]:
                yield StreamEvent(uuid="u", session_id="s",
                                  event={"type": "content_block_delta", "delta": {"type": "text_delta", "text": t}})

        async def disconnect(self):
            self.getrennt = True

    abo = dienst.ClaudeAbo()
    fc = FalscherClient()
    abo.sitzungen["x"] = (fc, asyncio.Lock(), 1e12)

    async def nur_erstes():
        gen = abo.strom("x", "sr", "Hallo")
        await gen.__anext__()
        await gen.aclose()

    asyncio.run(nur_erstes())
    assert "x" not in abo.sitzungen
    assert fc.getrennt


def test_vorwaermen_legt_die_sitzung_an_ohne_zu_uebersetzen(client):
    c, falsch = client
    angelegt = []

    async def vorwaermen(sitzung, ziel):
        angelegt.append((sitzung, ziel))

    falsch.vorwaermen = vorwaermen
    r = c.post("/vorwaermen", json={"sitzung": "w", "ziel": "sr", "text": "-"},
               headers={"Authorization": "Bearer geheim"})
    assert r.status_code == 200
    assert angelegt == [("w", "sr")]
    assert falsch.verlauf == {}


def test_leerer_text_wird_abgelehnt(client):
    c, _ = client
    r = c.post("/uebersetzen", json={"sitzung": "a", "ziel": "sr", "text": "  "},
               headers={"Authorization": "Bearer geheim"})
    assert r.status_code == 422


def test_protokollzeilen_der_app_landen_in_der_datei(client, monkeypatch, tmp_path):
    datei = tmp_path / "protokoll.txt"
    monkeypatch.setenv("DOLMETSCHER_PROTOKOLL", str(datei))
    c, _ = client
    for zeilen in (["23:14:25 SATZENDE", "23:14:26 ERKANNT Ich rede jetzt."], ["23:14:28 ÜBERSETZT Sad govorim."]):
        r = c.post("/protokoll", json={"zeilen": zeilen}, headers={"Authorization": "Bearer geheim"})
        assert r.status_code == 200
    assert datei.read_text().splitlines() == [
        "23:14:25 SATZENDE", "23:14:26 ERKANNT Ich rede jetzt.", "23:14:28 ÜBERSETZT Sad govorim."]


def test_protokoll_ohne_token_abgelehnt(client, monkeypatch, tmp_path):
    datei = tmp_path / "protokoll.txt"
    monkeypatch.setenv("DOLMETSCHER_PROTOKOLL", str(datei))
    c, _ = client
    assert c.post("/protokoll", json={"zeilen": ["x"]}).status_code == 401
    assert not datei.exists()


def test_anweisung_verlangt_nur_die_uebersetzung():
    a = dienst.anweisung("sr")
    assert "Serbian" in a and "Latin" in a
    assert "only the translation" in a.lower()
