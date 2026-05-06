"""
upload_router.py

파일 업로드 관련 API 라우터

역할
- 오디오 업로드
- 이미지 업로드

주의
- 실제 파일 저장, 전처리, STT/OCR/분석, DB 저장은 services 계층에서 처리
- 이 파일은 업로드 요청과 응답 처리에 집중
"""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from sqlalchemy.orm import Session

from config.database import get_db
from schemas.image_schema import ImageUploadResponse
from schemas.transcript_schema import TranscriptResponse
from services.audio_service import process_uploaded_audio
from services.image_service import process_uploaded_image

router = APIRouter(
    prefix="/upload",
    tags=["Uploads"],
)


@router.post(
    "/audio/{meeting_id}",
    response_model=TranscriptResponse,
    status_code=status.HTTP_201_CREATED,
    summary="오디오 업로드",
)
def upload_audio(
    meeting_id: int,
    files: Annotated[
        list[UploadFile],
        File(description="순서가 있는 오디오 세그먼트(1개면 기존 단일 업로드와 동일)"),
    ],
    db: Session = Depends(get_db),
) -> TranscriptResponse:
    """
    오디오 파일을 업로드하고 STT를 수행한 뒤 transcript를 저장합니다.

    - 파트 이름은 `files`이며, 동일 이름으로 여러 개를 보낼 수 있습니다.
    - 세그먼트가 2개 이상이면 서버에서 ffmpeg로 병합한 뒤 STT를 한 번만 호출합니다.
    """

    if not files:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="업로드할 오디오 파일이 없습니다.",
        )

    for f in files:
        if not f.filename:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="업로드할 오디오 파일명이 비어 있습니다.",
            )

    return process_uploaded_audio(
        db=db,
        meeting_id=meeting_id,
        upload_files=files,
    )


@router.post(
    "/image/{meeting_id}",
    response_model=list[ImageUploadResponse],
    status_code=status.HTTP_201_CREATED,
    summary="이미지 업로드",
)
def upload_image(
    meeting_id: int,
    files: Annotated[
        list[UploadFile],
        File(description="업로드할 이미지/문서 파일 목록"),
    ],
    image_type: str = Form("image", description="image 또는 whiteboard"),
    db: Session = Depends(get_db),
) -> list[ImageUploadResponse]:
    """
    이미지 파일을 업로드하고 OCR/분석을 수행한 뒤 결과를 저장합니다.

    image_type
    ----------
    - image
    - whiteboard
    """

    if not files:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="업로드할 이미지 파일이 없습니다.",
        )
    for file in files:
        if not file.filename:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="업로드할 이미지 파일명이 비어 있습니다.",
            )

    if image_type not in {"image", "whiteboard"}:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="image_type은 'image' 또는 'whiteboard'만 가능합니다.",
        )

    return [
        process_uploaded_image(
            db=db,
            meeting_id=meeting_id,
            upload_file=file,
            image_type=image_type,
        )
        for file in files
    ]
