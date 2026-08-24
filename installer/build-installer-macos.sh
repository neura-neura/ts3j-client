#!/bin/bash

# Build a self-contained Apple Silicon application and DMG without changing the
# Windows packaging flow. The application version can be supplied as the first
# argument or through APP_VERSION. Tool locations can be overridden with
# JAVA_HOME, JPACKAGE and MVN. Set MACOS_SIGNING_IDENTITY to use a Developer ID
# Application certificate; otherwise the application receives an ad-hoc
# signature suitable for local testing.

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
readonly REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd -P)"
readonly APP_NAME="ts3j-client"
readonly MAIN_CLASS="com.github.manevolent.ts3j.client.AppMain"
readonly BUNDLE_ID="com.github.manevolent.ts3j.client"
readonly ARCHITECTURE="aarch64"
readonly ICON_SOURCE="$SCRIPT_DIR/assets/ts3j-client.png"
readonly ICON_FILE="$SCRIPT_DIR/assets/ts3j-client.icns"
readonly TARGET_DIR="$REPO_ROOT/target"
readonly APP_IMAGE_DIR="$TARGET_DIR/macos-app-image"
readonly RUNTIME_DEPS_DIR="$TARGET_DIR/macos-runtime-dependencies"
readonly JPACKAGE_INPUT_DIR="$TARGET_DIR/macos-jpackage-input"
readonly ICONSET_DIR="$TARGET_DIR/macos-ts3j-client.iconset"
readonly DMG_STAGING_DIR="$TARGET_DIR/macos-dmg-staging"
readonly DIST_DIR="$REPO_ROOT/dist"

