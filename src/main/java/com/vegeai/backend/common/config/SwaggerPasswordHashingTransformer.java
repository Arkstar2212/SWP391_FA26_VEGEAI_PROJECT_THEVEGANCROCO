package com.vegeai.backend.common.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Makes Swagger UI behave like the frontend so the auth endpoints can be tested by hand:
 * <ul>
 *   <li>Plain-text password fields are SHA-256 hashed in the browser before the request is sent
 *       (values that are already 64-char hex are left untouched).</li>
 *   <li>A successful POST /api/auth/login automatically fills the "Authorize" bearer token.</li>
 * </ul>
 * Only the Swagger page is affected; the API contract (SHA-256 hex passwords) is unchanged.
 */
@Configuration
public class SwaggerPasswordHashingTransformer {

    private static final String INITIALIZER = "swagger-initializer.js";
    private static final String CONFIG_ANCHOR = "layout: \"StandaloneLayout\" ,";

    private static final String HELPERS = """
            const VEGEAI_PASSWORD_FIELDS = ['password', 'confirmPassword', 'currentPassword', 'newPassword', 'confirmNewPassword'];

            async function vegeaiSha256(text) {
              const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
              return Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, '0')).join('');
            }

            async function vegeaiHashPasswords(req) {
              if (typeof req.body !== 'string' || !req.body.trim().startsWith('{')) return req;
              let body;
              try { body = JSON.parse(req.body); } catch (e) { return req; }
              let changed = false;
              for (const field of VEGEAI_PASSWORD_FIELDS) {
                const value = body[field];
                if (typeof value === 'string' && value && !/^[A-Fa-f0-9]{64}$/.test(value)) {
                  body[field] = await vegeaiSha256(value);
                  changed = true;
                }
              }
              if (changed) req.body = JSON.stringify(body);
              return req;
            }

            function vegeaiAutoAuthorize(res) {
              try {
                if (res.ok && /\\/api\\/auth\\/login$/.test(new URL(res.url, location.href).pathname)) {
                  const token = res.body && res.body.data && res.body.data.accessToken;
                  if (token) window.ui.authActions.authorize({
                    bearerAuth: { name: 'bearerAuth', schema: { type: 'http', scheme: 'bearer' }, value: token }
                  });
                }
              } catch (e) { /* never break Swagger UI */ }
              return res;
            }

            """;

    @Bean
    public SwaggerIndexTransformer indexPageTransformer(SwaggerUiConfigProperties swaggerUiConfig,
                                                        SwaggerUiOAuthProperties swaggerUiOAuthProperties,
                                                        SwaggerUiConfigParameters swaggerUiConfigParameters,
                                                        SwaggerWelcomeCommon swaggerWelcomeCommon,
                                                        ObjectMapperProvider objectMapperProvider) {
        return new SwaggerIndexPageTransformer(swaggerUiConfig, swaggerUiOAuthProperties,
                swaggerUiConfigParameters, swaggerWelcomeCommon, objectMapperProvider) {
            @Override
            public Resource transform(HttpServletRequest request, Resource resource,
                                      ResourceTransformerChain chain) throws IOException {
                Resource transformed = super.transform(request, resource, chain);
                if (!INITIALIZER.equals(resource.getFilename())) return transformed;

                String js = new String(transformed.getContentAsByteArray(), StandardCharsets.UTF_8);
                if (!js.contains(CONFIG_ANCHOR)) return transformed;
                js = HELPERS + js.replace(CONFIG_ANCHOR, CONFIG_ANCHOR
                        + "\n    requestInterceptor: vegeaiHashPasswords,"
                        + "\n    responseInterceptor: vegeaiAutoAuthorize,");
                return new TransformedResource(resource, js.getBytes(StandardCharsets.UTF_8));
            }
        };
    }
}
