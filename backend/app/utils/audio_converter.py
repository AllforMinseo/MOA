"""
audio_converter.py

ffmpeg CLI를 사용해 오디오를 STT용 WAV(mono, 16kHz, PCM s16le)로 변환·병합한다.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
from pathlib import Path

# STT 서버와 맞춘 기본 출력 포맷
TARGET_SAMPLE_RATE_HZ = 16000
TARGET_CHANNELS = 1


def _which_ffmpeg() -> str:
    exe = shutil.which("ffmpeg")
    if not exe:
        raise RuntimeError(
            "ffmpeg 실행 파일을 찾을 수 없습니다. 서버에 ffmpeg를 설치하고 PATH에 등록하세요.",
        )
    return exe


def _run_ffmpeg(args: list[str]) -> None:
    ffmpeg = _which_ffmpeg()
    cmd = [ffmpeg, "-hide_banner", "-loglevel", "error", *args]
    proc = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        check=False,
    )
    if proc.returncode != 0:
        err = (proc.stderr or proc.stdout or "").strip()
        raise RuntimeError(f"ffmpeg 실패 (exit {proc.returncode}): {err or 'no stderr'}")


def convert_audio_to_wav(input_path: str) -> str:
    """
    단일 오디오 파일을 WAV로 변환한다.

    Returns
    -------
    str
        생성된 .wav 파일의 절대 경로 (호출 측에서 삭제 가능한 임시 파일)
    """

    path = Path(input_path)
    if not path.is_file():
        raise FileNotFoundError(str(path))

    fd, out_path = tempfile.mkstemp(suffix=".wav", prefix="moa_conv_")
    os.close(fd)
    out_path = str(Path(out_path).resolve())

    _run_ffmpeg(
        [
            "-y",
            "-i",
            str(path.resolve()),
            "-vn",
            "-ac",
            str(TARGET_CHANNELS),
            "-ar",
            str(TARGET_SAMPLE_RATE_HZ),
            "-c:a",
            "pcm_s16le",
            "-f",
            "wav",
            out_path,
        ],
    )
    return out_path


def merge_audio_segments_to_wav(segment_paths: list[str]) -> str:
    """
    여러 세그먼트(순서 유지)를 디코딩·연결해 하나의 WAV로 만든다.
    m4a 등 컨테이너 바이트 concat은 하지 않는다.

    Parameters
    ----------
    segment_paths
        순서가 보존된 입력 파일 경로들

    Returns
    -------
    str
        병합된 .wav 경로 (임시 파일)
    """

    if not segment_paths:
        raise ValueError("segment_paths가 비어 있습니다.")

    resolved = [str(Path(p).resolve()) for p in segment_paths]
    for p in resolved:
        if not Path(p).is_file():
            raise FileNotFoundError(p)

    if len(resolved) == 1:
        return convert_audio_to_wav(resolved[0])

    fd, out_path = tempfile.mkstemp(suffix=".wav", prefix="moa_merged_")
    os.close(fd)
    out_path = str(Path(out_path).resolve())

    inputs: list[str] = []
    for p in resolved:
        inputs.extend(["-i", p])

    n = len(resolved)
    concat_inputs = "".join(f"[{i}:a]" for i in range(n))
    filt = f"{concat_inputs}concat=n={n}:v=0:a=1[aout]"

    _run_ffmpeg(
        [
            "-y",
            *inputs,
            "-filter_complex",
            filt,
            "-map",
            "[aout]",
            "-ac",
            str(TARGET_CHANNELS),
            "-ar",
            str(TARGET_SAMPLE_RATE_HZ),
            "-c:a",
            "pcm_s16le",
            "-f",
            "wav",
            out_path,
        ],
    )
    return out_path
