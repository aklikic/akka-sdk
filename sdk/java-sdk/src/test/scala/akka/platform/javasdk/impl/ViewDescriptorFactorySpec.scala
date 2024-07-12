/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl

import scala.jdk.CollectionConverters.CollectionHasAsScala

import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import com.google.protobuf.timestamp.Timestamp
import kalix.JwtMethodOptions.JwtMethodMode
import kalix.JwtServiceOptions.JwtServiceMode
import com.google.protobuf.{ Any => JavaPbAny }
import akka.platform.spring.testmodels.subscriptions.PubSubTestModels.EventStreamSubscriptionView
import akka.platform.spring.testmodels.subscriptions.PubSubTestModels.SubscribeOnTypeToEventSourcedEvents
import akka.platform.spring.testmodels.view.ViewTestModels.MultiTableViewValidation
import akka.platform.spring.testmodels.view.ViewTestModels.MultiTableViewWithDuplicatedESSubscriptions
import akka.platform.spring.testmodels.view.ViewTestModels.MultiTableViewWithDuplicatedVESubscriptions
import akka.platform.spring.testmodels.view.ViewTestModels.MultiTableViewWithEmptyViewId
import akka.platform.spring.testmodels.view.ViewTestModels.MultiTableViewWithJoinQuery
import akka.platform.spring.testmodels.view.ViewTestModels.MultiTableViewWithMultipleQueries
import akka.platform.spring.testmodels.view.ViewTestModels.MultiTableViewWithTableName
import akka.platform.spring.testmodels.view.ViewTestModels.MultiTableViewWithViewIdInInnerView
import akka.platform.spring.testmodels.view.ViewTestModels.MultiTableViewWithoutQuery
import akka.platform.spring.testmodels.view.ViewTestModels.MultiTableViewWithoutViewId
import akka.platform.spring.testmodels.view.ViewTestModels.SubscribeToEventSourcedEvents
import akka.platform.spring.testmodels.view.ViewTestModels.SubscribeToEventSourcedWithMissingHandler
import akka.platform.spring.testmodels.view.ViewTestModels.SubscribeToSealedEventSourcedEvents
import akka.platform.spring.testmodels.view.ViewTestModels.TimeTrackerView
import akka.platform.spring.testmodels.view.ViewTestModels.TopicSubscriptionView
import akka.platform.spring.testmodels.view.ViewTestModels.TopicTypeLevelSubscriptionView
import akka.platform.spring.testmodels.view.ViewTestModels.TransformedUserView
import akka.platform.spring.testmodels.view.ViewTestModels.TransformedUserViewWithDeletes
import akka.platform.spring.testmodels.view.ViewTestModels.TransformedUserViewWithMethodLevelJWT
import akka.platform.spring.testmodels.view.ViewTestModels.TransformedViewWithoutSubscriptionOnMethodLevel
import akka.platform.spring.testmodels.view.ViewTestModels.TypeLevelSubscribeToEventSourcedEventsWithMissingHandler
import akka.platform.spring.testmodels.view.ViewTestModels.UserByEmailWithCollectionReturn
import akka.platform.spring.testmodels.view.ViewTestModels.ViewDuplicatedESSubscriptions
import akka.platform.spring.testmodels.view.ViewTestModels.ViewDuplicatedHandleDeletesAnnotations
import akka.platform.spring.testmodels.view.ViewTestModels.ViewDuplicatedVESubscriptions
import akka.platform.spring.testmodels.view.ViewTestModels.ViewHandleDeletesWithParam
import akka.platform.spring.testmodels.view.ViewTestModels.ViewWithEmptyTableAnnotation
import akka.platform.spring.testmodels.view.ViewTestModels.ViewWithEmptyViewIdAnnotation
import akka.platform.spring.testmodels.view.ViewTestModels.ViewWithHandleDeletesFalseOnMethodLevel
import akka.platform.spring.testmodels.view.ViewTestModels.ViewWithMethodLevelAcl
import akka.platform.spring.testmodels.view.ViewTestModels.ViewWithNoQuery
import akka.platform.spring.testmodels.view.ViewTestModels.ViewWithServiceLevelAcl
import akka.platform.spring.testmodels.view.ViewTestModels.ViewWithServiceLevelJWT
import akka.platform.spring.testmodels.view.ViewTestModels.ViewWithSubscriptionsInMixedLevels
import akka.platform.spring.testmodels.view.ViewTestModels.ViewWithSubscriptionsInMixedLevelsHandleDelete
import akka.platform.spring.testmodels.view.ViewTestModels.ViewWithTwoQueries
import akka.platform.spring.testmodels.view.ViewTestModels.ViewWithoutSubscriptionButWithHandleDelete
import akka.platform.spring.testmodels.view.ViewTestModels.ViewWithoutTableAnnotation
import akka.platform.spring.testmodels.view.ViewTestModels.ViewWithoutViewIdAnnotation
import org.scalatest.wordspec.AnyWordSpec

