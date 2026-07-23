#!/system/bin/sh
# On-device only: reports whether the newest call wav contains non-zero PCM.
cd /data/user/0/com.justsaid.app.debug/cache || exit 1
for f in call_*.wav; do
  echo "FILE: $f"
  ls -l "$f"
  NONZERO=$(tail -c 64000 "$f" | od -A n -t d2 | grep -c "[1-9]")
  echo "NONZERO_LINES_IN_LAST_2S: $NONZERO"
done
