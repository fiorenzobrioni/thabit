package com.callbackdev.thabit.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.thabit.data.EditorFile
import com.callbackdev.thabit.ui.components.EditorTabs

/**
 * The editor tab: two files behind one bar.
 *
 * `habits.test` is the suite and `README.md` is the same day in prose — the
 * app's plain-language layer (VISION §4.1). They are two files of one tab rather
 * than two tabs of the bottom bar, because they are the same subject read two
 * ways, and because the bottom bar is for *files that are about different
 * things*.
 *
 * The two scroll positions are held here rather than inside each screen: an
 * editor that scrolled a file back to the top every time you glanced at the
 * other one would be an editor nobody would use twice.
 */
@Composable
fun EditorScreen(
    onAddTest: () -> Unit,
    onEditTest: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = viewModel(factory = EditorViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }

    val testScroll = rememberLazyListState()
    val readmeScroll = rememberLazyListState()

    Column(modifier.fillMaxSize()) {
        EditorTabs(
            fileNames = listOf(SuiteDocument.FILE_NAME, ReadmeDocument.FILE_NAME),
            activeIndex = if (state.file == EditorFile.TEST) 0 else 1,
            onSelect = { index ->
                viewModel.onSelectFile(if (index == 0) EditorFile.TEST else EditorFile.README)
            }
        )
        when (state.file) {
            EditorFile.TEST -> HabitsTestScreen(
                onAddTest = onAddTest,
                onEditTest = onEditTest,
                listState = testScroll
            )
            EditorFile.README -> ReadmeScreen(state = state, listState = readmeScroll)
        }
    }
}
