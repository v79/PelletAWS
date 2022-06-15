package dev.pellet.server

import dev.pellet.logging.pelletLogger
import dev.pellet.server.buffer.PelletBuffer
import dev.pellet.server.buffer.PelletBufferPooling
import dev.pellet.server.nio.NIOSocket
import java.net.InetSocketAddress
import java.net.UnixDomainSocketAddress

sealed class CloseReason {

    object ClientInitiated : CloseReason()
    object ServerInitiated : CloseReason()
    data class ServerException(val throwable: Throwable) : CloseReason()
}

class PelletServerClient(
    internal val trackedSocket: NIOSocket,
    private val pool: PelletBufferPooling
) {

    private val logger = pelletLogger<PelletServerClient>()

    val remoteHostString: String
        get() {
            return when (val remoteAddress = trackedSocket.channel.remoteAddress) {
                is InetSocketAddress -> remoteAddress.hostString
                is UnixDomainSocketAddress -> remoteAddress.path.toString()
                else -> remoteAddress.toString()
            }
        }

    fun writeAndRelease(buffer: PelletBuffer): Result<Int> {
        val byteCount = buffer.byteBuffer.remaining()
        while (buffer.byteBuffer.hasRemaining()) {
            val attempt = runCatching {
                trackedSocket.channel.write(buffer.byteBuffer)
            }
            if (attempt.isFailure) {
                return attempt
            }
        }
        pool.release(buffer)
        return Result.success(byteCount)
    }

    fun close(source: CloseReason): Result<Unit> {
        logger.debug { "closed ${trackedSocket.channel} (initiator: $source)" }
        return runCatching {
            trackedSocket.channel.close()
        }
    }
}
