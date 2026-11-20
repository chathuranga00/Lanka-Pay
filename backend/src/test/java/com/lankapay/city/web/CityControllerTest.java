package com.lankapay.city.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.city.domain.CityCategory;
import com.lankapay.city.domain.CityMerchant;
import com.lankapay.city.domain.MerchantStatus;
import com.lankapay.city.service.CityService;
import com.lankapay.city.web.dto.*;
import com.lankapay.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CityController and AdminCityController Web MVC Tests")
class CityControllerTest {

    @Mock
    private CityService cityService;

    private MockMvc publicMockMvc;
    private MockMvc adminMockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        CityController cityController = new CityController(cityService, null);
        AdminCityController adminCityController = new AdminCityController(cityService);

        publicMockMvc = MockMvcBuilders.standaloneSetup(cityController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        adminMockMvc = MockMvcBuilders.standaloneSetup(adminCityController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/city/categories - returns categories")
    void testGetCategories() throws Exception {
        when(cityService.getCategories()).thenReturn(List.of(
                new CityCategoryDto("FOOD", "Food & Dining", "restaurant", 5),
                new CityCategoryDto("FUEL", "Fuel & Stations", "local_gas_station", 3)
        ));

        publicMockMvc.perform(get("/api/v1/city/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("FOOD"))
                .andExpect(jsonPath("$[0].displayName").value("Food & Dining"))
                .andExpect(jsonPath("$[0].merchantCount").value(5));
    }

    @Test
    @DisplayName("GET /city/merchants - returns filtered merchants and pagination")
    void testGetMerchants() throws Exception {
        CityMerchantDto dto = new CityMerchantDto(
                "merch_1",
                "Cinnamon Leaf Bistro",
                CityCategory.FOOD,
                "Colombo 03",
                "Artisan fusion dining",
                "08:00 - 22:00",
                "QR-CITY-MERCH-1",
                MerchantStatus.ACTIVE,
                false,
                List.of()
        );

        when(cityService.getMerchants(isNull(), eq(CityCategory.FOOD), eq("Bistro"), eq("Colombo 03"), isNull(), eq(10)))
                .thenReturn(new MerchantPageResponse(List.of(dto), "offset:10", true, 20));

        publicMockMvc.perform(get("/city/merchants")
                        .param("category", "FOOD")
                        .param("search", "Bistro")
                        .param("area", "Colombo 03")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchants[0].id").value("merch_1"))
                .andExpect(jsonPath("$.merchants[0].name").value("Cinnamon Leaf Bistro"))
                .andExpect(jsonPath("$.nextCursor").value("offset:10"))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    @DisplayName("GET /city/merchants/{id} - returns merchant detail")
    void testGetMerchantById() throws Exception {
        CityMerchantDto dto = new CityMerchantDto(
                "merch_1",
                "Cinnamon Leaf Bistro",
                CityCategory.FOOD,
                "Colombo 03",
                "Artisan fusion dining",
                "08:00 - 22:00",
                "QR-CITY-MERCH-1",
                MerchantStatus.ACTIVE,
                true,
                List.of()
        );

        when(cityService.getMerchantById(isNull(), eq("merch_1"))).thenReturn(dto);

        publicMockMvc.perform(get("/city/merchants/merch_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("merch_1"))
                .andExpect(jsonPath("$.isFavourite").value(true));
    }

    @Test
    @DisplayName("POST /city/admin/merchants - admin creates merchant")
    void testAdminCreateMerchant() throws Exception {
        CreateMerchantRequest req = new CreateMerchantRequest(
                "merch_new",
                "Kandy Spice Hub",
                CityCategory.GROCERIES,
                "Kandy City",
                "Fresh spices",
                "08:00 - 20:00",
                "QR-SPICE-1",
                MerchantStatus.ACTIVE
        );

        CityMerchant created = new CityMerchant(
                "merch_new",
                "Kandy Spice Hub",
                CityCategory.GROCERIES,
                "Kandy City",
                "Fresh spices",
                "08:00 - 20:00",
                "QR-SPICE-1",
                MerchantStatus.ACTIVE
        );

        when(cityService.adminCreateMerchant(eq(9999L), any(CreateMerchantRequest.class), anyString()))
                .thenReturn(created);

        adminMockMvc.perform(post("/city/admin/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("merch_new"))
                .andExpect(jsonPath("$.name").value("Kandy Spice Hub"));

        verify(cityService).adminCreateMerchant(eq(9999L), any(CreateMerchantRequest.class), anyString());
    }

    @Test
    @DisplayName("DELETE /city/admin/merchants/{id} - admin deletes merchant")
    void testAdminDeleteMerchant() throws Exception {
        adminMockMvc.perform(delete("/city/admin/merchants/merch_1"))
                .andExpect(status().isNoContent());

        verify(cityService).adminDeleteMerchant(eq(9999L), eq("merch_1"), anyString());
    }
}
