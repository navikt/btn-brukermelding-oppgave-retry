package no.nav.btn

import no.nav.btn.kafkaservices.RiverWithServiceCall
import no.nav.btn.packet.Packet

class RetryOppgaveRiver : RiverWithServiceCall(
        consumerTopics = listOf(TOPIC_RETRY_OPPGAVE_OPPRETTELSE),
        onSuccessfullCallTopic = TOPIC_MELDING_MED_OPPGAVE,
        retryOnFailedCallTopic = TOPIC_RETRY_OPPGAVE_OPPRETTELSE
) {
    override val SERVICE_APP_ID: String
        get() = "btn-brukermelding-oppgave-retry"

    override fun makeServiceCall(packet: Packet): Packet {
        makeMockServerCall()
        return packet
    }
}