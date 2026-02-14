package com.oogley.billbot.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.oogley.billbot.data.config.ConfigField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigFormScreen(
    fields: List<ConfigField>,
    isDirty: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onFieldChanged: (path: String, value: Any) -> Unit,
    onSave: () -> Unit
) {
    var expandedSections by remember { mutableStateOf(setOf<String>()) }

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search settings...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val filtered = if (searchQuery.isBlank()) fields else filterFields(fields, searchQuery)

            items(filtered, key = { it.path }) { field ->
                ConfigFieldItem(
                    field = field,
                    expandedSections = expandedSections,
                    onToggleSection = { path ->
                        expandedSections = if (path in expandedSections)
                            expandedSections - path else expandedSections + path
                    },
                    onFieldChanged = onFieldChanged
                )
            }
        }

        // Save FAB
        if (isDirty) {
            ExtendedFloatingActionButton(
                onClick = onSave,
                icon = { Icon(Icons.Default.Save, contentDescription = null) },
                text = { Text("Save") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigFieldItem(
    field: ConfigField,
    expandedSections: Set<String>,
    onToggleSection: (String) -> Unit,
    onFieldChanged: (String, Any) -> Unit
) {
    when (field) {
        is ConfigField.Section -> {
            val isExpanded = field.path in expandedSections
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ListItem(
                        headlineContent = { Text(field.label, style = MaterialTheme.typography.titleSmall) },
                        supportingContent = field.description?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
                        trailingContent = {
                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.clickable { onToggleSection(field.path) }
                    )
                    AnimatedVisibility(visible = isExpanded) {
                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                            field.children.forEach { child ->
                                ConfigFieldItem(
                                    field = child,
                                    expandedSections = expandedSections,
                                    onToggleSection = onToggleSection,
                                    onFieldChanged = onFieldChanged
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }

        is ConfigField.Text -> {
            var text by remember(field.value) { mutableStateOf(field.value) }
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onFieldChanged(field.path, it)
                },
                label = { Text(field.label) },
                supportingText = field.description?.let { { Text(it) } },
                placeholder = field.placeholder?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        is ConfigField.Number -> {
            var text by remember(field.value) { mutableStateOf(field.value?.let {
                if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
            } ?: "") }
            OutlinedTextField(
                value = text,
                onValueChange = { newVal ->
                    text = newVal
                    newVal.toDoubleOrNull()?.let { onFieldChanged(field.path, it) }
                },
                label = {
                    Text(buildString {
                        append(field.label)
                        if (field.min != null || field.max != null) {
                            append(" (")
                            field.min?.let { append("min: ${it.toLong()}") }
                            if (field.min != null && field.max != null) append(", ")
                            field.max?.let { append("max: ${it.toLong()}") }
                            append(")")
                        }
                    })
                },
                supportingText = field.description?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        is ConfigField.Toggle -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(field.label, style = MaterialTheme.typography.bodyLarge)
                    field.description?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(
                    checked = field.value,
                    onCheckedChange = { onFieldChanged(field.path, it) }
                )
            }
        }

        is ConfigField.Select -> {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = field.value,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(field.label) },
                    supportingText = field.description?.let { { Text(it) } },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    field.options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onFieldChanged(field.path, option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        is ConfigField.ReadOnly -> {
            OutlinedTextField(
                value = field.value,
                onValueChange = {},
                label = { Text(field.label) },
                supportingText = field.description?.let { { Text(it) } },
                enabled = false,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun filterFields(fields: List<ConfigField>, query: String): List<ConfigField> {
    val lowerQuery = query.lowercase()
    return fields.mapNotNull { field ->
        when (field) {
            is ConfigField.Section -> {
                val filteredChildren = filterFields(field.children, query)
                if (filteredChildren.isNotEmpty() || field.label.lowercase().contains(lowerQuery)) {
                    field.copy(children = filteredChildren)
                } else null
            }
            else -> {
                if (field.label.lowercase().contains(lowerQuery) ||
                    field.path.lowercase().contains(lowerQuery) ||
                    (field.description?.lowercase()?.contains(lowerQuery) == true)) {
                    field
                } else null
            }
        }
    }
}
