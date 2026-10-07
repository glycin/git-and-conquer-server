package com.glycin.api.rest

import com.glycin.api.model.OpResult
import com.glycin.world.WorldRepo

suspend fun WorldRepo.success(): OpResult = OpResult.success(snapshot())

suspend fun WorldRepo.committed(): OpResult = OpResult.success(snapshot(), commitSha = headAndBranch().first)

suspend fun WorldRepo.failure(message: String): OpResult = OpResult.failure(snapshot(), message)

suspend fun WorldRepo.conflict(operation: String, paths: List<String>): OpResult =
    OpResult.conflict(snapshot(), operation, paths)
