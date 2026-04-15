package com.dark.tool_neuron.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Tabler Icons (https://tabler.io/icons) as Compose ImageVector constants.
 * Stroke-based, 24x24 viewport, stroke-width 2, round caps/joins.
 *
 * To add a new icon:
 *   1. Find it at https://tabler.io/icons
 *   2. Copy the SVG path d="" values
 *   3. Add: val IconName by lazy { tabler("M... path data ...") }
 */
object TnIcons {

    // ── Navigation ──
    val ArrowLeft by lazy { tabler("M5 12l14 0", "M5 12l6 6", "M5 12l6 -6") }
    val ArrowRight by lazy { tabler("M5 12l14 0", "M13 18l6 -6", "M13 6l6 6") }
    val ChevronRight by lazy { tabler("M9 6l6 6l-6 6") }
    val ChevronDown by lazy { tabler("M6 9l6 6l6 -6") }
    val ChevronUp by lazy { tabler("M6 15l6 -6l6 6") }
    val Menu by lazy { tabler("M4 8l16 0", "M4 16l16 0") }

    // ── Actions ──
    val Plus by lazy { tabler("M12 5l0 14", "M5 12l14 0") }
    val X by lazy { tabler("M18 6l-12 12", "M6 6l12 12") }
    val Check by lazy { tabler("M5 12l5 5l10 -10") }
    val Edit by lazy { tabler("M4 20h4l10.5 -10.5a2.828 2.828 0 1 0 -4 -4l-10.5 10.5v4", "M13.5 6.5l4 4") }
    val Trash by lazy { tabler("M4 7l16 0", "M10 11l0 6", "M14 11l0 6", "M5 7l1 12a2 2 0 0 0 2 2h8a2 2 0 0 0 2 -2l1 -12", "M9 7v-3a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v3") }
    val Search by lazy { tabler("M3 10a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M21 21l-6 -6") }
    val Share by lazy { tabler("M3 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0", "M15 6a3 3 0 1 0 6 0a3 3 0 1 0 -6 0", "M15 18a3 3 0 1 0 6 0a3 3 0 1 0 -6 0", "M8.7 10.7l6.6 -3.4", "M8.7 13.3l6.6 3.4") }
    val Copy by lazy { tabler("M7 7m0 2.667a2.667 2.667 0 0 1 2.667 -2.667h8.666a2.667 2.667 0 0 1 2.667 2.667v8.666a2.667 2.667 0 0 1 -2.667 2.667h-8.666a2.667 2.667 0 0 1 -2.667 -2.667z", "M4.012 16.737a2.005 2.005 0 0 1 -1.012 -1.737v-10c0 -1.1 .9 -2 2 -2h10c.75 0 1.158 .385 1.5 1") }
    val Download by lazy { tabler("M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2 -2v-2", "M7 11l5 5l5 -5", "M12 4l0 12") }
    val Package by lazy { tabler("M12 3l8 4.5l0 9l-8 4.5l-8 -4.5l0 -9l8 -4.5", "M12 12l8 -4.5", "M12 12l0 9", "M12 12l-8 -4.5") }
    val Upload by lazy { tabler("M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2 -2v-2", "M7 9l5 -5l5 5", "M12 4l0 12") }
    val Restore by lazy { tabler("M3.06 13a9 9 0 1 0 .49 -3.99", "M3 4.001v5h5") }

    // ── Status / Feedback ──
    val Heart by lazy { tabler("M19.5 12.572l-7.5 7.428l-7.5 -7.428a5 5 0 1 1 7.5 -6.566a5 5 0 1 1 7.5 6.572") }
    val Star by lazy { tabler("M12 17.75l-6.172 3.245l1.179 -6.873l-5 -4.867l6.9 -1l3.086 -6.253l3.086 6.253l6.9 1l-5 4.867l1.179 6.873z") }
    val AlertCircle by lazy { tabler("M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0", "M12 8l0 4", "M12 16l.01 0") }
    val InfoCircle by lazy { tabler("M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0", "M12 9l.01 0", "M11 12l1 0l0 4l1 0") }
    val CircleCheck by lazy { tabler("M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0", "M9 12l2 2l4 -4") }

    // ── UI Controls ──
    val Settings by lazy { tabler("M10.325 4.317c.426 -1.756 2.924 -1.756 3.35 0a1.724 1.724 0 0 0 2.573 1.066c1.543 -.94 3.31 .826 2.37 2.37a1.724 1.724 0 0 0 1.065 2.572c1.756 .426 1.756 2.924 0 3.35a1.724 1.724 0 0 0 -1.066 2.573c.94 1.543 -.826 3.31 -2.37 2.37a1.724 1.724 0 0 0 -2.572 1.065c-.426 1.756 -2.924 1.756 -3.35 0a1.724 1.724 0 0 0 -2.573 -1.066c-1.543 .94 -3.31 -.826 -2.37 -2.37a1.724 1.724 0 0 0 -1.065 -2.572c-1.756 -.426 -1.756 -2.924 0 -3.35a1.724 1.724 0 0 0 1.066 -2.573c-.94 -1.543 .826 -3.31 2.37 -2.37c1 .608 2.296 .07 2.572 -1.065", "M9 12a3 3 0 1 0 6 0a3 3 0 0 0 -6 0") }
    val Filter by lazy { tabler("M4 4h16v2.172a2 2 0 0 1 -.586 1.414l-4.414 4.414v7l-6 2v-8.5l-4.48 -4.928a2 2 0 0 1 -.52 -1.345v-2.227z") }
    val SortAscending by lazy { tabler("M4 6l7 0", "M4 12l7 0", "M4 18l9 0", "M15 9l3 -3l3 3", "M18 6l0 12") }
    val Refresh by lazy { tabler("M20 11a8.1 8.1 0 0 0 -15.5 -2m-.5 -4v4h4", "M4 13a8.1 8.1 0 0 0 15.5 2m.5 4v-4h-4") }

    // ── Media ──
    val PlayerStop by lazy { tabler("M5 7a2 2 0 0 1 2 -2h10a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2l0 -10") }
    val PlayerPlay by lazy { tabler("M7 4v16l13 -8z") }
    val PlayerPause by lazy { tabler("M6 5m0 1a1 1 0 0 1 1 -1h2a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1z", "M14 5m0 1a1 1 0 0 1 1 -1h2a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1z") }

    // ── Development ──
    val Code by lazy { tabler("M7 8l-4 4l4 4", "M17 8l4 4l-4 4") }

    // ── Objects ──
    val Home by lazy { tabler("M5 12l-2 0l9 -9l9 9l-2 0", "M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-7", "M9 21v-6a2 2 0 0 1 2 -2h2a2 2 0 0 1 2 2v6") }
    val User by lazy { tabler("M8 7a4 4 0 1 0 8 0a4 4 0 0 0 -8 0", "M6 21v-2a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4v2") }
    val Lock by lazy { tabler("M5 13a2 2 0 0 1 2 -2h10a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-6z", "M11 16a1 1 0 1 0 2 0a1 1 0 0 0 -2 0", "M8 11v-4a4 4 0 1 1 8 0v4") }
    val LockOpen by lazy { tabler("M5 13a2 2 0 0 1 2 -2h10a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-6z", "M11 16a1 1 0 1 0 2 0a1 1 0 0 0 -2 0", "M8 11v-5a4 4 0 0 1 8 0") }
    val Eye by lazy { tabler("M10 12a2 2 0 1 0 4 0a2 2 0 0 0 -4 0", "M21 12c-2.4 4 -5.4 6 -9 6c-3.6 0 -6.6 -2 -9 -6c2.4 -4 5.4 -6 9 -6c3.6 0 6.6 2 9 6") }
    val EyeOff by lazy { tabler("M10.585 10.587a2 2 0 0 0 2.829 2.828", "M16.681 16.673a8.717 8.717 0 0 1 -4.681 1.327c-3.6 0 -6.6 -2 -9 -6c1.272 -2.12 2.712 -3.678 4.32 -4.674m2.86 -1.146a9.055 9.055 0 0 1 1.82 -.18c3.6 0 6.6 2 9 6c-.666 1.11 -1.379 2.067 -2.138 2.87", "M3 3l18 18") }
    val Palette by lazy { tabler("M12 21a9 9 0 0 1 0 -18c4.97 0 9 3.582 9 8c0 1.06 -.474 2.078 -1.318 2.828c-.844 .75 -1.989 1.172 -3.182 1.172h-2.5a2 2 0 0 0 -1 3.75a1.3 1.3 0 0 1 -1 2.25", "M8.5 10.5m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M12.5 7.5m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M16.5 10.5m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0") }
    val Bolt by lazy { tabler("M13 3l0 7l6 0l-8 11l0 -7l-6 0l8 -11") }
    val Stretching2 by lazy { tabler("M11 4a1 1 0 1 0 2 0a1 1 0 0 0 -2 0", "M6.5 21l3.5 -5", "M5 11l7 -2", "M16 21l-4 -7v-5l7 -4") }
    val Adjustments by lazy { tabler("M4 10a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M6 4v4", "M6 12v8", "M10 16a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M12 4v10", "M12 18v2", "M16 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M18 4v1", "M18 9v11") }
    val World by lazy { tabler("M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0", "M3.6 9l16.8 0", "M3.6 15l16.8 0", "M11.5 3a17 17 0 0 0 0 18", "M12.5 3a17 17 0 0 1 0 18") }
    val Brain by lazy { tabler("M15.5 13a3.5 3.5 0 0 0 -3.5 3.5v1a3.5 3.5 0 0 0 7 0v-1.8", "M8.5 13a3.5 3.5 0 0 1 3.5 3.5v1a3.5 3.5 0 0 1 -7 0v-1.8", "M17.5 16a3.5 3.5 0 0 0 0 -7h-.5", "M19 9.3v-2.8a3.5 3.5 0 0 0 -7 0", "M6.5 16a3.5 3.5 0 0 1 0 -7h.5", "M5 9.3v-2.8a3.5 3.5 0 0 1 7 0v0", "M12 3v3") }
    val Books by lazy { tabler("M5 4m0 1a1 1 0 0 1 1 -1h2a1 1 0 0 1 1 1v14a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1z", "M9 4m0 1a1 1 0 0 1 1 -1h2a1 1 0 0 1 1 1v14a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1z", "M5 8h4", "M9 16h4", "M13.803 4.56l2.184 -.56c.55 -.14 1.11 .18 1.25 .71l3.54 13.77a1.01 1.01 0 0 1 -.73 1.23l-2.18 .56c-.55 .14 -1.11 -.18 -1.25 -.71l-3.54 -13.77a1.01 1.01 0 0 1 .73 -1.23z", "M14 9l4 -1", "M16 16l3.923 -1.006") }
    val Photo by lazy { tabler("M15 8h.01", "M3 6a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v12a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-12z", "M3 16l5 -5c.928 -.893 2.072 -.893 3 0l5 5", "M14 14l1 -1c.928 -.893 2.072 -.893 3 0l3 3") }
    val Sparkles by lazy { tabler("M16 18a2 2 0 0 1 2 2a2 2 0 0 1 2 -2a2 2 0 0 1 -2 -2a2 2 0 0 1 -2 2", "M3 12a6 6 0 0 1 6 6a6 6 0 0 1 6 -6a6 6 0 0 1 -6 -6a6 6 0 0 1 -6 6", "M16 6a2 2 0 0 1 2 2a2 2 0 0 1 2 -2a2 2 0 0 1 -2 -2a2 2 0 0 1 -2 2") }

    // ── Security ──
    val Shield by lazy { tabler("M12 3a12 12 0 0 0 8.5 3a12 12 0 0 1 -8.5 15a12 12 0 0 1 -8.5 -15a12 12 0 0 0 8.5 -3") }
    val ShieldLock by lazy { tabler("M12 3a12 12 0 0 0 8.5 3a12 12 0 0 1 -8.5 15a12 12 0 0 1 -8.5 -15a12 12 0 0 0 8.5 -3", "M10 13a2 2 0 1 0 4 0a2 2 0 0 0 -4 0", "M11.5 11v-1a2.5 2.5 0 0 1 5 0v1") }

    // ── Radio / Selection ──
    val CircleFilled by lazy { tabler("M7 3.34a10 10 0 1 1 -4.995 8.984l-.005 -.324l.005 -.324a10 10 0 0 1 4.995 -8.336z") }
    val RadioButton by lazy { tabler("M12 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0 -18 0") }

    // ── Navigation (additional) ──
    val ChevronLeft by lazy { tabler("M15 6l-6 6l6 6") }
    val ArrowUp by lazy { tabler("M12 5l0 14", "M18 11l-6 -6", "M6 11l6 -6") }
    val CornerDownLeft by lazy { tabler("M18 6v6a3 3 0 0 1 -3 3h-10l4 -4m0 8l-4 -4") }
    val ExternalLink by lazy { tabler("M12 6h-6a2 2 0 0 0 -2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-6", "M11 13l9 -9", "M15 4h5v5") }

    // ── Actions (additional) ──
    val TrashX by lazy { tabler("M4 7h16", "M5 7l1 12a2 2 0 0 0 2 2h8a2 2 0 0 0 2 -2l1 -12", "M9 7v-3a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v3", "M10 12l4 4m0 -4l-4 4") }
    val XCircle by lazy { tabler("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M10 10l4 4m0 -4l-4 4") }
    val DeviceFloppy by lazy { tabler("M6 4h10l4 4v10a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-12a2 2 0 0 1 2 -2", "M10 14a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M14 4l0 4l-6 0l0 -4") }
    val Send by lazy { tabler("M10 14l11 -11", "M21 3l-6.5 18a.55 .55 0 0 1 -1 0l-3.5 -7l-7 -3.5a.55 .55 0 0 1 0 -1l18 -6.5") }
    val Eraser by lazy { tabler("M19 20h-10.5l-4.21 -4.3a1 1 0 0 1 0 -1.41l10 -10a1 1 0 0 1 1.41 0l5 5a1 1 0 0 1 0 1.41l-9.2 9.3", "M18 13.3l-6.3 -6.3") }
    val Wand by lazy { tabler("M6 21l15 -15l-3 -3l-15 15l3 3", "M15 6l3 3", "M9 3a2 2 0 0 0 2 2a2 2 0 0 0 -2 2a2 2 0 0 0 -2 -2a2 2 0 0 0 2 -2", "M19 13a2 2 0 0 0 2 2a2 2 0 0 0 -2 2a2 2 0 0 0 -2 -2a2 2 0 0 0 2 -2") }

    // ── Files & Folders ──
    val FileSearch by lazy { tabler("M14 3v4a1 1 0 0 0 1 1h4", "M12 21h-5a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2h7l5 5v4.5", "M14 17.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0", "M18.5 19.5l2.5 2.5") }
    val FileText by lazy { tabler("M14 3v4a1 1 0 0 0 1 1h4", "M17 21h-10a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2h7l5 5v11a2 2 0 0 1 -2 2", "M9 9l1 0", "M9 13l6 0", "M9 17l6 0") }
    val File by lazy { tabler("M14 3v4a1 1 0 0 0 1 1h4", "M17 21h-10a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2h7l5 5v11a2 2 0 0 1 -2 2") }
    val Folder by lazy { tabler("M5 4h4l3 3h7a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2") }
    val FolderOpen by lazy { tabler("M5 19l2.757 -7.351a1 1 0 0 1 .936 -.649h12.307a1 1 0 0 1 .986 1.164l-.996 5.211a2 2 0 0 1 -1.964 1.625h-14.026a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2h4l3 3h7a2 2 0 0 1 2 2v2") }
    val FileUpload by lazy { tabler("M14 3v4a1 1 0 0 0 1 1h4", "M17 21h-10a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2h7l5 5v11a2 2 0 0 1 -2 2", "M12 11v6", "M9.5 13.5l2.5 -2.5l2.5 2.5") }

    // ── Layout ──
    val LayoutGrid by lazy { tabler("M4 5a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1l0 -4", "M14 5a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1l0 -4", "M4 15a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1l0 -4", "M14 15a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1l0 -4") }

    // ── Status (additional) ──
    val AlertTriangle by lazy { tabler("M12 9v4", "M10.363 3.591l-8.106 13.534a1.914 1.914 0 0 0 1.636 2.871h16.214a1.914 1.914 0 0 0 1.636 -2.87l-8.106 -13.536a1.914 1.914 0 0 0 -3.274 0", "M12 16h.01") }

    // ── Time ──
    val Clock by lazy { tabler("M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0", "M12 7v5l3 3") }
    val CalendarTime by lazy { tabler("M11.795 21h-6.795a2 2 0 0 1 -2 -2v-12a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v4", "M14 18a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M15 3v4", "M7 3v4", "M3 11h16", "M18 16.496v1.504l1 1") }

    // ── Connectivity ──
    val Link by lazy { tabler("M9 15l6 -6", "M11 6l.463 -.536a5 5 0 0 1 7.071 7.072l-.534 .464", "M13 18l-.397 .534a5.068 5.068 0 0 1 -7.127 0a4.972 4.972 0 0 1 0 -7.071l.524 -.463") }

    // ── Hardware / System ──
    val Cpu by lazy { tabler("M5 6a1 1 0 0 1 1 -1h12a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1h-12a1 1 0 0 1 -1 -1l0 -12", "M9 9h6v6h-6l0 -6", "M3 10h2", "M3 14h2", "M10 3v2", "M14 3v2", "M21 10h-2", "M21 14h-2", "M14 21v-2", "M10 21v-2") }
    val Database by lazy { tabler("M4 6a8 3 0 1 0 16 0a8 3 0 1 0 -16 0", "M4 6v6a8 3 0 0 0 16 0v-6", "M4 12v6a8 3 0 0 0 16 0v-6") }
    val Gauge by lazy { tabler("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M13.41 10.59l2.59 -2.59", "M7 12a5 5 0 0 1 5 -5") }
    val Tool by lazy { tabler("M7 10h3v-3l-3.5 -3.5a6 6 0 0 1 8 8l6 6a2 2 0 0 1 -3 3l-6 -6a6 6 0 0 1 -8 -8l3.5 3.5") }
    val Wrench by lazy { tabler("M7 10h3v-3l-3.5 -3.5a6 6 0 0 1 8 8l6 6a2 2 0 0 1 -3 3l-6 -6a6 6 0 0 1 -8 -8l3.5 3.5") }
    val Terminal by lazy { tabler("M8 9l3 3l-3 3", "M13 15l3 0", "M3 6a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2l0 -12") }

    // ── Search (additional) ──
    val SearchOff by lazy { tabler("M5.039 5.062a7 7 0 0 0 9.91 9.89m1.584 -2.434a7 7 0 0 0 -9.038 -9.057", "M3 3l18 18") }

    // ── Cloud ──
    val CloudUpload by lazy { tabler("M7 18a4.6 4.4 0 0 1 0 -9a5 4.5 0 0 1 11 2h1a3.5 3.5 0 0 1 0 7h-1", "M9 15l3 -3l3 3", "M12 12l0 9") }
    val CloudDownload by lazy { tabler("M19 18a3.5 3.5 0 0 0 0 -7h-1a5 4.5 0 0 0 -11 -2a4.6 4.4 0 0 0 -2.1 8.4", "M12 13l0 9", "M9 19l3 3l3 -3") }
    val WifiOff by lazy { tabler("M12 18l.01 0", "M9.172 15.172a4 4 0 0 1 5.656 0", "M6.343 12.343a8 8 0 0 1 7.966 -2.022m2.684 2.675a7.967 7.967 0 0 1 -.644 -.649", "M3.515 9.515c2-1.96 4.58-2.98 7.17-3.065m4.634 .627a12 12 0 0 1 5.168 2.438", "M3 3l18 18") }

    // ── Communication ──
    val MessageCircle by lazy { tabler("M3 20l1.3 -3.9c-2.324 -3.437 -1.426 -7.872 2.1 -10.374c3.526 -2.501 8.59 -2.296 11.845 .48c3.255 2.777 3.695 7.266 1.029 10.501c-2.666 3.235 -7.615 4.215 -11.574 2.293l-4.7 1") }
    val Message by lazy { tabler("M8 9h8", "M8 13h6", "M18 4a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3h-5l-5 3v-3h-2a3 3 0 0 1 -3 -3v-8a3 3 0 0 1 3 -3h12") }
    val Messages by lazy { tabler("M21 14l-3 -3h-7a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1h9a1 1 0 0 1 1 1v10", "M14 15v2a1 1 0 0 1 -1 1h-7l-3 3v-10a1 1 0 0 1 1 -1h2") }
    val Prompt by lazy { tabler("M8 9h8", "M8 13h6", "M9 18h-3a3 3 0 0 1 -3 -3v-8a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3h-3l-3 3l-3 -3") }

    // ── Audio ──
    val Volume by lazy { tabler("M15 8a5 5 0 0 1 0 8", "M17.7 5a9 9 0 0 1 0 14", "M6 15h-2a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1h2l3.5 -4.5a.8 .8 0 0 1 1.5 .5v14a.8 .8 0 0 1 -1.5 .5l-3.5 -4.5") }

    // ── Misc ──
    val ArrowsExchange by lazy { tabler("M7 10h14l-4 -4", "M17 14h-14l4 4") }
    val Tag by lazy { tabler("M6.5 7.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M3 6v5.172a2 2 0 0 0 .586 1.414l7.71 7.71a2.41 2.41 0 0 0 3.408 0l5.592 -5.592a2.41 2.41 0 0 0 0 -3.408l-7.71 -7.71a2 2 0 0 0 -1.414 -.586h-5.172a3 3 0 0 0 -3 3") }
    val UserPlus by lazy { tabler("M8 7a4 4 0 1 0 8 0a4 4 0 0 0 -8 0", "M16 19h6", "M19 16v6", "M6 21v-2a4 4 0 0 1 4 -4h4") }
    val Stack2 by lazy { tabler("M12 4l-8 4l8 4l8 -4l-8 -4", "M4 12l8 4l8 -4", "M4 16l8 4l8 -4") }
    val BulbFilled by lazy { tabler("M3 12h1m8 -9v1m8 8h1m-15.4 -6.4l.7 .7m12.1 -.7l-.7 .7", "M9 16a5 5 0 1 1 6 0a3.5 3.5 0 0 0 -1 3a2 2 0 0 1 -4 0a3.5 3.5 0 0 0 -1 -3", "M9.7 17l4.6 0") }
    val Coins by lazy { tabler("M9 14c0 1.657 2.686 3 6 3s6 -1.343 6 -3s-2.686 -3 -6 -3s-6 1.343 -6 3", "M9 14v4c0 1.656 2.686 3 6 3s6 -1.344 6 -3v-4", "M3 6c0 1.072 1.144 2.062 3 2.598s4.144 .536 6 0c1.856 -.536 3 -1.526 3 -2.598c0 -1.072 -1.144 -2.062 -3 -2.598s-4.144 -.536 -6 0c-1.856 .536 -3 1.526 -3 2.598", "M3 6v10c0 .888 .772 1.45 2 2", "M3 11c0 .888 .772 1.45 2 2") }

    // ── Lucide ──
    val BrainCircuit by lazy { lucide("M12 5a3 3 0 1 0-5.997 .125a4 4 0 0 0-2.526 5.77a4 4 0 0 0 .556 6.588A4 4 0 1 0 12 18Z", "M9 13a4.5 4.5 0 0 0 3-4", "M6.003 5.125A3 3 0 0 0 6.401 6.5", "M3.477 10.896a4 4 0 0 1 .585-.396", "M6 18a4 4 0 0 1-1.967-.516", "M12 13h4", "M12 18h6a2 2 0 0 1 2 2v1", "M12 8h8", "M16 8V5a2 2 0 0 1 2-2") }
}

/** Builds a Tabler-style stroke icon from SVG path strings. */
private fun tabler(vararg paths: String): ImageVector {
    return ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        paths.forEach { svgPath ->
            addPath(
                pathData = PathParser().parsePathString(svgPath).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()
}

/** Builds a Lucide-style stroke icon — same format as Tabler (24x24, stroke 2, round). */
private fun lucide(vararg paths: String): ImageVector = tabler(*paths)
