import requests

url = "http://34.47.117.201:5000/transcript"

with open("3분 24초.wav", "rb") as f:
    response = requests.post(
        url,
        files={"file": ("3분 24초.wav", f, "audio/wav")},
        timeout=180
    )

print(response.status_code)
print(response.text)