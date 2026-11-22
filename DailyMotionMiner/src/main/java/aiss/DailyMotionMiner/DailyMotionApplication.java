package aiss.DailyMotionMiner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

@SpringBootApplication
public class DailyMotionApplication {

	public static void main(String[] args) {
		SpringApplication.run(DailyMotionApplication.class, args);
	}
	@Bean
	@Primary
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

    @Bean
    public RestTemplate dailymotionOAuthRestTemplate() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return new RestTemplate(requestFactory);
    }
}
