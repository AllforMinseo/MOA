"""
routers 패키지 초기화 파일

역할
- FastAPI 라우터 객체들을 한 곳에서 export
- main.py에서 app.include_router(...) 할 때 사용
"""

from .auth_router import router as auth_router
from .meeting_router import router as meeting_router
from .upload_router import router as upload_router

__all__ = [
    "auth_router",
    "meeting_router",
    "upload_router",
]