from __future__ import annotations

import io
import json
import logging
import os
import base64
import re
import socket
import sys
import threading
import time
from datetime import datetime
from typing import Any

from flask import Flask, jsonify, request
from flask_cors import CORS
from PIL import Image, ImageEnhance, ImageOps

try:
    import pymupdf as fitz  # PyMuPDF
except ImportError:  # pragma: no cover - reported by /health
    try:
        import fitz  # Older PyMuPDF releases
    except ImportError:
        fitz = None

try:
    from pdf2image import convert_from_bytes
except ImportError:  # pragma: no cover - reported by /health
    convert_from_bytes = None

try:
    from google import genai
    from google.genai import types
except ImportError:  # pragma: no cover - reported by /health
    genai = None
    types = None


app = Flask(__name__)
app.config["JSON_AS_ASCII"] = False
app.config["MAX_CONTENT_LENGTH"] = 25 * 1024 * 1024

if hasattr(app, "json"):
    app.json.ensure_ascii = False

CORS(
    app,
    resources={r"/*": {"origins": "*"}},
    methods=["GET", "POST", "OPTIONS"],
)

try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except (AttributeError, OSError, ValueError):
    pass

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s",
    stream=sys.stdout,
)

INPUT_FOLDER = "/data/data/com.termux/files/home/input"
OUTPUT_FOLDER = "/data/data/com.termux/files/home/output"
MAX_IMAGE_SIDE = 1600
JPEG_QUALITY = 80
GEMINI_MODEL = "gemini-3.6-flash"
GEMINI_REQUEST_TIMEOUT_SECONDS = 60
GEMINI_REQUEST_TIMEOUT_MS = GEMINI_REQUEST_TIMEOUT_SECONDS * 1000
GEMINI_MAX_ATTEMPTS = 3
GEMINI_INITIAL_DELAY_SECONDS = 3
GEMINI_RETRY_BACKOFF_SECONDS = (15, 15)
# Накладные могут содержать много позиций — маленький лимит обрезал ответ
# на середине и портил JSON. Берём с большим запасом, чтобы даже длинные
# таблицы (50+ позиций) не обрывали JSON на середине.
INVOICE_MAX_OUTPUT_TOKENS = 32768
# Если Gemini всё же вернул повреждённый/неполный JSON (не сетевая ошибка,
# а именно проблема с форматом ответа) — пробуем запрос ещё раз, прежде чем
# сдаться и вернуть клиенту понятную ошибку.
INVOICE_JSON_MAX_ATTEMPTS = 3
INVOICE_JSON_RETRY_DELAY_SECONDS = 2
# Глобальный замок: гарантирует, что к Gemini одновременно уходит НЕ БОЛЕЕ
# ОДНОГО запроса, даже если сервер обрабатывает несколько HTTP-запросов
# параллельно (несколько устройств/вкладок одновременно шлют файлы). Именно
# конкурентные запросы к Gemini — частая причина 503 Service Unavailable.
_GEMINI_CALL_LOCK = threading.Lock()

INVOICE_PROMPT = """Ты — модуль универсального распознавания накладных. Документ может быть одним из более чем 20 разных форматов бланков (разные поставщики, разная вёрстка). Извлеки данные СТРОГО по позиционным маркерам, а не по конкретным словам одного шаблона:

1. Строка 1 — наименование отправителя/бренда: как правило, это логотип или название компании в самом верху документа (например, «EQUOVIS»).
2. Строка 2 — внешний номер документа/заказа: ищи маркер «Ext. Beleg-Nr.» или код вида «EB» + цифры (например, EB1234567). Если такого маркера нет, возьми ближайший номер заказа/накладной рядом с шапкой.
3. Строка 3 — дата получения или дата документа.
4. Строки 4 и далее — позиции товаров: название товара, количество и единица измерения.

ВАЖНО:
- НЕ извлекай и не возвращай артикул (Artikel-Nr.) ни в одном поле — это поле должно оставаться пустым.
- Игнорируй служебные пометки: галочки, крестики, штампы и штрихкоды сами по себе — это не данные о товаре, а не название или количество.
- Структура ответа должна быть одинаковой независимо от формата бланка.

Верни СТРОГО JSON без markdown:
{
  "senderName": "название отправителя (строка 1)",
  "orderNumber": "внешний номер документа / EB-номер (строка 2)",
  "date": "дата (строка 3)",
  "items": [
    {"name": "название товара", "menge": 10, "unit": "единица измерения", "ean": ""}
  ]
}
Извлеки ВСЕ товары из таблицы, не пропускай ни одной строки.
Если значение неразборчиво или отсутствует, оставь соответствующее поле пустым.
menge должен быть целым числом. Не добавляй никаких пояснений вне JSON.

ФОРМАТ ОТВЕТА — СТРОГОЕ ТРЕБОВАНИЕ:
Ответом должен быть ИСКЛЮЧИТЕЛЬНО один валидный JSON-объект. Никакого текста
до или после JSON, никаких пояснений, никаких markdown-обёрток вида ```json
или ```. Первый символ ответа должен быть «{», последний — «}». Ничего, кроме
этого JSON-объекта, в ответе быть не должно.
JSON должен быть ПОЛНОСТЬЮ ЗАВЕРШЁН: все открытые скобки { } и [ ] обязаны
быть закрыты, у каждого объекта в массиве items должны быть все 4 поля.
Если позиций в таблице очень много, сокращай длину значений в полях name —
но НИКОГДА не обрывай сам JSON и не оставляй последнюю позицию неполной."""

