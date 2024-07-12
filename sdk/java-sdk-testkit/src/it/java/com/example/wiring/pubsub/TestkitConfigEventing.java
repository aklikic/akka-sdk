/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.pubsub;

import akka.platform.javasdk.testkit.KalixTestKit;

import static com.example.wiring.pubsub.PublishESToTopic.COUNTER_EVENTS_TOPIC;
import static com.example.wiring.pubsub.PublishVEToTopic.CUSTOMERS_TOPIC;
import static java.time.Duration.ofMillis;

public class TestkitConfigEventing {

  public KalixTestKit.Settings settingsMockedDestination() {
    // here only to show how to set different `Settings` in a test. See SpringSdkIntegrationTest.java
    return KalixTestKit.Settings.DEFAULT
        .withAclEnabled()
        .withAdvancedViews()
        .withWorkflowTickInterval(ofMillis(500))
        .withTopicOutgoingMessages(CUSTOMERS_TOPIC);
  }

  public KalixTestKit.Settings settingsMockedSubscription() {
    // here only to show how to set different `Settings` in a test. See SpringSdkIntegrationTest.java
    return KalixTestKit.Settings.DEFAULT
        .withAclEnabled()
        .withAdvancedViews()
        .withWorkflowTickInterval(ofMillis(500))
        .withTopicIncomingMessages(COUNTER_EVENTS_TOPIC)
        .withKeyValueEntityIncomingMessages("user");
  }
}
