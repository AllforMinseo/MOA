"""
image_ocr.py

이미지 OCR 및 화이트보드 분석 통합 모듈

역할
- 일반 이미지의 텍스트 추출(OCR)
- 화이트보드 이미지의 텍스트 추출 + 구조 분석
- image_type에 따라 처리 로직 분기
- PDF는 이 파일에서 직접 처리하지 않고,
  image_service.py에서 페이지별 이미지로 변환한 뒤 이 OCR 함수를 재사용한다.

지원 image_type
----------------
- "image"      : 일반 이미지
- "whiteboard" : 화이트보드 이미지

반환 형식
---------
{
    "ocr_text": "...",
    "analysis_text": "..."
}
"""

from __future__ import annotations

import base64
import json
import mimetypes
import os

from config.openai_client import get_openai_client
from config.settings import settings


SUPPORTED_IMAGE_MIME_TYPES = {
    "image/jpeg",
    "image/png",
    "image/webp",
    "image/gif",
}


def _detect_mime_type_from_file_header(file_path: str) -> str | None:
    """
    파일의 앞부분 바이트를 읽어서 이미지 MIME 타입을 추정한다.

    이유
    ----
    mimetypes.guess_type()은 파일 확장자만 보고 판단한다.
    Android 업로드 파일은 확장자가 없거나 content-type이 이상할 수 있으므로,
    파일 내용의 시그니처도 확인한다.

    Returns
    -------
    str | None
        감지된 MIME 타입.
        지원 이미지가 아니면 None.
    """

    with open(file_path, "rb") as file:
        header = file.read(16)

    if header.startswith(b"\xff\xd8\xff"):
        return "image/jpeg"

    if header.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"

    if header.startswith(b"GIF87a") or header.startswith(b"GIF89a"):
        return "image/gif"

    if header.startswith(b"RIFF") and header[8:12] == b"WEBP":
        return "image/webp"

    return None


def _guess_mime_type(file_path: str) -> str:
    """
    이미지 파일의 MIME 타입을 안전하게 추정한다.

    우선순위
    --------
    1. 파일 내용의 시그니처로 MIME 타입 확인
    2. 확장자로 MIME 타입 확인
    3. 지원하지 않는 타입이면 에러 발생
    """

    if not os.path.exists(file_path):
        raise FileNotFoundError(f"이미지 파일을 찾을 수 없습니다: {file_path}")

    header_mime_type = _detect_mime_type_from_file_header(file_path)

    if header_mime_type in SUPPORTED_IMAGE_MIME_TYPES:
        return header_mime_type

    mime_type, _ = mimetypes.guess_type(file_path)

    if mime_type == "image/jpg":
        mime_type = "image/jpeg"

    if mime_type in SUPPORTED_IMAGE_MIME_TYPES:
        return mime_type

    raise ValueError(
        "지원하지 않는 이미지 MIME 타입입니다. "
        f"file_path={file_path}, guessed_mime_type={mime_type}. "
        "jpg/jpeg, png, webp, gif 형식의 이미지를 업로드하세요."
    )


def _encode_image_to_data_url(file_path: str) -> str:
    """
    이미지 파일을 data URL(base64) 형태로 변환한다.

    예
    --
    data:image/jpeg;base64,....
    """

    if not os.path.exists(file_path):
        raise FileNotFoundError(f"이미지 파일을 찾을 수 없습니다: {file_path}")

    mime_type = _guess_mime_type(file_path)

    with open(file_path, "rb") as image_file:
        encoded = base64.b64encode(image_file.read()).decode("utf-8")

    return f"data:{mime_type};base64,{encoded}"


def _extract_json_from_response(content: str) -> dict:
    """
    모델 응답에서 JSON 파싱 시도.

    모델이 순수 JSON으로 응답하면 그대로 파싱하고,
    파싱이 실패하면 전체 텍스트를 analysis_text로 반환한다.
    """

    content = (content or "").strip()

    if not content:
        return {
            "ocr_text": "",
            "analysis_text": "",
        }

    try:
        return json.loads(content)

    except json.JSONDecodeError:
        start = content.find("{")
        end = content.rfind("}")

        if start != -1 and end != -1 and end > start:
            try:
                return json.loads(content[start:end + 1])
            except json.JSONDecodeError:
                pass

        return {
            "ocr_text": "",
            "analysis_text": content,
        }


def process_image_by_type(image_path: str, image_type: str = "image") -> dict:
    """
    이미지 종류에 따라 OCR / 화이트보드 분석 수행.

    Parameters
    ----------
    image_path : str
        이미지 파일 경로

    image_type : str
        이미지 종류
        - image
        - whiteboard

    Returns
    -------
    dict
        {
            "ocr_text": str,
            "analysis_text": str
        }
    """

    if image_type not in {"image", "whiteboard"}:
        raise ValueError("image_type은 'image' 또는 'whiteboard'만 가능합니다.")

    client = get_openai_client()
    image_data_url = _encode_image_to_data_url(image_path)

    if image_type == "whiteboard":
        user_instruction = """
다음 화이트보드 이미지를 분석하세요.

반드시 JSON 형식으로만 응답하세요.
형식:
{
  "ocr_text": "이미지에서 읽어낸 텍스트",
  "analysis_text": "화이트보드의 구조, 흐름, 핵심 내용을 정리한 설명"
}

규칙:
- ocr_text에는 실제로 보이는 텍스트를 최대한 정확히 적기
- analysis_text에는 구조, 관계, 흐름, 다이어그램 의미를 설명하기
- 응답은 JSON만 반환
""".strip()
    else:
        user_instruction = """
다음 일반 이미지를 분석하세요.

반드시 JSON 형식으로만 응답하세요.
형식:
{
  "ocr_text": "이미지에서 읽어낸 텍스트",
  "analysis_text": "이미지의 핵심 내용 또는 간단한 설명"
}

규칙:
- ocr_text에는 실제로 보이는 텍스트를 최대한 정확히 적기
- analysis_text에는 이미지의 핵심 내용을 간단히 설명하기
- 응답은 JSON만 반환
""".strip()

    try:
        response = client.chat.completions.create(
            model=settings.openai_model,
            messages=[
                {
                    "role": "system",
                    "content": "당신은 OCR 및 이미지 분석 도우미입니다. 반드시 JSON 형식으로만 응답하세요.",
                },
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": user_instruction},
                        {
                            "type": "image_url",
                            "image_url": {"url": image_data_url},
                        },
                    ],
                },
            ],
            temperature=0.2,
        )

    except Exception as e:
        raise RuntimeError(f"이미지 OCR/분석 중 OpenAI API 오류가 발생했습니다: {e}") from e

    content = response.choices[0].message.content

    if content is None:
        raise RuntimeError("이미지 OCR/분석 응답이 비어 있습니다.")

    parsed = _extract_json_from_response(content)

    return {
        "ocr_text": (parsed.get("ocr_text") or "").strip(),
        "analysis_text": (parsed.get("analysis_text") or "").strip(),
    }