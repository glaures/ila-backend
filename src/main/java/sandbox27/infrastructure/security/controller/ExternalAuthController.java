package sandbox27.infrastructure.security.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.*;
import sandbox27.infrastructure.security.jwt.JwtGenerator;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class ExternalAuthController {

    private final RestTemplate rest = new RestTemplate();
    private final JwtGenerator jwtGenerator;
    final UserManagement userMapper;
    final ApplicationContext applicationContext;

    @Value("${sandbox27.infrastructure.security.client-secret}")
    private String clientSecret;
    @Value("${sandbox27.infrastructure.security.client-id}")
    private String clientId;
    @Value("${sandbox27.infrastructure.security.token-uri}")
    private String tokenUri;
    @Value("${sandbox27.infrastructure.security.user-info-uri}")
    private String userInfoUri;

    public record OAuthRequest(String code, String redirectUri) {}

    @PostMapping
    public ResponseEntity<?> exchangeCode(@RequestBody OAuthRequest request) throws ServiceException {
        // 1. Token anfordern
        MultiValueMap<String, String> tokenRequest = new LinkedMultiValueMap<>();
        tokenRequest.add("grant_type", "authorization_code");
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
        userInfo.put(AuthenticationType.USER_INFO_AUTH_TYPE_KEY, AuthenticationType.external);

        SecUser user = userMapper.map(userInfo).
                orElseThrow(() -> new ServiceException(ErrorCode.UserNotFound, userInfo.get("name")));

        applicationContext.publishEvent(new AuthenticationEvent(user));

        String jwt = jwtGenerator.createToken(user.getId());
        return ResponseEntity.ok(Map.of("token", jwt, "username", userInfo.get("name"), "roles", user.getSecRoles()));

    }
}


