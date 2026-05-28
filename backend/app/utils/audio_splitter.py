"""
audio_splitter.py

긴 wav 오디오 파일을 일정 시간 단위로 분할하는 유틸리티

역할
- STT 서버 용량 제한 또는 timeout 방지를 위해 긴 음성을 여러 조각으로 나눈다.
- 기본 분할 단위는 5분(300초)
- 출력 파일은 mono 16kHz wav 형식으로 저장한다.
"""

from __future__ import annotations

import os
import subprocess
from pathlib import Path


def split_wav_file(
    wav_path: str,
    segment_seconds: int = 300,
) -> list[str]:
    """
    wav 파일을 segment_seconds 단위로 분할한다.

    Parameters
    ----------
    wav_path : str
        분할할 wav 파일 경로

    segment_seconds : int
        분할 단위(초), 기본값 300초 = 5분

    Returns
    -------
    list[str]
        분할된 wav 파일 경로 목록
    """

    source_path = Path(wav_path)

    if not source_path.exists():
        raise FileNotFoundError(f"분할할 오디오 파일을 찾을 수 없습니다: {wav_path}")

    if source_path.suffix.lower() != ".wav":
        raise ValueError(f"wav 파일만 분할할 수 있습니다: {wav_path}")

    split_dir = source_path.parent / f"{source_path.stem}_parts"
    split_dir.mkdir(parents=True, exist_ok=True)

    output_pattern = split_dir / f"{source_path.stem}_part_%03d.wav"

    command = [
        "ffmpeg",
        "-y",
        "-i",
        str(source_path),
        "-f",
        "segment",
        "-segment_time",
        str(segment_seconds),
        "-ac",
        "1",
        "-ar",
        "16000",
        "-c:a",
        "pcm_s16le",
        str(output_pattern),
    ]

    result = subprocess.run(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )

    if result.returncode != 0:
        raise RuntimeError(
            "오디오 분할 중 ffmpeg 오류가 발생했습니다.\n"
            f"stderr: {result.stderr}"
        )

    split_files = sorted(
        str(path)
        for path in split_dir.glob(f"{source_path.stem}_part_*.wav")
    )

    if not split_files:
        raise RuntimeError("오디오 분할 결과 파일이 생성되지 않았습니다.")

    return split_files