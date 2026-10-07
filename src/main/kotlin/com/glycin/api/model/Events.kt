package com.glycin.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class GitEvent {

    @Serializable
    @SerialName("HeadMoved")
    data class HeadMoved(val delta: Delta) : GitEvent()

    @Serializable
    @SerialName("Conflicted")
    data class Conflicted(
        val operation: String,
        val cellCount: Int,
        val objectCount: Int,
    ) : GitEvent()

    // Branch changed but HEAD didn't move, like `git checkout -b`.
    @Serializable
    @SerialName("BranchSwitched")
    data class BranchSwitched(
        val branch: String,
        val head: String,
    ) : GitEvent()

    @Serializable
    @SerialName("TagsChanged")
    data object TagsChanged : GitEvent()

    @Serializable
    @SerialName("GitCommand")
    data class GitCommand(
        val command: String,
        val exit: Int,
    ) : GitEvent()
}
