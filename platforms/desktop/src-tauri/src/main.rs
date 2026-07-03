// NomadMark Desktop - Tauri Backend
//
// This module bridges the frontend (Web UI) with the shared Rust Core.
// Since both are Rust, there's no FFI overhead - we call Core directly.

use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use std::sync::Mutex;
use tauri::State;

// Import from shared core (local path dependency)
use markdown_core::{
    parser::StreamingParser,
    render::RenderCommand,
    DocumentHandle,
    SearchResult,
    TocEntry,
};

// ============================================================================
// Application State
// ============================================================================

struct AppState {
    /// Currently open document
    current_document: Mutex<Option<DocumentHandle>>,
    /// Core instance
    core: markdown_core::Core,
}

// ============================================================================
// Command Definitions (Exposed to Frontend)
// ============================================================================

#[tauri::command]
async fn open_file(
    path: String,
    state: State<'_, AppState>,
) -> Result<DocumentInfo, String> {
    let core = &state.core;

    // Open document (uses mmap for large files)
    let handle = core.open_document(&path)
        .map_err(|e| e.to_string())?;

    // Get metadata
    let metadata = core.get_metadata(&handle)
        .map_err(|e| e.to_string())?;

    // Store in state
    *state.current_document.lock().unwrap() = Some(handle.clone());

    Ok(DocumentInfo {
        handle: handle.id(),
        path: PathBuf::from(path),
        total_chars: metadata.total_chars,
        total_lines: metadata.total_lines,
    })
}

#[tauri::command]
async fn render_document(
    handle: u64,
    viewport: Viewport,
    state: State<'_, AppState>,
) -> RenderResult {
    let core = &state.core;

    // Get document from handle
    let doc = core.get_document(handle)
        .unwrap_or_else(|| panic!("Invalid document handle: {}", handle));

    // Render visible range
    let commands = core.render_visible_range(doc, &viewport);

    RenderResult {
        commands,
        total_height: core.get_total_height(doc),
    }
}

#[tauri::command]
async fn search_document(
    handle: u64,
    query: String,
    state: State<'_, AppState>,
) -> Vec<SearchResult> {
    let core = &state.core;

    if let Some(doc) = core.get_document(handle) {
        core.search(doc, &query)
    } else {
        Vec::new()
    }
}

#[tauri::command]
async fn get_toc(
    handle: u64,
    state: State<'_, AppState>,
) -> Vec<TocEntry> {
    let core = &state.core;

    if let Some(doc) = core.get_document(handle) {
        core.get_toc(doc)
    } else {
        Vec::new()
    }
}

#[tauri::command]
async fn undo(
    handle: u64,
    state: State<'_, AppState>,
) -> bool {
    let core = &state.core;

    if let Some(doc) = core.get_document(handle) {
        core.undo(doc)
    } else {
        false
    }
}

#[tauri::command]
async fn redo(
    handle: u64,
    state: State<'_, AppState>,
) -> bool {
    let core = &state.core;

    if let Some(doc) = core.get_document(handle) {
        core.redo(doc)
    } else {
        false
    }
}

#[tauri::command]
async fn save_document(
    handle: u64,
    state: State<'_, AppState>,
) -> Result<(), String> {
    let core = &state.core;

    if let Some(doc) = core.get_document(handle) {
        core.save(doc).map_err(|e| e.to_string())
    } else {
        Err("Invalid document handle".to_string())
    }
}

// ============================================================================
// Data Structures (Shared with Frontend)
// ============================================================================

#[derive(Debug, Serialize, Deserialize)]
struct Viewport {
    x: f32,
    y: f32,
    width: f32,
    height: f32,
}

#[derive(Debug, Serialize, Deserialize)]
struct DocumentInfo {
    handle: u64,
    path: PathBuf,
    total_chars: usize,
    total_lines: usize,
}

#[derive(Debug, Serialize, Deserialize)]
struct RenderResult {
    commands: Vec<RenderCommand>,
    total_height: f32,
}

// ============================================================================
// Tauri Entry Point
// ============================================================================

fn main() {
    tauri::Builder::default()
        .manage(AppState {
            current_document: Mutex::new(None),
            core: markdown_core::Core::new(),
        })
        .invoke_handler(tauri::generate_handler![
            open_file,
            render_document,
            search_document,
            get_toc,
            undo,
            redo,
            save_document,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
