package sandbox27.ila.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.infrastructure.error.ErrorCode;
import sandbox27.ila.infrastructure.error.ServiceException;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RestTemplate rest = new RestTemplate();
    private final JwtGenerator jwtGenerator;
    private final IlaUserMapper userMapper;

    @Value("${iserv.client-secret}")
    private String clientSecret;
    @Value("${iserv.token-uri}")
    private String tokenUri;
    @Value("${iserv.user-info-uri}")
    private String userInfoUri;

    @PostMapping
    public ResponseEntity<?> exchangeCode(@RequestBody OAuthRequest request) throws ServiceException {
        // 1. Token anfordern
        MultiValueMap<String, String> tokenRequest = new LinkedMultiValueMap<>();
        tokenRequest.add("grant_type", "authorization_code");
        String clientId = "17_65m4l0ih9v8c84cwgsk08osc8s8o8cwsowo4k0s80gwoow448k";
        tokenRequest.add("client_id", clientId);
        tokenRequest.add("client_secret", clientSecret);
        tokenRequest.add("redirect_uri", request.redirectUri());
        tokenRequest.add("code", request.code());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<?> httpEntity = new HttpEntity<>(tokenRequest, headers);

        ResponseEntity<Map> tokenResponse = rest.postForEntity(tokenUri, httpEntity, Map.class);

        String accessToken = (String) tokenResponse.getBody().get("access_token");

        // 2. Userinfo abrufen
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(accessToken);
        HttpEntity<?> userRequest = new HttpEntity<>(userHeaders);

        ResponseEntity<Map> userInfoResponse = rest.exchange(userInfoUri, HttpMethod.GET, userRequest, Map.class);
        Map userInfo = userInfoResponse.getBody();

        User user = userMapper.map(userInfo).
                orElseThrow(() -> new ServiceException(ErrorCode.UserNotFound));

        String jwt = jwtGenerator.createToken(user.getId());

        return ResponseEntity.ok(Map.of("token", jwt));

    }
}


