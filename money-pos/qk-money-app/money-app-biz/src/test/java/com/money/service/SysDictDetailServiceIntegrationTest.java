package com.money.service;

import com.money.dto.SysDictDetailDTO;
import com.money.entity.SysDictDetail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class SysDictDetailServiceIntegrationTest {

    @Autowired
    private SysDictDetailService sysDictDetailService;

    @org.junit.jupiter.api.BeforeEach
    void authenticateTenant() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test", "N/A"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Y-tenant", "0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @org.junit.jupiter.api.AfterEach
    void clearTenant() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void persistsRequestedSortAndListsDetailsInAscendingSortOrder() {
        String suffix = Long.toString(System.nanoTime(), 36);
        String firstValue = "SORT_FIRST_" + suffix;
        String secondValue = "SORT_SECOND_" + suffix;

        sysDictDetailService.add(detail("permissionType", firstValue, 20));
        sysDictDetailService.add(detail("permissionType", secondValue, 10));

        List<SysDictDetail> added = sysDictDetailService.listByDict("permissionType").stream()
                .filter(detail -> firstValue.equals(detail.getValue()) || secondValue.equals(detail.getValue()))
                .collect(java.util.stream.Collectors.toList());
        assertThat(added).extracting(SysDictDetail::getValue).containsExactly(secondValue, firstValue);
        assertThat(added).extracting(SysDictDetail::getSort).containsExactly(10, 20);

        SysDictDetailDTO update = detail("permissionType", firstValue, 5);
        update.setId(added.stream().filter(detail -> firstValue.equals(detail.getValue())).findFirst().orElseThrow().getId());
        sysDictDetailService.updateById(update);

        List<SysDictDetail> updated = sysDictDetailService.listByDict("permissionType").stream()
                .filter(detail -> firstValue.equals(detail.getValue()) || secondValue.equals(detail.getValue()))
                .collect(java.util.stream.Collectors.toList());
        assertThat(updated).extracting(SysDictDetail::getValue).containsExactly(firstValue, secondValue);
        assertThat(updated).extracting(SysDictDetail::getSort).containsExactly(5, 10);
    }

    private SysDictDetailDTO detail(String dict, String value, int sort) {
        return new SysDictDetailDTO()
                .setDict(dict)
                .setValue(value)
                .setCnDesc(value)
                .setSort(sort)
                .setHidden(false);
    }
}