INVOICE_SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "senderName": {"type": "STRING"},
        "orderNumber": {"type": "STRING"},
        "date": {"type": "STRING"},
        "items": {
            "type": "ARRAY",
            "items": {
                "type": "OBJECT",
                "properties": {
                    "name": {"type": "STRING"},
                    "menge": {"type": "INTEGER"},
                    "unit": {"type": "STRING"},
                    "ean": {"type": "STRING"},
                },
                "required": ["name", "menge", "unit", "ean"],
            },
        },
    },
    "required": ["senderName", "orderNumber", "date", "items"],
}


def _error(message: str, status: int = 400):
    return jsonify({"status": "error", "message": message}), status


def _api_key() -> str:
    # Prefer the server environment. The form/header fallbacks keep the
    # existing settings screen working while the key is being migrated to
    # the server environment.
    return (
        os.getenv("GEMINI_API_KEY", "").strip()
        or request.form.get("api_key", "").strip()
        or request.headers.get("X-Gemini-Api-Key", "").strip()
    )


class GeminiGatewayTimeoutError(RuntimeError):
    """A transient Gemini request failed after all server-side retries."""

    status_code = 504


class GeminiRequestError(RuntimeError):
    """A non-retryable Gemini request error."""

    def __init__(self, message: str, status_code: int = 500):
        super().__init__(message)
        self.status_code = status_code


def _exception_status(error: BaseException) -> int:
    """Extract an HTTP status from google-genai/httpx style exceptions."""
    for value in (
        getattr(error, "status_code", None),
        getattr(error, "status", None),
        getattr(error, "code", None),
        getattr(getattr(error, "response", None), "status_code", None),
        getattr(getattr(error, "response", None), "status", None),
    ):
        try:
            status = int(value)
        except (TypeError, ValueError):
            continue
        if 100 <= status <= 599:
            return status
    return 0


def _is_timeout_error(error: BaseException) -> bool:
    error_name = type(error).__name__.lower()
    error_text = str(error).lower()
    return (
        isinstance(error, (TimeoutError, socket.timeout))
        or "timeout" in error_name
        or "timed out" in error_text
        or "timeout" in error_text
    )


def _is_retryable_gemini_error(error: BaseException) -> bool:
    status = _exception_status(error)
    return (
        _is_timeout_error(error)
        or isinstance(error, (ConnectionError, OSError))
        or status == 429
        or 500 <= status <= 599
        or any(
            marker in type(error).__name__.lower()
            for marker in ("connection", "connect", "network", "transport", "server")
        )
    )


def _gemini_error_message(error: BaseException) -> str:
    status = _exception_status(error)
    message = str(error).strip() or type(error).__name__
    return f"HTTP {status}: {message}" if status else message


def optimize_image_for_ocr(image_bytes: bytes) -> bytes:
    """Convert one page to high-contrast grayscale JPEG under 1600 px."""
    if not image_bytes:
        raise ValueError("Получен пустой файл изображения.")

    with Image.open(io.BytesIO(image_bytes)) as source:
        image = ImageOps.exif_transpose(source).convert("L")
        image = ImageOps.autocontrast(image, cutoff=1)
        image = ImageEnhance.Contrast(image).enhance(1.35)

        width, height = image.size
        longest_side = max(width, height)
        if longest_side > MAX_IMAGE_SIDE:
            scale = MAX_IMAGE_SIDE / longest_side
            image = image.resize(
                (max(1, round(width * scale)), max(1, round(height * scale))),
                Image.Resampling.LANCZOS,
            )

        output = io.BytesIO()
        image.save(
            output,
            format="JPEG",
            quality=JPEG_QUALITY,
            optimize=True,
            progressive=True,
        )
        return output.getvalue()


