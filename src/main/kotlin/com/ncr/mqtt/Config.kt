package com.ncr.mqtt

import com.typesafe.config.ConfigFactory
import java.io.File

data class Config(
    val forwarders: List<ForwarderConfig>,
)

data class ForwarderConfig(
    val name: String,
    val host: String,
    val port: Int,
    val publish: List<String>,
    val subscribe: List<String>,
)

// loadConfig does what it says on the tin, loads configuration from 'forwarders.conf' in the extension home directory
fun loadConfig(configFile: File): Config {
    val conf = ConfigFactory.parseFile(configFile)

    val forwarders = conf.entrySet()
        .asSequence()
        .map { it.key }
        .mapNotNull {
            val name = it.substring(0, it.lastIndexOf('.'))
            val fwdConfig = conf.getConfig(name)

            // This isn't a config without publish and/or subscribe
            if (!fwdConfig.hasPath("publish") && !fwdConfig.hasPath("subscribe")) {
                null
            } else {
                name
            }
        }
        .toSet()
        .map {
            val fwdConfig = conf.getConfig(it)

            val host = if (fwdConfig.hasPath("host")) {
                fwdConfig.getString("host")
            } else {
                it
            }

            val port = if (fwdConfig.hasPath("port")) {
                fwdConfig.getInt("port")
            } else {
                1883
            }

            val publish = if (fwdConfig.hasPath("publish")) {
                fwdConfig.getStringList("publish")
            } else {
                emptyList()
            }

            val subscribe = if (fwdConfig.hasPath("subscribe")) {
                fwdConfig.getStringList("subscribe")
            } else {
                emptyList()
            }

            ForwarderConfig(it, host, port, publish, subscribe)
        }
        .toList()

    return Config(forwarders)
}