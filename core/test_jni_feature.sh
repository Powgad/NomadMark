#!/bin/bash
# Test if JNI feature is being used during build
echo "Checking if JNI symbols are actually from jni feature or always compiled..."

# Check the source code to see if JNI functions are conditionally compiled
echo "Checking jni.rs conditional compilation..."
cd "C:\Users\Administrator\Desktop\Markdown文档编辑器的详细设计文档2.0\core"
grep -n "#\[cfg(feature = \"jni\")]\]" src/bridge/jni.rs | head -5
