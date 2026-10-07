package com.glycin.api.ws

import io.quarkus.websockets.next.OnClose
import io.quarkus.websockets.next.OnOpen
import io.quarkus.websockets.next.WebSocket
import io.quarkus.websockets.next.WebSocketConnection
import org.jboss.logging.Logger

@WebSocket(path = "/ws/events")
class EventsSocket {

    @OnOpen
    fun onOpen(connection: WebSocketConnection) {
        log.infof("WS connected: %s", connection.id())
    }

    @OnClose
    fun onClose(connection: WebSocketConnection) {
        log.infof("WS disconnected: %s", connection.id())
    }

    companion object {
        private val log: Logger = Logger.getLogger(EventsSocket::class.java)
    }
}
