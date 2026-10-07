package com.glycin.api.ws

import com.glycin.api.model.GitEvent
import com.glycin.world.WorldJson
import io.quarkus.websockets.next.OpenConnections
import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger

@ApplicationScoped
class EventBroadcaster(private val connections: OpenConnections) {

    fun broadcast(event: GitEvent) {
        val payload = WorldJson.EVENTS.encodeToString(GitEvent.serializer(), event)
        for (conn in connections) {
            conn.sendText(payload).subscribe().with(
                { /* sent */ },
                { err -> log.warnf("WS send to %s failed: %s", conn.id(), err.message) },
            )
        }
    }

    companion object {
        private val log: Logger = Logger.getLogger(EventBroadcaster::class.java)
    }
}
