import os, json, base64
import httpx
from fastapi import FastAPI, Header, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")
APP_TOKEN = os.environ.get("APP_TOKEN", "")
MODEL = os.environ.get("GEMINI_MODEL", "gemini-2.5-flash")
SYSTEM = ("Ты SLOOK — персональный ИИ-помощник. Отвечай на языке пользователя, "
          "по делу и без выдумок. Если не уверен, так и скажи.")
MAX_B64 = 14_000_000  # ~10 МБ файла


class M(BaseModel):
    role: str
    text: str


class A(BaseModel):
    name: str
    mime: str
    data: str


class Req(BaseModel):
    messages: list[M]
    attachment: A | None = None


app = FastAPI(title="SLOOK server")


def line(obj: dict) -> str:
    return json.dumps(obj, ensure_ascii=False) + "\n"


@app.get("/health")
def health():
    return {"ok": True, "model": MODEL, "key_set": bool(GEMINI_API_KEY), "token_set": bool(APP_TOKEN)}


@app.post("/chat")
async def chat(req: Req, authorization: str = Header(default="")):
    if not APP_TOKEN or authorization != f"Bearer {APP_TOKEN}":
        raise HTTPException(401, "Неверный токен доступа")
    if not GEMINI_API_KEY:
        raise HTTPException(500, "На сервере не задан GEMINI_API_KEY")

    contents = [
        {"role": "user" if m.role == "user" else "model", "parts": [{"text": m.text}]}
        for m in req.messages if m.text.strip()
    ]
    if not contents or contents[-1]["role"] != "user":
        raise HTTPException(400, "Пустой запрос")

    a = req.attachment
    if a:
        if len(a.data) > MAX_B64:
            raise HTTPException(413, "Файл больше 10 МБ")
        if a.mime.startswith("text/") or a.mime in ("application/json", "application/xml"):
            text = base64.b64decode(a.data).decode("utf-8", "replace")
            contents[-1]["parts"].append({"text": f"\n\n[Файл {a.name}]\n{text[:100000]}"})
        else:
            contents[-1]["parts"].append({"inline_data": {"mime_type": a.mime, "data": a.data}})

    async def gen():
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:streamGenerateContent?alt=sse"
        body = {"contents": contents, "systemInstruction": {"parts": [{"text": SYSTEM}]}}
        try:
            async with httpx.AsyncClient(timeout=120) as client:
                async with client.stream("POST", url, json=body,
                                         headers={"x-goog-api-key": GEMINI_API_KEY}) as r:
                    if r.status_code != 200:
                        err = (await r.aread()).decode("utf-8", "replace")[:300]
                        yield line({"e": f"Gemini вернул {r.status_code}: {err}"})
                        return
                    async for ln in r.aiter_lines():
                        if not ln.startswith("data:"):
                            continue
                        try:
                            d = json.loads(ln[5:])
                        except ValueError:
                            continue
                        for cand in d.get("candidates", []):
                            for p in cand.get("content", {}).get("parts", []):
                                if "text" in p:
                                    yield line({"t": p["text"]})
        except Exception as ex:  # сетевые ошибки показываем, а не прячем
            yield line({"e": f"Ошибка сервера: {ex}"})

    return StreamingResponse(gen(), media_type="application/x-ndjson")
