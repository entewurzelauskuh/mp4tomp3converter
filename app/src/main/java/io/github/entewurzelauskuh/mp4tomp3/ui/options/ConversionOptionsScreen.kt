package io.github.entewurzelauskuh.mp4tomp3.ui.options

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.entewurzelauskuh.mp4tomp3.R
import io.github.entewurzelauskuh.mp4tomp3.jobs.ConversionOptions

/**
 * The pre-conversion options screen (issue #5): shown after files are picked and before the
 * batch is enqueued, so the user can review/adjust per-conversion encoding options, then Start
 * (or back out and enqueue nothing).
 *
 * **Scaffold for contributors.** Each option is one [OptionSection] wrapping a control that edits
 * a field of [ConversionOptions]. The **bitrate** control below (issue #6) is the worked example.
 * To add a new option (trim → issue #7, ID3 tags → issue #8):
 *  1. add a defaulted field to [ConversionOptions];
 *  2. add a `rememberSaveable` state here, seeded from [initialOptions];
 *  3. render its control in a new [OptionSection] (copy [BitrateOption]);
 *  4. include the field in the [ConversionOptions] built for [onStart].
 *
 * State is transient (per batch) and not persisted; it survives rotation via `rememberSaveable`.
 *
 * @param fileCount how many files were picked (shown in the header).
 * @param onStart invoked with the chosen options when the user confirms.
 * @param onCancel invoked when the user backs out (toolbar back or system back) — enqueues nothing.
 * @param initialOptions the options the controls start from (defaults to [ConversionOptions.Default]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversionOptionsScreen(
    fileCount: Int,
    onStart: (ConversionOptions) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    initialOptions: ConversionOptions = ConversionOptions.Default,
) {
    // One saveable state per option field, seeded from initialOptions (see the KDoc checklist).
    var bitrateKbps by rememberSaveable { mutableIntStateOf(initialOptions.bitrateKbps) }
    // Add future option state here (issues #7 trim, #8 ID3 tags).

    // System back = cancel, so we never strand a half-made selection behind the job list.
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

            // ---- Bitrate (issue #6) — the worked example for this scaffold ----
            BitrateOption(selectedKbps = bitrateKbps, onSelect = { bitrateKbps = it })

            // ---- Add new option sections here (issue #7 trim, issue #8 ID3 tags) ----

            Button(
                onClick = { onStart(ConversionOptions(bitrateKbps = bitrateKbps)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.action_start_conversion))
            }
        }
    }
}

/**
 * The bitrate control (issue #6) — the reference implementation of an option on this screen:
 * a single-choice segmented row over [ConversionOptions.BITRATE_CHOICES].
 */
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
 * A titled container for one option control — the reusable building block of this screen.
 * Wrap each new option (issues #7, #8) in one of these.
 */
@Composable
private fun OptionSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.padding(top = 8.dp), content = content)
    }
}
