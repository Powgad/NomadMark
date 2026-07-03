#!/bin/bash
# NomadMark Build All Platforms Script
#
# Builds Core and all platform-specific targets

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"

echo "======================================"
echo "NomadMark Build All Platforms"
echo "======================================"
echo ""
echo "Project Root: $PROJECT_ROOT"
echo ""

# Build Core
echo "Step 1/3: Building Core..."
"$SCRIPT_DIR/build-core.sh"
if [ $? -ne 0 ]; then
    echo "❌ Core build failed"
    exit 1
fi

# Build Android (if Android build tools available)
echo ""
echo "Step 2/3: Building Android..."
if [ -f "$SCRIPT_DIR/build-android.sh" ]; then
    "$SCRIPT_DIR/build-android.sh"
    if [ $? -eq 0 ]; then
        echo "✅ Android build complete"
    else
        echo "⚠️  Android build skipped or failed"
    fi
else
    echo "⚠️  Android build script not found, skipping"
fi

# Build Desktop (if Desktop environment available)
echo ""
echo "Step 3/3: Building Desktop..."
if [ -d "$PROJECT_ROOT/platforms/desktop" ] && command -v npm &> /dev/null; then
    cd "$PROJECT_ROOT/platforms/desktop"
    npm run tauri:build
    if [ $? -eq 0 ]; then
        echo "✅ Desktop build complete"
    else
        echo "⚠️  Desktop build failed"
    fi
else
    echo "⚠️  Desktop build skipped (npm not found or directory missing)"
fi

echo ""
echo "======================================"
echo "Build All Complete!"
echo "======================================"
echo ""