def render_pdf_pages(pdf_bytes: bytes) -> list[bytes]:
    """Render a PDF one page at a time and optimize each page."""
    if not pdf_bytes:
        raise ValueError("Получен пустой PDF-файл.")

    pages: list[bytes] = []
    if fitz is None and convert_from_bytes is None:
        raise RuntimeError(
            "Не установлен PDF-рендерер. Установите pdf2image и пакет poppler "
            "командой: pkg install poppler."
        )

    # pdf2image + poppler is the Termux/Android-compatible path. It avoids
    # compiling PyMuPDF's native C++/SWIG extensions on the phone.
    if fitz is None:
        try:
            rendered_images = convert_from_bytes(
                pdf_bytes,
                dpi=200,
                fmt="png",
                thread_count=1,
            )
        except Exception as error:
            raise RuntimeError(
                "Не удалось прочитать PDF через poppler. Установите его "
                "командой: pkg install poppler."
            ) from error
        for image in rendered_images:
            image_buffer = io.BytesIO()
            image.save(image_buffer, format="PNG")
            pages.append(optimize_image_for_ocr(image_buffer.getvalue()))
        if not pages:
            raise ValueError("PDF не содержит страниц.")
        return pages

    document = fitz.open(stream=pdf_bytes, filetype="pdf")
    try:
        if document.page_count == 0:
            raise ValueError("PDF не содержит страниц.")

        for page_number in range(document.page_count):
            page = document.load_page(page_number)
            rect = page.rect
            longest_point_side = max(float(rect.width), float(rect.height), 1.0)
            scale = MAX_IMAGE_SIDE / longest_point_side
            pixmap = page.get_pixmap(
                matrix=fitz.Matrix(scale, scale),
                alpha=False,
            )
            image = Image.frombytes(
                "RGB",
                (pixmap.width, pixmap.height),
                pixmap.samples,
            )
            image_buffer = io.BytesIO()
            image.save(image_buffer, format="PNG")
            pages.append(optimize_image_for_ocr(image_buffer.getvalue()))
    finally:
        document.close()

    return pages


def render_uploaded_pages(filename: str, file_bytes: bytes) -> list[bytes]:
    """Support PDF and images; PDF is the primary invoice input."""
    is_pdf = (
        filename.lower().endswith(".pdf")
        or file_bytes[:5] == b"%PDF-"
    )
    if is_pdf:
        return render_pdf_pages(file_bytes)
    return [optimize_image_for_ocr(file_bytes)]


def extract_pdf_page_texts(pdf_bytes: bytes, filename: str) -> list[str]:
    """Extract lightweight text hints for initial page grouping only.

    This function deliberately does not perform OCR and never contacts Gemini.
    Scanned pages simply receive an empty hint and remain in their original
    order.
    """
    is_pdf = filename.lower().endswith(".pdf") or pdf_bytes[:5] == b"%PDF-"
    if not is_pdf or fitz is None:
        return []

    document = fitz.open(stream=pdf_bytes, filetype="pdf")
    try:
        return [
            str(document.load_page(index).get_text("text") or "")
            for index in range(document.page_count)
        ]
    finally:
        document.close()


