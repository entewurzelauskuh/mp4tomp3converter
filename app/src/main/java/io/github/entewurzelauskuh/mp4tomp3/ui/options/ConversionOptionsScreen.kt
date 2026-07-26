package io.github.entewurzelauskuh.mp4tomp3.ui.options

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.entewurzelauskuh.mp4tomp3.R
import io.github.entewurzelauskuh.mp4tomp3.jobs.ConversionOptions
import io.github.entewurzelauskuh.mp4tomp3.jobs.Id3Tags
import io.github.entewurzelauskuh.mp4tomp3.jobs.SourceInfo

/**
 * The pre-conversion options screen: shown after files are picked and before the batch
 * is enqueued. Hosts the per-conversion controls, each with a sensible default, then Start.
 *
 * **Batch vs per-file.** Bitrate and delete-original apply to the whole batch. Trim,
 * ID3 tags and the size estimate are inherently per-file, so they appear only when
 * exactly one file is selected — i.e. when [sourceInfo] is non-null (resolved asynchronously).
 *
 * @param fileCount how many files were picked.
 * @param sourceInfo the single-file probe (duration + suggested tags), or null for a multi-file pick.
 * @param onStart invoked with the chosen options when the user confirms.
 * @param onCancel invoked when the user backs out — enqueues nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversionOptionsScreen(
    fileCount: Int,
    sourceInfo: SourceInfo?,
    onStart: (ConversionOptions) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var bitrateKbps by rememberSaveable { mutableIntStateOf(ConversionOptions.DEFAULT_BITRATE_KBPS) }
    var deleteSource by rememberSaveable { mutableStateOf(false) }

    // Per-file state (used only when sourceInfo != null); seeded once the probe arrives.
    var trimStartMs by rememberSaveable { mutableLongStateOf(0L) }
    var trimEndMs by rememberSaveable { mutableLongStateOf(0L) }
    var editTags by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var artist by rememberSaveable { mutableStateOf("") }
    var album by rememberSaveable { mutableStateOf("") }
    var albumArtist by rememberSaveable { mutableStateOf("") }
    var track by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var composer by rememberSaveable { mutableStateOf("") }
    var disc by rememberSaveable { mutableStateOf("") }
    var comment by rememberSaveable { mutableStateOf("") }
    var seeded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(sourceInfo) {
        if (sourceInfo != null && !seeded) {
            val t = sourceInfo.suggestedTags
            trimStartMs = 0L
            trimEndMs = sourceInfo.durationMs
            title = t.title.orEmpty()
            artist = t.artist.orEmpty()
            album = t.album.orEmpty()
            albumArtist = t.albumArtist.orEmpty()
            track = t.track.orEmpty()
            genre = t.genre.orEmpty()
            year = t.year.orEmpty()
            composer = t.composer.orEmpty()
            disc = t.disc.orEmpty()
            comment = t.comment.orEmpty()
            seeded = true
        }
    }
    val durationMs = sourceInfo?.durationMs ?: 0L
    // Tags to write when the editor is left closed: the file's own detected tags (the user's choice).
    val detectedTags = sourceInfo?.suggestedTags ?: Id3Tags.None
    // Tags need only a single file; trim and size also need a known duration.
    val perFile = sourceInfo != null && seeded
    val hasDuration = perFile && durationMs > 0L

    BackHandler(onBack = onCancel)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_conversion_options)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = pluralStringResource(R.plurals.options_file_count, fileCount, fileCount),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            BitrateOption(selectedKbps = bitrateKbps, onSelect = { bitrateKbps = it })

            if (perFile) {
                if (hasDuration) {
                    TrimOption(
                        durationMs = durationMs,
                        startMs = trimStartMs,
                        endMs = trimEndMs,
                        onChange = { s, e ->
                            trimStartMs = s
                            trimEndMs = e
                        },
                    )
                }
                TagsOption(
                    editing = editTags,
                    onEditing = { editTags = it },
                    title = title, onTitle = { title = it },
                    artist = artist, onArtist = { artist = it },
                    album = album, onAlbum = { album = it },
                    albumArtist = albumArtist, onAlbumArtist = { albumArtist = it },
                    track = track, onTrack = { track = it },
                    genre = genre, onGenre = { genre = it },
                    year = year, onYear = { year = it },
                    composer = composer, onComposer = { composer = it },
                    disc = disc, onDisc = { disc = it },
                    comment = comment, onComment = { comment = it },
                )
                if (hasDuration) {
                    // Same resolver as buildOptions, so the estimate always matches what gets encoded.
                    SizeEstimate(bitrateKbps = bitrateKbps, spanMs = resolveTrim(trimStartMs, trimEndMs, durationMs).spanMs)
                }
            }

            DeleteOption(checked = deleteSource, onCheckedChange = { deleteSource = it })

            Button(
                onClick = {
                    // Editor open → the edited fields; editor closed → the file's detected tags.
                    val chosenTags = when {
                        !perFile -> Id3Tags.None

                        editTags -> Id3Tags(
                            title = title.cleaned(),
                            artist = artist.cleaned(),
                            album = album.cleaned(),
                            albumArtist = albumArtist.cleaned(),
                            track = track.cleaned(),
                            genre = genre.cleaned(),
                            year = year.cleaned(),
                            composer = composer.cleaned(),
                            disc = disc.cleaned(),
                            comment = comment.cleaned(),
                        )

                        else -> detectedTags
                    }
                    onStart(
                        buildOptions(
                            bitrateKbps = bitrateKbps,
                            deleteSource = deleteSource,
                            perFile = perFile,
                            trimStartMs = trimStartMs,
                            trimEndMs = trimEndMs,
                            durationMs = durationMs,
                            tags = chosenTags,
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.action_start_conversion))
            }
        }
    }
}

private fun buildOptions(
    bitrateKbps: Int,
    deleteSource: Boolean,
    perFile: Boolean,
    trimStartMs: Long,
    trimEndMs: Long,
    durationMs: Long,
    tags: Id3Tags,
): ConversionOptions {
    if (!perFile) {
        return ConversionOptions(bitrateKbps = bitrateKbps, deleteSourceAfter = deleteSource)
    }
    val trim = resolveTrim(trimStartMs, trimEndMs, durationMs)
    return ConversionOptions(
        bitrateKbps = bitrateKbps,
        trimStartMs = trim.startMs,
        trimEndMs = trim.optionEndMs,
        tags = tags,
        deleteSourceAfter = deleteSource,
    )
}

/** Trimmed text, or null when blank — the shape [Id3Tags] wants for "unset". */
private fun String.cleaned(): String? = trim().ifBlank { null }

