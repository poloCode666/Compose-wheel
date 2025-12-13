package com.polo.composewheel.ui.loading

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Minimal stub implementation of OneCallPagingList to satisfy compilation and provide basic behavior.
 * It requests the first page once and displays items via [itemContent].
 */
@Composable
fun OneCallPagingList(
    needRefresh: Boolean,
    needLoadMore: Boolean,
    requestPage: suspend (size: Int, pageNo: Int) -> List<String>,
    pageSize: Int,
    itemContent: @Composable (index: Int, item: String) -> Unit
) {
    var items by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        // request first page
        try {
            val result = requestPage(pageSize, 1)
            items = result
        } catch (_: Throwable) {
            // ignore errors in stub
        }
    }

    Column {
        for ((i, it) in items.withIndex()) {
            itemContent(i, it)
        }
    }
}

