#!/bin/bash
# 构建 HapticBoost APK
set -u
W=/root/vibapp
cd $W || exit 1
rm -rf out dex build apk
mkdir -p out dex build apk

echo "== [1/5] javac =="
javac -source 8 -target 8 -encoding UTF-8 -nowarn \
      -classpath $W/libs/android.jar -d $W/out \
      $W/src/MainActivity.java 2>&1 | grep -v "bootstrap class path" | head -30
if [ ! -f $W/out/io/github/sakurainakeipoi/hapticboost/MainActivity.class ]; then
  echo "JAVAC FAILED"; exit 1
fi

echo "== [2/5] d8 -> classes.dex =="
java -cp $W/libs/r8.jar com.android.tools.r8.D8 \
     --min-api 21 --lib $W/libs/android.jar \
     --output $W/dex $W/out/io/github/sakurainakeipoi/hapticboost/*.class 2>&1 | tail -5
ls -la $W/dex

echo "== [3/5] AndroidManifest.xml =="
python3 $W/mkmanifest.py $W/apk/AndroidManifest.xml || exit 1

echo "== [4/5] zip =="
rm -f $W/HapticBoost-unsigned.apk
python3 - << 'PY'
import zipfile, os
os.chdir('/root/vibapp')
z = zipfile.ZipFile('HapticBoost-unsigned.apk', 'w', zipfile.ZIP_DEFLATED)
z.write('apk/AndroidManifest.xml', 'AndroidManifest.xml')
z.write('dex/classes.dex', 'classes.dex')
z.close()
print('zip ok', os.path.getsize('HapticBoost-unsigned.apk'))
PY

echo "== [5/5] done =="
ls -la $W/*.apk
