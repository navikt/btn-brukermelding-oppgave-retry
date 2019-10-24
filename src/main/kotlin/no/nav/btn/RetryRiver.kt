package no.nav.btn

import io.confluent.kafka.serializers.KafkaJsonSerializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

private val logger = LoggerFactory.getLogger("no.nav.btn.RetryRiver")
private val producer: KafkaProducer<String, Packet> = KafkaProducer(getProducerConfig())

fun startRetryConsumer() {
    val consumer = KafkaConsumer<String, Packet>(getConsumerConfig())
    consumer.subscribe(listOf(TOPIC_RETRY_OPPGAVE_OPPRETTELSE))

    while(true) {
        val records = consumer.poll(Duration.ofMillis(1000))
        records.forEach {
            logger.info("Retrying ${it.value().message}")
            try {
                makeMockServerCall()
                producer.send(ProducerRecord(TOPIC_MELDING_MED_OPPGAVE, UUID.randomUUID().toString(), addBreadcrumbs(it.value())))
            } catch(e: Exception) {
                logger.warn("We failed again")
                val attempts = it.headers()?.lastHeader("X-Failed-Attempts")?.value()?.let { attempt -> String(attempt).toInt() } ?: 0
                if (attempts == 10) {
                    producer.send(ProducerRecord(TOPIC_MULTIPLE_FAILING_SERVICE_CALLS, 0, it.key(), it.value(), listOf(RecordHeader("X-Failed-Attempts", "$attempts".toByteArray()))))
                }
                producer.send(ProducerRecord(TOPIC_RETRY_OPPGAVE_OPPRETTELSE, 0, it.key(), it.value(), listOf(RecordHeader("X-Failed-Attempts", "${attempts + 1}".toByteArray()))))
            }
        }
    }
}

private fun addBreadcrumbs(packet: Packet): Packet = Packet(
        breadcrumbs = packet.breadcrumbs + Breadcrumb("btn-brukermelding-oppgave"),
        timestamp = packet.timestamp,
        message = packet.message
)

private fun getConsumerConfig(): Properties {
    val config = Configuration()
    val props = Properties()

    props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = config.serverUrl
    props[ConsumerConfig.GROUP_ID_CONFIG] = config.clientId
    props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.name
    props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = PacketDeserializer::class.java.name
    props[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"

    return props
}

private fun getProducerConfig(): Properties {
    val config = Configuration()
    val props = Properties()

    props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = config.serverUrl
    props[ProducerConfig.CLIENT_ID_CONFIG] = config.clientId
    props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
    props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] =  KafkaJsonSerializer::class.java

    return props
}