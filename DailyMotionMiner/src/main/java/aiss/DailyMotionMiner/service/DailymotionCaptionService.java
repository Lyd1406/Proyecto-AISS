package aiss.DailyMotionMiner.service;

import aiss.DailyMotionMiner.mapper.CaptionMapper;
import aiss.DailyMotionMiner.model.dailymotion.Captions;
import aiss.DailyMotionMiner.model.videominer.VMCaption;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class DailymotionCaptionService {
    private static final String PARTNER_API = "https://partner.api.dailymotion.com";
    private static final String TOKEN_URL = PARTNER_API + "/oauth/v1/token";
    private static final String SUBTITLES_URL = PARTNER_API + "/rest/video/{videoId}/subtitles?fields=id,url,language";

    private final RestTemplate restTemplate;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private AccessToken cachedToken;

    public DailymotionCaptionService(
            @Qualifier("dailymotionOAuthRestTemplate") RestTemplate restTemplate,
            @Value("${dailymotionminer.oauth.client-id}") String clientId,
            @Value("${dailymotionminer.oauth.client-secret}") String clientSecret,
            @Value("${dailymotionminer.oauth.scope}") String scope) {
        this.restTemplate = restTemplate;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
    }

    public List<VMCaption> getCaptions(String videoId) {
        AccessToken token = getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token.value());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<Captions> response = restTemplate.exchange(
                    SUBTITLES_URL, HttpMethod.GET, new HttpEntity<>(headers), Captions.class, videoId);
            Captions captions = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || captions == null || captions.getList() == null) {
                throw new DailymotionCaptionException(HttpStatus.BAD_GATEWAY, "INVALID_CAPTIONS_RESPONSE",
                        "Dailymotion no devolvió una lista de subtítulos válida.", response.getStatusCode().value());
            }
            return captions.getList().stream().map(CaptionMapper::toVMCaption).toList();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                invalidate(token);
            }
            throw providerError(e, false);
        } catch (RestClientException e) {
            throw unavailable(false);
        }
    }

    private synchronized AccessToken getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(cachedToken.refreshAt())) {
            return cachedToken;
        }
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new DailymotionCaptionException(HttpStatus.SERVICE_UNAVAILABLE, "OAUTH_NOT_CONFIGURED",
                    "Configura DAILYMOTION_CLIENT_ID y DAILYMOTION_CLIENT_SECRET en el servidor.", null);
        }

        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        if (!scope.isBlank()) {
            form.add("scope", scope);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        Instant requestedAt = Instant.now();

        try {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                    TOKEN_URL, new HttpEntity<>(form, headers), TokenResponse.class);
            TokenResponse body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null
                    || body.accessToken() == null || body.accessToken().isBlank()
                    || !"Bearer".equalsIgnoreCase(body.tokenType()) || body.expiresIn() <= 0) {
                throw new DailymotionCaptionException(HttpStatus.BAD_GATEWAY, "INVALID_TOKEN_RESPONSE",
                        "Dailymotion no devolvió un token Bearer válido con vencimiento.", response.getStatusCode().value());
            }
            long margin = Math.min(30, body.expiresIn() / 10);
            cachedToken = new AccessToken(body.accessToken(), requestedAt.plusSeconds(body.expiresIn() - margin));
            return cachedToken;
        } catch (RestClientResponseException e) {
            throw providerError(e, true);
        } catch (RestClientException e) {
            throw unavailable(true);
        }
    }

    private synchronized void invalidate(AccessToken rejectedToken) {
        if (cachedToken == rejectedToken) {
            cachedToken = null;
        }
    }

    private static DailymotionCaptionException providerError(RestClientResponseException e, boolean tokenRequest) {
        int status = e.getStatusCode().value();
        if (status == 429) {
            var failure = new DailymotionCaptionException(HttpStatus.SERVICE_UNAVAILABLE, "DAILYMOTION_RATE_LIMITED",
                    "Dailymotion ha limitado temporalmente las peticiones.", status);
            HttpHeaders headers = e.getResponseHeaders();
            String retryAfter = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
            if (validRetryAfter(retryAfter)) {
                failure.getHeaders().set(HttpHeaders.RETRY_AFTER, retryAfter);
            }
            return failure;
        }
        if (tokenRequest && (status == 400 || status == 401 || status == 403)) {
            return new DailymotionCaptionException(HttpStatus.BAD_GATEWAY, "OAUTH_TOKEN_REJECTED",
                    "Dailymotion rechazó la solicitud del token. Comprueba la clave y los permisos.", status);
        }
        if (!tokenRequest && status == 401) {
            return new DailymotionCaptionException(HttpStatus.BAD_GATEWAY, "CAPTIONS_UNAUTHENTICATED",
                    "Dailymotion no aceptó el token para consultar los subtítulos.", status);
        }
        if (!tokenRequest && status == 403) {
            return new DailymotionCaptionException(HttpStatus.BAD_GATEWAY, "CAPTIONS_FORBIDDEN",
                    "La clave de Dailymotion no tiene acceso a los subtítulos de este vídeo.", status);
        }
        return new DailymotionCaptionException(HttpStatus.BAD_GATEWAY, "DAILYMOTION_UPSTREAM_ERROR",
                "Dailymotion devolvió un error al " + (tokenRequest ? "solicitar el token." : "consultar los subtítulos."), status);
    }

    private static boolean validRetryAfter(String value) {
        if (value == null) {
            return false;
        }
        if (value.matches("[0-9]+")) {
            return true;
        }
        try {
            DateTimeFormatter.RFC_1123_DATE_TIME.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static DailymotionCaptionException unavailable(boolean tokenRequest) {
        return new DailymotionCaptionException(HttpStatus.BAD_GATEWAY, "DAILYMOTION_UNAVAILABLE",
                "No se pudo completar la petición a Dailymotion para "
                        + (tokenRequest ? "obtener el token." : "leer los subtítulos."), null);
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") int expiresIn) {}

    private record AccessToken(String value, Instant refreshAt) {}
}
