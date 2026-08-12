package com.trevio.android.ui.household

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trevio.android.R
import com.trevio.android.core.designsystem.theme.*
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.Member
import com.trevio.android.domain.model.TransactionType
import com.trevio.android.util.FormatUtils
import com.trevio.android.util.HouseholdCategories
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailSheet(
    entry: Expense,
    members: List<Member>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    currencySymbol: String = "₹"
) {
    val category = HouseholdCategories.getCategory(entry.category)
    val isIncome = entry.transactionType == TransactionType.INCOME
    val payer = members.find { it.uid == entry.paidBy }
    val dateTimeFormat = SimpleDateFormat("EEE, MMM d, yyyy · h:mm a", Locale.getDefault())
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.entry_detail_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.entry_detail_close))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category + Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = (category?.color ?: CategoryFallback).copy(alpha = 0.15f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = category?.icon ?: Icons.Default.Category,
                            contentDescription = stringResource(R.string.entry_detail_category),
                            modifier = Modifier.size(28.dp),
                            tint = category?.color ?: CategoryFallback
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category?.labelResId?.let { stringResource(it) } ?: stringResource(R.string.entry_detail_other),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${if (isIncome) "+" else "-"}$currencySymbol${FormatUtils.formatAmount(entry.amount)}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isIncome) BalancePositive else MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isIncome) BalancePositive.copy(alpha = 0.12f) else BalanceNegative.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if (isIncome) stringResource(R.string.entry_detail_received) else stringResource(R.string.entry_detail_spent),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isIncome) TrevioSuccess else TrevioError,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Description (if different from category label)
            val categoryLabel = category?.labelResId?.let { stringResource(it) } ?: ""
            if (entry.description.isNotBlank() && entry.description != categoryLabel) {
                DetailRow(
                    icon = Icons.Default.Notes,
                    label = stringResource(R.string.entry_detail_description),
                    value = entry.description
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Paid by
            DetailRow(
                icon = Icons.Default.Person,
                label = stringResource(R.string.entry_detail_paid_by),
                value = payer?.displayName ?: stringResource(R.string.entry_detail_someone)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Date
            DetailRow(
                icon = Icons.Default.CalendarMonth,
                label = stringResource(R.string.entry_detail_date),
                value = if (entry.date > 0) dateTimeFormat.format(Date(entry.date)) else "—"
            )

            // Note
            if (entry.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                DetailRow(
                    icon = Icons.Default.Notes,
                    label = stringResource(R.string.entry_detail_note),
                    value = entry.note
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.entry_detail_edit), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.entry_detail_edit), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(vertical = 14.dp, horizontal = 20.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.entry_detail_delete), modifier = Modifier.size(18.dp))
                }
            }

            if (showDeleteConfirm) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.entry_detail_delete_confirm), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.entry_detail_cannot_undo), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showDeleteConfirm = false },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.common_cancel)) }
                            Button(
                                onClick = { showDeleteConfirm = false; onDelete() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.common_delete)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Column {
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