/** Bitrate control: a single-choice segmented row over [ConversionOptions.BITRATE_CHOICES]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BitrateOption(selectedKbps: Int, onSelect: (Int) -> Unit) {
    OptionSection(title = stringResource(R.string.option_bitrate_title)) {
        val choices = ConversionOptions.BITRATE_CHOICES
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            choices.forEachIndexed { index, kbps ->
                SegmentedButton(
                    selected = kbps == selectedKbps,
                    onClick = { onSelect(kbps) },
                    shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                ) {
                    Text(kbps.toString())
                }
            }
        }
        Text(
            text = stringResource(R.string.option_bitrate_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Trim control: a range slider over `[0, durationMs]` plus two editable `mm:ss` fields.
 * Slider and fields are two views of the same start/end: dragging updates the fields, typing moves
 * the slider. Typed values are clamped to the clip length (over-max snaps to the max) and kept a
 * strict ≥1s window apart, so the pair can never cross or leave the file.
 */
@Composable
private fun TrimOption(durationMs: Long, startMs: Long, endMs: Long, onChange: (Long, Long) -> Unit) {
    // Enough digits for this clip's minutes + two for seconds, so the clock buffer doesn't overflow.
    val maxDigits = (durationMs / 60_000L).toString().length + 2
    OptionSection(title = stringResource(R.string.option_trim_title)) {
        RangeSlider(
            value = startMs.toFloat()..endMs.toFloat(),
            onValueChange = { r -> onChange(r.start.toLong(), r.endInclusive.toLong()) },
            valueRange = 0f..durationMs.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TimeField(
                label = stringResource(R.string.option_trim_start),
                valueMs = startMs,
                maxDigits = maxDigits,
                // Live: keep start on the slider's side of the end. Commit: enforce the 1s window.
                onLive = { ms -> onChange(ms.coerceIn(0L, endMs), endMs) },
                onCommit = { ms -> onChange(ms.coerceIn(0L, (endMs - 1000L).coerceAtLeast(0L)), endMs) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            TimeField(
                label = stringResource(R.string.option_trim_end),
                valueMs = endMs,
                maxDigits = maxDigits,
                onLive = { ms -> onChange(startMs, ms.coerceIn(startMs, durationMs)) },
                onCommit = { ms -> onChange(startMs, ms.coerceIn((startMs + 1000L).coerceAtMost(durationMs), durationMs)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * A single `mm:ss` time entry bound to [valueMs]. Digits fill in clock-style from the right (typing
 * `3 2 4 2` → `32:42`), so the value is always well-formed. [onLive] fires on each keystroke (to
 * track the slider); [onCommit] fires when editing ends (focus loss / Done) to apply range clamping.
 * While the field is focused its text is left alone; otherwise it mirrors [valueMs] (slider drags).
 */
@Composable
private fun TimeField(
    label: String,
    valueMs: Long,
    maxDigits: Int,
    onLive: (Long) -> Unit,
    onCommit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(formatClock(valueMs)) }
    // Mirror external changes (slider, clamps) into the field only while it isn't being edited.
    LaunchedEffect(valueMs, focused) {
        if (!focused) text = formatClock(valueMs)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val formatted = clockFromDigits(raw, maxDigits)
            text = formatted
            onLive(parseClock(formatted))
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = modifier.onFocusChanged { state ->
            val lostFocus = focused && !state.isFocused
            focused = state.isFocused
            if (lostFocus) onCommit(parseClock(text))
        },
    )
}

/**
 * ID3 tags. Collapsed by default behind an "Edit tags" switch to keep the screen short:
 * left closed, the file's own detected tags are written; opened, the full extended field set appears
 * (pre-filled from the source) for editing. Cover art is out of scope.
 */
@Composable
@Suppress("LongParameterList") // One (value, onValueChange) pair per editable ID3 field.
private fun TagsOption(
    editing: Boolean,
    onEditing: (Boolean) -> Unit,
    title: String,
    onTitle: (String) -> Unit,
    artist: String,
    onArtist: (String) -> Unit,
    album: String,
    onAlbum: (String) -> Unit,
    albumArtist: String,
    onAlbumArtist: (String) -> Unit,
    track: String,
    onTrack: (String) -> Unit,
    genre: String,
    onGenre: (String) -> Unit,
    year: String,
    onYear: (String) -> Unit,
    composer: String,
    onComposer: (String) -> Unit,
    disc: String,
    onDisc: (String) -> Unit,
    comment: String,
    onComment: (String) -> Unit,
) {
    OptionSection(title = stringResource(R.string.option_tags_title)) {
        Row(
            // Toggle from the whole row (label included); the Switch is display-only (null callback).
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = editing, role = Role.Switch, onValueChange = onEditing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.option_tags_edit_toggle), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = editing, onCheckedChange = null)
        }
        if (!editing) {
            Text(
                text = stringResource(R.string.option_tags_keep_detected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            return@OptionSection
        }
        TagField(title, onTitle, R.string.option_tags_field_title)
        TagField(artist, onArtist, R.string.option_tags_field_artist)
        TagField(album, onAlbum, R.string.option_tags_field_album)
        TagField(albumArtist, onAlbumArtist, R.string.option_tags_field_album_artist)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TagField(track, onTrack, R.string.option_tags_field_track, Modifier.weight(1f), numeric = true, topPad = false)
            TagField(disc, onDisc, R.string.option_tags_field_disc, Modifier.weight(1f), numeric = true, topPad = false)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TagField(year, onYear, R.string.option_tags_field_year, Modifier.weight(1f), numeric = true, topPad = false)
            TagField(genre, onGenre, R.string.option_tags_field_genre, Modifier.weight(1f), topPad = false)
        }
        TagField(composer, onComposer, R.string.option_tags_field_composer)
        TagField(comment, onComment, R.string.option_tags_field_comment)
    }
}

/** One ID3 text field. [topPad] adds the standard inter-field gap (off when the caller spaces a Row). */
@Composable
private fun TagField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    topPad: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = modifier.fillMaxWidth().padding(top = if (topPad) 8.dp else 0.dp),
    )
}

/** Estimated output size: reliable for CBR = bitrate × duration. No time estimate. */
@Composable
private fun SizeEstimate(bitrateKbps: Int, spanMs: Long) {
    val context = LocalContext.current
    val bytes = bitrateKbps.toLong() * spanMs / 8 // (bitrate·1000/8 bytes/s) × (spanMs/1000 s)
    Text(
        text = stringResource(R.string.option_size_estimate, Formatter.formatShortFileSize(context, bytes)),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** Delete-original toggle: honest "try to" wording — it often can't (read-only grant). */
@Composable
private fun DeleteOption(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.option_delete_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.option_delete_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A titled container for one option control — the reusable building block of this screen. */
@Composable
private fun OptionSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.padding(top = 8.dp), content = content)
    }
}

/** Milliseconds → `m:ss` (minutes are not capped at 59, matching the slider labels). */
private fun formatClock(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/**
 * Re-render whatever the user typed as a canonical `m:ss` string, clock-style: keep only digits,
 * drop leading zeros, take the rightmost [maxDigits], then read the last two as seconds (capped at
 * 59) and the rest as minutes. This makes malformed input impossible — every keystroke maps to a
 * valid time — and gives the "fill from the right" feel (`3`→`0:03`, `32`→`0:32`, `324`→`3:24`).
 */
private fun clockFromDigits(raw: String, maxDigits: Int): String {
    val digits = raw.filter { it.isDigit() }.dropWhile { it == '0' }.takeLast(maxDigits.coerceAtLeast(3))
    if (digits.isEmpty()) return "0:00"
    val seconds = (if (digits.length <= 2) digits else digits.takeLast(2)).toInt().coerceAtMost(59)
    val minutes = if (digits.length <= 2) 0 else digits.dropLast(2).toInt()
    return "%d:%02d".format(minutes, seconds)
}

/** Parse a canonical `m:ss` string back to milliseconds. */
private fun parseClock(text: String): Long {
    val parts = text.split(':')
    val minutes = parts.getOrNull(0)?.toLongOrNull() ?: 0L
    val seconds = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    return (minutes * 60L + seconds) * 1000L
}
