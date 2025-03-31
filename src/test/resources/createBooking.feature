Feature: Booking is created on /bookings
  Scenario: Client makes a POST request to create a ride booking
    When the client makes a POST request to /bookings
    Then the client receives status code 201
    And the client receives an empty response
