/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.admin.kafka.admin.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.strimzi.admin.common.data.fetchers.AdminClientWrapper;
import io.strimzi.admin.common.data.fetchers.TopicOperations;
import io.strimzi.admin.common.data.fetchers.model.Types;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.graphql.VertxDataFetcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TopicCreateHandler extends CommonHandler {
    protected static final Logger log = LogManager.getLogger(TopicCreateHandler.class);

    public static VertxDataFetcher createTopicFetcher(Map<String, Object> acConfig, Vertx vertx) {
        VertxDataFetcher<Types.Topic> dataFetcher = new VertxDataFetcher<>((environment, prom) -> {
            setOAuthToken(acConfig, environment.getContext());
            Future<AdminClientWrapper> acw = createAdminClient(vertx, acConfig);

            Types.NewTopic inputTopic = new Types.NewTopic();

            Map<String, Object> input = environment.getArgument("input");
            List<Map<String, Object>> inputConfig = (List<Map<String, Object>>) input.get("config");
            List<Types.NewTopicConfigEntry> newTopicConfigEntries = new ArrayList<>();

            inputConfig.forEach(entry -> {
                Types.NewTopicConfigEntry newTopicConfigEntry = new Types.NewTopicConfigEntry();
                newTopicConfigEntry.setKey(entry.get("key").toString());
                newTopicConfigEntry.setValue(entry.get("value").toString());
                newTopicConfigEntries.add(newTopicConfigEntry);
            });

            inputTopic.setConfig(newTopicConfigEntries);
            inputTopic.setName(input.get("name").toString());
            inputTopic.setNumPartitions(Integer.parseInt(input.get("numPartitions").toString()));
            inputTopic.setReplicationFactor(Integer.parseInt(input.get("replicationFactor").toString()));

            TopicOperations.createTopic(acw, prom, inputTopic);
        });
        return dataFetcher;
    }

    public static Handler<RoutingContext> createTopicHandler(Map<String, Object> acConfig, Vertx vertx) {
        return routingContext -> {
            setOAuthToken(acConfig, routingContext);
            Future<AdminClientWrapper> acw = createAdminClient(vertx, acConfig);

            Types.NewTopic inputTopic = new Types.NewTopic();

            Promise<Types.NewTopic> prom = Promise.promise();
            ObjectMapper mapper = new ObjectMapper();
            try {
                inputTopic = mapper.readValue(routingContext.getBody().getBytes(), Types.NewTopic.class);
            } catch (IOException e) {
                routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
                routingContext.response().end(e.getMessage());
                prom.fail(e);
            }

            TopicOperations.createTopic(acw, prom, inputTopic);

            prom.future().onComplete(res -> {
                if (res.failed()) {
                    routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
                    routingContext.response().end(res.cause().getMessage());
                } else {
                    ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
                    String json = null;
                    try {
                        json = ow.writeValueAsString(res.result());
                    } catch (JsonProcessingException e) {
                        routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
                        routingContext.response().end(e.getMessage());
                    }
                    routingContext.response().setStatusCode(HttpResponseStatus.OK.code());
                    routingContext.response().end(json);
                }
            });
        };
    }
}
