// =============================================================================
// 解析器错误类型
// =============================================================================

use thiserror::Error;

#[derive(Debug, Error)]
pub enum ParseError {
    #[error("IO error: {0}")]
    Io(String),

    #[error("File not mapped")]
    NotMapped,

    #[error("Invalid UTF-8 at position {position}")]
    InvalidUtf8 { position: usize },

    #[error("Syntax error at line {line}: {message}")]
    Syntax { line: usize, message: String },

    #[error("Memory limit exceeded: {current} > {limit}")]
    MemoryLimitExceeded { current: usize, limit: usize },
}
