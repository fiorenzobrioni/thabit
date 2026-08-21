package com.callbackdev.thabit.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.callbackdev.thabit.ui.components.CodeCanvas
import com.callbackdev.thabit.ui.components.EditorNavBar
import com.callbackdev.thabit.ui.components.EditorNavItem
import com.callbackdev.thabit.ui.components.EditorNavItems
import com.callbackdev.thabit.ui.components.EditorOptions
import com.callbackdev.thabit.ui.components.LocalEditorOptions
import com.callbackdev.thabit.ui.components.commentLine
import com.callbackdev.thabit.ui.editor.HabitsTestScreen
import com.callbackdev.thabit.ui.settings.SettingsScreen
import com.callbackdev.thabit.ui.wizard.WizardScreen
import com.callbackdev.thabit.ui.theme.SyntaxColors
import com.callbackdev.thabit.ui.theme.ThabitTheme

/**
 * The shell: four files behind the editor's bottom bar.
 *
 * Navigation Compose with one destination per tab and the series' tab behaviour —
 * `saveState`/`restoreState` around the start destination, so switching to Stats
 * and back finds `habits.test` scrolled where it was left, and the system back
 * button walks to the editor tab before leaving the app.
 *
 * The editor tab is a **nested graph** now that it has a second destination: the
 * suite file and the `$ thabit add` transcript are two screens of one tab, so
 * the bottom bar stays put, the Editor tab stays lit while the wizard is open,
 * and back returns to the file rather than leaving the app. The other three tabs
 * stay single destinations until they have a reason not to (VISION §3.3.1) —
 * which is what this graph looked like one phase ago.
 *
 * [editorOptions] comes from `settings.config` and is provided once for every
 * file: line numbers and word wrap are properties of the editor, not of a screen.
 */
@Composable
fun ThabitApp(
    editorOptions: EditorOptions = EditorOptions(),
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.statusBarsPadding()) {
            CompositionLocalProvider(LocalEditorOptions provides editorOptions) {
                Box(Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = EditorNavItems.Editor.route
                    ) {
                        navigation(
                            route = EditorNavItems.Editor.route,
                            startDestination = EditorRoutes.SUITE
                        ) {
                            composable(EditorRoutes.SUITE) {
                                HabitsTestScreen(
                                    onAddTest = { navController.navigate(EditorRoutes.WIZARD) },
                                    onEditTest = { habitId ->
                                        navController.navigate(EditorRoutes.wizardFor(habitId))
                                    }
                                )
                            }
                            composable(EditorRoutes.WIZARD) {
                                WizardScreen(onClose = { navController.popBackStack() })
                            }
                            composable(
                                route = EditorRoutes.WIZARD_EDIT,
                                arguments = listOf(
                                    navArgument(EditorRoutes.ARG_HABIT_ID) {
                                        type = NavType.LongType
                                    }
                                )
                            ) { entry ->
                                WizardScreen(
                                    onClose = { navController.popBackStack() },
                                    editingId = entry.arguments
                                        ?.getLong(EditorRoutes.ARG_HABIT_ID)
                                )
                            }
                        }
                        composable(EditorNavItems.Log.route) {
                            NotYetWritten("habits_history.diff")
                        }
                        composable(EditorNavItems.Stats.route) { NotYetWritten("stats.md") }
                        composable(EditorNavItems.Settings.route) { SettingsScreen() }
                    }
                }
            }
            EditorNavBar(
                items = EditorNavItems.All,
                // By hierarchy, not by route: the wizard is a destination *of*
                // the editor tab, and that tab must stay lit while it is open.
                isSelected = { item ->
                    destination?.hierarchy?.any { it.route == item.route } == true
                },
                onSelect = { item -> navController.openTab(item, destination) }
            )
        }
    }
}

/**
 * Switches tab without stacking one on top of the other.
 *
 * Re-tapping the tab you are on does nothing at all — not even a recomposition of
 * the graph — because the alternative is a file that scrolls back to the top
 * every time a thumb brushes the bar it is already on.
 */
private fun NavHostController.openTab(
    item: EditorNavItem,
    destination: androidx.navigation.NavDestination?
) {
    if (destination?.hierarchy?.any { it.route == item.route } == true) return
    navigate(item.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** The destinations inside the editor tab. */
object EditorRoutes {
    const val SUITE = "editor/suite"
    const val WIZARD = "editor/wizard"
    const val ARG_HABIT_ID = "habitId"
    const val WIZARD_EDIT = "editor/wizard/{$ARG_HABIT_ID}"

    fun wizardFor(habitId: Long): String = "editor/wizard/$habitId"
}

/** The honest empty tab: the file exists in the plan, not yet in the app. */
@Composable
private fun NotYetWritten(fileName: String) {
    val syntax = ThabitTheme.syntax
    val lines = remember(fileName, syntax) { notYetWritten(fileName, syntax) }
    CodeCanvas(lines = lines, modifier = Modifier.fillMaxSize())
}

internal fun notYetWritten(fileName: String, syntax: SyntaxColors) = listOf(
    commentLine(
        // Placeholders are terminal output, so the comment marker follows the
        // future host file's syntax (VISION §1.1): # for yaml/md/diff-header
        // territory, // for the JSON-style settings.config.
        if (fileName.endsWith(".config")) "// $fileName — not yet written"
        else "# $fileName — not yet written",
        syntax
    )
)

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 480)
@Composable
private fun ThabitAppPreview() {
    ThabitTheme {
        ThabitApp()
    }
}
