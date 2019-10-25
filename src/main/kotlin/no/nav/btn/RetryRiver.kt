package no.nav.btn

import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

private val logger = LoggerFactory.getLogger("no.nav.btn.RetryRiver")

class RetryRiver(
    val retryTopic: String,
    val producerTopic: String,
    val deadLetterTopic: String
) : ConsumerService() {
    lateinit var reproducer: KafkaProducer<String, Packet>
    override val SERVICE_APP_ID: String
        get() = "btn-brukermelding-oppgave"

    private fun initializeReproducer() {
        reproducer = KafkaProducer(getProducerConfig())
    }

    override fun run() {
        if (!::reproducer.isInitialized) {
            initializeReproducer()
        }

        val consumer = KafkaConsumer<String, Packet>(getConsumerConfig())
        consumer.subscribe(listOf(retryTopic))

        while(true) {
            val records = consumer.poll(Duration.ofMillis(1000))
            records.forEach {
                logger.info("Retrying ${it.value().message}")
                try {
                    makeMockServerCall()
                    reproducer.send(ProducerRecord(producerTopic, UUID.randomUUID().toString(), addBreadcrumbs(it.value())))
                } catch(e: Exception) {
                    logger.warn("We failed again")
                    val attempts = it.headers()?.lastHeader("X-Failed-Attempts")?.value()?.let { attempt -> String(attempt).toInt() } ?: 0
                    if (attempts == 10) {
                        reproducer.send(ProducerRecord(deadLetterTopic, 0, it.key(), it.value(), listOf(RecordHeader("X-Failed-Attempts", "$attempts".toByteArray()))))
                    }
                    reproducer.send(ProducerRecord(retryTopic, 0, it.key(), it.value(), listOf(RecordHeader("X-Failed-Attempts", "${attempts + 1}".toByteArray()))))
                }
            }
        }
    }

    override fun shutdown() {
        if (::reproducer.isInitialized) {
            reproducer.flush()
            reproducer.close(Duration.ofSeconds(5))
        }
    }

    private fun addBreadcrumbs(packet: Packet): Packet = Packet(
            breadcrumbs = packet.breadcrumbs + Breadcrumb("btn-brukermelding-oppgave"),
            timestamp = packet.timestamp,
            message = packet.message
    )
}