package com.example.healthapp.feature.componets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

@Composable
fun CustomTopMenu(
    color: Color
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf("Chọn tùy chọn") }
    val options = listOf("Chạy Bộ", "Đạp xe", "Leo Núi")

    Column {
        // Button hiển thị text theo option đã chọn
        Button(onClick = { expanded = true }) {
            Text(selectedOption)
        }

        // Menu hiển thị phía trên
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(x = 0.dp, y = (-0).dp) // dịch chuyển lên trên
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        selectedOption = option
                        expanded = false
                    }
                )
            }

        }
        if (selectedOption == "Chạy Bộ"){

        }
    }
}