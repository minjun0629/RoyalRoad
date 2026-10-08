#!/bin/sh
# 오리지널 콘텐츠 (content/original/*.yml) 를 다시 만든다. 저장소 맨 위에서: sh tools/gen_originals/build.sh
set -e
T=$(mktemp -d)
python3 tools/gen_originals/orig_regions.py . "$T/regions.yml"
python3 tools/gen_originals/orig_items.py "$T" .
python3 tools/gen_originals/orig_world.py "$T" .
D=src/main/resources/content/original
mkdir -p "$D"
for f in "$T"/*.yml; do
  n=$(basename "$f")
  { echo "# 오리지널 콘텐츠 (원작 『달빛조각사』에 없는 이 게임의 것) — 같은 이름의 원작 파일 content/$n 에 합쳐진다."
    echo "# tools/gen_originals/*.py 로 생성. 같은 id 가 원작 파일에 있으면 시작할 때 오류 (원작을 덮어쓰지 않는다)."
    cat "$f"; } > "$D/$n"
done
rm -rf "$T"
python3 tools/gen_originals/balance_report.py .
