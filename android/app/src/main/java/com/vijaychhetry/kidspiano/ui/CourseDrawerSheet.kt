package com.vijaychhetry.kidspiano.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vijaychhetry.kidspiano.core.learning.Copy
import com.vijaychhetry.kidspiano.core.learning.CourseMenuItem
import com.vijaychhetry.kidspiano.core.learning.REQUIRE_SEQUENTIAL_UNLOCK

@Composable
fun CourseDrawerSheet(
    items: List<CourseMenuItem>,
    onOpen: (CourseMenuItem) -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight(),
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            Text(
                Copy.COURSES,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Text(
                if (REQUIRE_SEQUENTIAL_UNLOCK) {
                    "Finish one course to open the next."
                } else {
                    "All courses open for review."
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            items.forEach { item ->
                val alpha = if (item.enabled) 1f else 0.45f
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .alpha(alpha)
                        .semantics { contentDescription = item.title }
                        .then(
                            if (item.enabled) {
                                Modifier.clickable { onOpen(item) }
                            } else {
                                Modifier
                            },
                        ),
                    shape = RoundedCornerShape(12.dp),
                    color = if (item.selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(item.title, fontWeight = FontWeight.SemiBold)
                        Text(item.subtitle, style = MaterialTheme.typography.bodySmall)
                        item.lockedReason?.let { reason ->
                            Text(reason, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
