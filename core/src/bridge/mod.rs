// =============================================================================
// Bridge Module (FFI)
// =============================================================================

pub mod types;

// JNI module - always included when jni feature is enabled (Android builds)
#[cfg(feature = "jni")]
pub mod jni;

pub use types::*;
