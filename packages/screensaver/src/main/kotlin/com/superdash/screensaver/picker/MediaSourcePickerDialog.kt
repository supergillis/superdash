package com.superdash.screensaver.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.superdash.ha.BrowseMediaSource
import kotlinx.coroutines.CancellationException

private data class PickerNode(
    val node: BrowseMediaSource,
    val children: List<BrowseMediaSource>,
)

/** Modal dialog for picking a folder/album from HA's media_source tree.
 *  Calls [onConfirm] with (mediaContentId, displayTitle) on confirm,
 *  [onDismiss] on cancel. */
@Composable
fun MediaSourcePickerDialog(
    browse: suspend (parentId: String?) -> BrowseMediaSource,
    onConfirm: (mediaContentId: String, displayTitle: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var backStack by remember { mutableStateOf<List<BrowseMediaSource>>(emptyList()) }
    var current by remember { mutableStateOf<PickerNode?>(null) }
    var selectedSource by remember { mutableStateOf<BrowseMediaSource?>(null) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }

    LaunchedEffect(backStack.size, retryKey) {
        loading = true
        error = null
        try {
            val target = backStack.lastOrNull()
            val response = browse(target?.mediaContentId)
            current = PickerNode(node = response, children = response.children)
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            error = t.message ?: "browseMedia failed"
            current = null
        } finally {
            loading = false
        }
    }

    val selected = selectedSource

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = current?.node?.title ?: "Pick a source",
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
                when {
                    loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    error != null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.align(Alignment.Center),
                        ) {
                            Text("Couldn't load: $error")
                            Spacer(Modifier.size(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { retryKey += 1 }) {
                                    Text("Retry")
                                }
                                if (backStack.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            query = ""
                                            backStack = backStack.dropLast(1)
                                        },
                                    ) {
                                        Text("Back")
                                    }
                                }
                            }
                        }
                    }
                    current != null -> {
                        Column {
                            Text(
                                text = "Current: ${currentFolderLabel(current!!.node, backStack)}",
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            Text(
                                text = "Selected: ${selected?.title ?: "None"}",
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            OutlinedTextField(
                                value = query,
                                onValueChange = { value -> query = value },
                                label = { Text("Search") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.size(8.dp))
                            val backLabel =
                                if (backStack.isNotEmpty()) {
                                    backStack.dropLast(1).lastOrNull()?.title ?: "root"
                                } else {
                                    null
                                }
                            PickerList(
                                currentNode = current!!.node,
                                backLabel = backLabel,
                                children = current!!.children,
                                query = query,
                                selectedSource = selected,
                                onSelectCurrent = { selectedSource = current!!.node },
                                onBack = {
                                    query = ""
                                    backStack = backStack.dropLast(1)
                                },
                                onDrill = { child ->
                                    if (child.canExpand) {
                                        query = ""
                                        backStack = backStack + child
                                    }
                                },
                            )
                            if (current!!.children.isEmpty()) {
                                Spacer(Modifier.size(8.dp))
                                Text("No media in this folder")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val node = selectedSource ?: return@Button
                    onConfirm(node.mediaContentId, node.title)
                },
                enabled = selected != null,
            ) {
                Text("Use this folder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun PickerList(
    currentNode: BrowseMediaSource,
    backLabel: String?,
    children: List<BrowseMediaSource>,
    query: String,
    selectedSource: BrowseMediaSource?,
    onSelectCurrent: () -> Unit,
    onBack: () -> Unit,
    onDrill: (BrowseMediaSource) -> Unit,
) {
    val filteredChildren =
        remember(children, query) {
            filterBrowseMediaSources(children, query)
        }
    val canSelectCurrent = children.any { child -> child.mediaClass == "image" }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
    ) {
        if (backLabel != null) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onBack() }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                ) {
                    Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Back to $backLabel")
                }
            }
        }
        item {
            ListItem(
                headlineContent = { Text("This folder") },
                supportingContent = {
                    if (canSelectCurrent) {
                        Text(currentNode.title)
                    } else {
                        Text("No images available here")
                    }
                },
                trailingContent =
                    if (selectedSource?.mediaContentId == currentNode.mediaContentId) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                            )
                        }
                    } else {
                        null
                    },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canSelectCurrent) { onSelectCurrent() },
            )
        }
        if (children.isNotEmpty() && filteredChildren.isEmpty()) {
            item {
                ListItem(
                    headlineContent = { Text("No matching media") },
                    supportingContent = { Text("Try a different search.") },
                )
            }
        }
        items(filteredChildren, key = { child -> child.mediaContentId }) { child ->
            val icon =
                if (child.mediaClass == "image") {
                    Icons.Filled.Image
                } else {
                    Icons.Filled.Folder
                }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = child.canExpand) { onDrill(child) }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(text = child.title, modifier = Modifier.weight(1f))
                if (child.canExpand) {
                    Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

fun filterBrowseMediaSources(
    children: List<BrowseMediaSource>,
    query: String,
): List<BrowseMediaSource> {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isEmpty()) {
        return children
    }

    return children.filter { child ->
        child.title.lowercase().contains(normalizedQuery) ||
            child.mediaClass.lowercase().contains(normalizedQuery) ||
            child.mediaContentId.lowercase().contains(normalizedQuery) ||
            child.mediaContentType.lowercase().contains(normalizedQuery)
    }
}

private fun currentFolderLabel(
    currentNode: BrowseMediaSource,
    backStack: List<BrowseMediaSource>,
): String {
    if (backStack.isEmpty()) {
        return currentNode.title
    }

    return backStack.joinToString(" / ") { node -> node.title }
}