def extract_header_metadata(text: str) -> tuple[str, str]:
    """Extract the supplier name and EB document number from embedded PDF text.

    This is intentionally a text-only operation. It is used for document
    metadata and grouping; Gemini remains a browser-side page recognizer.
    """
    sender_name = "no_name"

    email_matches = re.findall(
        r"([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\.[a-zA-Z]{2,})",
        str(text or ""),
    )
    for user, domain in email_matches:
        domain_name = domain.split(".")[0].lower()
        if "stroeh" not in domain_name and "creditor" not in user.lower():
            sender_name = domain_name.capitalize()
            break

    if sender_name == "no_name":
        url_match = re.search(
            r"www\.([a-zA-Z0-9-]+)\.[a-zA-Z]{2,}",
            str(text or ""),
            re.IGNORECASE,
        )
        if url_match and "stroeh" not in url_match.group(1).lower():
            sender_name = url_match.group(1).capitalize()
        elif re.search(r"\bINNOPHA\b", str(text or ""), re.IGNORECASE):
            sender_name = "Innopha"

    eb_number = "EB_no_data"
    eb_match = re.search(r"\bEB\s*[-/#:]?\s*(\d{5,9})\b", str(text or ""), re.IGNORECASE)
    if eb_match:
        eb_number = f"EB{eb_match.group(1)}"
    else:
        beleg_match = re.search(
            r"Ext\.?\s*Beleg-?Nr\.?\s*[:.]?\s*([A-Za-z0-9\-/]+)",
            str(text or ""),
            re.IGNORECASE,
        )
        if beleg_match:
            eb_number = beleg_match.group(1).upper()

    return sender_name, eb_number


def page_batch_key(page_text: str) -> str:
    """Return a stable, conservative grouping key for one page."""
    text = re.sub(r"\s+", " ", str(page_text or "")).strip().upper()
    order_match = re.search(r"\bEB\s*[-/#:]?\s*(\d{5,9})\b", text)
    if order_match:
        return f"order:EB{order_match.group(1)}"

    keyword_groups = (
        ("invoice", ("RECHNUNG", "INVOICE", "FAKTURA", "НАКЛАДНАЯ", "СЧЁТ")),
        ("delivery", ("LIEFERSCHEIN", "DELIVERY NOTE", "ДОСТАВКА", "ТОВАРНАЯ")),
        ("order", ("BESTELLUNG", "BESTELL-NR", "ORDER", "ЗАКАЗ")),
        ("goods", ("WARENEINGANG", "WARE", "ТОВАР", "ПОСТАВКА")),
    )
    for group, keywords in keyword_groups:
        if any(keyword in text for keyword in keywords):
            return f"keyword:{group}"
    return "keyword:unknown"


def prepare_pdf_document(file_bytes: bytes, filename: str) -> dict[str, Any]:
    """Render and group pages without making any Gemini request."""
    optimized_pages = render_uploaded_pages(filename, file_bytes)
    page_texts = extract_pdf_page_texts(file_bytes, filename)
    if len(page_texts) != len(optimized_pages):
        page_texts = [""] * len(optimized_pages)
    sender_name, eb_number = extract_header_metadata("\n".join(page_texts))

    grouped: dict[str, list[dict[str, Any]]] = {}
    group_order: list[str] = []
    for index, image_bytes in enumerate(optimized_pages):
        key = page_batch_key(page_texts[index])
        if key not in grouped:
            grouped[key] = []
            group_order.append(key)
        grouped[key].append({
            "pageIndex": index + 1,
            "imageData": (
                "data:image/jpeg;base64,"
                + base64.b64encode(image_bytes).decode("ascii")
            ),
        })

    metadata = {
        "senderName": sender_name,
        "orderNumber": eb_number,
        "date": datetime.now().strftime("%d.%m.%Y"),
    }
    return {
        "totalPages": len(optimized_pages),
        **metadata,
        "metadata": metadata,
        "batches": [grouped[key] for key in group_order],
    }


def _response_text(response: Any) -> str:
    text = getattr(response, "text", None)
    if text:
        return str(text).strip()

    parts: list[str] = []
    for candidate in getattr(response, "candidates", []) or []:
        content = getattr(candidate, "content", None)
        for part in getattr(content, "parts", []) or []:
            part_text = getattr(part, "text", None)
            if part_text:
                parts.append(str(part_text))
    return "\n".join(parts).strip()


