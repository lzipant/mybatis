#!/bin/bash
TARGET_DATE="2025-08-25"
echo "恢复 $TARGET_DATE 修改的文件..."

git status --porcelain | awk '{print $2}' | while read file; do
    if [ -f "$file" ]; then
        file_mtime=$(date -r "$file" +%Y-%m-%d)
        if [ "$file_mtime" = "$TARGET_DATE" ]; then
            echo "恢复: $file (修改时间: $file_mtime)"
            git checkout HEAD -- "$file"
        fi
    fi
done
