package com.money.feature.gms.application.catalog;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.web.exception.BaseException;
import com.money.dto.GmsGoodsCategory.GmsGoodsCategoryDTO;
import com.money.dto.SelectVO;
import com.money.dto.TreeNodeVO;
import com.money.entity.GmsGoodsCategory;
import com.money.feature.gms.infrastructure.persistence.mapper.GmsGoodsCategoryMapper;
import com.money.oss.OSSDelegate;
import com.money.oss.core.FileNameStrategy;
import com.money.oss.core.FolderPath;
import com.money.oss.local.LocalOSS;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 商品分类表 服务实现类
 * </p>
 *
 * @author money
 * @since 2023-02-27
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class GmsGoodsCategoryServiceImpl extends ServiceImpl<GmsGoodsCategoryMapper, GmsGoodsCategory> implements GmsGoodsCategoryService {

    private final OSSDelegate<LocalOSS> localOSS;

    @Override
    public void add(GmsGoodsCategoryDTO addDTO, MultipartFile icon) {
        boolean exists = this.lambdaQuery().eq(GmsGoodsCategory::getName, addDTO.getName()).exists();
        if (exists) {
            throw new BaseException("商品类别已存在");
        }
        GmsGoodsCategory gmsGoodsCategory = new GmsGoodsCategory();
        BeanUtil.copyProperties(addDTO, gmsGoodsCategory);

        String newIconUrl = null;
        if (icon != null) {
            newIconUrl = localOSS.upload(icon, FolderPath.builder().cd("goods").cd("category").build(), FileNameStrategy.TIMESTAMP);
            gmsGoodsCategory.setIcon(newIconUrl);
        }

        this.save(gmsGoodsCategory);

        // 🌟 修复 B：利用事务钩子，如果事务回滚，及时清理刚刚上传的无效图片
        if (newIconUrl != null) {
            final String uploadedUrl = newIconUrl;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        localOSS.delete(uploadedUrl);
                    }
                }
            });
        }
    }

    @Override
    public void update(GmsGoodsCategoryDTO updateDTO, MultipartFile icon) {
        boolean exists = this.lambdaQuery().ne(GmsGoodsCategory::getId, updateDTO.getId()).eq(GmsGoodsCategory::getName, updateDTO.getName()).exists();
        if (exists) {
            throw new BaseException("商品类别已存在");
        }
        GmsGoodsCategory gmsGoodsCategory = this.getById(updateDTO.getId());
        BeanUtil.copyProperties(updateDTO, gmsGoodsCategory);

        String oldIcon = gmsGoodsCategory.getIcon();
        String newIconUrl = null;

        if (icon != null) {
            newIconUrl = localOSS.upload(icon, FolderPath.builder().cd("goods").cd("category").build(), FileNameStrategy.TIMESTAMP);
            gmsGoodsCategory.setIcon(newIconUrl);
        }

        this.updateById(gmsGoodsCategory);

        // 🌟 修复 B：严防物理删除不可逆！只有等事务提交成功，才能把旧图标删掉
        if (icon != null) {
            final String finalOldIcon = oldIcon;
            final String finalNewIcon = newIconUrl;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (StrUtil.isNotBlank(finalOldIcon)) {
                        localOSS.delete(finalOldIcon);
                    }
                }
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED && StrUtil.isNotBlank(finalNewIcon)) {
                        localOSS.delete(finalNewIcon);
                    }
                }
            });
        }
    }

    @Override
    public void delete(Set<Long> ids) {
        // 🌟 修复 C：强约束拦截，严禁产出孤儿节点和悬空商品
        boolean hasChildren = this.lambdaQuery().in(GmsGoodsCategory::getPid, ids).exists();
        if (hasChildren) {
            throw new BaseException("存在子分类，请先解除或删除子分类");
        }

        List<GmsGoodsCategory> gmsGoodsCategoryList = this.listByIds(ids);

        // 校验分类下是否还有商品（利用实体自带的 goods_count 进行校验，无需跨服务查库）
        boolean hasGoods = gmsGoodsCategoryList.stream()
                .anyMatch(c -> c.getGoodsCount() != null && c.getGoodsCount() > 0);
        if (hasGoods) {
            throw new BaseException("选中的分类下仍关联有商品，禁止删除");
        }

        this.removeByIds(ids);

        // 🌟 修复 B：事务隔离后的批量后置物理删除
        List<String> iconsToDelete = gmsGoodsCategoryList.stream()
                .map(GmsGoodsCategory::getIcon)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());

        if (!iconsToDelete.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    iconsToDelete.forEach(localOSS::delete);
                }
            });
        }
    }

    @Override
    public List<SelectVO> getGoodsCategorySelect() {
        // 🌟 修复 D：使用 select 投影，不再把庞大的描述和 Icon URL 拉入内存
        return this.lambdaQuery()
                .select(GmsGoodsCategory::getId, GmsGoodsCategory::getName)
                .list()
                .stream().map(gmsGoodsCategory -> {
                    SelectVO selectVO = new SelectVO();
                    selectVO.setLabel(gmsGoodsCategory.getName());
                    selectVO.setValue(gmsGoodsCategory.getId());
                    return selectVO;
                }).collect(Collectors.toList());
    }

    @Override
    public TreeNodeVO tree() {
        TreeNodeVO root = new TreeNodeVO();
        root.setId(0L);
        root.setPid(-1L);
        root.setName("全部分类");

        // 🌟 修复 A：一次性全量加载，消灭 N+1 数据库探针
        List<GmsGoodsCategory> allCategories = this.list();

        // 按 PID 将分类快速划分为 Map 分组
        Map<Long, List<GmsGoodsCategory>> pidMap = allCategories.stream()
                .collect(Collectors.groupingBy(c -> c.getPid() == null ? 0L : c.getPid()));

        // 在纯 Java 内存中闪电成树
        root.setChildren(buildTreeInMemory(root.getId(), pidMap));
        return root;
    }

    private List<TreeNodeVO> buildTreeInMemory(Long pid, Map<Long, List<GmsGoodsCategory>> pidMap) {
        List<GmsGoodsCategory> children = pidMap.getOrDefault(pid, new ArrayList<>());
        return children.stream().map(c -> {
            TreeNodeVO vo = new TreeNodeVO();
            vo.setId(c.getId());
            vo.setPid(pid);
            vo.setName(c.getName());
            vo.setIcon(c.getIcon());
            vo.setChildren(buildTreeInMemory(c.getId(), pidMap));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Long> getAllSubId(Long pid) {
        List<Long> result = new ArrayList<>();
        result.add(pid);

        // 🌟 修复 A：全表扫描精简字段，内存中递归提速
        List<GmsGoodsCategory> allCategories = this.lambdaQuery()
                .select(GmsGoodsCategory::getId, GmsGoodsCategory::getPid)
                .list();

        Map<Long, List<Long>> pidToIdsMap = allCategories.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getPid() == null ? 0L : c.getPid(),
                        Collectors.mapping(GmsGoodsCategory::getId, Collectors.toList())
                ));

        fillSubIdsInMemory(pid, pidToIdsMap, result);
        return result;
    }

    private void fillSubIdsInMemory(Long pid, Map<Long, List<Long>> pidToIdsMap, List<Long> result) {
        List<Long> children = pidToIdsMap.getOrDefault(pid, new ArrayList<>());
        for (Long childId : children) {
            result.add(childId);
            fillSubIdsInMemory(childId, pidToIdsMap, result); // 内存极速递归，无 IO 损耗
        }
    }

    @Override
    public void updateGoodsCount(Long categoryId, int step) {
        this.lambdaUpdate().setSql("goods_count = goods_count + " + step).eq(GmsGoodsCategory::getId, categoryId).update();
    }
}
