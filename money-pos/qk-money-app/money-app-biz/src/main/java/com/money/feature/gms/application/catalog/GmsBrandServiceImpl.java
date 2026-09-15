package com.money.feature.gms.application.catalog;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.web.exception.BaseException;
import com.money.web.vo.PageVO;
import com.money.dto.GmsBrand.GmsBrandDTO;
import com.money.dto.GmsBrand.GmsBrandQueryDTO;
import com.money.dto.GmsBrand.GmsBrandVO;
import com.money.dto.SelectVO;
import com.money.entity.GmsBrand;
import com.money.mapper.GmsBrandMapper;
import com.money.oss.OSSDelegate;
import com.money.oss.core.FileNameStrategy;
import com.money.oss.core.FolderPath;
import com.money.oss.local.LocalOSS;
import com.money.util.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 商品品牌表 服务实现类
 * </p>
 *
 * @author money
 * @since 2023-02-27
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class GmsBrandServiceImpl extends ServiceImpl<GmsBrandMapper, GmsBrand> implements GmsBrandService {

    private final OSSDelegate<LocalOSS> localOSS;

    @Override
    public PageVO<GmsBrandVO> list(GmsBrandQueryDTO queryDTO) {
        Page<GmsBrand> page = this.lambdaQuery()
                .like(StrUtil.isNotBlank(queryDTO.getName()), GmsBrand::getName, queryDTO.getName())
                .last(StrUtil.isNotBlank(queryDTO.getOrderBy()), queryDTO.getOrderBySql())
                .page(PageUtil.toPage(queryDTO));
        return PageUtil.toPageVO(page, GmsBrandVO::new);
    }

    @Override
    public void add(GmsBrandDTO addDTO, MultipartFile logo) {
        boolean exists = this.lambdaQuery().eq(GmsBrand::getName, addDTO.getName()).exists();
        if (exists) {
            throw new BaseException("品牌已存在");
        }
        GmsBrand gmsBrand = new GmsBrand();
        BeanUtil.copyProperties(addDTO, gmsBrand);

        String newLogoUrl = null;
        if (logo != null) {
            newLogoUrl = localOSS.upload(logo, FolderPath.builder().cd("brand").build(), FileNameStrategy.TIMESTAMP);
            gmsBrand.setLogo(newLogoUrl);
        }

        this.save(gmsBrand);

        // 🌟 修复孤儿文件风险：如果 DB 保存抛出异常导致回滚，必须把刚刚上传的文件删掉
        if (newLogoUrl != null) {
            final String uploadedUrl = newLogoUrl;
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
    public void update(GmsBrandDTO updateDTO, MultipartFile logo) {
        boolean exists = this.lambdaQuery().ne(GmsBrand::getId, updateDTO.getId()).eq(GmsBrand::getName, updateDTO.getName()).exists();
        if (exists) {
            throw new BaseException("品牌已存在");
        }
        GmsBrand gmsBrand = this.getById(updateDTO.getId());
        BeanUtil.copyProperties(updateDTO, gmsBrand);

        String oldLogo = gmsBrand.getLogo();
        String newLogoUrl = null;

        // 🌟 修复物理删除不可逆风险：不能在这里直接 delete 旧图！
        if (logo != null) {
            newLogoUrl = localOSS.upload(logo, FolderPath.builder().cd("brand").build(), FileNameStrategy.TIMESTAMP);
            gmsBrand.setLogo(newLogoUrl);
        }

        this.updateById(gmsBrand);

        if (logo != null) {
            final String finalOldLogo = oldLogo;
            final String finalNewLogo = newLogoUrl;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 只有数据库事务成功提交后，才安全地删掉旧图片
                    if (StrUtil.isNotBlank(finalOldLogo)) {
                        localOSS.delete(finalOldLogo);
                    }
                }

                @Override
                public void afterCompletion(int status) {
                    // 如果中途报错回滚，说明更新失败，要把刚刚传上来的新图片当做垃圾清理掉
                    if (status != STATUS_COMMITTED && StrUtil.isNotBlank(finalNewLogo)) {
                        localOSS.delete(finalNewLogo);
                    }
                }
            });
        }
    }

    @Override
    public void delete(Set<Long> ids) {
        List<GmsBrand> gmsBrandList = this.listByIds(ids);
        this.removeByIds(ids);

        List<String> logosToDelete = gmsBrandList.stream()
                .map(GmsBrand::getLogo)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());

        // 🌟 修复长事务与不可逆风险：将物理文件删除移出数据库加锁生命周期
        if (!logosToDelete.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    logosToDelete.forEach(localOSS::delete);
                }
            });
        }
    }

    @Override
    public List<SelectVO> getBrandSelect() {
        // 🌟 修复内存消耗：使用投影查询，拒绝 SELECT * 捞取冗余的大字段
        return this.lambdaQuery()
                .select(GmsBrand::getId, GmsBrand::getName)
                .list()
                .stream().map(gmsBrand -> {
                    SelectVO selectVO = new SelectVO();
                    selectVO.setLabel(gmsBrand.getName());
                    selectVO.setValue(gmsBrand.getId());
                    return selectVO;
                }).collect(Collectors.toList());
    }

    @Override
    public void updateGoodsCount(Long id, int step) {
        this.lambdaUpdate().setSql("goods_count = goods_count + " + step).eq(GmsBrand::getId, id).update();
    }
}
