package com.example.beiwang.ui.common
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable
fun CreateButton(label: String, onClick: () -> Unit) {
 ExtendedFloatingActionButton(onClick = onClick, containerColor = MaterialTheme.colorScheme.primary,
 contentColor = MaterialTheme.colorScheme.onPrimary, shape = RoundedCornerShape(28.dp),
 icon = { Icon(Icons.Default.Add, null) }, text = { Text(label) })
}
@Composable
fun ListFilters(selected: Int, labels: List<String>, onSelect: (Int) -> Unit) {
 Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
 .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(28.dp)).padding(4.dp),
 horizontalArrangement = Arrangement.spacedBy(4.dp)) {
 labels.forEachIndexed { index, label ->
 if (selected == index) Button(onClick = { onSelect(index) }, modifier = Modifier.weight(1f),
 contentPadding = PaddingValues(horizontal = 8.dp)) { Text(label, maxLines = 1) }
 else TextButton(onClick = { onSelect(index) }, modifier = Modifier.weight(1f),
 contentPadding = PaddingValues(horizontal = 8.dp)) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
 }
 }
}
