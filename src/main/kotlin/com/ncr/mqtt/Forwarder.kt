package com.ncr.mqtt

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttClientState.CONNECTED
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE
import com.hivemq.client.mqtt.datatypes.MqttTopic
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter
import com.hivemq.client.mqtt.lifecycle.MqttClientAutoReconnect
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5RetainHandling.DO_NOT_SEND
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscription
import com.hivemq.extension.sdk.api.packets.publish.PublishPacket
import com.hivemq.extension.sdk.api.services.Services
import com.hivemq.extension.sdk.api.services.builder.Builders
import com.hivemq.extension.sdk.api.services.publish.Publish
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit.SECONDS

class Forwarder(config: ForwarderConfig) {
    private val log: Logger = LoggerFactory.getLogger("${javaClass}.${config.name}")

    private val name: String = config.name
    private val client: Mqtt5Client = MqttClient.builder()
        .useMqttVersion5()
        .identifier("mqtt-forwarder-${config.name.toLowerCase()}")
        .serverHost(config.host)
        .serverPort(config.port)
        .addConnectedListener { log.debug("MQTTClient connected") }
        .addDisconnectedListener { log.debug("MQTTClient disconnected") }
        .automaticReconnect(
            MqttClientAutoReconnect.builder()
                .initialDelay(5, SECONDS)
                .maxDelay(60, SECONDS)
                .build()
        )
        .build()
        .toAsync()

    // Filters for messages to send to target broker
    private val pubFilters: List<MqttTopicFilter> = config.publish.map { MqttTopicFilter.of(it) }

    init {
        // Connect (asynchronously)
        val ack = client.toAsync().connectWith()
            .cleanStart(true)
            .send()

        // If we have subscriptions configured, subscribe when connection completes
        if (config.subscribe.isNotEmpty()) {
            ack.thenRun {
                val subs = config.subscribe.map {
                    Mqtt5Subscription.builder()
                        .topicFilter(MqttTopicFilter.of(it))
                        .noLocal(true)
                        .retainHandling(DO_NOT_SEND)
                        .qos(AT_LEAST_ONCE)
                        .build()
                }

                log.debug("Subscribing to: {}", subs)

                client.toAsync().subscribeWith()
                    .addSubscriptions(subs)
                    .callback { pub ->
                        log.debug("Received message on: {}", pub.topic)

                        // Publish the received message via the local broker
                        Services.publishService().publish(pub.toPublish())
                            .whenComplete { _, t -> log.debug("Publish complete. (throwable={})", t) }
                    }
                    .send()
                    .thenRun { log.debug("Subscription(s) successful") }
            }
        }
    }

    fun disconnect() {
        try {
            client.toBlocking().disconnect()
        } catch (ignored: Exception) {}
    }

    fun send(p: PublishPacket) {
        if (client.state != CONNECTED) {
            // TODO: Queue messages to send when connection restored, if offline
            return
        }

        if (pubFilters.none { it.matches(MqttTopic.of(p.topic)) }) {
            log.debug("Skipping message as no topic filters match: {}", p.topic)
            return
        }

        client.toAsync().publish(p.toMessage()).thenRun {
            log.debug("Published message to: {}", p.topic)
        }
    }

    override fun toString(): String {
        return "Forwarder($name)"
    }

    private fun Mqtt5Publish.toPublish(): Publish {
        val pb = Builders.publish().topic(topic.toString())

        contentType.ifPresent { pb.contentType(it.toString()) }
        responseTopic.ifPresent { pb.responseTopic(it.toString()) }
        correlationData.ifPresent { pb.correlationData(it) }
        payload.ifPresent { pb.payload(it) }

        for (up in userProperties.asList()) {
            pb.userProperty(up.name.toString(), up.value.toString())
        }

        return pb.build()
    }

    private fun PublishPacket.toMessage(): Mqtt5Publish {
        val msg = Mqtt5Publish.builder()
            .topic(topic)
            .qos(MqttQos.fromCode(qos.qosNumber)!!)
            .retain(retain)

        // Add content if present in original message
        contentType.ifPresent { msg.contentType(it) }
        correlationData.ifPresent { msg.correlationData(it) }
        payload.ifPresent { msg.payload(it) }

        // Add all user properties
        userProperties.asList().forEach {
            msg.userProperties().add(it.name, it.value)
        }

        return msg.build()
    }
}