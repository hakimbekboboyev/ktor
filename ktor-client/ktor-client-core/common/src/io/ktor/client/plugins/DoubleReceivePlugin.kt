/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins

import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

private val SAVED_BODY = AttributeKey<ByteArray>("DoubleReceiveBody")
private val SKIP_SAVE_BODY = AttributeKey<Unit>("DoubleReceiveIgnore")

/**
 * Configuration for [SaveBodyPlugin]
 */
public class SaveBodyPluginConfig {
    /**
     * Disables the plugin for all request.
     *
     * If you need to disable it only for the specific request, please use [HttpRequestBuilder.skipSavingBody]:
     * ```kotlin
     * client.get("http://myurl.com") {
     *     skipSavingBody()
     * }
     * ```
     */
    public var disabled: Boolean = false
}

/**
 * [SaveBodyPlugin] saving the whole body in memory, so it can be received multiple times.
 *
 * It may be useful to prevent saving body in case of big size or streaming. To do so use [HttpRequestBuilder.skipSavingBody]:
 * ```kotlin
 * client.get("http://myurl.com") {
 *     skipSavingBody()
 * }
 * ```
 *
 * The plugin is installed by default, if you need to disable it use:
 * ```kotlin
 * val client = HttpClient {
 *     install(SaveBodyPlugin) {
 *         disabled = true
 *     }
 * }
 * ```
 */
public val SaveBodyPlugin: ClientPlugin<SaveBodyPluginConfig> = createClientPlugin(
    "DoubleReceivePlugin", ::SaveBodyPluginConfig
) {

    client.responsePipeline.intercept(HttpResponsePipeline.Receive) {
        if (this@createClientPlugin.pluginConfig.disabled) return@intercept
        if (context.attributes.contains(SKIP_SAVE_BODY)) return@intercept

        val response = it.response
        if (response !is ByteReadChannel) return@intercept

        val content = if (context.response.isSaved()) {
            context.attributes[SAVED_BODY]
        } else {
            val data = response.readRemaining().readBytes()
            context.attributes.put(SAVED_BODY, data)
            data
        }

        val body = ByteReadChannel(content)
        proceedWith(it.copy(response = body))
    }
}

/**
 * Prevent saving response body in memory for the specific request.
 *
 * To disable the plugin for all requests use [SaveBodyPluginConfig.disabled] property:
 * ```kotlin
 * val client = HttpClient {
 *     install(SaveBodyPlugin) {
 *         disabled = true
 *     }
 * }
 * ```
 */
public fun HttpRequestBuilder.skipSavingBody() {
    attributes.put(SKIP_SAVE_BODY, Unit)
}

/**
 * Check if the [HttpResponse] body is saved in memory.
 */
public fun HttpResponse.isSaved(): Boolean {
    return call.attributes.contains(SAVED_BODY)
}

