#!/bin/bash
# NomadMark Core Build Script
#
# Builds the shared Rust core library for all platforms

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
CORE_DIR="$PROJECT_ROOT/core"

echo "======================================"
echo "NomadMark Core Build Script"
echo "======================================"
echo ""
echo "Project Root: $PROJECT_ROOT"
echo "Core Directory: $CORE_DIR"
echo ""

# Check prerequisites
if ! command -v cargo &> /dev/null; then
    echo "❌ Rust/Cargo not found. Install from https://rustup.rs/"
    exit 1
fi

echo "✅ Prerequisites check passed"
echo ""

# Build Core
echo "======================================"
echo "Building NomadMark Core..."
echo "======================================"

cd "$CORE_DIR"
cargo build --release

echo ""
echo "✅ Core build complete!"
echo ""
echo "Output location:"
echo "  - $CORE_DIR/target/release/libmarkdown_core.a (static lib)"
echo "  - $CORE_DIR/target/release/libmarkdown_core.so (Linux shared)"
echo ""
