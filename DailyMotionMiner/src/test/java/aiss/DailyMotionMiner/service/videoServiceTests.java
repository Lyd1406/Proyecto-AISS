package aiss.DailyMotionMiner.service;

import aiss.DailyMotionMiner.controller.DailymotionController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class videoServiceTests {
    private static final String VIDEO_ROUTE = "/dailymotion/v1/video/xa7kbou";
    private static final String TOKEN_URL = "https://partner.api.dailymotion.com/oauth/v1/token";
    private static final String SUBTITLES_URL =
            "https://partner.api.dailymotion.com/rest/video/xa7kbou/subtitles?fields=id,url,language";
    private RestTemplate restTemplate;
    private MockRestServiceServer provider;
    private VideoService videos;
    private MockMvc api;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        provider = MockRestServiceServer.bindTo(restTemplate).build();
        videos = new VideoService();
        videos.restTemplate = restTemplate;
        videos.userService = new UserService();
        videos.userService.restTemplate = restTemplate;
        videos.captionService = new DailymotionCaptionService(restTemplate, "test-id", "a+b&c", "test-scope");

        ChannelService channels = new ChannelService();
        channels.restTemplate = restTemplate;
        channels.videoService = videos;
        DailymotionController controller = new DailymotionController();
        ReflectionTestUtils.setField(controller, "videoService", videos);
        ReflectionTestUtils.setField(controller, "channelService", channels);
        api = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void consultsPublicMetadataAndReusesTheTokenForSubtitles() throws Exception {
        expectVideo();
        expectToken("test-bearer");
        expectSubtitles("test-bearer", json("""
                {"list":[{"id":"sub1","language":"es","url":"https://example.test/es.srt"}]}
                """));
        expectVideo();
        expectSubtitles("test-bearer", json("{\"list\":[]}"));

        api.perform(get(VIDEO_ROUTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Vídeo de prueba"))
                .andExpect(jsonPath("$.captions[0].language").value("es"));
        api.perform(get(VIDEO_ROUTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captions").isEmpty());
        provider.verify();
    }

    @Test
    void rejectsAnInvalidTokenAndObtainsANewOneOnTheNextRequest() throws Exception {
        expectVideo();
        expectToken("rejected");
        expectSubtitles("rejected", withStatus(HttpStatus.UNAUTHORIZED));
        expectVideo();
        expectToken("replacement");
        expectSubtitles("replacement", json("{\"list\":[]}"));

        api.perform(get(VIDEO_ROUTE))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CAPTIONS_UNAUTHENTICATED"))
                .andExpect(jsonPath("$.upstreamStatus").value(401));
        api.perform(get(VIDEO_ROUTE)).andExpect(status().isOk());
        provider.verify();
    }

    @Test
    void reportsMissingOAuthConfigurationWithoutRequestingAToken() throws Exception {
        videos.captionService = new DailymotionCaptionService(restTemplate, "", "", "");
        expectVideo();

        api.perform(get(VIDEO_ROUTE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("OAUTH_NOT_CONFIGURED"));
        provider.verify();
    }

    @Test
    void reportsRejectedCredentialsWithoutReturningTheProvidersResponseBody() throws Exception {
        expectVideo();
        provider.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("mock-sensitive-body"));

        api.perform(get(VIDEO_ROUTE))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("OAUTH_TOKEN_REJECTED"))
                .andExpect(jsonPath("$.upstreamStatus").value(400))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertFalse(
                        result.getResponse().getContentAsString().contains("mock-sensitive-body")));
        provider.verify();
    }

    @Test
    void propagatesRetryAfterWithoutRetryingTheProvider() throws Exception {
        expectVideo();
        expectToken("test-bearer");
        expectSubtitles("test-bearer", withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "30"));

        api.perform(get(VIDEO_ROUTE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DAILYMOTION_RATE_LIMITED"))
                .andExpect(jsonPath("$.upstreamStatus").value(429))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.RETRY_AFTER, "30"));
        provider.verify();
    }

    @Test
    void doesNotImportAChannelWhenTheProviderDeniesSubtitles() throws Exception {
        provider.expect(requestTo("https://api.dailymotion.com/user/xc?fields=id,screenname,description,created_time"))
                .andRespond(json("{\"id\":\"xc\",\"screenname\":\"Canal de prueba\",\"created_time\":1700000000}"));
        provider.expect(requestTo("https://api.dailymotion.com/user/xc/videos?limit=1&page=1&fields=id,title,description,created_time,tags"))
                .andRespond(json("{\"list\":[{\"id\":\"xa7kbou\",\"title\":\"Vídeo de prueba\"}]}"));
        expectUser();
        expectToken("test-bearer");
        expectSubtitles("test-bearer", withStatus(HttpStatus.FORBIDDEN));

        api.perform(post("/dailymotion/v1/xc?maxVideos=1&maxPages=1"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CAPTIONS_FORBIDDEN"))
                .andExpect(jsonPath("$.upstreamStatus").value(403));
        provider.verify();
    }

    private void expectVideo() {
        provider.expect(requestTo("https://api.dailymotion.com/video/xa7kbou?fields=id,title,description,created_time,tags,owner"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(json("{\"id\":\"xa7kbou\",\"title\":\"Vídeo de prueba\",\"owner\":\"xc\"}"));
        expectUser();
    }

    private void expectUser() {
        provider.expect(requestTo("https://api.dailymotion.com/user/xc?fields=id,username,url,avatar_25_url"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(json("{\"id\":\"xc\",\"username\":\"autor\"}"));
    }

    private void expectToken(String token) {
        provider.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string("grant_type=client_credentials&client_id=test-id&client_secret=a%2Bb%26c&scope=test-scope"))
                .andRespond(json("""
                        {"access_token":"%s","token_type":"Bearer","expires_in":3600}
                        """.formatted(token)));
    }

    private void expectSubtitles(String token, ResponseCreator result) {
        provider.expect(requestTo(SUBTITLES_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andRespond(result);
    }

    private static ResponseCreator json(String data) {
        return withSuccess(data, MediaType.APPLICATION_JSON);
    }
}
