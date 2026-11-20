package com.lankapay.story.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.common.web.GlobalExceptionHandler;
import com.lankapay.story.domain.StoryAudience;
import com.lankapay.story.domain.StoryStatus;
import com.lankapay.story.service.StoryService;
import com.lankapay.story.web.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoryController and AdminStoryController Web MVC Tests")
class StoryControllerTest {

    @Mock
    private StoryService storyService;

    private MockMvc publicMockMvc;
    private MockMvc adminMockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        StoryController storyController = new StoryController(storyService);
        AdminStoryController adminStoryController = new AdminStoryController(storyService);

        publicMockMvc = MockMvcBuilders.standaloneSetup(storyController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        adminMockMvc = MockMvcBuilders.standaloneSetup(adminStoryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/stories - returns active stories for current user")
    void getStoriesReturnsList() throws Exception {
        StoryResponse responseItem = new StoryResponse(
                "story-1",
                "Link Account",
                "Add your account",
                "gradient:linear(135deg, #10B981, #047857)",
                List.of(new StoryPageDto("Page 1", null, "Link", "/accounts/add")),
                StoryAudience.ALL,
                null,
                null,
                10,
                StoryStatus.PUBLISHED,
                false,
                0L,
                Instant.now(),
                Instant.now()
        );

        when(storyService.getActiveStoriesForUser(eq(1L))).thenReturn(List.of(responseItem));

        publicMockMvc.perform(get("/api/v1/stories")
                        .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("story-1"))
                .andExpect(jsonPath("$[0].title").value("Link Account"))
                .andExpect(jsonPath("$[0].seen").value(false));

        verify(storyService).getActiveStoriesForUser(1L);
    }

    @Test
    @DisplayName("POST /api/v1/stories/{id}/seen - marks story as seen")
    void markStorySeenSucceeds() throws Exception {
        publicMockMvc.perform(post("/api/v1/stories/story-1/seen")
                        .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.seen").value(true));

        verify(storyService).markStorySeen(1L, "story-1");
    }

    @Test
    @DisplayName("POST /api/v1/stories/{id}/action-click - records action click")
    void recordActionClickSucceeds() throws Exception {
        when(storyService.recordActionClick("story-1"))
                .thenReturn(new StoryActionClickResponse("story-1", 5, true));

        publicMockMvc.perform(post("/api/v1/stories/story-1/action-click"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storyId").value("story-1"))
                .andExpect(jsonPath("$.actionClickCount").value(5))
                .andExpect(jsonPath("$.success").value(true));

        verify(storyService).recordActionClick("story-1");
    }

    @Test
    @DisplayName("Admin stories endpoints: list, create, update, publish, archive")
    void adminEndpointsFunctionality() throws Exception {
        StoryResponse sample = new StoryResponse(
                "story-admin-1",
                "Admin Story",
                "Caption",
                "gradient:linear(135deg, #4F46E5, #3B82F6)",
                List.of(new StoryPageDto("Page", null, null, null)),
                StoryAudience.ALL,
                null,
                null,
                100,
                StoryStatus.DRAFT,
                false,
                0L,
                Instant.now(),
                Instant.now()
        );

        // 1. List
        when(storyService.getAllStoriesForAdmin()).thenReturn(List.of(sample));
        adminMockMvc.perform(get("/api/v1/admin/stories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("story-admin-1"));

        // 2. Create
        CreateStoryRequest createReq = new CreateStoryRequest(
                "story-admin-1",
                "Admin Story",
                "Caption",
                "gradient:linear(135deg, #4F46E5, #3B82F6)",
                List.of(new StoryPageDto("Page", null, null, null)),
                StoryAudience.ALL,
                null,
                null,
                100,
                false
        );
        when(storyService.createStory(any(), anyLong(), anyString())).thenReturn(sample);

        adminMockMvc.perform(post("/api/v1/admin/stories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("story-admin-1"));

        // 3. Publish
        StoryResponse published = new StoryResponse(
                "story-admin-1",
                "Admin Story",
                "Caption",
                "gradient:linear(135deg, #4F46E5, #3B82F6)",
                List.of(new StoryPageDto("Page", null, null, null)),
                StoryAudience.ALL,
                null,
                null,
                100,
                StoryStatus.PUBLISHED,
                false,
                0L,
                Instant.now(),
                Instant.now()
        );
        when(storyService.publishStory(eq("story-admin-1"), anyLong(), anyString())).thenReturn(published);

        adminMockMvc.perform(post("/api/v1/admin/stories/story-admin-1/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // 4. Archive
        StoryResponse archived = new StoryResponse(
                "story-admin-1",
                "Admin Story",
                "Caption",
                "gradient:linear(135deg, #4F46E5, #3B82F6)",
                List.of(new StoryPageDto("Page", null, null, null)),
                StoryAudience.ALL,
                null,
                null,
                100,
                StoryStatus.ARCHIVED,
                false,
                0L,
                Instant.now(),
                Instant.now()
        );
        when(storyService.archiveStory(eq("story-admin-1"), anyLong(), anyString())).thenReturn(archived);

        adminMockMvc.perform(post("/api/v1/admin/stories/story-admin-1/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }
}
