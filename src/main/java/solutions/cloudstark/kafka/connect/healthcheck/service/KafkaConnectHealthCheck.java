/*
 *    Copyright 2020 SMB GmbH
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package solutions.cloudstark.kafka.connect.healthcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import javax.enterprise.context.ApplicationScoped;
import javax.validation.constraints.NotNull;
import java.net.URI;
import java.util.Objects;

@Slf4j
@Readiness
@ApplicationScoped
public class KafkaConnectHealthCheck implements HealthCheck {

    @ConfigProperty(name = "healthcheck.kafka.connect.url")
    String connectUrl;

    @ConfigProperty(name = "healthcheck.kafka.connect.workerid")
    String workerId;

    @Override
    public HealthCheckResponse call() {
        log.debug(connectUrl);
        log.debug(workerId);
        HealthCheckResponseBuilder responseBuilder = HealthCheckResponse
                .named("Kafka Connect health check");
        try {
            KafkaConnectService kafkaConnectService = RestClientBuilder
                    .newBuilder()
                    .baseUri(URI.create(connectUrl))
                    .build(KafkaConnectService.class);

            responseBuilder.up();
            kafkaConnectService
                    .getConnectors().stream()
                    .map(kafkaConnectService::getConnectorStatus)
                    .filter(this::sameWorkerId)
                    .forEach(cs -> {
                        responseBuilder.withData("connector",
                                String.format("name: %s, type: %s, state: %s",
                                        cs.getName(),
                                        cs.getType(),
                                        cs.getConnector().getState()));
                        if ("FAILED".equals(cs.getConnector().getState())) {
                            responseBuilder.down();
                        }
                        cs.getTasks().stream()
                                .filter(this::sameWorkerId)
                                .forEach(t -> {
                                    responseBuilder.withData(
                                            String.format("task-%d", t.getId()),
                                            String.format("state: %s%s",
                                                    t.getState(),
                                                    t.getTrace() != null ?
                                                            ", error: " + t.getTrace().substring(0, 150) +"..." :
                                                            ""));
                                    if ("FAILED".equals(t.getState())) {
                                        responseBuilder.down();
                                    }
                                });
                    });
        } catch (Exception e) {
            responseBuilder.down().withData("error", e.getMessage());
        }
        return responseBuilder.build();
    }

    private boolean sameWorkerId(@NotNull ConnectorStatus cs) {
        return cs.getConnector().getWorkerId().equals(workerId);
    }

    private boolean sameWorkerId(@NotNull Task t) {
        return t.getWorkerId().equals(workerId);
    }
}