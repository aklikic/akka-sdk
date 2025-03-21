package counter.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("counter-command-from-topic")
@Consume.FromTopic(value = "counter-commands", ignoreUnknown = true)
public class CounterCommandFromTopicConsumer extends Consumer {

  public record IncreaseCounter(String counterId, int value) {
  }

  public record MultiplyCounter(String counterId, int value) {
  }

  public record IgnoredEvent(String message) {
  }

  private ComponentClient componentClient;

  public CounterCommandFromTopicConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  private Logger logger = LoggerFactory.getLogger(CounterCommandFromTopicConsumer.class);

  public Effect onValueIncreased(IncreaseCounter increase) {
    logger.info("Received increase event: {}", increase.toString());
    var increaseReply =
      componentClient.forEventSourcedEntity(increase.counterId)
        .method(CounterEntity::increase)
        .invokeAsync(increase.value)
        .thenApply(__ -> Done.done());
    return effects().asyncDone(increaseReply);
  }

  public Effect onValueMultiplied(MultiplyCounter multiply) {
    logger.info("Received multiply event: {}", multiply.toString());
    var increaseReply =
      componentClient.forEventSourcedEntity(multiply.counterId)
        .method(CounterEntity::multiply)
        .invokeAsync(multiply.value)
        .thenApply(__ -> Done.done());
    return effects().asyncDone(increaseReply);
  }
}
