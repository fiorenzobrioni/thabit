package com.callbackdev.thabit.ui.editor

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.thabit.data.EditorFile
import com.callbackdev.thabit.ui.components.EditorTabs
import com.callbackdev.thabit.ui.components.GlowFab

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
 * other one would be an editor nobody would use twice. The FAB is held here for
 * the same kind of reason — it belongs to the *tab*, not to one of its files.
 */
@Composable
fun EditorScreen(
    onAddTest: () -> Unit,
    onEditTest: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = viewModel(factory = EditorViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val file by viewModel.activeFile.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }

    val testScroll = rememberLazyListState()
    val readmeScroll = rememberLazyListState()

    Column(modifier.fillMaxSize()) {
        EditorTabs(
            fileNames = listOf(SuiteDocument.FILE_NAME, ReadmeDocument.FILE_NAME),
            activeIndex = if (file == EditorFile.TEST) 0 else 1,
            onSelect = { index ->
                viewModel.onSelectFile(if (index == 0) EditorFile.TEST else EditorFile.README)
            }
        )
        when (file) {
            EditorFile.TEST -> HabitsTestScreen(
                onAddTest = onAddTest,
                onEditTest = onEditTest,
                listState = testScroll
            )
            EditorFile.README -> ReadmeScreen(
                state = state,
                onAddTest = onAddTest,
                listState = readmeScroll
            )
        }
    }
}

/**
 * Room at the foot of *every* file in this tab, so the FAB never sits on top of
 * the last line: the last test of the suite and the last sentence of the README
 * are both things somebody came here to read.
 *
 * One value for both files because there is one FAB for both files — a clearance
 * that only one of them knew about was how the README ended up with its footer
 * under the glow (the siblings hold the same constant, for the same reason).
 */
internal val EditorFileClearance = PaddingValues(top = 8.dp, bottom = 88.dp)

/**
 * The one glowing verb of this app: growing the suite (VISION §4.1).
 *
 * Defined once and drawn by both files. It is the tab's action rather than the
 * suite's — reading the README and deciding *there* that something is missing
 * from the suite is exactly when a reader wants it, and a verb that disappears
 * when you switch file reads as a verb that stopped being available. It hands
 * the reader to `$ thabit add` either way; the file they came from is still the
 * file they come back to, because which file is open is theirs to decide, not
 * the wizard's.
 */
@Composable
internal fun BoxScope.AddTestFab(onClick: () -> Unit) {
    GlowFab(
        onClick = onClick,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp)
    )
}
