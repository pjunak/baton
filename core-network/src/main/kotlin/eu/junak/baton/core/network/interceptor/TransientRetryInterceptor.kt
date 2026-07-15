package eu.junak.baton.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.inject.Inject

/**
 * Retries GETs that die on a transient reachability error — the classic case
 * being DNS ("Unable to resolve host") failing in the first moments after app
 * open or a network handover, before the OS network is actually ready. Two
 * quick retries bridge that window so one-shot fetches (library browse, track
 * resolution) don't surface an error the next attempt would not have hit.
 * Real outages still fail, just ~1.5s later. Non-GETs are never retried:
 * they're not idempotent, and a login must fail visibly, not repeat.
 */
class TransientRetryInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        while (true) {
            try {
                return chain.proceed(chain.request())
            } catch (e: IOException) {
                val retriable = chain.request().method == "GET" && isTransientReachability(e)
                if (!retriable || attempt >= MAX_EXTRA_ATTEMPTS) throw e
                attempt++
                Thread.sleep(BASE_DELAY_MS * attempt) // 500ms, then 1s — on an OkHttp thread, never main
            }
        }
    }

    /** Only errors that mean "the network path isn't there *right now*" —
     *  not timeouts (server slow) and not TLS failures (config problem). */
    private fun isTransientReachability(e: IOException): Boolean =
        e is UnknownHostException || e is ConnectException

    private companion object {
        const val MAX_EXTRA_ATTEMPTS = 2
        const val BASE_DELAY_MS = 500L
    }
}
