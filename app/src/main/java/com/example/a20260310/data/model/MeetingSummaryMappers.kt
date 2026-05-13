package com.example.a20260310.data.remote.mapper

import com.example.a20260310.data.model.ActionItem
import com.example.a20260310.data.model.DecisionItem
import com.example.a20260310.data.model.MeetingSummary
import com.example.a20260310.data.remote.dto.ActionItemDto
import com.example.a20260310.data.remote.dto.DecisionDto
import com.example.a20260310.data.remote.dto.MeetingSummaryResponseDto

fun MeetingSummaryResponseDto.toDomain(): MeetingSummary =
    MeetingSummary(
        id = id,
        meetingId = meetingId,
        summary = summary,
        decisions = decisions.map { it.toDomain() },
        actionItems = actionItems.map { it.toDomain() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun DecisionDto.toDomain(): DecisionItem =
    DecisionItem(
        id = id,
        meetingId = meetingId,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun ActionItemDto.toDomain(): ActionItem =
    ActionItem(
        id = id,
        meetingId = meetingId,
        title = task,
        owner = assignee.orEmpty(),
        deadline = dueDate.orEmpty(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )