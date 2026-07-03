#!/bin/bash
# Check build configuration info
echo "Checking if build was done with JNI feature..."

# Check if there's any way to determine what features were used
# by examining the target directory or build artifacts
TARGET_DIR="target/aarch64-linux-android/release"

if [ -d "$TARGET_DIR" ]; then
    echo "Target directory exists: $TARGET_DIR"
    
    # Check for any metadata files that might indicate features used
    find "$TARGET_DIR" -name "*.json" -o -name "*.rlib" | head -3
else
    echo "Target directory not found"
fi
