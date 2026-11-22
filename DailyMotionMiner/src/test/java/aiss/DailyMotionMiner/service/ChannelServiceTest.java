package aiss.DailyMotionMiner.service;

import aiss.DailyMotionMiner.model.videominer.VMChannel;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ChannelServiceTest {
    @Test
    void getChannelById() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer provider = MockRestServiceServer.bindTo(restTemplate).build();
        provider.expect(requestTo("https://api.dailymotion.com/user/x4zjqxy?fields=id,screenname,description,created_time"))
                .andRespond(withSuccess("{\"id\":\"x4zjqxy\",\"screenname\":\"Canal de prueba\"}", MediaType.APPLICATION_JSON));
        provider.expect(requestTo("https://api.dailymotion.com/user/x4zjqxy/videos?limit=10&page=1&fields=id,title,description,created_time,tags"))
                .andRespond(withSuccess("{\"list\":[]}", MediaType.APPLICATION_JSON));

        VideoService videos = new VideoService();
        videos.restTemplate = restTemplate;
        ChannelService channels = new ChannelService();
        channels.restTemplate = restTemplate;
        channels.videoService = videos;

        VMChannel result = channels.getChannel("x4zjqxy", 10, 1);
        assertEquals("x4zjqxy", result.getId());
        assertTrue(result.getVideos().isEmpty());
        provider.verify();
    }
}
