package com.example.healthapp.feature.componets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share

import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun FabMenu(
    expanded: Boolean,
    onExpandChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Các FAB phụ, chỉ hiện khi expanded = true
            AnimatedVisibility(visible = expanded) {
                FloatingActionButton(
                    onClick = { /* TODO: Action 1 */ },
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(Icons.Default.DirectionsRun, contentDescription = "Edit")
                }
            }

            AnimatedVisibility(visible = expanded) {
                FloatingActionButton(
                    onClick = { /* TODO: Action 2 */ },
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }
            }

            // FAB chính
            FloatingActionButton(
                onClick = { onExpandChanged(!expanded) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "Menu"
                )
            }
        }
    }
}
@Composable
fun FabMenuDemo() {
    var expanded by remember { mutableStateOf(false) }

    FabMenu(
        expanded = expanded,
        onExpandChanged = { expanded = it }
    )
}

@Preview
@Composable
fun FabMenuDemoPreview() {
    FabMenuDemo()
}