class ViewDescriptorFactorySpec extends AnyWordSpec with ComponentDescriptorSuite {

  "View descriptor factory" should {

    "validate a View must be declared as public" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[NotPublicComponents.NotPublicView]).failIfInvalid
      }.getMessage should include("NotPublicView is not marked with `public` modifier. Components must be public.")
    }

    "generate ACL annotations at service level" in {
      assertDescriptor[ViewWithServiceLevelAcl] { desc =>
        val extension = desc.serviceDescriptor.getOptions.getExtension(kalix.Annotations.service)
        val service = extension.getAcl.getAllow(0).getService
        service shouldBe "test"
      }
    }

    "generate ACL annotations at method level" in {
      assertDescriptor[ViewWithMethodLevelAcl] { desc =>
        val extension = findKalixMethodOptions(desc, "GetEmployeeByEmail")
        val service = extension.getAcl.getAllow(0).getService
        service shouldBe "test"
      }
    }

    "generate query with collection return type" in {
      assertDescriptor[UserByEmailWithCollectionReturn] { desc =>
        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetUser")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * AS users FROM users_view WHERE name = :name"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "UserCollection"

        val streamUpdates = queryMethodOptions.getView.getQuery.getStreamUpdates
        streamUpdates shouldBe false
      }
    }

  }

  "View descriptor factory (for Key Value Entity)" should {

    "not allow View without Table annotation" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithoutTableAnnotation]).failIfInvalid
      }.getMessage should include("A View should be annotated with @Table.")
    }

    "not allow View with empty Table name" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithEmptyTableAnnotation]).failIfInvalid
      }.getMessage should include("@Table name is empty, must be a non-empty string.")
    }

    "not allow View without ViewId annotation" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithoutViewIdAnnotation]).failIfInvalid
      }.getMessage should include("A View should be annotated with @ViewId.")
    }

    "not allow View with empty ViewId" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithEmptyViewIdAnnotation]).failIfInvalid
      }.getMessage should include("@ViewId name is empty, must be a non-empty string.")
    }

    "not allow @Consume annotations in mixed levels" in {
      // it should be annotated either on type or on method level
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithSubscriptionsInMixedLevels]).failIfInvalid
      }.getMessage should include("You cannot use @Consume.FromKeyValueEntity annotation in both methods and class.")
    }

    "not allow @Consume annotations on type level with transformation" in {
      // it should be annotated either on type or on method level
      intercept[InvalidComponentException] {
        Validations.validate(classOf[TransformedViewWithoutSubscriptionOnMethodLevel]).failIfInvalid
      }.getMessage should include("and move the @Consume.FromKeyValueEntity to it")
    }

    "not allow method level handle deletes with type level subscription" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithSubscriptionsInMixedLevelsHandleDelete]).failIfInvalid
      }.getMessage should include("You cannot use @Consume.FromKeyValueEntity annotation in both methods and class.")
    }

    "not allow method level handle deletes without method level subscription" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithoutSubscriptionButWithHandleDelete]).failIfInvalid
      }.getMessage should include("Method annotated with handleDeletes=true has no matching update method.")
    }

    "not allow duplicated handle deletes methods" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewDuplicatedHandleDeletesAnnotations]).failIfInvalid
      }.getMessage should include(
        "Multiple methods annotated with @Consume.FromKeyValueEntity(handleDeletes=true) is not allowed.")
    }

    "not allow handle deletes method with param" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewHandleDeletesWithParam]).failIfInvalid
      }.getMessage should include(
        "Method annotated with '@Consume.FromKeyValueEntity' and handleDeletes=true must not have parameters.")
    }

    "not allow handle deletes false on method level" in {
      // on method level only true is acceptable
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithHandleDeletesFalseOnMethodLevel]).failIfInvalid
      }.getMessage should include(
        "Subscription method must have exactly one parameter, unless it's marked as handleDeletes.")
    }

    "not allow duplicated subscriptions methods" in {
      // it should be annotated either on type or on method level
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewDuplicatedVESubscriptions]).failIfInvalid
      }.getMessage should include(
        "Ambiguous handlers for akka.platform.spring.testmodels.keyvalueentity.User, methods: [onChange, onChange2] consume the same type.")
    }

    "generate proto for a View with explicit update method" in {
      assertDescriptor[TransformedUserView] { desc =>

        val methodOptions = this.findKalixMethodOptions(desc, "OnChange")
        val entityType = methodOptions.getEventing.getIn.getValueEntity
        val handleDeletes = methodOptions.getEventing.getIn.getHandleDeletes
        entityType shouldBe "user"
        handleDeletes shouldBe false

        methodOptions.getView.getUpdate.getTable shouldBe "users_view"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "TransformedUser"

        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetUser")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * FROM users_view WHERE email = :email"
        queryMethodOptions.getView.getJsonSchema.getJsonBodyInputField shouldBe "json_body"
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe "ByEmail"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "TransformedUser"

        val tableMessageDescriptor = desc.fileDescriptor.findMessageTypeByName("TransformedUser")
        tableMessageDescriptor should not be null

        val rule = findHttpRule(desc, "GetUser")
        rule.getPost shouldBe "/akka/v1.0/view/users_view/getUser"
      }

    }

    "convert Interval fields to proto Timestamp" in {
      assertDescriptor[TimeTrackerView] { desc =>

        val timerStateMsg = desc.fileDescriptor.findMessageTypeByName("TimerState")
        val createdTimeField = timerStateMsg.findFieldByName("createdTime")
        createdTimeField.getMessageType shouldBe Timestamp.javaDescriptor

        val timerEntry = desc.fileDescriptor.findMessageTypeByName("TimerEntry")
        val startedField = timerEntry.findFieldByName("started")
        startedField.getMessageType shouldBe Timestamp.javaDescriptor

        val stoppedField = timerEntry.findFieldByName("stopped")
        stoppedField.getMessageType shouldBe Timestamp.javaDescriptor
      }
    }

    "generate proto for a View with delete handler" in {
      assertDescriptor[TransformedUserViewWithDeletes] { desc =>

        val methodOptions = this.findKalixMethodOptions(desc, "OnChange")
        val in = methodOptions.getEventing.getIn
        in.getValueEntity shouldBe "user"
        in.getHandleDeletes shouldBe false

        val deleteMethodOptions = this.findKalixMethodOptions(desc, "OnDelete")
        val deleteIn = deleteMethodOptions.getEventing.getIn
        deleteIn.getValueEntity shouldBe "user"
        deleteIn.getHandleDeletes shouldBe true
      }
    }

    "generate proto for a View with explicit update method and method level JWT annotation" in {
      assertDescriptor[TransformedUserViewWithMethodLevelJWT] { desc =>

        val methodOptions = this.findKalixMethodOptions(desc, "OnChange")
        val entityType = methodOptions.getEventing.getIn.getValueEntity
        entityType shouldBe "user"

        methodOptions.getView.getUpdate.getTable shouldBe "users_view"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "TransformedUser"

        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetUser")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * FROM users_view WHERE email = :email"
        queryMethodOptions.getView.getJsonSchema.getJsonBodyInputField shouldBe "json_body"
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe "ByEmail"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "TransformedUser"

        val tableMessageDescriptor = desc.fileDescriptor.findMessageTypeByName("TransformedUser")
        tableMessageDescriptor should not be null

        val rule = findHttpRule(desc, "GetUser")
        rule.getPost shouldBe "/akka/v1.0/view/users_view/getUser"

        val method = desc.commandHandlers("GetUser")
        val jwtOption = findKalixMethodOptions(desc, method.grpcMethodName).getJwt
        jwtOption.getBearerTokenIssuer(0) shouldBe "a"
        jwtOption.getBearerTokenIssuer(1) shouldBe "b"
        jwtOption.getValidate(0) shouldBe JwtMethodMode.BEARER_TOKEN
        assertRequestFieldJavaType(method, "json_body", JavaType.MESSAGE)

        val Seq(claim1, claim2) = jwtOption.getStaticClaimList.asScala.toSeq
        claim1.getClaim shouldBe "role"
        claim1.getValue(0) shouldBe "admin"
        claim2.getClaim shouldBe "aud"
        claim2.getValue(0) shouldBe "${ENV}.kalix.io"
      }
    }

    "generate proto for a View with service level JWT annotation" in {
      assertDescriptor[ViewWithServiceLevelJWT] { desc =>
        val extension = desc.serviceDescriptor.getOptions.getExtension(kalix.Annotations.service)
        val jwtOption = extension.getJwt
        jwtOption.getBearerTokenIssuer(0) shouldBe "a"
        jwtOption.getBearerTokenIssuer(1) shouldBe "b"
        jwtOption.getValidate shouldBe JwtServiceMode.BEARER_TOKEN

        val Seq(claim1, claim2) = jwtOption.getStaticClaimList.asScala.toSeq
        claim1.getClaim shouldBe "role"
        claim1.getValue(0) shouldBe "admin"
        claim2.getClaim shouldBe "aud"
        claim2.getValue(0) shouldBe "${ENV}.kalix.io"
      }
    }

    "fail if no query method found" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithNoQuery]).failIfInvalid
      }
    }

    "allow more than one query method" in {
      Validations.validate(classOf[ViewWithTwoQueries]).failIfInvalid
    }
  }

  "View descriptor factory (for Event Sourced Entity)" should {

    "generate proto for a View" in {
      assertDescriptor[SubscribeToEventSourcedEvents] { desc =>

        val methodOptions = this.findKalixMethodOptions(desc, "KalixSyntheticMethodOnESEmployee")
        methodOptions.getEventing.getIn.getEventSourcedEntity shouldBe "employee"

        methodOptions.getView.getUpdate.getTable shouldBe "employees_view"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"

        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetEmployeeByEmail")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * FROM employees_view WHERE email = :email"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"
        // not defined when query body not used
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe "ByEmail"

        val tableMessageDescriptor = desc.fileDescriptor.findMessageTypeByName("Employee")
        tableMessageDescriptor should not be null

        val rule = findHttpRule(desc, "GetEmployeeByEmail")
        rule.getPost shouldBe "/akka/v1.0/view/users_view/getEmployeeByEmail"
      }
    }

    "generate proto for a View when subscribing to sealed interface" in {
      assertDescriptor[SubscribeToSealedEventSourcedEvents] { desc =>

        val methodOptions = this.findKalixMethodOptions(desc, "KalixSyntheticMethodOnESEmployee")
        methodOptions.getEventing.getIn.getEventSourcedEntity shouldBe "employee"

        methodOptions.getView.getUpdate.getTable shouldBe "employees_view"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"

        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetEmployeeByEmail")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * FROM employees_view WHERE email = :email"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"
        // not defined when query body not used
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe "ByEmail"

        val tableMessageDescriptor = desc.fileDescriptor.findMessageTypeByName("Employee")
        tableMessageDescriptor should not be null

        val rule = findHttpRule(desc, "GetEmployeeByEmail")
        rule.getPost shouldBe "/akka/v1.0/view/users_view/getEmployeeByEmail"

        val onUpdateMethod = desc.commandHandlers("KalixSyntheticMethodOnESEmployee")
        onUpdateMethod.requestMessageDescriptor.getFullName shouldBe JavaPbAny.getDescriptor.getFullName

        val eventing = findKalixMethodOptions(desc, "KalixSyntheticMethodOnESEmployee").getEventing.getIn
        eventing.getEventSourcedEntity shouldBe "employee"

        onUpdateMethod.methodInvokers.view.mapValues(_.method.getName).toMap should
        contain only ("json.kalix.io/created" -> "handle", "json.kalix.io/old-created" -> "handle", "json.kalix.io/emailUpdated" -> "handle")
      }
    }

    "generate proto for a View with multiple methods to handle different events" in {
      assertDescriptor[SubscribeOnTypeToEventSourcedEvents] { desc =>

        val serviceOptions = findKalixServiceOptions(desc)
        val eveningIn = serviceOptions.getEventing.getIn
        eveningIn.getEventSourcedEntity shouldBe "employee"

        val methodOptions = this.findKalixMethodOptions(desc, "KalixSyntheticMethodOnESEmployee")
        methodOptions.getView.getUpdate.getTable shouldBe "employee_table"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"
        methodOptions.getEventing.getIn.getIgnore shouldBe false // we don't set the property so the proxy won't ignore. Ignore is only internal to the SDK
      }
    }

    "validate missing handlers for method level subscription" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[SubscribeToEventSourcedWithMissingHandler]).failIfInvalid
      }.getMessage shouldBe
      "On 'akka.platform.spring.testmodels.view.ViewTestModels$SubscribeToEventSourcedWithMissingHandler': missing an event handler for 'akka.platform.spring.testmodels.eventsourcedentity.EmployeeEvent$EmployeeEmailUpdated'."
    }

    "validate missing handlers for type level subscription" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[TypeLevelSubscribeToEventSourcedEventsWithMissingHandler]).failIfInvalid
      }.getMessage shouldBe
      "On 'akka.platform.spring.testmodels.view.ViewTestModels$TypeLevelSubscribeToEventSourcedEventsWithMissingHandler': missing an event handler for 'akka.platform.spring.testmodels.eventsourcedentity.EmployeeEvent$EmployeeEmailUpdated'."
    }

    "not allow duplicated ES subscriptions methods" in {
      // it should be annotated either on type or on method level
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewDuplicatedESSubscriptions]).failIfInvalid
      }.getMessage should include(
        "Ambiguous handlers for akka.platform.spring.testmodels.keyvalueentity.User, methods: [onChange, onChange2] consume the same type.")
    }
  }

  "View descriptor factory (for multi-table views)" should {

    "not allow ViewTable without Table annotation" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MultiTableViewValidation.ViewTableWithoutTableAnnotation]).failIfInvalid
      }.getMessage should include("A View should be annotated with @Table.")
    }

    "not allow ViewTable with empty Table name" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MultiTableViewValidation.ViewTableWithEmptyTableAnnotation]).failIfInvalid
      }.getMessage should include("@Table name is empty, must be a non-empty string.")
    }

    "not allow MultiTable View without ViewId annotation" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MultiTableViewWithoutViewId]).failIfInvalid
      }.getMessage should include("A View should be annotated with @ViewId.")
    }

    "not allow MultiTable View with empty ViewId" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MultiTableViewWithEmptyViewId]).failIfInvalid
      }.getMessage should include("@ViewId name is empty, must be a non-empty string.")
    }

    "not allow MultiTable View with inner views with ViewId annotation" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MultiTableViewWithViewIdInInnerView]).failIfInvalid
      }.getMessage should include("A nested View should not be annotated with @ViewId.")
    }

    "not allow MultiTable View with Table annotation" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MultiTableViewWithTableName]).failIfInvalid
      }.getMessage should include("A multi-table View should not be annotated with @Table.")
    }

    "not allow @Consume annotations in mixed levels on a ViewTable" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MultiTableViewValidation.ViewTableWithMixedLevelSubscriptions]).failIfInvalid
      }.getMessage should include("You cannot use @Consume.FromKeyValueEntity annotation in both methods and class.")
    }

    "fail if no query method found" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MultiTableViewWithoutQuery]).failIfInvalid
      }
    }

    "allow more than one query method in multi table view" in {
      Validations.validate(classOf[MultiTableViewWithMultipleQueries]).failIfInvalid
    }

    "not allow duplicated VE subscriptions methods in multi table view" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MultiTableViewWithDuplicatedVESubscriptions]).failIfInvalid
      }.getMessage should include(
        "Ambiguous handlers for akka.platform.spring.testmodels.keyvalueentity.CounterState, methods: [onEvent, onEvent2] consume the same type.")
    }

    "not allow duplicated ES subscriptions methods in multi table view" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MultiTableViewWithDuplicatedESSubscriptions]).failIfInvalid
      }.getMessage should include(
        "Ambiguous handlers for akka.platform.spring.testmodels.keyvalueentity.CounterState, methods: [onEvent, onEvent2] consume the same type.")
    }

    "generate proto for multi-table view with join query" in {
      assertDescriptor[MultiTableViewWithJoinQuery] { desc =>
        val queryMethodOptions = findKalixMethodOptions(desc, "Get")
        queryMethodOptions.getView.getQuery.getQuery should be("""|SELECT employees.*, counters.* as counters
            |FROM employees
            |JOIN assigned ON assigned.assigneeId = employees.email
            |JOIN counters ON assigned.counterId = counters.id
            |WHERE employees.email = :email
            |""".stripMargin)
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "EmployeeCounters"
        // not defined when query body not used
//        queryMethodOptions.getView.getJsonSchema.getJsonBodyInputField shouldBe ""
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe "ByEmail"

        val queryHttpRule = findHttpRule(desc, "Get")
        queryHttpRule.getPost shouldBe "/akka/v1.0/view/multi-table-view-with-join-query/get"

        val employeeCountersMessage = desc.fileDescriptor.findMessageTypeByName("EmployeeCounters")
        employeeCountersMessage should not be null
        val firstNameField = employeeCountersMessage.findFieldByName("firstName")
        firstNameField should not be null
        firstNameField.getType shouldBe FieldDescriptor.Type.STRING
        val lastNameField = employeeCountersMessage.findFieldByName("lastName")
        lastNameField should not be null
        lastNameField.getType shouldBe FieldDescriptor.Type.STRING
        val emailField = employeeCountersMessage.findFieldByName("email")
        emailField should not be null
        emailField.getType shouldBe FieldDescriptor.Type.STRING
        val countersField = employeeCountersMessage.findFieldByName("counters")
        countersField should not be null
        countersField.getMessageType.getName shouldBe "CounterState"
        countersField.isRepeated shouldBe true

        val employeeOnEventOptions = findKalixMethodOptions(desc, "KalixSyntheticMethodOnESEmployee")
        employeeOnEventOptions.getEventing.getIn.getEventSourcedEntity shouldBe "employee"
        employeeOnEventOptions.getView.getUpdate.getTable shouldBe "employees"
        employeeOnEventOptions.getView.getUpdate.getTransformUpdates shouldBe true
        employeeOnEventOptions.getView.getJsonSchema.getOutput shouldBe "Employee"

        val employeeMessage = desc.fileDescriptor.findMessageTypeByName("Employee")
        employeeMessage should not be null
        val employeeFirstNameField = employeeMessage.findFieldByName("firstName")
        employeeFirstNameField should not be null
        employeeFirstNameField.getType shouldBe FieldDescriptor.Type.STRING
        val employeeLastNameField = employeeMessage.findFieldByName("lastName")
        employeeLastNameField should not be null
        employeeLastNameField.getType shouldBe FieldDescriptor.Type.STRING
        val employeeEmailField = employeeMessage.findFieldByName("email")
        employeeEmailField should not be null
        employeeEmailField.getType shouldBe FieldDescriptor.Type.STRING

        val counterOnChangeOptions = findKalixMethodOptions(desc, "OnChange1")
        counterOnChangeOptions.getEventing.getIn.getValueEntity shouldBe "ve-counter"
        counterOnChangeOptions.getView.getUpdate.getTable shouldBe "counters"
        counterOnChangeOptions.getView.getUpdate.getTransformUpdates shouldBe false
        counterOnChangeOptions.getView.getJsonSchema.getOutput shouldBe "CounterState"

        val counterStateMessage = desc.fileDescriptor.findMessageTypeByName("CounterState")
        counterStateMessage should not be null
        val counterStateIdField = counterStateMessage.findFieldByName("id")
        counterStateIdField should not be null
        counterStateIdField.getType shouldBe FieldDescriptor.Type.STRING
        val counterStateValueField = counterStateMessage.findFieldByName("value")
        counterStateValueField should not be null
        counterStateValueField.getType shouldBe FieldDescriptor.Type.INT32

        val assignedCounterOnChangeOptions = findKalixMethodOptions(desc, "OnChange")
        assignedCounterOnChangeOptions.getEventing.getIn.getValueEntity shouldBe "assigned-counter"
        assignedCounterOnChangeOptions.getView.getUpdate.getTable shouldBe "assigned"
        assignedCounterOnChangeOptions.getView.getUpdate.getTransformUpdates shouldBe false
        assignedCounterOnChangeOptions.getView.getJsonSchema.getOutput shouldBe "AssignedCounterState"

        val assignedCounterStateMessage = desc.fileDescriptor.findMessageTypeByName("AssignedCounterState")
        assignedCounterStateMessage should not be null
        val counterIdField = assignedCounterStateMessage.findFieldByName("counterId")
        counterIdField should not be null
        counterIdField.getType shouldBe FieldDescriptor.Type.STRING
        val assigneeIdField = assignedCounterStateMessage.findFieldByName("assigneeId")
        assigneeIdField should not be null
        assigneeIdField.getType shouldBe FieldDescriptor.Type.STRING
      }
    }
  }

  "View descriptor factory (for Stream)" should {
    "generate mappings for service to service subscription " in {
      assertDescriptor[EventStreamSubscriptionView] { desc =>

        val serviceOptions = findKalixServiceOptions(desc)
        val eventingInDirect = serviceOptions.getEventing.getIn.getDirect
        eventingInDirect.getService shouldBe "employee_service"
        eventingInDirect.getEventStreamId shouldBe "employee_events"

        val methodOptions = this.findKalixMethodOptions(desc, "KalixSyntheticMethodOnStreamEmployeeevents")

        methodOptions.hasEventing shouldBe false
        methodOptions.getView.getUpdate.getTable shouldBe "employee_table"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"

      }
    }
  }

  "View descriptor factory (for Topic)" should {
    "generate mappings for topic type level subscription " in {
      assertDescriptor[TopicTypeLevelSubscriptionView] { desc =>

        val serviceOptions = findKalixServiceOptions(desc)

        val eventingInTopic = serviceOptions.getEventing.getIn
        eventingInTopic.getTopic shouldBe "source"
        eventingInTopic.getConsumerGroup shouldBe "cg"

        val methodOptions = this.findKalixMethodOptions(desc, "KalixSyntheticMethodOnTopicSource")

        methodOptions.getView.getUpdate.getTable shouldBe "employee_table"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"
      }
    }

    "generate mappings for topic subscription " in {
      assertDescriptor[TopicSubscriptionView] { desc =>

        val methodOptions = this.findKalixMethodOptions(desc, "KalixSyntheticMethodOnTopicSource")

        val eventingInTopic = methodOptions.getEventing.getIn
        eventingInTopic.getTopic shouldBe "source"
        eventingInTopic.getConsumerGroup shouldBe "cg"

        methodOptions.getView.getUpdate.getTable shouldBe "employee_table"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"
      }
    }
  }
}
