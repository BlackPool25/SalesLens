package com.shreyas.saleslens.controller;
import com.shreyas.saleslens.config.TestCacheConfig;
import com.shreyas.saleslens.config.TestSecurityConfig;
import org.springframework.context.annotation.Import;
import com.shreyas.saleslens.config.filters.JwtFilter;
import com.shreyas.saleslens.dto.CreateSourceRequest;
import com.shreyas.saleslens.dto.DataSourceResponse;
import com.shreyas.saleslens.model.Users;
import com.shreyas.saleslens.security.CustomUserDetailsService;
import com.shreyas.saleslens.security.UserPrincipal;
import com.shreyas.saleslens.service.DataSourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@WebMvcTest(DataSourceController.class)
@Import({TestCacheConfig.class, TestSecurityConfig.class})
class DataSourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DataSourceService dataSourceService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private JwtFilter jwtFilter;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);
            FilterChain filterChain = invocation.getArgument(2);
            filterChain.doFilter(request, response);
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    private DataSourceResponse createTestResponse() {
        DataSourceResponse response = new DataSourceResponse();
        response.setId(UUID.randomUUID());
        response.setName("Test Source");
        response.setSourceType("CSV_FILE");
        response.setTrustScore(BigDecimal.valueOf(0.9));
        response.setActive(true);
        response.setCreatedAt(Instant.now());
        response.setCreatedBy(1L);
        return response;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllSources_returnsPage() throws Exception {
        DataSourceResponse response = createTestResponse();

        when(dataSourceService.getAllSources(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/datasources/get-all-sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Test Source"))
                .andExpect(jsonPath("$.content[0].sourceType").value("CSV_FILE"))
                .andExpect(jsonPath("$.content[0].active").value(true))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void createSource_returnsSourceId() throws Exception {
        Users adminUser = new Users();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setPassword("password");
        adminUser.setRole("ADMIN");
        UserPrincipal adminPrincipal = new UserPrincipal(adminUser);
        UsernamePasswordAuthenticationToken auth =
                UsernamePasswordAuthenticationToken.authenticated(
                        adminPrincipal, adminPrincipal.getPassword(),
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        var sourceId = UUID.randomUUID();
        when(dataSourceService.createSource(any(CreateSourceRequest.class), eq(1L)))
                .thenReturn(sourceId);

        String json = """
                {
                    "name": "Test Source",
                    "sourceType": "CSV_FILE",
                    "trustScore": 0.9,
                    "active": true
                }
                """;

        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            mockMvc.perform(post("/datasources/create-source")
                            .with(csrf())
                            .with(user(adminPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(sourceId.toString()));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getBySourceId_returnsSource() throws Exception {
        DataSourceResponse response = createTestResponse();

        when(dataSourceService.getBySourceId(response.getId()))
                .thenReturn(response);

        mockMvc.perform(get("/datasources/get-by-id")
                        .param("id", response.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Source"))
                .andExpect(jsonPath("$.sourceType").value("CSV_FILE"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdBy").value(1));
    }
}
