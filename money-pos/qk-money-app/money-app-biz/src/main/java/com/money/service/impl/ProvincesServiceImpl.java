package com.money.service.impl;

import cn.hutool.core.util.StrUtil;
import com.money.dto.SelectVO;
import com.money.feature.sys.infrastructure.persistence.entity.Provinces;
import com.money.mapper.ProvincesMapper;
import com.money.service.ProvincesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * <p>
 * 省市区字典 服务实现类 (全内存缓存高性能版)
 * </p>
 *
 * @author money
 * @since 2023-02-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProvincesServiceImpl implements ProvincesService {

    // 🌟 核心优化：JVM 级内存缓存，彻底阻断高频无意义的数据库 IO
    private volatile boolean initialized = false;
    private final Object lock = new Object();
    private final ProvincesMapper provincesMapper;

    // 缓存容器
    private List<SelectVO> provinceCache = new ArrayList<>();
    private Map<String, List<SelectVO>> cityCacheMap = new ConcurrentHashMap<>();
    private Map<String, List<SelectVO>> districtCacheMap = new ConcurrentHashMap<>();

    /**
     * 懒加载机制：双重检查锁保证只在首次调用时初始化一次
     */
    private void ensureCacheInitialized() {
        if (!initialized) {
            synchronized (lock) {
                if (!initialized) {
                    loadDataToMemory();
                    initialized = true;
                }
            }
        }
    }

    /**
     * 核心预热引擎：一次性拉取，内存中闪电分组去重
     */
    private void loadDataToMemory() {
        log.info("开始初始化省市区内存缓存...");
        long start = System.currentTimeMillis();

        // 仅触发一次全表扫描（约三四千条数据，占用内存极小）
        List<Provinces> allData = provincesMapper.selectAll();

        // 临时数据结构，使用 LinkedHashSet 保证去重且维持插入顺序
        Set<String> pSet = new LinkedHashSet<>();
        Map<String, Set<String>> cMap = new HashMap<>();
        Map<String, Set<String>> dMap = new HashMap<>();

        for (Provinces p : allData) {
            String province = p.getProvince();
            String city = p.getCity();
            String district = p.getDistrict();

            if (StrUtil.isNotBlank(province)) {
                pSet.add(province);

                if (StrUtil.isNotBlank(city)) {
                    cMap.computeIfAbsent(province, k -> new LinkedHashSet<>()).add(city);

                    if (StrUtil.isNotBlank(district)) {
                        dMap.computeIfAbsent(city, k -> new LinkedHashSet<>()).add(district);
                    }
                }
            }
        }

        // 转换为视图对象 (VO) 并写入最终的缓存容器
        this.provinceCache = toSelectVOList(pSet);
        cMap.forEach((k, v) -> this.cityCacheMap.put(k, toSelectVOList(v)));
        dMap.forEach((k, v) -> this.districtCacheMap.put(k, toSelectVOList(v)));

        log.info("省市区内存缓存初始化完成，耗时: {} ms", (System.currentTimeMillis() - start));
    }

    /**
     * DRY 原则：公共的 VO 转换提取，消除重复的 stream 样板代码
     */
    private List<SelectVO> toSelectVOList(Collection<String> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream().map(item -> {
            SelectVO vo = new SelectVO();
            vo.setLabel(item);
            vo.setValue(item);
            return vo;
        }).collect(Collectors.toList());
    }

    // ================= 以下为完全无损的对外接口 =================

    @Override
    public List<SelectVO> listProvinces() {
        ensureCacheInitialized();
        return this.provinceCache; // 直接从内存返回 O(1)
    }

    @Override
    public List<SelectVO> listCities(String province) {
        ensureCacheInitialized();
        return this.cityCacheMap.getOrDefault(province, Collections.emptyList()); // 直接从内存返回 O(1)
    }

    @Override
    public List<SelectVO> listDistricts(String city) {
        ensureCacheInitialized();
        return this.districtCacheMap.getOrDefault(city, Collections.emptyList()); // 直接从内存返回 O(1)
    }

}
