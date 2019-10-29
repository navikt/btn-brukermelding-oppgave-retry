package no.nav.btn

fun main() {
    val retryRiver = RetryOppgaveRiver()

    Runtime.getRuntime().addShutdownHook(Thread {
        retryRiver.stop()
    })

    retryRiver.start()
}
