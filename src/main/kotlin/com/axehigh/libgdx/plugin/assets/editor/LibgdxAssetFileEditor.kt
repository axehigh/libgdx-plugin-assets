package com.axehigh.libgdx.plugin.assets.editor

import com.axehigh.libgdx.plugin.assets.atlas.LibgdxAsset
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/** Read-only editor that shows an [AtlasImagePanel] for a libGDX asset. */
class LibgdxAssetFileEditor(
    project: Project,
    private val file: VirtualFile,
    asset: LibgdxAsset,
) : UserDataHolderBase(), FileEditor, DumbAware {

    private val panel = AtlasImagePanel(project, asset)

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent = panel
    override fun getName(): String = "libGDX Asset"
    override fun getFile(): VirtualFile = file
    override fun isValid(): Boolean = true
    override fun isModified(): Boolean = false
    override fun getState(level: FileEditorStateLevel): FileEditorState = State
    override fun setState(state: FileEditorState) {}
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun dispose() = panel.dispose()

    private object State : FileEditorState {
        override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean = true
    }
}
