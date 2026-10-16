package com.money.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.web.exception.BaseException;
import com.money.web.response.RStatus;
import com.money.web.vo.PageVO;
import com.money.constant.SysErrorStatus;
import com.money.dto.ChangePasswordDTO;
import com.money.dto.ChangeUsernameDTO;
import com.money.dto.SysUserDTO;
import com.money.dto.UpdateProfileDTO;
import com.money.dto.query.SysUserPageQueryDTO;
import com.money.entity.SysUser;
import com.money.entity.SysUserRoleRelation;
import com.money.entity.SysUsernameReservation;
import com.money.entity.SysRole;
import com.money.entity.SysPermission;
import com.money.mapper.SysUserMapper;
import com.money.mapper.SysUsernameReservationMapper;
import com.money.oss.OSSDelegate;
import com.money.oss.core.FileNameStrategy;
import com.money.oss.core.FolderPath;
import com.money.oss.local.LocalOSS;
import com.money.service.SysRoleService;
import com.money.service.SysPermissionService;
import com.money.service.SysUserService;
import com.money.security.component.SecurityTokenSupport;
import com.money.util.PageUtil;
import com.money.vo.SysUserVO;
import com.money.vo.LoginCandidateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@CacheConfig(cacheNames = "user")
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final PasswordEncoder passwordEncoder;
    private final OSSDelegate<LocalOSS> localOSS;
    private final SecurityTokenSupport securityTokenSupport;
    private final SysUsernameReservationMapper sysUsernameReservationMapper;

    private final SysRoleService sysRoleService;
    private final SysPermissionService sysPermissionService;

    @Override
    public SysUser getByUsername(String username) {
        return this.lambdaQuery().eq(SysUser::getUsername, username).one();
    }

    @Override
    public List<LoginCandidateVO> listEnabledLoginCandidates() {
        return this.lambdaQuery()
                .select(SysUser::getUsername, SysUser::getNickname)
                .eq(SysUser::getEnabled, true)
                .orderByAsc(SysUser::getNickname)
                .orderByAsc(SysUser::getUsername)
                .list()
                .stream()
                .map(user -> new LoginCandidateVO(
                        user.getUsername(),
                        StrUtil.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername()))
                .collect(Collectors.toList());
    }

    @Override
    public List<LoginCandidateVO> listEnabledPosLoginCandidates() {
        return this.lambdaQuery()
                .select(SysUser::getId, SysUser::getUsername, SysUser::getNickname)
                .eq(SysUser::getEnabled, true)
                .orderByAsc(SysUser::getNickname)
                .orderByAsc(SysUser::getUsername)
                .list()
                .stream()
                .filter(this::hasPosCashierPermission)
                .map(user -> new LoginCandidateVO(
                        user.getUsername(),
                        StrUtil.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername()))
                .collect(Collectors.toList());
    }

    private boolean hasPosCashierPermission(SysUser user) {
        List<SysRole> roles = sysRoleService.getByUser(user.getId());
        if (roles.stream().anyMatch(role -> "SUPER_ADMIN".equals(role.getRoleCode()))) return true;
        List<Long> roleIds = roles.stream().map(SysRole::getId).collect(Collectors.toList());
        return !roleIds.isEmpty() && sysPermissionService.getByRole(roleIds).stream()
                .anyMatch(permission -> "pos:cashier".equals(permission.getPermission()));
    }

    // ============================================================

    @Override
    public String uploadAvatar(String username, MultipartFile file) {
        SysUser sysUser = this.getByUsername(username);
        String oldAvatar = sysUser.getAvatar();
        if (StrUtil.isNotBlank(oldAvatar)) {
            localOSS.delete(oldAvatar);
        }
        String avatar = localOSS.upload(file, FolderPath.builder().cd("user").build(), FileNameStrategy.TIMESTAMP);
        sysUser.setAvatar(avatar);
        this.updateById(sysUser);
        return avatar;
    }

    @Override
    public void updateProfile(String username, UpdateProfileDTO updateProfileDTO) {
        SysUser sysUser = new SysUser();
        BeanUtil.copyProperties(updateProfileDTO, sysUser);
        this.lambdaUpdate().eq(SysUser::getUsername, username).update(sysUser);
    }

    @Override
    public void changePassword(String username, ChangePasswordDTO changePasswordDTO) {
        SysUser sysUser = this.getByUsername(username);
        if (sysUser == null || !passwordEncoder.matches(changePasswordDTO.getOldPassword(), sysUser.getPassword())) {
            throw new BaseException(SysErrorStatus.OLD_PASSWORD_ERROR);
        }
        sysUser.setPassword(passwordEncoder.encode(changePasswordDTO.getNewPassword()));
        this.updateById(sysUser);
    }

    @Override
    public void changeOwnUsername(Long userId, ChangeUsernameDTO changeUsernameDTO) {
        SysUser sysUser = this.getById(userId);
        if (sysUser == null) {
            throw new BaseException(SysErrorStatus.DATA_NOT_FOUND);
        }
        boolean superAdmin = sysRoleService.getByUser(userId).stream()
                .anyMatch(role -> "SUPER_ADMIN".equals(role.getRoleCode()));
        if (!superAdmin) {
            throw new BaseException(RStatus.FORBIDDEN);
        }

        String oldUsername = sysUser.getUsername();
        String newUsername = changeUsernameDTO.getUsername().trim();
        if (oldUsername.equals(newUsername)) {
            throw new BaseException(SysErrorStatus.DATA_ALREADY_EXIST, "新登录名不能与当前登录名相同");
        }
        assertUsernameAvailable(newUsername);

        long reservationTtl = Math.max(securityTokenSupport.getTokenConfig().getTtl(),
                securityTokenSupport.getTokenConfig().getRefreshTtl());
        SysUsernameReservation reservation = new SysUsernameReservation();
        reservation.setUsername(oldUsername);
        reservation.setExpiresAt(LocalDateTime.now().plus(Duration.ofMillis(reservationTtl)));
        reservation.setTenantId(sysUser.getTenantId());
        sysUsernameReservationMapper.insert(reservation);

        sysUser.setUsername(newUsername);
        this.updateById(sysUser);
        invalidateOldUsernameTokensAfterCommit(oldUsername);
    }

    // ============================================================

    @Override
    public PageVO<SysUserVO> list(SysUserPageQueryDTO queryDTO) {
        IPage<SysUser> page = this.lambdaQuery()
                .eq(ObjectUtil.isNotNull(queryDTO.getEnabled()), SysUser::getEnabled, queryDTO.getEnabled())
                .like(StrUtil.isNotBlank(queryDTO.getPhone()), SysUser::getPhone, queryDTO.getPhone())
                .and(StrUtil.isNotBlank(queryDTO.getName()), wrapper -> wrapper.like(SysUser::getUsername, queryDTO.getName())
                        .or(orWrapper -> orWrapper.like(SysUser::getNickname, queryDTO.getName())))
                .orderByDesc(StrUtil.isBlank(queryDTO.getOrderBy()), SysUser::getLastTime)
                .last(StrUtil.isNotBlank(queryDTO.getOrderBy()), queryDTO.getOrderBySql())
                .page(PageUtil.toPage(queryDTO));
        return PageUtil.toPageVO(page, sysUser -> {
            SysUserVO sysUserVO = new SysUserVO();
            BeanUtil.copyProperties(sysUser, sysUserVO);
            sysUserVO.setRoles(sysRoleService.getByUser(sysUserVO.getId()));
            return sysUserVO;
        });
    }

    @Override
    public void add(SysUserDTO sysUserDTO) {
        // 唯一性判断
        assertUsernameAvailable(sysUserDTO.getUsername());
        SysUser sysUser = new SysUser();
        BeanUtil.copyProperties(sysUserDTO, sysUser);
        // 加密密码
        sysUser.setPassword(passwordEncoder.encode("123456"));
        this.save(sysUser);
        // 关联角色
        List<SysUserRoleRelation> userRoleRelations = sysUserDTO.getRoles().stream().map(roleId -> {
            SysUserRoleRelation relation = new SysUserRoleRelation();
            relation.setUserId(sysUser.getId());
            relation.setRoleId(roleId);
            return relation;
        }).collect(Collectors.toList());
        sysRoleService.relate(userRoleRelations);
    }

    @Override
    public void updateById(SysUserDTO sysUserDTO) {
        SysUser sysUser = new SysUser();
        BeanUtil.copyProperties(sysUserDTO, sysUser);
        // 用户名不允许修改
        sysUser.setUsername(null);
        this.updateById(sysUser);
        Optional.ofNullable(sysUserDTO.getRoles()).ifPresent(roles -> {
            // 重新关联角色
            sysRoleService.relevanceByUser(sysUserDTO.getId());
            List<SysUserRoleRelation> userRoleRelations = roles.stream().map(roleId -> {
                SysUserRoleRelation relation = new SysUserRoleRelation();
                relation.setUserId(sysUser.getId());
                relation.setRoleId(roleId);
                return relation;
            }).collect(Collectors.toList());
            sysRoleService.relate(userRoleRelations);
        });
    }

    @Override
    public void deleteById(Set<Long> ids) {
        this.removeBatchByIds(ids);
        // 删除角色关联
        ids.forEach(sysRoleService::relevanceByUser);
    }

    private void assertUsernameAvailable(String username) {
        LocalDateTime now = LocalDateTime.now();
        sysUsernameReservationMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUsernameReservation>()
                .eq(SysUsernameReservation::getUsername, username)
                .le(SysUsernameReservation::getExpiresAt, now));
        if (this.getByUsername(username) != null
                || sysUsernameReservationMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUsernameReservation>()
                .eq(SysUsernameReservation::getUsername, username)
                .gt(SysUsernameReservation::getExpiresAt, now)) > 0) {
            throw new BaseException(SysErrorStatus.DATA_ALREADY_EXIST, "用户已存在或登录名仍在保护期");
        }
    }

    private void invalidateOldUsernameTokensAfterCommit(String username) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            securityTokenSupport.invalidateTokensForUsername(username);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                securityTokenSupport.invalidateTokensForUsername(username);
            }
        });
    }

}
