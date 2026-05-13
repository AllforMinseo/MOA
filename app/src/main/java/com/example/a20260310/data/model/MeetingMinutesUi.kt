package com.example.a20260310.data.model

data class MeetingDraft(
    val title: String = "",
    val date: String = "",
    val time: String = "",
    val attendees: String = "",
) {
    fun toDescription(): String? = buildString {
        val dt = "${date.trim()} ${time.trim()}".trim()
        if (dt.isNotBlank()) appendLine("일시: $dt")
        if (attendees.isNotBlank()) appendLine("참석자: $attendees")
    }.trim().ifBlank { null }

    fun displayDatetime(): String {
        val dt = "${date.trim()} ${time.trim()}".trim()
        return dt.ifBlank { "—" }
    }

    fun participantList(): List<String> =
        attendees.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

data class MinutesUiModel(
    val subject: String,
    val datetime: String,
    val attendees: String,
    val summary: MeetingSummary,
    val summaryText: String,
    val agenda: String,
    val discussion: String,
    val note: String,
    val followup: String,
    val writerLabel: String,
)

object MinutesUiMapper {
    fun build(
        draft: MeetingDraft,
        transcript: String,
        summary: MeetingSummary,
    ): MinutesUiModel {
        val agenda = if (summary.decisions.isNotEmpty()) {
            summary.decisions.joinToString("\n") { "• ${it.content.trim()}" }
        } else {
            "—"
        }

        val discussion = buildString {
            append(summary.summary.trim().ifBlank { "—" })
            val t = transcript.trim()
            if (t.isNotEmpty()) {
                append("\n\n──── 전사 ────\n")
                append(t)
            }
        }

        val note = "—"

        val followup = if (summary.actionItems.isNotEmpty()) {
            summary.actionItems.joinToString("\n") { item ->
                val owner = item.owner.trim().ifBlank { "미정" }
                val deadline = item.deadline.trim().ifBlank { "미정" }
                val title = item.title.trim().ifBlank { "—" }
                "• $title (담당: $owner / 마감: $deadline)"
            }
        } else {
            "—"
        }

        return MinutesUiModel(
            subject = draft.title.trim().ifBlank { "—" },
            datetime = draft.displayDatetime(),
            attendees = draft.attendees.trim().ifBlank { "—" },
            summary = summary,
            summaryText = summary.summary.trim().ifBlank { "—" },
            agenda = agenda,
            discussion = discussion,
            note = note,
            followup = followup,
            writerLabel = "작성자: MOA",
        )
    }
}