def _parse_json_response(response: Any, page_number: int) -> dict[str, Any]:
    raw = _response_text(response)
    if not raw:
        raise RuntimeError(f"Gemini вернул пустой ответ для страницы {page_number}.")

    cleaned = raw.strip()
    # Снимаем возможные markdown-обёртки ```json ... ``` / ``` ... ```
    # (в любом регистре, с пробелами/переводами строк вокруг).
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```[a-zA-Z]*\s*", "", cleaned)
        cleaned = re.sub(r"\s*```$", "", cleaned)
        cleaned = cleaned.strip()

    try:
        value = json.loads(cleaned)
    except json.JSONDecodeError:
        # Фолбэк: вырезаем содержимое между первой «{» и последней «}» —
        # спасает случаи, когда модель добавила пояснение до/после JSON.
        # Если JSON обрезан лимитом токенов (нет закрывающей «}»), это не
        # починит строку — тогда честно сообщаем об ошибке ниже, а вызывающий
        # код (analyze_page) повторит запрос к Gemini.
        start = cleaned.find("{")
        end = cleaned.rfind("}")
        value = None
        if start != -1 and end != -1 and end > start:
            try:
                value = json.loads(cleaned[start:end + 1])
            except json.JSONDecodeError:
                value = None
        if value is None:
            raise RuntimeError(
                f"Gemini вернул некорректный или неполный JSON для страницы "
                f"{page_number} (возможно, ответ обрезан лимитом токенов)."
            )

    if not isinstance(value, dict):
        raise RuntimeError(f"Ответ Gemini для страницы {page_number} не является объектом.")
    return value


def normalize_invoice_page(value: dict[str, Any]) -> dict[str, Any]:
    items: list[dict[str, Any]] = []
    for item in value.get("items", []) or []:
        if not isinstance(item, dict):
            continue
        name = str(item.get("name", "") or "").strip()
        try:
            menge = max(0, int(float(item.get("menge", 0) or 0)))
        except (TypeError, ValueError):
            menge = 0
        unit = str(item.get("unit", "") or "").strip()
        ean = "".join(character for character in str(item.get("ean", "") or "")
                       if character.isdigit())
        if name and menge > 0:
            items.append({"name": name, "menge": menge, "unit": unit, "ean": ean})

    return {
        "senderName": str(value.get("senderName", "") or "").strip(),
        "orderNumber": str(value.get("orderNumber", "") or "").strip(),
        "date": str(value.get("date", "") or "").strip(),
        "items": items,
    }


def _generate_content_with_retry(
    client: Any,
    contents: Any,
    config: Any,
    context: str,
    initial_delay_seconds: int = 0,
    filename: str = "",
) -> Any:
    """Call google-genai with the document retry schedule."""
    last_error: BaseException | None = None

    if initial_delay_seconds > 0:
        logging.info(
            "Gemini initial delay (%s): %ss before attempt 1",
            context,
            initial_delay_seconds,
        )
        time.sleep(initial_delay_seconds)

    for attempt in range(1, GEMINI_MAX_ATTEMPTS + 1):
        logging.info(
            "Gemini attempt %s/%s (%s), model=%s, timeout=%ss",
            attempt,
            GEMINI_MAX_ATTEMPTS,
            context,
            GEMINI_MODEL,
            GEMINI_REQUEST_TIMEOUT_SECONDS,
        )
        try:
            # Только один запрос к Gemini может выполняться одновременно во
            # всём процессе — даже если Flask обрабатывает несколько HTTP-
            # соединений параллельно. Это и есть «строго по одному файлу/
            # странице за раз», применённое на уровне сервера, а не только
            # на уровне очереди в браузере.
            with _GEMINI_CALL_LOCK:
                response = client.models.generate_content(
                    model=GEMINI_MODEL,
                    contents=contents,
                    config=config,
                )
            response_text = _response_text(response)
            logging.info(
                "Gemini final response (%s, attempt %s): %s",
                context,
                attempt,
                response_text[:4000] if response_text else "<empty>",
            )
            return response
        except Exception as error:  # google-genai exposes several error classes
            last_error = error
            is_timeout = _is_timeout_error(error)
            status = _exception_status(error)
            retryable = _is_retryable_gemini_error(error)
            logging.warning(
                "Gemini attempt %s/%s failed (%s): timeout=%s status=%s "
                "retryable=%s error=%s",
                attempt,
                GEMINI_MAX_ATTEMPTS,
                context,
                is_timeout,
                status or "n/a",
                retryable,
                _gemini_error_message(error),
            )

            if not retryable or attempt >= GEMINI_MAX_ATTEMPTS:
                break

            delay = GEMINI_RETRY_BACKOFF_SECONDS[attempt - 1]
            logging.info(
                "Gemini retry scheduled (%s): attempt %s in %ss",
                context,
                attempt + 1,
                delay,
            )
            time.sleep(delay)

    assert last_error is not None
    status = _exception_status(last_error)
    if _is_retryable_gemini_error(last_error):
        if filename:
            message = (
                f"Ошибка обработки файла «{filename}»: "
                "превышено время ожидания Gemini API."
            )
        else:
            message = (
                "Gemini API не ответил после 3 попыток "
                f"({_gemini_error_message(last_error)})."
            )
        raise GeminiGatewayTimeoutError(
            message
        ) from last_error
    raise GeminiRequestError(
        f"Gemini отклонил запрос: {_gemini_error_message(last_error)}",
        status_code=status if 400 <= status <= 499 else 500,
    ) from last_error


