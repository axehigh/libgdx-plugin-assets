package com.axehigh.libgdx.plugin.assets.editor

import com.axehigh.libgdx.plugin.assets.atlas.LibgdxAsset
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Opens the interactive viewer for a file that is half of a libGDX asset pair.
 *
 * [FileEditorPolicy] is not per-file, so the `.png` and `.atlas` cases use separate registrations:
 * a paired `.png` hides the stock ImageEditor, while a `.atlas` keeps its text editor and the
 * viewer is placed after it (so the atlas gutter thumbnails stay visible).
 */
abstract class LibgdxAssetEditorProvider(private val extension: String) : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.extension.equals(extension, ignoreCase = true) && LibgdxAsset.forFile(file) != null

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val asset = LibgdxAsset.forFile(file)
            ?: error("Not a libGDX asset: ${file.path}")
        return LibgdxAssetFileEditor(project, file, asset)
    }

    override fun getEditorTypeId(): String = "libgdx-asset-$extension-editor"
}

/** Replaces the stock image editor for paired `.png` files. */
class PngLibgdxAssetEditorProvider : LibgdxAssetEditorProvider("png") {
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

/** Adds the viewer after the text editor for `.atlas` files. */
class AtlasLibgdxAssetEditorProvider : LibgdxAssetEditorProvider("atlas") {
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}
