package com.money.controller;

import com.money.dto.SysTenantDTO;
import com.money.service.SysTenantService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.multipart.MultipartFile;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SysTenantMultipartContractTest {

    @Test
    void updateBindsNamedTenantJsonAndLogoFileParts() throws Exception {
        SysTenantService service = mock(SysTenantService.class);
        MockMvc mockMvc = standaloneSetup(new SysTenantController(service)).build();
        MockMultipartFile tenant = new MockMultipartFile("tenant", "tenant.json", MediaType.APPLICATION_JSON_VALUE,
                "{\"id\":0,\"tenantCode\":\"M\",\"tenantName\":\"主租户\"}".getBytes());
        MockMultipartFile logo = new MockMultipartFile("logo", "store.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});

        mockMvc.perform(MockMvcRequestBuilders.multipart("/tenants")
                        .file(tenant)
                        .file(logo)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .with(request -> { request.setMethod("PUT"); return request; }))
                .andExpect(status().isOk());

        verify(service).updateById(argThat(dto -> dto.getId().equals(0L)
                        && dto.getTenantCode().equals("M") && dto.getTenantName().equals("主租户")),
                argThat(file -> file != null && file.getName().equals("logo")
                        && file.getOriginalFilename().equals("store.png") && file.getSize() == 3));
    }
}
