package com.thinatech.lightweightserver

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.env.EnvironmentEndpoint
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.actuate.metrics.MetricsEndpoint
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

inline fun <reified T:Any> logger(): Logger = LoggerFactory.getLogger(T::class.java)

open class LightWeightHttpServer(private val healths: List<HealthIndicator?>, private val objectMapper: ObjectMapper) {

    @JvmField
    val log: Logger = logger<LightWeightHttpServer>()

    @Value("\${server.port:8080}")
    private var port: Int = 0

    private lateinit var httpServer: HttpServer

    private lateinit var httpServerThread: Thread

    private var stopping = AtomicBoolean(false)

    @PostConstruct
    open fun start() {
        httpServer = HttpServer.create(InetSocketAddress(port), 0)
                with(httpServer) {
                    createContext("/actuator") { exchange ->
                        log.debug("requestURI {}", exchange.requestURI)
                        if (exchange.requestMethod.equals("GET", ignoreCase = true)) {
                            var path = exchange.requestURI.toASCIIString().substring("/actuator".length)
                            log.debug("resolved path {}", path)

                            contextMappingsForGet[path]?.handle(exchange) ?: notFound(exchange)

                        } else {
                            notFound(exchange)
                        }
                    }
                }

        Runtime.getRuntime().addShutdownHook(Thread {stop()})

        httpServerThread = Thread {
            log.info("Starting server at port {}", port)
            httpServer.start()
        }
        httpServerThread.start()
    }

    @PreDestroy
    open fun stop() {
        if (stopping.get()) {
            return
        }
        stopping.set(true)
        log.info("Stopping server ...")
        httpServer.stop(3)
        httpServerThread.join()
    }

    private val contextMappingsForGet = mapOf(
            "/health" to HttpHandler {
                json(exchange = it, checkHealths())
            },
            "/metrics" to HttpHandler {
                json(exchange = it, metrics())
            },
            "/env" to HttpHandler {
                json(exchange = it, envs())
            }
    )

    @Autowired
    lateinit var envEndpoint: EnvironmentEndpoint

    @Autowired
    lateinit var metricsEndpoint: MetricsEndpoint

    private fun envs(): ByteArray {
        return envEndpoint.environment(null)
                .let { objectMapper.writeValueAsBytes(it) }
    }

    private fun metrics(): ByteArray {
        return metricsEndpoint.listNames()
                .let { objectMapper.writeValueAsBytes(it) }
    }

    private fun checkHealths(): ByteArray =
            objectMapper.writeValueAsBytes(healths.map { it?.health() }.asIterable())

    private fun json(exchange: HttpExchange, body: ByteArray) {
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.write(body)
        exchange.responseBody.close()
    }

    private fun notFound(exchange: HttpExchange) {
        "<h1>404 Not Found</h1>".toByteArray()
                .apply {
                    exchange.sendResponseHeaders(404, this.size.toLong())
                    exchange.responseHeaders.add("Content-Type", "text/html")
                    exchange.responseBody.write(this)
                    exchange.responseBody.close()
                }
    }
}
