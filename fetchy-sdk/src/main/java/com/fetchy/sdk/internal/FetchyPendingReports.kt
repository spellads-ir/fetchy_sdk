package com.fetchy.sdk.internal

internal data class PendingReportRow(
    val localId: Long,
    val createdAtEpochMs: Long,
    val scope: String,
    val remoteNotificationId: Long,
    val channel: String
)

internal data class EncodedPendingReports(
    val exclusiveAck: String,
    val delivered: String,
    val includedLocalIds: List<Long>
)

internal fun encodePendingReports(
    reports: List<PendingReportRow>,
    limit: Int = FetchyConstants.maxPendingReportsPerRequest
): EncodedPendingReports {
    val selected = reports
        .sortedWith(compareBy<PendingReportRow> { it.createdAtEpochMs }.thenBy { it.localId })
        .take(limit.coerceAtLeast(0))
    val ackIds = LinkedHashSet<Long>()
    val delivered = ArrayList<String>(selected.size)
    for (report in selected) {
        if (report.remoteNotificationId <= 0L) continue
        if (report.scope != "b" && report.scope != "e") continue
        if (report.channel != "p" && report.channel != "l") continue
        if (report.scope == "e") ackIds.add(report.remoteNotificationId)
        delivered.add("${report.scope}${report.remoteNotificationId}${report.channel}")
    }
    return EncodedPendingReports(
        exclusiveAck = ackIds.joinToString(","),
        delivered = delivered.joinToString(","),
        includedLocalIds = selected.map { it.localId }
    )
}

internal fun pendingReportIdsToDelete(succeeded: Boolean, includedLocalIds: List<Long>): List<Long> =
    if (succeeded) includedLocalIds else emptyList()