def analyze_page(
    client: Any,
    image_bytes: bytes,
    page_number: int,
    filename: str,
    initial_delay_seconds: int = 0,
) -> dict[str, Any]:
    last_error: BaseException | None = None
    for json_attempt in range(1, INVOICE_JSON_MAX_ATTEMPTS + 1):
        response = _generate_content_with_retry(
            client,
            contents=[
                INVOICE_PROMPT,
                types.Part.from_bytes(data=image_bytes, mime_type="image/jpeg"),
            ],
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                response_schema=INVOICE_SCHEMA,
                temperature=0,
                max_output_tokens=INVOICE_MAX_OUTPUT_TOKENS,
            ),
            context=f"invoice file «{filename}», page {page_number}",
            # Задержку перед первой попыткой соблюдаем один раз; повторные
            # попытки из-за плохого JSON делаем без начальной паузы.
            initial_delay_seconds=initial_delay_seconds if json_attempt == 1 else 0,
            filename=filename,
        )
        try:
            return normalize_invoice_page(_parse_json_response(response, page_number))
        except RuntimeError as error:
            last_error = error
            logging.warning(
                "Повреждённый/неполный JSON от Gemini (файл «%s», страница %s, "
                "попытка %s/%s): %s",
                filename,
                page_number,
                json_attempt,
                INVOICE_JSON_MAX_ATTEMPTS,
                error,
            )
            if json_attempt < INVOICE_JSON_MAX_ATTEMPTS:
                time.sleep(INVOICE_JSON_RETRY_DELAY_SECONDS)

    assert last_error is not None
    raise GeminiRequestError(
        f"Gemini вернул некорректный JSON для страницы {page_number} файла "
        f"«{filename}» после {INVOICE_JSON_MAX_ATTEMPTS} попыток: {last_error}",
        status_code=502,
    ) from last_error


def process_invoice(file_bytes: bytes, filename: str, api_key: str) -> dict[str, Any]:
    """Render every page, run the universal invoice recognizer on each one via
    Gemini, then merge the pages into a single invoice payload.

    Header fields (senderName/orderNumber/date) are expected on page 1, per
    the 4-line marker protocol, but every page is scanned as a fallback and
    for multi-page item tables. A lightweight text-layer regex pass
    (extract_header_metadata) fills in the sender name / EB order number
    only if Gemini could not read them.
    """
    if genai is None or types is None:
        raise RuntimeError("Не установлена библиотека google-genai.")
    if not api_key:
        raise ValueError(
            "API-ключ Gemini не найден. Укажите ключ в настройках или GEMINI_API_KEY."
        )

    pages = render_uploaded_pages(filename, file_bytes)
    if not pages:
        raise ValueError("Документ не содержит страниц для распознавания.")

    client = genai.Client(
        api_key=api_key,
        http_options=types.HttpOptions(timeout=GEMINI_REQUEST_TIMEOUT_MS),
    )

    sender_name = ""
    order_number = ""
    date = ""
    items: list[dict[str, Any]] = []
    failed_pages: list[dict[str, Any]] = []

    for page_number, image_bytes in enumerate(pages, start=1):
        try:
            page_result = analyze_page(
                client,
                image_bytes,
                page_number,
                filename,
                initial_delay_seconds=GEMINI_INITIAL_DELAY_SECONDS if page_number > 1 else 0,
            )
        except (GeminiRequestError, GeminiGatewayTimeoutError) as error:
            # Одна повреждённая/необработанная страница не должна обрушивать
            # весь документ: логируем и переходим к следующей странице, а не
            # выбрасываем ошибку клиенту (иначе теряются уже распознанные
            # страницы этого же файла).
            logging.error(
                "Страница %s файла «%s» не распознана, пропускаем: %s",
                page_number,
                filename,
                error,
            )
            failed_pages.append({"page": page_number, "error": str(error)})
            continue
        if not sender_name and page_result.get("senderName"):
            sender_name = page_result["senderName"]
        if not order_number and page_result.get("orderNumber"):
            order_number = page_result["orderNumber"]
        if not date and page_result.get("date"):
            date = page_result["date"]
        items.extend(page_result.get("items", []))

    if failed_pages and not items and not sender_name and not order_number:
        # Ни одной страницы распознать не удалось — это уже настоящая ошибка,
        # клиенту нужно об этом сообщить явно, а не вернуть пустую накладную.
        raise GeminiRequestError(
            f"Не удалось распознать ни одной страницы файла «{filename}» "
            f"({len(failed_pages)} из {len(pages)} стр. с ошибкой).",
            status_code=502,
        )

    if not sender_name or not order_number:
        page_texts = extract_pdf_page_texts(file_bytes, filename)
        fallback_sender, fallback_order = extract_header_metadata("\n".join(page_texts))
        if not sender_name and fallback_sender != "no_name":
            sender_name = fallback_sender
        if not order_number and fallback_order != "EB_no_data":
            order_number = fallback_order
    if not date:
        date = datetime.now().strftime("%d.%m.%Y")

    return {
        "totalPages": len(pages),
        "senderName": sender_name,
        "orderNumber": order_number,
        "date": date,
        "items": items,
        "failedPages": failed_pages,
    }


