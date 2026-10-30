package com.coopledger.core.outbox;

import com.coopledger.shared.TransferCompleted;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {
    @Bean
    NewTopic completedTransfers() {
        return TopicBuilder.name(TransferCompleted.TOPIC).partitions(3).replicas(1).build();
    }
}
