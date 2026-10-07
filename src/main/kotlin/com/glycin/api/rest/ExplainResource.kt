package com.glycin.api.rest

import com.glycin.api.model.MergeExplain
import com.glycin.api.model.MerkleExplain
import com.glycin.world.ExplainQueries
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/explain")
@Produces(MediaType.APPLICATION_JSON)
class ExplainResource(private val explain: ExplainQueries) {

    @GET
    @Path("/merkle")
    suspend fun merkle(): MerkleExplain = explain.merkle()

    @GET
    @Path("/merge")
    suspend fun merge(): MergeExplain = explain.merge()
}
