package aiss.DailyMotionMiner.service;

import aiss.DailyMotionMiner.mapper.VideoMapper;
import aiss.DailyMotionMiner.model.dailymotion.Video;
import aiss.DailyMotionMiner.model.dailymotion.VideoList;
import aiss.DailyMotionMiner.model.videominer.VMCaption;
import aiss.DailyMotionMiner.model.videominer.VMVideo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;


@Service
public class VideoService {
    @Autowired
    RestTemplate restTemplate;
    @Autowired
    UserService userService;
    @Autowired
    DailymotionCaptionService captionService;
    String baseUri = "https://api.dailymotion.com";

    //GET https://api.dailymotion.com/video/{videoId}
    public Video getVideoFromId(String id){
        String uri = baseUri+"/video"+"/"+id+"?fields=id,title,description,created_time,tags,owner";
        try{
            return restTemplate.getForObject(uri, Video.class);

        } catch ( HttpClientErrorException e){
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Video not found: " + e.getMessage()
            );
        } catch ( HttpServerErrorException e){
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Dailymotion server error: " + e.getMessage()
            );
        } catch (ResponseStatusException e) {
            throw e;
        } catch ( Exception e){
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error: " + e.getMessage()
            );
        }
    }

    public VMVideo getVMVideoFromId(String id) {
        Video dmVideo = getVideoFromId(id);
        if (dmVideo == null) {
            return null;
        }

        VMVideo vmVideo = VideoMapper.toVMVideo(dmVideo);
        if (dmVideo.getUser() != null) {
            vmVideo.setUser(userService.getUserFromVideo(dmVideo.getUser()));
        }
        vmVideo.setCaptions(getCaptionsFromVideo(id));
        return vmVideo;
    }
    //GET  https://api.dailymotion.com/user/{userId}/videos

    public List<VMVideo> getVideosFromChannel(String userId, int maxVideos, int maxPages){
        List<VMVideo> vmVideos = new ArrayList<>();
        try{
            for (int page = 1; page <= maxPages; page++){
                String uri = baseUri + "/user/" + userId + "/videos?limit=" + maxVideos
                        + "&page=" + page
                        + "&fields=id,title,description,created_time,tags";

                VideoList dmVideos = restTemplate.getForObject(uri, VideoList.class);

                if(dmVideos != null && dmVideos.getList() != null) {
                    for (Video dmVideo : dmVideos.getList()) {
                        VMVideo vmVideo = VideoMapper.toVMVideo(dmVideo);
                        vmVideo.setUser(userService.getUserFromVideo(userId));
                        vmVideo.setCaptions(getCaptionsFromVideo(dmVideo.getId()));

                        vmVideos.add(vmVideo);
                    }
                }
            }
        } catch (HttpClientErrorException e){
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Videos not found: " + e.getMessage()
            );
        } catch (HttpServerErrorException e){
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Dailymotion server error: " + e.getMessage()
            );
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e){
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error: " + e.getMessage()
            );
        }
        return vmVideos;
    }

    public List<VMCaption> getCaptionsFromVideo(String videoId) {
        return captionService.getCaptions(videoId);
    }
}
