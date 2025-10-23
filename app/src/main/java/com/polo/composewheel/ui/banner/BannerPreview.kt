package com.polo.composewheel.ui.banner


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class BannerPreview {
}

data class BannerItem(
    val id: Int,
    val title: String,
    val bg: Color
)

fun mockBannerItems(): List<BannerItem> = listOf(
    BannerItem(1, "Top Picks for You", Color(0xFF5B8FFF)),
    BannerItem(2, "Super Sale · 50% OFF", Color(0xFF7B61FF)),
    BannerItem(3, "New Arrivals", Color(0xFFFF8FAB)),
    BannerItem(4, "Daily Surprise", Color(0xFF3DDC84))
)

/* --------------- Preview（Light / Dark） --------------- */

@Preview(name = "Banner - Light", showBackground = true, widthDp = 360, heightDp = 200)
@Composable
private fun BannerPreviewLight() {
    val items = remember { mockBannerItems() }
    MaterialTheme {
        Surface {
            Banner(
                items = items,
                autoPlayMillis = 2500,
                modifier = Modifier

                    .padding(vertical = 12.dp)
                    .height(180.dp),
                onItemClick = { idx, _ -> println("click banner index=$idx") }
            ) { index, item ->
                // 这里是每一页的内容（可替换成你的图片组件）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(item.bg)
                ) {
                    Text(
                        text = "${item.title}  #$index",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.BottomStart)

                    )
                }
            }
        }
    }
}

@Preview(name = "Banner - Dark", showBackground = true, widthDp = 360, heightDp = 200)
@Composable
fun BannerPreviewDark() {
    val items = remember { mockBannerItems() }
    MaterialTheme {
        Surface(color = Color(0xFF111214)) {
            Banner(
                items = items,

                autoPlayMillis = 2500,
                modifier = Modifier
                    .height(180.dp),
                onItemClick = { idx, _ -> println("click banner index=$idx") }
            ) { index, item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(item.bg)
                ) {
                    Text(
                        text = "${item.title}  #$index",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

