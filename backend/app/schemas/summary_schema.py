"""
summary_schema.py

회의 요약(Summary) 관련 요청/응답 스키마 정의

역할
- 요약 생성 요청/응답 형식 정의
- 회의 요약 조회 API 응답 형식 정의

주의
- 실제 요약 생성은 ai/meeting_summarizer.py에서 수행
- DB 저장 구조는 models/summary_model.py에서 관리
- DB에는 JSON 문자열 형태로 저장
- API 응답에서는 dict 형태로 구조화하여 반환 가능
"""

from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class SummaryCreate(BaseModel):
    """
    summary 생성 요청 스키마

    실제 서비스에서는 transcript를 바탕으로 자동 생성되는 경우가 많지만,
    테스트나 수동 생성 시 사용할 수 있음
    """

    meeting_id: int = Field(..., gt=0, description="회의 ID")
    content: str = Field(..., min_length=1, description="DB 저장용 요약 문자열")



class SummaryResponse(BaseModel):
    """
    summary DB 응답 스키마

    ORM 객체를 그대로 응답해야 할 때 사용
    content는 JSON 문자열 상태
    """
     
    model_config = ConfigDict(from_attributes=True)

    id: int
    meeting_id: int
    content: str
    created_at: datetime
    updated_at: datetime


class SummaryGenerateResponse(BaseModel):
    """
    요약 생성 API 응답 스키마

    summary는 구조화된 dict 형태로 반환
    """

    meeting_id: int
    summary: dict[str, Any]


class ActionItem(BaseModel):
    """
    회의 action item 객체 스키마

    예시
    ----
    {
        "task": "프론트는 access_token을 헤더에 포함",
        "assignee": "김철수",
        "due_date": "2026.05.13"
    }
    """

    task: str = Field(..., description="해야 할 일")
    assignee: str | None = Field(default=None, description="담당자")
    due_date: str | None = Field(default=None, description="마감일")


class SummaryUpdateRequest(BaseModel):
    """
    회의 summary 수정 요청 스키마

    사용 위치
    -------
    PATCH /meetings/{meeting_id}/summary
    """

    summary: str = Field(default="", description="회의 요약")
    decisions: list[str] = Field(default_factory=list, description="결정사항 목록")
    action_items: list[ActionItem] = Field(
        default_factory=list,
        description="할 일 목록",
    )


class SummaryDetailResponse(BaseModel):
    """
    회의 요약 조회 API 응답 스키마

    DB의 JSON 문자열을 다시 파싱해서 dict로 반환할 때 사용
    """

    id: int
    meeting_id: int
    summary: dict[str, Any]
    created_at: datetime
    updated_at: datetime

