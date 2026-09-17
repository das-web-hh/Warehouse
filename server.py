from __future__ import annotations

import base64
import io
import json
import logging
import os
import sys
import time
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
GEMINI_DELAY_SECONDS = 5
GEMINI_MODEL = "gemini-3.6-flash"

INVOICE_PROMPT = """Проанализируй накладную и верни СТРОГО JSON без markdown:
{
  "senderName": "название отправителя",
  "orderNumber": "номер заказа",
  "date": "дата",
  "items": [
    {"name": "название товара", "menge": 10, "ean": ""}
  ]
}
Извлеки ВСЕ товары из таблицы, не пропускай ни одной строки.
Если значение неразборчиво или отсутствует, оставь соответствующее поле пустым.
menge должен быть целым числом. Не добавляй никаких пояснений вне JSON."""

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
                    "ean": {"type": "STRING"},
                },
                "required": ["name", "menge", "ean"],
            },
        },
    },
    "required": ["senderName", "orderNumber", "date", "items"],
}


def _error(message: str, status: int = 400):
    return jsonify({"status": "error", "message": message}), status


def _api_key() -> str:
    # The browser setting is supported for the existing UI. In production,
    # GEMINI_API_KEY should be configured on the Python server instead.
    return (
        request.form.get("api_key", "").strip()
        or request.headers.get("X-Gemini-Api-Key", "").strip()
        or os.getenv("GEMINI_API_KEY", "").strip()
    )


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
    if cleaned.startswith("```"):
        cleaned = cleaned.removeprefix("```json").removeprefix("```")
        cleaned = cleaned.removesuffix("```").strip()

    try:
        value = json.loads(cleaned)
    except json.JSONDecodeError as exc:
        raise RuntimeError(
            f"Gemini вернул некорректный JSON для страницы {page_number}."
        ) from exc

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
        ean = "".join(character for character in str(item.get("ean", "") or "")
                       if character.isdigit())
        if name and menge > 0:
            items.append({"name": name, "menge": menge, "ean": ean})

    return {
        "senderName": str(value.get("senderName", "") or "").strip(),
        "orderNumber": str(value.get("orderNumber", "") or "").strip(),
        "date": str(value.get("date", "") or "").strip(),
        "items": items,
    }


def analyze_page(client: Any, image_bytes: bytes, page_number: int) -> dict[str, Any]:
    response = client.models.generate_content(
        model=GEMINI_MODEL,
        contents=[
            INVOICE_PROMPT,
            types.Part.from_bytes(data=image_bytes, mime_type="image/jpeg"),
        ],
        config=types.GenerateContentConfig(
            response_mime_type="application/json",
            response_schema=INVOICE_SCHEMA,
            temperature=0,
        ),
    )
    return normalize_invoice_page(_parse_json_response(response, page_number))


def process_invoice(file_bytes: bytes, filename: str, api_key: str) -> dict[str, Any]:
    if genai is None or types is None:
        raise RuntimeError("Не установлена библиотека google-genai.")
    if not api_key:
        raise ValueError(
            "API-ключ Gemini не найден. Укажите ключ в настройках или GEMINI_API_KEY."
        )

    optimized_pages = render_uploaded_pages(filename, file_bytes)
    client = genai.Client(api_key=api_key)
    page_results: list[dict[str, Any]] = []

    # Deliberately sequential: do not parallelize pages or requests.
    for index, image_bytes in enumerate(optimized_pages):
        if index > 0:
            time.sleep(5)
        logging.info(
            "Gemini processing page %s/%s of %s",
            index + 1,
            len(optimized_pages),
            filename,
        )
        page_results.append(analyze_page(client, image_bytes, index + 1))

    first_page = page_results[0]
    all_items = [
        item
        for page in page_results
        for item in page.get("items", [])
    ]
    return {
        "senderName": first_page.get("senderName", ""),
        "orderNumber": first_page.get("orderNumber", ""),
        "date": first_page.get("date", ""),
        "items": all_items,
        "pages": page_results,
        "pagesProcessed": len(page_results),
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
            "invoice": result,
            "data": result,
        }), 200
    except ValueError as error:
        return _error(str(error), 400)
    except Exception as error:
        logging.exception("Ошибка обработки накладной %s", uploaded_file.filename)
        return _error(str(error) or "Не удалось обработать накладную.", 500)


@app.post("/prepare-image")
def prepare_image():
    """Backward-compatible endpoint used by the camera workflow."""
    uploaded_file = request.files.get("file")
    if uploaded_file is None or not uploaded_file.filename:
        return _error("Файл не передан.")

    try:
        processed_bytes = optimize_image_for_ocr(uploaded_file.read())
        return jsonify({
            "status": "success",
            "image_base64": base64.b64encode(processed_bytes).decode("ascii"),
            "mime_type": "image/jpeg",
        }), 200
    except Exception as error:
        logging.exception("Ошибка подготовки изображения %s", uploaded_file.filename)
        return _error(str(error) or "Не удалось обработать изображение.", 500)


@app.errorhandler(413)
def request_too_large(_error):
    return _error("Файл слишком большой. Максимальный размер — 25 МБ.", 413)


if __name__ == "__main__":
    port = int(os.getenv("PORT", "8050"))
    logging.info("Запуск Python-сервера на порту %s...", port)
    app.run(host="0.0.0.0", port=port, debug=False)