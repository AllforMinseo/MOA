"""
meeting_service.py

회의 관련 비즈니스 로직을 담당하는 서비스 계층

역할
- 회의 생성
- 로그인 사용자 기준 회의 목록 조회
- 로그인 사용자 기준 회의 단건 조회
- 로그인 사용자 기준 회의 수정
- 로그인 사용자 기준 회의 삭제
- 회의의 전체 transcript(전문) 조회

주의
- summary 생성/조회/수정 로직은 summary_service.py에서 담당한다.
- meeting_service.py는 회의 기본 정보 관리에 집중한다.
"""

from __future__ import annotations

import shutil
from pathlib import Path

from sqlalchemy.orm import Session

from models.user_model import User
from repositories.meeting_repository import (
    create_meeting,
    delete_meeting,
    get_meeting_by_id_and_user_id,
    get_meetings_by_user_id,
    update_meeting,
)
from repositories.transcript_repository import get_transcripts_by_meeting_id
from schemas.meeting_schema import (
    MeetingCreate,
    MeetingResponse,
    MeetingUpdate,
)


# -----------------------------------------
# 참석자 변환 유틸
# -----------------------------------------

def attendees_list_to_text(attendees: list[str] | None) -> str | None:
    """
    API 요청에서 받은 참석자 list를 DB 저장용 문자열로 변환

    예시
    ----
    ["홍길동", "김철수"] -> "홍길동,김철수"
    """

    if not attendees:
        return None

    cleaned_attendees = [
        attendee.strip()
        for attendee in attendees
        if attendee and attendee.strip()
    ]

    if not cleaned_attendees:
        return None

    return ",".join(cleaned_attendees)


def attendees_text_to_list(attendees_text: str | None) -> list[str]:
    """
    DB에 저장된 참석자 문자열을 API 응답용 list로 변환

    예시
    ----
    "홍길동,김철수" -> ["홍길동", "김철수"]
    """

    if not attendees_text:
        return []

    return [
        attendee.strip()
        for attendee in attendees_text.split(",")
        if attendee.strip()
    ]


def meeting_to_response(
    meeting,
    *,
    server_file_paths: list[str] | None = None,
) -> MeetingResponse:
    """
    Meeting ORM 객체를 MeetingResponse로 변환

    이유
    ----
    DB에는 attendees가 문자열로 저장되지만,
    API 응답에서는 list[str]로 반환해야 하기 때문이다.
    """

    paths = list(server_file_paths) if server_file_paths is not None else []

    return MeetingResponse(
        id=meeting.id,
        title=meeting.title,
        meeting_date=getattr(meeting, "meeting_date", None),
        meeting_time=getattr(meeting, "meeting_time", None),
        attendees=attendees_text_to_list(getattr(meeting, "attendees", None)),
        description=getattr(meeting, "description", None),
        server_file_paths=paths,
        created_at=meeting.created_at,
        updated_at=meeting.updated_at,
    )


def _image_paths_for_meeting(db: Session, meeting_id: int) -> list[str]:
    """회의에 연결된 image 행의 file_path 목록 (첨부·다운로드 UI용)."""
    images = get_images_by_meeting_id(db, meeting_id, skip=0, limit=500)
    return [img.file_path for img in images if getattr(img, "file_path", None)]


# -----------------------------------------
# 회의 기본 CRUD
# -----------------------------------------

def create_new_meeting(
    db: Session,
    meeting_data: MeetingCreate,
    current_user: User,
) -> MeetingResponse:
    """
    회의 생성 서비스

    현재 로그인한 사용자의 id를 user_id로 저장한다.
    """

    attendees_text = attendees_list_to_text(meeting_data.attendees)

    meeting = create_meeting(
        db=db,
        meeting_data=meeting_data,
        user_id=current_user.id,
        attendees_text=attendees_text,
    )

    return meeting_to_response(meeting, server_file_paths=[])


def get_meeting_detail(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> MeetingResponse | None:
    """
    현재 로그인한 사용자의 회의 단건 조회 서비스
    """

    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )

    if meeting is None:
        return None

    paths = _image_paths_for_meeting(db, meeting_id)
    return meeting_to_response(meeting, server_file_paths=paths)


def get_meeting_list(
    db: Session,
    current_user: User,
    skip: int = 0,
    limit: int = 100,
) -> list[MeetingResponse]:
    """
    현재 로그인한 사용자의 회의 목록 조회 서비스
    """

    meetings = get_meetings_by_user_id(
        db=db,
        user_id=current_user.id,
        skip=skip,
        limit=limit,
    )

    return [
        meeting_to_response(meeting)
        for meeting in meetings
    ]


def update_meeting_detail(
    db: Session,
    meeting_id: int,
    meeting_data: MeetingUpdate,
    current_user: User,
) -> MeetingResponse | None:
    """
    현재 로그인한 사용자의 회의 수정 서비스
    """

    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )

    if meeting is None:
        return None

    attendees_text = None

    if meeting_data.attendees is not None:
        attendees_text = attendees_list_to_text(meeting_data.attendees)

    updated_meeting = update_meeting(
        db=db,
        meeting=meeting,
        meeting_data=meeting_data,
        attendees_text=attendees_text,
    )

    paths = _image_paths_for_meeting(db, meeting_id)
    return meeting_to_response(updated_meeting, server_file_paths=paths)


def remove_meeting(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> bool:
    """
    현재 로그인한 사용자의 회의 삭제 서비스

    동작 방식
    --------
    1. 현재 로그인한 사용자의 회의인지 확인
    2. 해당 회의의 업로드 폴더 경로 계산
    3. DB에서 회의 삭제
    4. 서버에 저장된 실제 업로드 폴더 삭제

    삭제 대상 폴더
    ------------
    uploads/users/{user_id}/meetings/{meeting_id}

    Returns
    -------
    bool
        삭제 성공 여부
    """

    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )

    if meeting is None:
        return False

    # DB 삭제 전에 삭제할 폴더 경로를 먼저 만들어 둔다.
    meeting_upload_dir = Path(
        f"uploads/users/{current_user.id}/meetings/{meeting_id}"
    )

    # 1. DB에서 회의 삭제
    # ForeignKey ON DELETE CASCADE가 설정되어 있으면
    # 관련 transcript, summary, image 데이터도 함께 삭제된다.
    delete_meeting(db, meeting)

    # 2. 서버에 남아 있는 실제 업로드 폴더 삭제
    # 폴더가 없으면 아무 작업도 하지 않는다.
    if meeting_upload_dir.exists() and meeting_upload_dir.is_dir():
        shutil.rmtree(meeting_upload_dir)

    return True


def get_full_transcript_for_meeting(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> str | None:
    """
    특정 회의의 전체 transcript(전문)를 하나의 문자열로 반환

    동작 방식
    --------
    1. 현재 로그인한 사용자의 회의인지 확인
    2. 해당 회의의 transcript 전체 조회
    3. 시간 순서대로 transcript 내용을 이어 붙여 하나의 문자열 생성

    Returns
    -------
    str | None
        회의가 없으면 None
        transcript가 없으면 빈 문자열("")
    """

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

    full_text = "\n".join(
        transcript.content
        for transcript in reversed(transcripts)
        if (transcript.content or "").strip()
    )

    return full_text