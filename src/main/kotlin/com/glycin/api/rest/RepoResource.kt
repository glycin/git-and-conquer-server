package com.glycin.api.rest

import com.glycin.api.model.BranchesResponse
import com.glycin.api.model.Delta
import com.glycin.api.model.GraphResponse
import com.glycin.api.model.LogResponse
import com.glycin.api.model.MapSnapshot
import com.glycin.api.model.RepoPaths
import com.glycin.api.model.RepoStatus
import com.glycin.git.Packing
import com.glycin.world.Diff
import com.glycin.world.MetaFiles
import com.glycin.world.RepoQueries
import com.glycin.world.WorldPaths
import com.glycin.world.WorldRepo
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Path("/repo")
@Produces(MediaType.APPLICATION_JSON)
class RepoResource(
    private val worldRepo: WorldRepo,
    private val repoQueries: RepoQueries,
    private val diffCalculator: Diff,
    private val metaFiles: MetaFiles,
    private val packing: Packing,
    private val worldPaths: WorldPaths,
) {

    @GET
    @Path("/paths")
    suspend fun paths(): RepoPaths = RepoPaths(repo = worldPaths.repo.toString(), gitDir = packing.gitDir())

    @GET
    @Path("/meta")
    suspend fun meta(): MetaFiles.MapMeta = withContext(Dispatchers.IO) {
        metaFiles.readMapMeta() ?: throw NotFoundException("meta/map.json is missing")
    }

    @GET
    @Path("/snapshot")
    suspend fun snapshot(): MapSnapshot = worldRepo.snapshot()

    @GET
    @Path("/status")
    suspend fun status(): RepoStatus = worldRepo.status()

    @GET
    @Path("/log")
    suspend fun log(
        @QueryParam("branch") branch: String?,
        @QueryParam("limit") @DefaultValue("50") limit: Int,
    ): LogResponse = LogResponse(repoQueries.log(branch, limit))

    @GET
    @Path("/graph")
    suspend fun graph(
        @QueryParam("limit") @DefaultValue("200") limit: Int,
    ): GraphResponse = repoQueries.graph(limit)

    @GET
    @Path("/branches")
    suspend fun branches(
        @QueryParam("compareTo") compareTo: String?,
    ): BranchesResponse = repoQueries.branches(compareTo)

    @GET
    @Path("/diff")
    suspend fun diff(
        @QueryParam("from") from: String,
        @QueryParam("to") to: String,
    ): Delta = diffCalculator.diff(from, to)
}
