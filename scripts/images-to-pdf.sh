#!/usr/bin/env bash
set -euo pipefail

project_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
application_jar="$project_directory/target/ghost-pdf-0.0.1-SNAPSHOT.jar"

if [[ ! -f "$application_jar" ]]; then
    echo '先にプロジェクト直下で bash mvnw -B -ntp -DskipTests package を実行してください。' >&2
    exit 1
fi

exec java -Dloader.main=com.clip.ghost.pdfcontent.cli.ImageDirectoryPdfCommand \
    -cp "$application_jar" org.springframework.boot.loader.launch.PropertiesLauncher "$@"
