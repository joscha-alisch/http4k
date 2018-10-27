package org.http4k.k8s

import org.http4k.client.JavaHttpClient
import org.http4k.core.*
import org.http4k.core.Method.GET
import org.http4k.core.Status.Companion.OK
import org.http4k.filter.ClientFilters
import org.http4k.filter.DebuggingFilters
import org.http4k.lens.Lens
import org.http4k.server.SunHttp
import org.http4k.server.asServer

object ProxyApp {

    operator fun invoke(env: K8sEnvironment): Http4kK8sServer {
        val otherServiceUri: Lens<K8sEnvironment, Uri> = K8sEnvKey.serviceUriFor("otherservice")

        val proxyApp = ClientFilters.SetHostFrom(otherServiceUri(env))
            .then(rewriteUriToLocalhostAsWeDoNotHaveDns)
            .then(JavaHttpClient())

        return proxyApp.asK8sServer(::SunHttp, env)
    }

    private val rewriteUriToLocalhostAsWeDoNotHaveDns = Filter { next ->
        {
            println("Rewriting ${it.uri} so we can proxy properly")
            next(it.uri(it.uri.authority("localhost:9000")))
        }
    }
}

val environmentSetByK8s =
    K8sEnvironment.from(
        mapOf(
            "SERVICE_PORT" to "8000",
            "HEALTH_PORT" to "8001",
            "OTHERSERVICE_SERVICE_PORT" to "9000"
        )
    )

fun main(args: Array<String>) {

    // start the other service
    { _: Request -> Response(OK).body("HELLO!") }.asServer(SunHttp(9000)).start().use {

        // start our service with the environment set by K8S
        ProxyApp(environmentSetByK8s).start().use {
            val client = DebuggingFilters.PrintResponse().then(JavaHttpClient())

            // health checks
            client(Request(GET, "http://localhost:8001/liveness"))
            client(Request(GET, "http://localhost:8001/readiness"))

            // proxied call
            client(Request(GET, "http://localhost:8000"))
        }
    }


}