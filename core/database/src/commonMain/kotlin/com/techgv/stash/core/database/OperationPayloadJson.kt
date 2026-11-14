package com.techgv.stash.core.database

import com.techgv.stash.core.model.FetchStatus
import com.techgv.stash.core.model.LifecycleStamps
import com.techgv.stash.core.model.OperationPayload
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

// JSON shape of `operation.payload_json`. The model types stay annotation-free; these DTOs carry the serial names, so
// renaming a Kotlin property must keep its @SerialName. Instants are epoch milliseconds like the columns.
// Stored rows outlive the code: renaming a payload type's @SerialName or a FetchStatus constant makes existing rows
// unreadable. Keys a newer version added are ignored, so a downgrade can still read them.

internal object OperationPayloadJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(payload: OperationPayload): String = json.encodeToString(PayloadDto.serializer(), payload.toDto())

    fun decode(text: String): OperationPayload = json.decodeFromString(PayloadDto.serializer(), text).toModel()
}

@Serializable
private sealed interface PayloadDto {
    @Serializable
    @SerialName("lifecycle")
    data class Lifecycle(@SerialName("before") val before: StampsDto?, @SerialName("after") val after: StampsDto) : PayloadDto

    @Serializable
    @SerialName("metadata")
    data class Metadata(@SerialName("before") val before: FetchStatus, @SerialName("after") val after: FetchStatus) : PayloadDto

    @Serializable
    @SerialName("undo")
    data class Undo(
        @SerialName("undone_op_id") val undoneOpId: String,
        @SerialName("before") val before: StampsDto,
        @SerialName("after") val after: StampsDto,
    ) : PayloadDto
}

@Serializable
private data class StampsDto(
    @SerialName("added_at") val addedAt: Long,
    @SerialName("read_at") val readAt: Long?,
    @SerialName("archived_at") val archivedAt: Long?,
    @SerialName("deleted_at") val deletedAt: Long?,
)

private fun OperationPayload.toDto(): PayloadDto = when (this) {
    is OperationPayload.Lifecycle -> PayloadDto.Lifecycle(before?.toDto(), after.toDto())
    is OperationPayload.Metadata -> PayloadDto.Metadata(before, after)
    is OperationPayload.Undo -> PayloadDto.Undo(undoneOpId, before.toDto(), after.toDto())
}

private fun PayloadDto.toModel(): OperationPayload = when (this) {
    is PayloadDto.Lifecycle -> OperationPayload.Lifecycle(before?.toModel(), after.toModel())
    is PayloadDto.Metadata -> OperationPayload.Metadata(before, after)
    is PayloadDto.Undo -> OperationPayload.Undo(undoneOpId, before.toModel(), after.toModel())
}

private fun LifecycleStamps.toDto() = StampsDto(
    addedAt.toEpochMilliseconds(),
    readAt?.toEpochMilliseconds(),
    archivedAt?.toEpochMilliseconds(),
    deletedAt?.toEpochMilliseconds(),
)

private fun StampsDto.toModel() = LifecycleStamps(
    Instant.fromEpochMilliseconds(addedAt),
    readAt?.let(Instant::fromEpochMilliseconds),
    archivedAt?.let(Instant::fromEpochMilliseconds),
    deletedAt?.let(Instant::fromEpochMilliseconds),
)