def _chat_file_parts(uploaded_file: Any) -> list[Any]:
    """Prepare chat attachments on the server, never in the browser."""
    filename = uploaded_file.filename or "attachment"
    file_bytes = uploaded_file.read()
    if not file_bytes:
        raise ValueError(f"Файл «{filename}» пустой.")

    mime_type = (
        uploaded_file.mimetype
        or "application/octet-stream"
    ).lower()
    is_image = mime_type.startswith("image/")
    is_pdf = mime_type == "application/pdf" or filename.lower().endswith(".pdf")

    if is_pdf:
        return [
            types.Part.from_bytes(data=page, mime_type="image/jpeg")
            for page in render_uploaded_pages(filename, file_bytes)
        ]
    if is_image:
        return [
            types.Part.from_bytes(
                data=optimize_image_for_ocr(file_bytes),
                mime_type="image/jpeg",
            )
        ]
    if mime_type.startswith("text/"):
        return [types.Part.from_text(text=file_bytes.decode("utf-8", errors="replace"))]

    return [types.Part.from_bytes(data=file_bytes, mime_type=mime_type)]


def _chat_history_parts(history: Any) -> list[Any]:
    """Accept only the small text history needed by the chat UI."""
    if not isinstance(history, list):
        return []

    contents: list[Any] = []
    for entry in history[-2:]:
        if not isinstance(entry, dict):
            continue
        role = "model" if entry.get("role") == "model" else "user"
        parts = entry.get("parts")
        if not isinstance(parts, list):
            continue
        text_parts = [
            types.Part.from_text(text=str(part.get("text", "")))
            for part in parts
            if isinstance(part, dict) and str(part.get("text", "")).strip()
        ]
        if text_parts:
            contents.append(types.Content(role=role, parts=text_parts))
    return contents


def process_chat(
    file_storage: list[Any],
    text: str,
    instruction: str,
    history: Any,
    api_key: str,
) -> dict[str, Any]:
    if genai is None or types is None:
        raise RuntimeError("Не установлена библиотека google-genai.")
    if not api_key:
        raise ValueError(
            "API-ключ Gemini не найден. Укажите ключ в настройках или GEMINI_API_KEY."
        )

    parts: list[Any] = []
    if text.strip():
        parts.append(types.Part.from_text(text=text.strip()))
    if instruction.strip():
        parts.append(types.Part.from_text(text=instruction.strip()))
    for uploaded_file in file_storage:
        parts.extend(_chat_file_parts(uploaded_file))
    if not parts:
        parts.append(types.Part.from_text(text="Ответь коротко."))

    client = genai.Client(
        api_key=api_key,
        http_options=types.HttpOptions(timeout=GEMINI_REQUEST_TIMEOUT_MS),
    )
    contents = _chat_history_parts(history)
    contents.append(types.Content(role="user", parts=parts))
    response = _generate_content_with_retry(
        client,
        contents=contents,
        config=types.GenerateContentConfig(
            max_output_tokens=8192,
            temperature=0.2,
        ),
        context="chat",
        initial_delay_seconds=(
            GEMINI_INITIAL_DELAY_SECONDS if file_storage else 0
        ),
    )
    response_text = _response_text(response)
    if not response_text:
        raise GeminiRequestError("Gemini не вернул текстовый ответ.", 502)
    usage = getattr(response, "usage_metadata", None)
    return {
        "text": response_text,
        "usage": {
            "promptTokenCount": getattr(usage, "prompt_token_count", 0) or 0,
            "candidatesTokenCount": getattr(usage, "candidates_token_count", 0) or 0,
            "totalTokenCount": getattr(usage, "total_token_count", 0) or 0,
        },
    }


@app.get("/health")
def health():
    return jsonify({
        "status": "ok",
        "pymupdf": fitz is not None,
        "pdf2image": convert_from_bytes is not None,
        "google_genai": genai is not None,
        "gemini_model": GEMINI_MODEL,
    }), 200


@app.get("/get-config")
def get_config():
    return jsonify({
        "status": "ok",
        "input_folder": INPUT_FOLDER,
        "output_folder": OUTPUT_FOLDER,
        "gemini_model": GEMINI_MODEL,
    }), 200


@app.post("/process-invoice")
def process_invoice_endpoint():
    uploaded_file = request.files.get("file")
    if uploaded_file is None or not uploaded_file.filename:
        return _error("PDF-файл не передан.")

    try:
        result = process_invoice(
            uploaded_file.read(),
            uploaded_file.filename,
            _api_key(),
        )
        return jsonify({
            "status": "success",
            **result,
        }), 200
    except ValueError as error:
        return _error(str(error), 400)
    except GeminiGatewayTimeoutError as error:
        logging.error("Gemini gateway timeout for %s: %s", uploaded_file.filename, error)
        return _error(str(error), 504)
    except GeminiRequestError as error:
        logging.error("Gemini request failed for %s: %s", uploaded_file.filename, error)
        return _error(str(error), error.status_code)
    except Exception as error:
        logging.exception("Ошибка обработки накладной %s", uploaded_file.filename)
        return _error(str(error) or "Не удалось обработать накладную.", 500)


@app.post("/prepare-pdf")
def prepare_pdf_endpoint():
    """Prepare PDF pages for browser-side, one-page-at-a-time Gemini OCR."""
    uploaded_file = request.files.get("file")
    if uploaded_file is None or not uploaded_file.filename:
        return _error("PDF-файл не передан.")

    try:
        result = prepare_pdf_document(
            uploaded_file.read(),
            uploaded_file.filename,
        )
        return jsonify({"status": "success", **result}), 200
    except ValueError as error:
        return _error(str(error), 400)
    except Exception as error:
        logging.exception("Ошибка подготовки PDF %s", uploaded_file.filename)
        return _error(str(error) or "Не удалось подготовить PDF.", 500)


@app.post("/gemini-chat")
def gemini_chat_endpoint():
    try:
        uploaded_files = request.files.getlist("file")
        if not uploaded_files:
            uploaded_files = request.files.getlist("files")
        result = process_chat(
            uploaded_files,
            request.form.get("text", ""),
            request.form.get("instruction", ""),
            json.loads(request.form.get("history", "[]") or "[]"),
            _api_key(),
        )
        return jsonify({
            "status": "success",
            "gemini_model": GEMINI_MODEL,
            **result,
        }), 200
    except json.JSONDecodeError:
        return _error("История чата имеет некорректный формат JSON.", 400)
    except ValueError as error:
        return _error(str(error), 400)
    except GeminiGatewayTimeoutError as error:
        logging.error("Gemini chat gateway timeout: %s", error)
        return _error(str(error), 504)
    except GeminiRequestError as error:
        logging.error("Gemini chat request failed: %s", error)
        return _error(str(error), error.status_code)
    except Exception as error:
        logging.exception("Ошибка чата Gemini")
        return _error(str(error) or "Не удалось получить ответ Gemini.", 500)

@app.errorhandler(413)
def request_too_large(_exception):
    return _error("Файл слишком большой. Максимальный размер — 25 МБ.", 413)


if __name__ == "__main__":
    port = int(os.getenv("PORT", "8050"))
    logging.info("Запуск Python-сервера на порту %s...", port)
    # threaded=True: лёгкие эндпоинты (/health, /get-config) отвечают, даже
    # пока идёт долгая обработка накладной. Запросы к самому Gemini при этом
    # всё равно строго последовательны — это обеспечивает _GEMINI_CALL_LOCK
    # выше, а не однопоточность сервера.
    app.run(host="0.0.0.0", port=port, debug=False, threaded=True)