import sys
import json
import subprocess
import os
import time
from faster_whisper import WhisperModel

# 1. 입력 파일 체크
if len(sys.argv) < 2:
    print("사용법: python3 stt_whisper.py meeting.mp4")
    sys.exit(1)

input_file = sys.argv[1]

# 2. wav 변환 (파일명 충돌 방지)
wav_file = f"temp_{int(time.time())}.wav"

try:
    print("🎧 음성 파일 변환 중 (wav)...")
    subprocess.run([
        "ffmpeg", "-y",
        "-i", input_file,
        "-ar", "16000",
        "-ac", "1",
        wav_file
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    # 3. 모델 로드
    print("🤖 모델 로딩 중 (tiny + int8)...")
    model = WhisperModel(
        "tiny",
        device="cpu", 
        compute_type="int8", 
        cpu_threads=4, 
        num_workers=1
    )

    # 4. STT 실행 (VAD 필터 추가, 500ms 무음 제어)
    print(" 음성 인식 중 ...")
    segments, info = model.transcribe(
        wav_file,
        language="ko",
        beam_size=1,             
        vad_filter=True,         
        vad_parameters=dict(min_silence_duration_ms=500),
        condition_on_previous_text=False,
        initial_prompt="이 회의는 AI 회의록 시스템 캡스톤 디자인 팀프로젝트입니다."
    )

    # 5. 결과 정제
    result = []
    print("\n📄 변환 결과:\n")

    for segment in segments:
        text = segment.text.strip()
        if len(text) > 1: 
            print(f"[{segment.start:.2f} - {segment.end:.2f}] {text}")
            result.append({
                "start": round(segment.start, 2),
                "end": round(segment.end, 2),
                "text": text
            })

    # 6. 결과 파일 저장
    output_name = os.path.splitext(input_file)[0]
    with open(f"{output_name}_transcript.json", "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=4)

    with open(f"{output_name}_transcript.txt", "w", encoding="utf-8") as f:
        for r in result:
            f.write(r["text"] + "\n")

finally:
    # 7. 에러가 나든 성공하든 임시 파일은 '무조건' 삭제하여 서버 용량 보존
    if os.path.exists(wav_file):
        os.remove(wav_file)

print("\n✅ 최적화 완료!")
print(f"📁 결과 확인: {output_name}_transcript.txt")
