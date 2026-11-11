#!/usr/bin/env bash
# Builds the plugin zip using the JDK and Kotlin compiler bundled with Android Studio.
# No Gradle or internet needed. Output: build/distributions/open-branch-changes-<version>.zip
#
# Usage: ./build.sh                       (uses /Applications/Android Studio.app)
#        STUDIO="/path/to/Android Studio.app" ./build.sh
set -euo pipefail

cd "$(dirname "$0")"
STUDIO="${STUDIO:-/Applications/Android Studio.app}"
CONTENTS="$STUDIO/Contents"
[ -d "$CONTENTS" ] || CONTENTS="$STUDIO"   # Linux/Windows-style layout

export JAVA_HOME="$CONTENTS/jbr/Contents/Home"
[ -d "$JAVA_HOME" ] || JAVA_HOME="$CONTENTS/jbr"
KOTLINC="$CONTENTS/plugins/Kotlin/kotlinc/bin/kotlinc"
JAR="$JAVA_HOME/bin/jar"

VERSION="$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' src/main/resources/META-INF/plugin.xml | head -1)"
NAME="branch-changes"
OUT="build"
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/distributions" "$OUT/zip/$NAME/lib"

# Compile against the IDE's own jars (platform + Git plugin). Nothing is bundled into the plugin.
# From 2026.3 the shared VCS classes live in a separate bundled plugin, so include it when present.
CP_DIRS=("$CONTENTS/lib" "$CONTENTS/plugins/vcs-git/lib")
[ -d "$CONTENTS/plugins/platform-vcs-plugin" ] && CP_DIRS+=("$CONTENTS/plugins/platform-vcs-plugin")
CP="$(find "${CP_DIRS[@]}" -name '*.jar' | tr '\n' ':')"

echo "Compiling with $("$KOTLINC" -version 2>&1 | grep -o 'kotlinc-jvm [^ ]*')..."
"$KOTLINC" \
  -no-stdlib -no-reflect \
  -jvm-target 21 \
  -language-version 2.0 -api-version 2.0 \
  -classpath "$CP" \
  -d "$OUT/classes" \
  src/main/kotlin

# Copy resources, skipping hidden files/folders (.DS_Store, editor/tool metadata).
rsync -a --exclude '.*' src/main/resources/ "$OUT/classes/"
cp LICENSE "$OUT/classes/META-INF/LICENSE"
if [ -x "$JAR" ]; then
  "$JAR" --create --file "$OUT/zip/$NAME/lib/$NAME.jar" -C "$OUT/classes" .
else
  # Some bundled runtimes (e.g. IntelliJ IDEA's) ship without the jar tool; a jar is just a zip.
  (cd "$OUT/classes" && zip -qr "$OLDPWD/$OUT/zip/$NAME/lib/$NAME.jar" .)
fi

ZIP="$OUT/distributions/$NAME-$VERSION.zip"
(cd "$OUT/zip" && zip -qr "../../$ZIP" "$NAME")
echo "Built $ZIP"
