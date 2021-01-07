package com.ncr.mqtt

import com.hivemq.extension.sdk.api.ExtensionMain
import com.hivemq.extension.sdk.api.parameter.ExtensionStartInput
import com.hivemq.extension.sdk.api.parameter.ExtensionStartOutput
import com.hivemq.extension.sdk.api.parameter.ExtensionStopInput
import com.hivemq.extension.sdk.api.parameter.ExtensionStopOutput
import com.hivemq.extension.sdk.api.services.Services
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class ExtensionMain : ExtensionMain {
    private val log: Logger = LoggerFactory.getLogger(javaClass)
    private val targets: MutableList<Forwarder> = mutableListOf()

    override fun extensionStart(startInput: ExtensionStartInput, startOutput: ExtensionStartOutput) {
        val configFile = File(startInput.extensionInformation.extensionHomeFolder, "forwarders.conf")
        if (!configFile.exists()) {
            log.warn("Configuration file not present: {}", configFile.absolutePath)
            return
        }

        val conf = loadConfig(configFile)

        // Add (and initialize/connect) forwarders
        for (fc in conf.forwarders) {
            if (fc.publish.isEmpty() && fc.subscribe.isEmpty()) {
                log.warn("You must configure publish/subscribe topics for: {}", fc.name)
                continue
            }

            log.info("Creating forwarder for {}: {}:{}", fc.name, fc.host, fc.port)
            targets.add(Forwarder(fc))
        }

        // When a client connects our initializer will add a PublishInboundInterceptor that will forward a copy of the
        // message to any targets that are configured to forward messages (based on topic).
        Services.initializerRegistry().setClientInitializer { _, context ->
            context.addPublishInboundInterceptor { input, _ ->
                targets.forEach { it.send(input.publishPacket) }
            }
        }
    }

    override fun extensionStop(stopInput: ExtensionStopInput, stopOutput: ExtensionStopOutput) {
        targets.forEach {
            it.disconnect()
        }
    }
}