if (( $# > 1 )); then
    echo "Uso: $0 [version]" >&2
    exit 2
fi

readonly VERSION="${1:-${APP_VERSION:-1.0.15}}"
readonly DMG_FILE="$DIST_DIR/${APP_NAME}-${VERSION}-macos-${ARCHITECTURE}.dmg"
readonly SIGNING_IDENTITY="${MACOS_SIGNING_IDENTITY:-${DEVELOPER_ID_APPLICATION:-}}"
readonly SIGNING_KEYCHAIN="${MACOS_SIGNING_KEYCHAIN:-}"
readonly MICROPHONE_DESCRIPTION="TS3J Client uses the microphone for its local input meter and activity indicator."

log() {
    printf '[macOS] %s\n' "$*"
}

die() {
    printf '[macOS] ERROR: %s\n' "$*" >&2
    exit 1
}

resolve_executable() {
    local requested="$1"
    local resolved

    if [[ "$requested" == */* ]]; then
        [[ -x "$requested" ]] || die "No se puede ejecutar: $requested"
        printf '%s\n' "$requested"
        return
    fi

    resolved="$(command -v "$requested" 2>/dev/null || true)"
    [[ -n "$resolved" && -x "$resolved" ]] || die "No se encontró el ejecutable '$requested'."
    printf '%s\n' "$resolved"
}

assert_generated_path() {
    local candidate="$1"

    [[ "$candidate" == "$REPO_ROOT/"* ]] || die "Ruta generada fuera del repositorio: $candidate"
    [[ "$candidate" != "$REPO_ROOT" && "$candidate" != "/" ]] || die "Ruta de limpieza no segura: $candidate"

    case "$candidate" in
        "$APP_IMAGE_DIR"|"$RUNTIME_DEPS_DIR"|"$JPACKAGE_INPUT_DIR"|"$ICONSET_DIR"|"$DMG_STAGING_DIR"|"$ICON_FILE"|"$DMG_FILE")
            ;;
        *)
            die "Ruta no incluida en la lista de artefactos generados: $candidate"
            ;;
    esac
}

remove_generated_path() {
    local candidate="$1"
    assert_generated_path "$candidate"
    if [[ -e "$candidate" || -L "$candidate" ]]; then
        rm -rf -- "$candidate"
    fi
}

cleanup_transient_paths() {
    local original_status=$?
    trap - EXIT
    remove_generated_path "$ICONSET_DIR"
    remove_generated_path "$DMG_STAGING_DIR"
    exit "$original_status"
}

trap cleanup_transient_paths EXIT

discover_java_home() {
    local requested_mvn="$1"
    local detected=""
    local maven_info=""
    local candidate=""
    local candidates=()

    if [[ -n "${JAVA_HOME:-}" ]]; then
        [[ -x "$JAVA_HOME/bin/java" ]] || die "JAVA_HOME no contiene bin/java: $JAVA_HOME"
        printf '%s\n' "$(cd "$JAVA_HOME" && pwd -P)"
        return
    fi

    # Prefer JDK 17 because it is the runtime generation baseline for this
    # desktop client, even if Maven itself happens to use a newer JDK.
    if [[ -x /usr/libexec/java_home ]]; then
        if detected="$(/usr/libexec/java_home -v '17' 2>/dev/null)"; then
            candidates+=("$detected")
        fi
    fi

    candidates+=(
        "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
        "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
    )

    if [[ -x /usr/libexec/java_home ]]; then
        if detected="$(/usr/libexec/java_home -v '17+' 2>/dev/null)"; then
            candidates+=("$detected")
        fi
    fi

    # Homebrew's Maven wrapper can use an unlinked JDK that java_home does not
    # know about. Reuse that runtime when possible.
    if maven_info="$("$requested_mvn" -version 2>&1)"; then
        detected="$(printf '%s\n' "$maven_info" | sed -n 's/^Java version:.*, runtime: //p' | head -n 1)"
        if [[ -n "$detected" ]]; then
            candidates+=("$detected")
        fi
    fi

    candidates+=(
        "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
        "/usr/local/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
    )

    for candidate in "${candidates[@]}"; do
        if [[ -x "$candidate/bin/java" && -x "$candidate/bin/jpackage" ]]; then
            printf '%s\n' "$(cd "$candidate" && pwd -P)"
            return
        fi
    done

    die "No se encontró un JDK 17+ con jpackage. Define JAVA_HOME con la ruta del JDK."
}

java_major_version() {
    local java_bin="$1"
    local version_text
    local version
    local major

    version_text="$("$java_bin" -version 2>&1)" || die "No se pudo ejecutar $java_bin."
    version="$(printf '%s\n' "$version_text" | sed -n 's/.*version "\([^"]*\)".*/\1/p' | head -n 1)"
    [[ -n "$version" ]] || die "No se pudo determinar la versión de Java."

    if [[ "$version" == 1.* ]]; then
        major="${version#1.}"
    else
        major="$version"
    fi
    major="${major%%.*}"
    major="${major%%-*}"
    [[ "$major" =~ ^[0-9]+$ ]] || die "Versión de Java no reconocida: $version"
    printf '%s\n' "$major"
}

generate_icns() {
    local source_width
    local source_height
    local index
    local sizes=(16 32 32 64 128 256 256 512 512 1024)
    local names=(
        icon_16x16.png
        icon_16x16@2x.png
        icon_32x32.png
        icon_32x32@2x.png
        icon_128x128.png
        icon_128x128@2x.png
        icon_256x256.png
        icon_256x256@2x.png
        icon_512x512.png
        icon_512x512@2x.png
    )

    [[ -s "$ICON_SOURCE" ]] || die "No se encontró el PNG maestro: $ICON_SOURCE"
    source_width="$("$SIPS_BIN" -g pixelWidth "$ICON_SOURCE" | awk '/pixelWidth:/ {print $2}')"
    source_height="$("$SIPS_BIN" -g pixelHeight "$ICON_SOURCE" | awk '/pixelHeight:/ {print $2}')"
    [[ "$source_width" =~ ^[0-9]+$ && "$source_height" =~ ^[0-9]+$ ]] || die "No se pudieron leer las dimensiones del PNG maestro."
    [[ "$source_width" == "$source_height" ]] || die "El PNG maestro debe ser cuadrado (${source_width}x${source_height})."
    (( source_width >= 1024 )) || die "El PNG maestro debe medir al menos 1024x1024."

    remove_generated_path "$ICONSET_DIR"
    remove_generated_path "$ICON_FILE"
    mkdir -p "$ICONSET_DIR"

    for (( index=0; index<${#sizes[@]}; index++ )); do
        "$SIPS_BIN" -z "${sizes[$index]}" "${sizes[$index]}" "$ICON_SOURCE" \
            --out "$ICONSET_DIR/${names[$index]}" >/dev/null
    done

    "$ICONUTIL_BIN" -c icns "$ICONSET_DIR" -o "$ICON_FILE"
    [[ -s "$ICON_FILE" ]] || die "iconutil no creó un archivo ICNS válido."
    "$FILE_BIN" "$ICON_FILE" | grep -q 'Mac OS X icon' || die "El archivo generado no fue reconocido como ICNS."
    log "Icono macOS generado: $ICON_FILE"
}

maven_evaluate() {
    local expression="$1"
    local raw
    local value

    raw="$("$MVN_BIN" -q -Dstyle.color=never -DforceStdout help:evaluate "-Dexpression=$expression")"
    value="$(printf '%s\n' "$raw" | tr -d '\r' | sed $'s/\033\\[[0-9;]*[[:alpha:]]//g' | awk 'NF { value=$0 } END { print value }')"
    [[ -n "$value" && "$value" != "null object or invalid expression" ]] || die "Maven no pudo evaluar $expression."
    printf '%s\n' "$value"
}

[[ "$(uname -s)" == "Darwin" ]] || die "Este script solo se puede ejecutar en macOS."
[[ "$(uname -m)" == "arm64" ]] || die "Se requiere un Mac Apple Silicon (arm64) para generar este paquete aarch64."
[[ "$VERSION" =~ ^[0-9]+([.][0-9]+){0,2}$ ]] || die "Versión no válida para jpackage: $VERSION"
[[ -f "$REPO_ROOT/pom.xml" ]] || die "No se encontró pom.xml en $REPO_ROOT."

MVN_BIN="$(resolve_executable "${MVN:-mvn}")"
readonly MVN_BIN
RESOLVED_JAVA_HOME="$(discover_java_home "$MVN_BIN")"
readonly RESOLVED_JAVA_HOME
readonly JAVA_BIN="$RESOLVED_JAVA_HOME/bin/java"
JAVA_MAJOR="$(java_major_version "$JAVA_BIN")"
readonly JAVA_MAJOR
(( JAVA_MAJOR >= 17 )) || die "Se requiere JDK 17 o posterior; se encontró Java $JAVA_MAJOR."

export JAVA_HOME="$RESOLVED_JAVA_HOME"
export PATH="$JAVA_HOME/bin:${PATH:-/usr/bin:/bin}"

JPACKAGE_BIN="$(resolve_executable "${JPACKAGE:-$JAVA_HOME/bin/jpackage}")"
readonly JPACKAGE_BIN
SIPS_BIN="$(resolve_executable sips)"
readonly SIPS_BIN
ICONUTIL_BIN="$(resolve_executable iconutil)"
readonly ICONUTIL_BIN
PLUTIL_BIN="$(resolve_executable plutil)"
readonly PLUTIL_BIN
CODESIGN_BIN="$(resolve_executable codesign)"
readonly CODESIGN_BIN
HDIUTIL_BIN="$(resolve_executable hdiutil)"
readonly HDIUTIL_BIN
DITTO_BIN="$(resolve_executable ditto)"
readonly DITTO_BIN
FILE_BIN="$(resolve_executable file)"
readonly FILE_BIN
readonly PLIST_BUDDY="/usr/libexec/PlistBuddy"
[[ -x "$PLIST_BUDDY" ]] || die "No se encontró /usr/libexec/PlistBuddy."

JAVA_ARCH="$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*os.arch = //p' | head -n 1)"
[[ "$JAVA_ARCH" == "aarch64" || "$JAVA_ARCH" == "arm64" ]] || die "El JDK debe ser ARM64; se encontró os.arch=$JAVA_ARCH."
"$JPACKAGE_BIN" --version >/dev/null 2>&1 || die "jpackage no funciona: $JPACKAGE_BIN"
"$MVN_BIN" -version >/dev/null || die "Maven no funciona con JAVA_HOME=$JAVA_HOME."

log "JDK $JAVA_MAJOR ARM64: $JAVA_HOME"
log "Maven: $MVN_BIN"
log "jpackage: $JPACKAGE_BIN"

generate_icns

cd "$REPO_ROOT"
log "Ejecutando pruebas y compilación Maven..."
"$MVN_BIN" clean verify

log "Copiando dependencias de ejecución..."
"$MVN_BIN" dependency:copy-dependencies \
    "-DincludeScope=runtime" \
    "-DoutputDirectory=$RUNTIME_DEPS_DIR"

MAVEN_BUILD_DIR="$(maven_evaluate project.build.directory)"
MAVEN_FINAL_NAME="$(maven_evaluate project.build.finalName)"
if [[ "$MAVEN_BUILD_DIR" != /* ]]; then
    MAVEN_BUILD_DIR="$REPO_ROOT/$MAVEN_BUILD_DIR"
fi
[[ -d "$MAVEN_BUILD_DIR" ]] || die "El directorio de compilación Maven no existe: $MAVEN_BUILD_DIR"
MAVEN_BUILD_DIR="$(cd "$MAVEN_BUILD_DIR" && pwd -P)"
[[ "$MAVEN_BUILD_DIR" == "$REPO_ROOT/"* ]] || die "Maven produjo una ruta fuera del repositorio: $MAVEN_BUILD_DIR"
[[ -n "$MAVEN_FINAL_NAME" && "$MAVEN_FINAL_NAME" != */* && "$MAVEN_FINAL_NAME" != "." && "$MAVEN_FINAL_NAME" != ".." ]] \
    || die "project.build.finalName no es un nombre de archivo seguro: $MAVEN_FINAL_NAME"

readonly MAIN_JAR="$MAVEN_BUILD_DIR/$MAVEN_FINAL_NAME.jar"
[[ -s "$MAIN_JAR" ]] || die "Maven no creó el JAR principal esperado: $MAIN_JAR"

runtime_jars=("$RUNTIME_DEPS_DIR"/*.jar)
[[ -e "${runtime_jars[0]}" ]] || die "Maven no copió dependencias de ejecución a $RUNTIME_DEPS_DIR."
javafx_arm_jars=("$RUNTIME_DEPS_DIR"/javafx-graphics-*-mac-aarch64.jar)
[[ -e "${javafx_arm_jars[0]}" ]] || die "Falta la dependencia nativa JavaFX mac-aarch64; se impediría iniciar la interfaz en Apple Silicon."

remove_generated_path "$JPACKAGE_INPUT_DIR"
remove_generated_path "$APP_IMAGE_DIR"
mkdir -p "$JPACKAGE_INPUT_DIR" "$APP_IMAGE_DIR"
cp -p "$MAIN_JAR" "$JPACKAGE_INPUT_DIR/"
for dependency_jar in "${runtime_jars[@]}"; do
    dependency_destination="$JPACKAGE_INPUT_DIR/$(basename "$dependency_jar")"
    [[ ! -e "$dependency_destination" ]] || die "Dos JAR producirían el mismo nombre en jpackage: $dependency_destination"
    cp -p "$dependency_jar" "$dependency_destination"
done

log "Creando la aplicación autocontenida ARM64..."
"$JPACKAGE_BIN" \
    --type app-image \
    --name "$APP_NAME" \
    --app-version "$VERSION" \
    --vendor "ts3j" \
    --description "TeamSpeak shared voice session timer" \
    --copyright "Copyright (c) ts3j contributors" \
    --input "$JPACKAGE_INPUT_DIR" \
    --main-jar "$(basename "$MAIN_JAR")" \
    --main-class "$MAIN_CLASS" \
    --add-modules ALL-MODULE-PATH \
    --java-options "-Dfile.encoding=UTF-8" \
    --icon "$ICON_FILE" \
    --dest "$APP_IMAGE_DIR" \
    --mac-package-identifier "$BUNDLE_ID" \
    --mac-package-name "$APP_NAME"

readonly APP_BUNDLE="$APP_IMAGE_DIR/$APP_NAME.app"
readonly INFO_PLIST="$APP_BUNDLE/Contents/Info.plist"
readonly APP_CONFIG="$APP_BUNDLE/Contents/app/$APP_NAME.cfg"
[[ -d "$APP_BUNDLE" ]] || die "jpackage no creó el bundle esperado: $APP_BUNDLE"
[[ -f "$INFO_PLIST" ]] || die "El bundle no contiene Info.plist: $INFO_PLIST"
[[ -f "$APP_CONFIG" ]] || die "El bundle no contiene la configuración del lanzador: $APP_CONFIG"

expected_classpath_entries=$(( ${#runtime_jars[@]} + 1 ))
actual_classpath_entries="$(grep -c '^app[.]classpath=.*[.]jar$' "$APP_CONFIG" || true)"
[[ "$actual_classpath_entries" -eq "$expected_classpath_entries" ]] \
    || die "jpackage incluyó $actual_classpath_entries JAR en el classpath; se esperaban $expected_classpath_entries."
grep -Fqx "app.classpath=\$APPDIR/$(basename "$MAIN_JAR")" "$APP_CONFIG" \
    || die "El JAR principal no aparece en el classpath generado."
for dependency_jar in "${runtime_jars[@]}"; do
    grep -Fqx "app.classpath=\$APPDIR/$(basename "$dependency_jar")" "$APP_CONFIG" \
        || die "Falta una dependencia en el classpath generado: $(basename "$dependency_jar")"
done

if "$PLIST_BUDDY" -c 'Print :NSMicrophoneUsageDescription' "$INFO_PLIST" >/dev/null 2>&1; then
    "$PLIST_BUDDY" -c "Set :NSMicrophoneUsageDescription $MICROPHONE_DESCRIPTION" "$INFO_PLIST"
else
    "$PLIST_BUDDY" -c "Add :NSMicrophoneUsageDescription string $MICROPHONE_DESCRIPTION" "$INFO_PLIST"
fi
"$PLUTIL_BIN" -lint "$INFO_PLIST" >/dev/null

ACTUAL_BUNDLE_ID="$("$PLIST_BUDDY" -c 'Print :CFBundleIdentifier' "$INFO_PLIST")"
[[ "$ACTUAL_BUNDLE_ID" == "$BUNDLE_ID" ]] || die "Bundle ID inesperado: $ACTUAL_BUNDLE_ID"
ACTUAL_MIC_DESCRIPTION="$("$PLIST_BUDDY" -c 'Print :NSMicrophoneUsageDescription' "$INFO_PLIST")"
[[ "$ACTUAL_MIC_DESCRIPTION" == "$MICROPHONE_DESCRIPTION" ]] || die "No se guardó NSMicrophoneUsageDescription."

BUNDLE_EXECUTABLE="$("$PLIST_BUDDY" -c 'Print :CFBundleExecutable' "$INFO_PLIST")"
[[ -n "$BUNDLE_EXECUTABLE" && "$BUNDLE_EXECUTABLE" != */* ]] || die "CFBundleExecutable no es seguro: $BUNDLE_EXECUTABLE"
[[ -x "$APP_BUNDLE/Contents/MacOS/$BUNDLE_EXECUTABLE" ]] || die "Falta el lanzador de la aplicación."
"$FILE_BIN" "$APP_BUNDLE/Contents/MacOS/$BUNDLE_EXECUTABLE" | grep -q 'arm64' \
    || die "El lanzador generado no contiene arquitectura arm64."
readonly RUNTIME_JLI="$APP_BUNDLE/Contents/runtime/Contents/Home/lib/libjli.dylib"
readonly RUNTIME_JVM="$APP_BUNDLE/Contents/runtime/Contents/Home/lib/server/libjvm.dylib"
[[ -f "$RUNTIME_JLI" && -f "$RUNTIME_JVM" ]] || die "El bundle no contiene el runtime Java esperado."
"$FILE_BIN" "$RUNTIME_JLI" | grep -q 'arm64' || die "libjli del runtime no contiene arquitectura arm64."
"$FILE_BIN" "$RUNTIME_JVM" | grep -q 'arm64' || die "libjvm del runtime no contiene arquitectura arm64."

if [[ -n "$SIGNING_IDENTITY" ]]; then
    log "Firmando con Developer ID: $SIGNING_IDENTITY"
    codesign_args=(--force --deep --timestamp --sign "$SIGNING_IDENTITY")
    if [[ -n "$SIGNING_KEYCHAIN" ]]; then
        codesign_args+=(--keychain "$SIGNING_KEYCHAIN")
    fi
    "$CODESIGN_BIN" "${codesign_args[@]}" "$APP_BUNDLE"
else
    log "No se proporcionó Developer ID; aplicando firma ad hoc para pruebas locales."
    "$CODESIGN_BIN" --force --deep --sign - "$APP_BUNDLE"
fi
"$CODESIGN_BIN" --verify --deep --strict --verbose=2 "$APP_BUNDLE"

remove_generated_path "$DMG_STAGING_DIR"
remove_generated_path "$DMG_FILE"
mkdir -p "$DMG_STAGING_DIR" "$DIST_DIR"
"$DITTO_BIN" "$APP_BUNDLE" "$DMG_STAGING_DIR/$APP_NAME.app"
ln -s /Applications "$DMG_STAGING_DIR/Applications"

log "Creando imagen de disco..."
"$HDIUTIL_BIN" create \
    -volname "$APP_NAME $VERSION" \
    -srcfolder "$DMG_STAGING_DIR" \
    -format UDZO \
    -ov \
    "$DMG_FILE"

[[ -s "$DMG_FILE" ]] || die "No se creó el DMG esperado: $DMG_FILE"
"$HDIUTIL_BIN" verify "$DMG_FILE" >/dev/null

if [[ -n "$SIGNING_IDENTITY" ]]; then
    dmg_codesign_args=(--force --timestamp --sign "$SIGNING_IDENTITY")
    if [[ -n "$SIGNING_KEYCHAIN" ]]; then
        dmg_codesign_args+=(--keychain "$SIGNING_KEYCHAIN")
    fi
    "$CODESIGN_BIN" "${dmg_codesign_args[@]}" "$DMG_FILE"
    "$CODESIGN_BIN" --verify --verbose=2 "$DMG_FILE"
fi

log "Aplicación: $APP_BUNDLE"
log "Instalador: $DMG_FILE"
