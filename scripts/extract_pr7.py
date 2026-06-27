import json
import re

path = r"C:\Users\Sehaj\.cursor\projects\c-Users-Sehaj-Desktop-iptv\agent-transcripts\4fdbcef6-441f-43a3-b1b1-334ca7b7b985\4fdbcef6-441f-43a3-b1b1-334ca7b7b985.jsonl"
with open(path, encoding="utf-8") as f:
    for line in f:
        if '"text":"# Playback A-Grade' in line:
            text = json.loads(line)["message"]["content"][0]["text"]
            m = re.search(r"## (?:PR-7|Phase 7).*?(?=## |\Z)", text, re.S)
            out = m.group(0) if m else "not found"
            with open(r"C:\Users\Sehaj\Desktop\iptv\pr7-plan.txt", "w", encoding="utf-8") as outf:
                outf.write(out)
            print(out[:6000])
            break
