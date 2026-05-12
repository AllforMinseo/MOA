"""
meeting_service.py

회의 관련 비즈니스 로직을 담당하는 서비스 계층
"""

from __future__ import annotations

import json
from typing import Any

from sqlalchemy.orm import Session

from ai.meeting_summarizer import summarize_meeting_from_payload
from models.user_model import User
from repositories.image_repository import get_images_by_meeting_id
from repositories.meeting_repository import (
    create_meeting,
    delete_meeting,
    get_meeting_by_id_and_user_id,
    get_meetings_by_user_id,
    update_meeting,
)
from repositories.summary_repository import (
    get_summary_by_meeting_id,
    upsert_summary,
)
from repositories.transcript_repository import get_transcripts_by_meeting_id
from schemas.meeting_schema import MeetingCreate, MeetingResponse, MeetingUpdate
from schemas.summary_schema import (
    SummaryCreate,
    SummaryDetailResponse,
    SummaryGenerateResponse,
    SummaryUpdate,
)


def create_new_meeting(
    db: Session,
    meeting_data: MeetingCreate,
    current_user: User,
) -> MeetingResponse:
    meeting = create_meeting(
        db=db,
        meeting_data=meeting_data,
        user_id=current_user.id,
    )
    return MeetingResponse.model_validate(meeting)


def get_meeting_detail(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> MeetingResponse | None:
    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )
    if meeting is None:
        return None
    return MeetingResponse.model_validate(meeting)


def get_meeting_list(
    db: Session,
    current_user: User,
    skip: int = 0,
    limit: int = 100,
) -> list[MeetingResponse]:
    meetings = get_meetings_by_user_id(
        db=db,
        user_id=current_user.id,
        skip=skip,
        limit=limit,
    )
    return [MeetingResponse.model_validate(meeting) for meeting in meetings]


def update_meeting_detail(
    db: Session,
    meeting_id: int,
    meeting_data: MeetingUpdate,
    current_user: User,
) -> MeetingResponse | None:
    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )
    if meeting is None:
        return None

    updated_meeting = update_meeting(db, meeting, meeting_data)
    return MeetingResponse.model_validate(updated_meeting)


def remove_meeting(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> bool:
    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )
    if meeting is None:
        return False

    delete_meeting(db, meeting)
    return True


def get_full_transcript_for_meeting(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> str | None:
    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )
    if meeting is None:
        return None

    transcripts = get_transcripts_by_meeting_id(db, meeting_id)
    if not transcripts:
        return ""

    return "\n".join(
        transcript.content
        for transcript in reversed(transcripts)
        if (transcript.content or "").strip()
    )


def _build_stt_payload(transcripts: list[Any]) -> list[dict[str, Any]]:
    stt_items: list[dict[str, Any]] = []

    for transcript in reversed(transcripts):
        content = (transcript.content or "").strip()
        if not content:
            continue

        stt_items.append(
            {
                "id": transcript.id,
                "meeting_id": transcript.meeting_id,
                "content": content,
                "created_at": (
                    transcript.created_at.isoformat()
                    if getattr(transcript, "created_at", None)
                    else None
                ),
            }
        )

    return stt_items


def _build_ocr_payload(images: list[Any]) -> list[dict[str, Any]]:
    ocr_items: list[dict[str, Any]] = []

    for image in reversed(images):
        ocr_text = (getattr(image, "ocr_text", "") or "").strip()
        analysis_text = (getattr(image, "analysis_text", "") or "").strip()
        if not ocr_text and not analysis_text:
            continue

        ocr_items.append(
            {
                "id": image.id,
                "meeting_id": image.meeting_id,
                "file_path": image.file_path,
                "image_type": getattr(image, "image_type", None),
                "ocr_text": ocr_text,
                "analysis_text": analysis_text,
                "created_at": (
                    image.created_at.isoformat()
                    if getattr(image, "created_at", None)
                    else None
                ),
            }
        )

    return ocr_items


def _normalize_summary_payload(payload: dict[str, Any]) -> dict[str, Any]:
    payload.setdefault("summary", "")
    payload.setdefault("decisions", [])
    payload.setdefault("action_items", [])
    return payload


def _summary_detail_from_orm(summary: Any) -> SummaryDetailResponse:
    try:
        parsed_summary = json.loads(summary.content)
    except json.JSONDecodeError:
        parsed_summary = {
            "summary": summary.content,
            "decisions": [],
            "action_items": [],
        }

    return SummaryDetailResponse(
        id=summary.id,
        meeting_id=summary.meeting_id,
        summary=_normalize_summary_payload(parsed_summary),
        created_at=summary.created_at,
        updated_at=summary.updated_at,
    )


def create_summary_for_meeting(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> SummaryGenerateResponse | None:
    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )
    if meeting is None:
        return None

    transcripts = get_transcripts_by_meeting_id(db, meeting_id)
    images = get_images_by_meeting_id(db, meeting_id)
    stt_items = _build_stt_payload(transcripts)
    ocr_items = _build_ocr_payload(images)

    if not stt_items and not ocr_items:
        return None

    llm_payload = {
        "meeting": {
            "meeting_id": meeting.id,
            "title": meeting.title,
            "description": getattr(meeting, "description", None),
        },
        "stt": stt_items,
        "ocr": ocr_items,
    }

    summary_result = _normalize_summary_payload(summarize_meeting_from_payload(llm_payload))
    summary_data = SummaryCreate(
        meeting_id=meeting_id,
        content=json.dumps(summary_result, ensure_ascii=False),
    )
    upsert_summary(db, summary_data)

    return SummaryGenerateResponse(
        meeting_id=meeting_id,
        summary=summary_result,
    )


def get_summary_for_meeting(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> SummaryDetailResponse | None:
    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )
    if meeting is None:
        return None

    summary = get_summary_by_meeting_id(db, meeting_id)
    if summary is None:
        return None

    return _summary_detail_from_orm(summary)


def update_summary_for_meeting(
    db: Session,
    meeting_id: int,
    summary_update: SummaryUpdate,
    current_user: User,
) -> SummaryDetailResponse | None:
    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )
    if meeting is None:
        return None

    payload = _normalize_summary_payload(summary_update.model_dump())
    summary_data = SummaryCreate(
        meeting_id=meeting_id,
        content=json.dumps(payload, ensure_ascii=False),
    )
    summary = upsert_summary(db, summary_data)

    return _summary_detail_from_orm(summary)
