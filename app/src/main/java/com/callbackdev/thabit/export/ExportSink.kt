package com.callbackdev.thabit.export

/**
 * Where a rendered file lands.
 *
 * The seam exists so everything above it runs on the JVM with a fake, and only
 * [DownloadsExportSink] ever touches Android — the same split the rest of the
 * app uses to keep its documents assertable character by character.
 */
interface ExportSink {

    /**
     * Writes [file] and returns the name it actually got.
     *
     * The store may rename on collision (`thabit-export-2026-08-21 (1).json`),
     * and the terminal line reports **what was written**, not what was asked
     * for: telling the user a filename that is not there is exactly the kind of
     * lie VISION §1.1 bans.
     *
     * Throws on failure; the caller turns it into an `// ERROR:` line.
     */
    suspend fun write(file: ExportFile): String
}
