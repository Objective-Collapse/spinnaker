/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.notification

import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.api.events.Event
import com.netflix.spinnaker.echo.api.events.NotificationAgent
import com.netflix.spinnaker.echo.api.events.NotificationParameter
import com.netflix.spinnaker.echo.controller.NotificationController
import com.netflix.spinnaker.echo.notification.InteractiveNotificationCallbackHandler.SpinnakerService
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.RequestEntity
import org.springframework.http.ResponseEntity
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject

class NotificationControllerSpec extends Specification {
  static final String INTERACTIVE_CALLBACK_URI = "/notifications/callbacks"
  SpinnakerService spinnakerService
  NotificationService notificationService
  InteractiveNotificationService interactiveNotificationService
  InteractiveNotificationCallbackHandler interactiveNotificationCallbackHandler

  @Subject
  NotificationController notificationController

  void setup() {
    notificationService = Mock()
    interactiveNotificationService = Mock()
    spinnakerService = Mock()
    interactiveNotificationCallbackHandler = new InteractiveNotificationCallbackHandler(
      [ interactiveNotificationService ],
      [ "test": spinnakerService ],
      Mock(Environment)
    )
    notificationController = new NotificationController(
      [interactiveNotificationService, notificationService],
      interactiveNotificationCallbackHandler,
      Optional.of([new MyNotificationAgent()]),
    )
  }

  void 'creating a notification delegates to the appropriate service'() {
    given:
    Notification notification = new Notification()
    notification.notificationType = "SLACK"

    notificationService.supportsType("SLACK") >> true

    when:
    notificationController.create(notification)

    then:
    1 * notificationService.handle(notification)
  }

  void 'interactive notifications are only delegated to interactive notification services'() {
    given:
    Notification notification = new Notification()
    notification.notificationType = "SLACK"
    notification.interactiveActions = new Notification.InteractiveActions(
      callbackServiceId: "test",
      callbackMessageId: "test",
      actions: [
        new Notification.ButtonAction(
          name: "button",
          value: "test"
        )
      ]
    )

    notificationService.supportsType("SLACK") >> true
    interactiveNotificationService.supportsType("SLACK") >> true

    when:
    notificationController.create(notification)

    then:
    0 * notificationService.handle(notification)
    1 * interactiveNotificationService.handle(notification)
  }

  void 'only interactive notifications are delegated to interactive notification services'() {
    given:
    Notification nonInteractiveNotification = new Notification()
    nonInteractiveNotification.notificationType = "PAGER_DUTY"

    notificationService.supportsType("PAGER_DUTY") >> true
    interactiveNotificationService.supportsType("SLACK") >> true

    when:
    notificationController.create(nonInteractiveNotification)

    then:
    1 * notificationService.handle(nonInteractiveNotification)
    0 * interactiveNotificationService.handle(nonInteractiveNotification)
  }

  void 'an incoming callback from the notification service delegates to the appropriate service class'() {
    given:
    RequestEntity<String> request = new RequestEntity<>(
      "blah", new HttpHeaders(), HttpMethod.POST, new URI(INTERACTIVE_CALLBACK_URI))

    Notification.InteractiveActionCallback callbackObject = new Notification.InteractiveActionCallback()
    callbackObject.serviceId = "test"
    callbackObject.user = "john.doe"

    interactiveNotificationService.supportsType("SLACK") >> true
    spinnakerService.notificationCallback(*_) >> Calls.response(mockResponse())

    when:
    notificationController.processCallback("slack", request)

    then:
    1 * interactiveNotificationService.parseInteractionCallback(request) >> callbackObject
    1 * interactiveNotificationService.respondToCallback(request) >> { Optional.empty() }
  }

  void 'handling of the incoming callback throws an exception if Spinnaker service config not found'() {
    given:
    RequestEntity<String> request = new RequestEntity<>(
      "blah", new HttpHeaders(), HttpMethod.POST, new URI(INTERACTIVE_CALLBACK_URI))

    Notification.InteractiveActionCallback callbackObject = new Notification.InteractiveActionCallback()
    callbackObject.serviceId = "unknown"
    callbackObject.user = "john.doe"

    interactiveNotificationService.supportsType("SLACK") >> true
    interactiveNotificationService.parseInteractionCallback(request) >> callbackObject

    when:
    notificationController.processCallback("slack", request)

    then:
    thrown(InvalidRequestException)
  }

  void 'handling of the incoming callback returns the response generated by the corresponding service class'() {
    given:
    RequestEntity<String> request = new RequestEntity<>(
      "blah", new HttpHeaders(), HttpMethod.POST, new URI(INTERACTIVE_CALLBACK_URI))

    ResponseEntity<String> expectedResponse = new ResponseEntity(HttpStatus.OK)

    Notification.InteractiveActionCallback callbackObject = new Notification.InteractiveActionCallback()
    callbackObject.serviceId = "test"
    callbackObject.user = "john.doe"

    interactiveNotificationService.supportsType("SLACK") >> true
    spinnakerService.notificationCallback(*_) >> Calls.response(mockResponse())

    when:
    ResponseEntity<String> response = notificationController.processCallback("slack", request)

    then:
    1 * interactiveNotificationService.parseInteractionCallback(request) >> callbackObject
    1 * interactiveNotificationService.respondToCallback(request) >> { Optional.of(expectedResponse) }
    response == expectedResponse
  }

  void 'serves notification agent metadata'() {
    when:
    def response = notificationController.getNotificationTypeMetadata()

    then:
    response.size() == 1
    response[0] instanceof MyNotificationAgent
  }

  static ResponseBody mockResponse() {
    ResponseBody.create("{}", MediaType.parse("application/json"))
  }

  static class MyNotificationAgent implements NotificationAgent {
    @Override
    List<NotificationParameter> getParameters() {
      return [[
                type        : "string",
                name        : "my-parameter",
                description : "This is an extension notification parameter",
                label       : "My Parameter",
                defaultValue: "wow!"
              ] as NotificationParameter]
    }

    @Override
    String getNotificationType() {
      return "my-notification-agent"
    }

    @Override
    void sendNotifications(Map<String, Object> notificationConfig,
                           String application,
                           Event event,
                           String status) {}
  }
}
