#!/bin/bash
# 機構を 1 つずつ壊し、落ちたテスト数を記録する。
# 注入はメソッド本体の先頭 (シグネチャを閉じる `{` の直後) に置く。
set -u
cd .
JH=~/.jdks/amazon-corretto-25.jdk/Contents/Home
out="$1"; : > "$out"

mutate() {
    local label="$1" file="$2" anchor="$3" injection="$4"
    cp "$file" /tmp/mutant.bak
    python3 - "$file" "$anchor" "$injection" << 'PY'
import sys, re
path, anchor, injection = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(path).read()
at = s.find(anchor)
if at < 0:
    sys.exit(9)
# シグネチャを閉じる `{` を、括弧の釣り合いを見ながら探す
i = at + len(anchor); depth = 0
while i < len(s):
    if s[i] == '(': depth += 1
    elif s[i] == ')': depth -= 1
    elif s[i] == '{' and depth <= 0: break
    i += 1
else:
    sys.exit(9)
open(path, 'w').write(s[:i + 1] + '\n        ' + injection + s[i + 1:])
PY
    if [ $? -eq 9 ]; then echo "$label|ANCHOR_NG" >> "$out"; cp /tmp/mutant.bak "$file"; return; fi
    local result
    result=$(JAVA_HOME=$JH ./gradlew --offline test 2>&1)
    if echo "$result" | grep -q 'compileJava FAILED\|compileTestJava FAILED'; then
        echo "$label|COMPILE_NG" >> "$out"
    else
        echo "$label|$(echo "$result" | grep -cE '> .* FAILED$')" >> "$out"
    fi
    cp /tmp/mutant.bak "$file"
}

T=src/main/java/dev/javakky/phpstantypedoc/type
P=src/main/java/dev/javakky/phpstantypedoc/parser

mutate "条件型の評価"       "$T/ConditionalReturnType.java" "static @Nullable String resolve(String declared" "if (true) { return null; }"
mutate "callable の束縛"    "$T/CallableUnification.java" "static void unify(" "if (true) { return; }"
mutate "テキストの単一化"   "$T/TemplateUnification.java" "static void unifyText(" "if (true) { return; }"
mutate "型の単一化"         "$T/TemplateUnification.java" "static void unify(" "if (true) { return; }"
mutate "エイリアス展開"     "$T/AliasExpansion.java" "static @Nullable String expand(String declared" "if (true) { return null; }"
mutate "union の整理"       "$T/TypeUnionSimplifier.java" "public static Optional<List<String>> simplify(" "if (true) { return Optional.empty(); }"
mutate "精密化の置換"       "$T/TypeUnionSimplifier.java" "public static List<String> refineAgainst(" "if (true) { return resolved; }"
mutate "union の丸ごと読み" "$P/PhpDocDeclaredTypeReader.java" "public static String unionTypeFor(" "if (true) { return null; }"
