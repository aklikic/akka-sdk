/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.testkit

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.testkit.TestKit
import akka.platform.testkit.eventsourcedentity.TestEventSourcedProtocol
import akka.platform.testkit.replicatedentity.TestReplicatedEntityProtocol
import akka.platform.testkit.keyvalueentity.TestKeyValueEntityProtocol
import com.typesafe.config.{ Config, ConfigFactory }
import akka.platform.testkit.workflow.TestWorkflowProtocol

// FIXME: should we be doing protocol-level testing in the SDK?
// Copied over from Kalix framework (parts that are used here).
final class TestProtocol(host: String, port: Int) {
  import TestProtocol._

  val context = new TestProtocolContext(host, port)

  val eventSourced = new TestEventSourcedProtocol(context)
  val valueEntity = new TestKeyValueEntityProtocol(context)
  val replicatedEntity = new TestReplicatedEntityProtocol(context)
  val workflow = new TestWorkflowProtocol(context)

  def settings: GrpcClientSettings = context.clientSettings

  def terminate(): Unit = {
    eventSourced.terminate()
    valueEntity.terminate()
    replicatedEntity.terminate()
    workflow.terminate()
  }
}

object TestProtocol {
  def apply(port: Int): TestProtocol = apply("localhost", port)
  def apply(host: String, port: Int): TestProtocol = new TestProtocol(host, port)

  final class TestProtocolContext(val host: String, val port: Int) {
    val config: Config = ConfigFactory.load(ConfigFactory.parseString(s"""
      akka.loglevel = ERROR
      akka.stdout-loglevel = ERROR
      akka.http.server {
        preview.enable-http2 = on
      }
    """))

    implicit val system: ActorSystem = ActorSystem("TestProtocol", config)

    val clientSettings: GrpcClientSettings = GrpcClientSettings.connectToServiceAt(host, port).withTls(false)

    def terminate(): Unit = TestKit.shutdownActorSystem(system)
  }
}
