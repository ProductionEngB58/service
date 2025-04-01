package ro.unibuc.hello.e2e.steps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestTemplate;
import ro.unibuc.hello.dto.Greeting;
import ro.unibuc.hello.e2e.util.HeaderSetup;
import ro.unibuc.hello.e2e.util.ResponseErrorHandler;
import ro.unibuc.hello.e2e.util.ResponseResults;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

@CucumberContextConfiguration
@SpringBootTest()
public class RideBookingSteps {
    public static ResponseResults latestResponse = null;

    @Autowired
    protected RestTemplate restTemplate;

    @When("^the client makes a POST request to /bookings$")
    public void the_client_makes_a_POST_request_to_bookings() {
        executePost("http://localhost:8080/bookings");
    }

    @Then("^the client receives status code (\\d+)$")
    public void the_client_receives_status_code(int statusCode) {
        try {
            final HttpStatusCode currentStatusCode = latestResponse.getTheResponse().getStatusCode();
            assertThat("Status code is incorrect: " + latestResponse.getBody(), currentStatusCode.value(), is(statusCode));
        } catch (IOException e) {
            e.printStackTrace();
            fail("IOException occurred while retrieving the status code.");
        }
    }

    @And("^the client receives an empty response$")
    public void the_client_receives_an_empty_response() {
        assertThat("Response body should be empty", latestResponse.getBody(), is(""));
    }

    public void executePost(String url) {
        final Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        final HeaderSetup requestCallback = new HeaderSetup(headers);
        final ResponseErrorHandler errorHandler = new ResponseErrorHandler();

        restTemplate.setErrorHandler(errorHandler);
        latestResponse = restTemplate.execute(url, HttpMethod.POST, requestCallback, response -> {
            if (errorHandler.getHadError()) {
                return errorHandler.getResults();
            } else {
                return new ResponseResults(response);
            }
        });
    }
}
