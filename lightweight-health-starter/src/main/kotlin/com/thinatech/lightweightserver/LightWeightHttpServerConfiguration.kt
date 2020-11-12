package com.thinatech.lightweightserver

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["lightweight.server"], havingValue = "true")
open class LightWeightHttpServerConfiguration {

    @Bean
    open fun lightWeightHttpServer(healths: List<HealthIndicator?>, objectMapper: ObjectMapper) =
            LightWeightHttpServer(healths, objectMapper)

    @Bean
    @ConditionalOnMissingBean
    open fun objectMapper() = ObjectMapper()

}
