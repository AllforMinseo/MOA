한성대학교 모바일소프트웨어 캡스톤 연대기팀입니다.
<img width="1061" height="922" alt="스크린샷 2026-03-10 143527" src="https://github.com/user-attachments/assets/075a8ff8-c62d-4dd8-80c0-d2ff33d5018d" />


# MOA Backend (Multimodal Orchestrated Assistant)

## 📌 프로젝트 소개

MOA는 **회의 음성(STT) + 회의 자료(OCR)**를 기반으로
AI가 자동으로 회의 내용을 분석하여

* 회의 요약 (Summary)
* 결정 사항 (Decisions)
* Action Item (할 일 목록)

을 생성하는 **멀티모달 AI 회의 분석 시스템**입니다.

본 backend는 해당 기능을 수행하는 **AI 파이프라인 및 서버 로직**을 담당합니다.

---

## 🧠 시스템 구조

```
Audio / Image Input
        ↓
Whisper STT / OCR
        ↓
Preprocessing
        ↓
LLM (GPT)
        ↓
Summary / Decisions / Action Items
        ↓
DB 저장 및 응답
```

---

## 📁 폴더 구조

```
backend/
└─ app/
   ├─ main.py
   ├─ ai/
   ├─ config/
   ├─ models/
   ├─ repositories/
   ├─ routers/
   ├─ services/
   ├─ storage/
   ├─ utils/
```

---

## 📂 폴더 및 파일 설명

### 🔹 main.py

* 백엔드 실행 진입점
* API 서버 초기화 및 라우터 연결

---

### 🔹 ai/ (AI 모델 실행 계층)

| 파일                    | 설명                           |
| --------------------- | ---------------------------- |
| stt_engine.py         | Whisper 기반 STT 처리 (음성 → 텍스트) |
| image_ocr.py          | 이미지에서 텍스트 추출 (OCR)           |
| meeting_summarizer.py | LLM(GPT)을 이용한 회의 요약/구조화      |

👉 실제 AI 모델 호출이 일어나는 영역

---

### 🔹 config/ (설정 관리)

| 파일               | 설명                  |
| ---------------- | ------------------- |
| settings.py      | 환경 변수 및 설정 관리       |
| openai_client.py | OpenAI API 클라이언트 설정 |
| database.py      | DB 연결 설정 (추후 구현)    |

---

### 🔹 models/ (데이터 모델)

| 파일                  | 설명                      |
| ------------------- | ----------------------- |
| meeting_model.py    | 회의 기본 정보                |
| transcript_model.py | STT 텍스트 데이터             |
| summary_model.py    | 요약 / 결정사항 / Action Item |
| image_model.py      | 이미지 및 OCR 결과            |
| base.py             | 공통 모델 정의                |

---

### 🔹 repositories/ (DB 접근 계층)

| 파일                       | 설명               |
| ------------------------ | ---------------- |
| meeting_repository.py    | 회의 데이터 저장/조회     |
| transcript_repository.py | STT 텍스트 저장       |
| summary_repository.py    | 요약 결과 저장         |
| image_repository.py      | 이미지 및 OCR 데이터 저장 |

👉 DB CRUD 담당 (비즈니스 로직 없음)

---

### 🔹 routers/ (API 엔드포인트)

| 파일                | 설명           |
| ----------------- | ------------ |
| meeting_router.py | 회의 분석 요청 API |
| upload_router.py  | 파일 업로드 API   |

👉 클라이언트(Android)와 직접 연결되는 부분

---

### 🔹 services/ (비즈니스 로직)

| 파일                 | 설명                        |
| ------------------ | ------------------------- |
| meeting_service.py | 전체 회의 처리 흐름 orchestration |
| stt_service.py     | STT 처리                    |
| summary_service.py | LLM 요약 처리                 |
| image_service.py   | OCR 처리                    |
| upload_service.py  | 파일 업로드 처리                 |

👉 실제 기능 흐름을 담당하는 핵심 계층

---

### 🔹 storage/ (파일 관리)

| 파일              | 설명         |
| --------------- | ---------- |
| file_manager.py | 파일 저장 및 관리 |
| upload_paths.py | 업로드 경로 설정  |

---

### 🔹 utils/

| 파일            | 설명                         |
| ------------- | -------------------------- |
| preprocess.py | STT 텍스트 전처리 (공백 정리, 구조화 등) |

👉 LLM 입력 품질을 높이기 위한 전처리

---

## ⚙️ 실행 방법

### 1. backend 폴더 이동

```bash
cd backend
```

### 2. 라이브러리 설치

```bash
pip install -r requirements.txt
```

### 3. 환경 변수 설정

`.env` 파일 생성

```env
OPENAI_API_KEY=your_api_key
OPENAI_MODEL=gpt-4o-mini
```

---

## 🧪 테스트 방법

현재는 API 서버 없이도 테스트 가능

```python
from services.meeting_service import process_meeting_text

result = process_meeting_text(
    stt_raw="검색 속도가 느린 것 같아요 그래서 확인이 필요합니다",
    ocr_text="회의 주제: 검색 성능 개선",
    title="검색 개선 회의"
)

print(result)
```

---

## 🚧 현재 구현 상태

| 기능            | 상태   |
| ------------- | ---- |
| STT (Whisper) | 예정   |
| OCR           | 예정   |
| LLM 요약        | ✅ 구현 |
| 전처리           | ✅ 구현 |
| API 서버        | 일부   |
| DB            | 예정   |

---

## 🔥 핵심 설계 특징

* **멀티모달 처리 (음성 + 이미지)**
* **프레임워크 독립적인 AI 파이프라인**
* **전처리 최소화 → LLM 중심 구조**
* **확장 가능한 계층형 구조 (ai / service / repository 분리)**

---

## 📌 향후 계획

* Whisper STT 실제 연동
* OCR 정확도 개선
* 실시간 자막 기능
* Android 앱 연동
* DB 저장 및 조회 기능 구현

---

## 👥 역할 분담 (예시)

| 역할            | 담당 |
| ------------- | -- |
| AI (LLM, 전처리) | 본인 |
| STT           | 팀원 |
| OCR           | 팀원 |
| Backend API   | 팀원 |
| Android 앱     | 팀원 |

---

## 📌 한 줄 정리

> MOA Backend는 **회의 데이터를 AI로 분석하여 자동으로 회의록을 생성하는 시스템의 핵심 엔진**이다.
