package aiss.DailyMotionMiner.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class DailymotionCaptionException extends ResponseStatusException {
    private final HttpHeaders headers = new HttpHeaders();

    public DailymotionCaptionException(HttpStatus status, String code, String detail, Integer upstreamStatus) {
        super(status, detail);
        getBody().setTitle("Error de subtítulos de Dailymotion");
        getBody().setProperty("code", code);
        if (upstreamStatus != null) {
            getBody().setProperty("upstreamStatus", upstreamStatus);
        }
    }

    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }
}
