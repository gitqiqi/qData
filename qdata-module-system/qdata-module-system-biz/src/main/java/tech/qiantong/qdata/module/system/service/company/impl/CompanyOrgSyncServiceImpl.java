/*
 * Copyright © 2025-present Jiangsu Qiantong Technology Co., Ltd.
 *
 * This file is part of qData Data Middle Platform (Open Source Edition).
 *
 * qData is licensed under Apache License 2.0 with additional qData terms.
 * You may use qData for commercial purposes, but you may not remove, hide,
 * modify, or replace the qData logo, copyright notices, license notices,
 * or attribution information without a separate commercial license.
 *
 * White-label use, OEM distribution, rebranding, or presenting qData as
 * another product requires separate commercial authorization from
 * Jiangsu Qiantong Technology Co., Ltd.
 *
 * Business License: https://community.qdata.tech/business/policy.html
 * See the LICENSE file in the project root for full license information.
 */

package tech.qiantong.qdata.module.system.service.company.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.qiantong.qdata.common.constant.UserConstants;
import tech.qiantong.qdata.common.core.domain.entity.SysDept;
import tech.qiantong.qdata.common.core.domain.entity.SysRole;
import tech.qiantong.qdata.common.core.domain.entity.SysUser;
import tech.qiantong.qdata.common.database.DataSourceFactory;
import tech.qiantong.qdata.common.database.DbQuery;
import tech.qiantong.qdata.common.database.constants.DbQueryProperty;
import tech.qiantong.qdata.common.database.constants.DbType;
import tech.qiantong.qdata.common.exception.ServiceException;
import tech.qiantong.qdata.common.utils.SecurityUtils;
import tech.qiantong.qdata.common.utils.StringUtils;
import tech.qiantong.qdata.module.da.api.datasource.dto.DaDatasourceRespDTO;
import tech.qiantong.qdata.module.da.api.service.asset.IDaDatasourceApiService;
import tech.qiantong.qdata.module.system.domain.SysRoleMenu;
import tech.qiantong.qdata.module.system.domain.SysUserRole;
import tech.qiantong.qdata.module.system.mapper.SysDeptMapper;
import tech.qiantong.qdata.module.system.mapper.SysRoleMapper;
import tech.qiantong.qdata.module.system.mapper.SysRoleMenuMapper;
import tech.qiantong.qdata.module.system.mapper.SysUserMapper;
import tech.qiantong.qdata.module.system.mapper.SysUserRoleMapper;
import tech.qiantong.qdata.module.system.service.company.CompanyOrgSyncProperties;
import tech.qiantong.qdata.module.system.service.company.CompanyOrgSyncResult;
import tech.qiantong.qdata.module.system.service.company.ICompanyOrgSyncService;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Synchronizes qData system users/departments from the read-only company PostgreSQL table.
 */
@Service
public class CompanyOrgSyncServiceImpl implements ICompanyOrgSyncService {

    private static final Logger log = LoggerFactory.getLogger(CompanyOrgSyncServiceImpl.class);
    private static final Pattern SOURCE_TABLE_PATTERN =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]?\\$\\d{2}\\$.+");
    private static final int AUTH_ID_MAX_LENGTH = 20;
    private static final int USER_NAME_MAX_LENGTH = 30;
    private static final int NICK_NAME_MAX_LENGTH = 30;
    private static final int DEPT_NAME_MAX_LENGTH = 30;
    private static final int ROLE_NAME_MAX_LENGTH = 30;
    private static final int ROLE_KEY_MAX_LENGTH = 100;
    private static final String SYNC_MARK = "company-org-sync";
    private static final String SYNC_REMARK = "由公司组织用户同步";

    @Resource
    private CompanyOrgSyncProperties properties;
    @Resource
    private IDaDatasourceApiService datasourceApiService;
    @Resource
    private DataSourceFactory dataSourceFactory;
    @Resource
    private SysDeptMapper sysDeptMapper;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private SysRoleMapper sysRoleMapper;
    @Resource
    private SysRoleMenuMapper sysRoleMenuMapper;
    @Resource
    private SysUserRoleMapper sysUserRoleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompanyOrgSyncResult syncFromSource() {
        String sourceTable = validateSourceTable(properties.getSourceTable());
        DaDatasourceRespDTO datasource = resolveDatasource();
        CompanyOrgSyncResult result = new CompanyOrgSyncResult();
        result.setDatasource(datasource.getDatasourceName());
        result.setSourceTable(sourceTable);

        List<CompanyUserRow> sourceRows = querySourceRows(datasource, sourceTable);
        result.setSourceRows(sourceRows.size());
        Map<Long, CompanyDept> sourceDepartments = buildDepartments(sourceRows);

        syncDepartments(sourceDepartments, result);
        Map<String, Long> roleIdMap = syncRoles(sourceRows, result);
        syncUsers(sourceRows, roleIdMap, result);

        log.info("Company org sync finished, sourceRows={}, deptCreated={}, deptUpdated={}, roleCreated={}, "
                        + "userCreated={}, userUpdated={}, userDisabled={}, userRoleUpdated={}",
                result.getSourceRows(), result.getDeptCreated(), result.getDeptUpdated(), result.getRoleCreated(),
                result.getUserCreated(), result.getUserUpdated(), result.getUserDisabled(), result.getUserRoleUpdated());
        return result;
    }

    private DaDatasourceRespDTO resolveDatasource() {
        List<DaDatasourceRespDTO> datasourceList = datasourceApiService.getDatasourceList();
        DaDatasourceRespDTO datasource = null;
        if (properties.getDatasourceId() != null) {
            datasource = findDatasourceById(datasourceList, properties.getDatasourceId());
        } else if (StringUtils.isNotBlank(properties.getDatasourceName())) {
            datasource = findDatasourceByName(datasourceList, properties.getDatasourceName());
        }
        if (datasource == null) {
            datasource = findUniquePostgreDatasource(datasourceList);
            if (datasource != null) {
                log.warn("Company org sync datasource '{}' not found, fallback to unique PostgreSQL datasource '{}' (id={})",
                        properties.getDatasourceName(), datasource.getDatasourceName(), datasource.getId());
            }
        }
        if (datasource == null) {
            throw new ServiceException("Company org sync datasource does not exist: id="
                    + properties.getDatasourceId() + ", name=" + properties.getDatasourceName());
        }
        DbType dbType = DbType.getDbType(datasource.getDatasourceType());
        if (dbType != DbType.POSTGRE_SQL) {
            throw new ServiceException("Company org sync only supports PostgreSQL datasource");
        }
        return datasource;
    }

    private DaDatasourceRespDTO findDatasourceById(List<DaDatasourceRespDTO> datasourceList, Long datasourceId) {
        if (datasourceList == null || datasourceId == null) {
            return null;
        }
        for (DaDatasourceRespDTO datasource : datasourceList) {
            if (datasource != null && Objects.equals(datasource.getId(), datasourceId)) {
                return datasource;
            }
        }
        return null;
    }

    private DaDatasourceRespDTO findDatasourceByName(List<DaDatasourceRespDTO> datasourceList, String datasourceName) {
        if (datasourceList == null || StringUtils.isBlank(datasourceName)) {
            return null;
        }
        String targetName = StringUtils.trim(datasourceName);
        for (DaDatasourceRespDTO datasource : datasourceList) {
            if (datasource == null || StringUtils.isBlank(datasource.getDatasourceName())) {
                continue;
            }
            if (StringUtils.equalsIgnoreCase(StringUtils.trim(datasource.getDatasourceName()), targetName)) {
                return datasource;
            }
        }
        return null;
    }

    private DaDatasourceRespDTO findUniquePostgreDatasource(List<DaDatasourceRespDTO> datasourceList) {
        if (datasourceList == null || datasourceList.isEmpty()) {
            return null;
        }
        List<DaDatasourceRespDTO> postgreDatasources = datasourceList.stream()
                .filter(this::isPostgreDatasource)
                .collect(Collectors.toList());
        return postgreDatasources.size() == 1 ? postgreDatasources.get(0) : null;
    }

    private boolean isPostgreDatasource(DaDatasourceRespDTO datasource) {
        return datasource != null && DbType.POSTGRE_SQL.getDb().equals(datasource.getDatasourceType());
    }

    private List<CompanyUserRow> querySourceRows(DaDatasourceRespDTO datasource, String sourceTable) {
        DbQuery dbQuery = null;
        try {
            DbQueryProperty property = new DbQueryProperty(
                    datasource.getDatasourceType(),
                    datasource.getIp(),
                    datasource.getPort(),
                    datasource.getDatasourceConfig()
            );
            dbQuery = dataSourceFactory.createDbQuery(property);
            String sql = "select admin_id, mobile, status, create_by, create_date, update_by, update_date, "
                    + "we_user_id, user_name, role_id, employee_id, admin_organ_id, organ_name, parent_id, "
                    + "is_full_view, permission_type, permission_scope, admin_organ_ids, parent_ids, "
                    + "teacher_uid, subject, password, role_name from " + sourceTable
                    + " order by parent_ids, admin_organ_id, admin_id";
            List<Map<String, Object>> rows = dbQuery.queryList(sql);
            return rows.stream().map(this::toCompanyUserRow).collect(Collectors.toList());
        } finally {
            if (dbQuery != null) {
                dbQuery.close();
            }
        }
    }

    private CompanyUserRow toCompanyUserRow(Map<String, Object> row) {
        CompanyUserRow user = new CompanyUserRow();
        user.authId = limit(toText(row.get("admin_id")), AUTH_ID_MAX_LENGTH);
        user.mobile = normalizeMobile(toText(row.get("mobile")));
        user.sourceStatus = toText(row.get("status"));
        user.sourceUserName = limit(toText(row.get("user_name")), NICK_NAME_MAX_LENGTH);
        user.nickName = limit(firstNonBlank(user.sourceUserName, user.authId), NICK_NAME_MAX_LENGTH);
        user.employeeId = limit(toText(row.get("employee_id")), USER_NAME_MAX_LENGTH);
        // Use the company mobile as the qData login account. admin_id remains the sync identity.
        user.account = limit(user.mobile, USER_NAME_MAX_LENGTH);
        user.deptId = toLong(row.get("admin_organ_id"));
        user.deptName = limit(toText(row.get("organ_name")), DEPT_NAME_MAX_LENGTH);
        user.sourceParentId = toLong(row.get("parent_id"));
        user.parentPath = parseParentPath(toText(row.get("parent_ids")), user.deptId);
        user.password = toText(row.get("password"));
        user.roleName = limit(toText(row.get("role_name")), ROLE_NAME_MAX_LENGTH);
        return user;
    }

    private Map<Long, CompanyDept> buildDepartments(List<CompanyUserRow> rows) {
        Map<Long, CompanyDept> departments = new LinkedHashMap<>();
        int[] order = new int[] {0};
        for (CompanyUserRow row : rows) {
            addAncestorDepartments(departments, row.parentPath, order);
            if (row.deptId == null || row.deptId <= 0) {
                continue;
            }
            Long parentId = resolveParentId(row);
            String ancestors = normalizeAncestors(row.parentPath);
            String deptName = firstNonBlank(row.deptName, "组织" + row.deptId);
            CompanyDept dept = departments.get(row.deptId);
            if (dept == null) {
                departments.put(row.deptId, new CompanyDept(row.deptId, parentId, ancestors, deptName, order[0]++, false));
            } else {
                dept.parentId = parentId;
                dept.ancestors = ancestors;
                dept.orderNum = Math.min(dept.orderNum, order[0]++);
                if (StringUtils.isNotBlank(row.deptName)) {
                    dept.deptName = deptName;
                    dept.placeholder = false;
                }
            }
        }
        return departments;
    }

    private void addAncestorDepartments(Map<Long, CompanyDept> departments, List<Long> parentPath, int[] order) {
        if (parentPath == null || parentPath.isEmpty()) {
            return;
        }
        for (int i = 0; i < parentPath.size(); i++) {
            Long deptId = parentPath.get(i);
            if (deptId == null || deptId <= 0) {
                continue;
            }
            Long parentId = previousPositive(parentPath, i);
            String ancestors = normalizeAncestors(parentPath.subList(0, i));
            CompanyDept dept = departments.get(deptId);
            if (dept == null) {
                departments.put(deptId, new CompanyDept(deptId, parentId, ancestors, "组织" + deptId, order[0]++, true));
            } else if (dept.placeholder) {
                dept.parentId = parentId;
                dept.ancestors = ancestors;
            }
        }
    }

    private void syncDepartments(Map<Long, CompanyDept> sourceDepartments, CompanyOrgSyncResult result) {
        Map<Long, SysDept> existingDeptMap = sysDeptMapper.selectDeptListAll(new SysDept()).stream()
                .filter(item -> item.getDeptId() != null)
                .collect(Collectors.toMap(SysDept::getDeptId, item -> item, (left, right) -> left));

        for (CompanyDept sourceDept : sourceDepartments.values()) {
            SysDept existing = existingDeptMap.get(sourceDept.deptId);
            SysDept target = toSysDept(sourceDept, existing);
            if (existing == null) {
                sysDeptMapper.insertDept(target);
                result.incrementDeptCreated();
            } else if (deptNeedsUpdate(existing, target)) {
                sysDeptMapper.updateDept(target);
                result.incrementDeptUpdated();
            } else {
                result.incrementDeptSkipped();
            }
        }

        if (properties.isDisableMissingDepartments() || properties.isDisableNonSourceDepartments()) {
            disableMissingDepartments(sourceDepartments.keySet(), existingDeptMap, result);
        }
    }

    private SysDept toSysDept(CompanyDept sourceDept, SysDept existing) {
        SysDept dept = new SysDept();
        dept.setDeptId(sourceDept.deptId);
        dept.setParentId(sourceDept.parentId == null ? 0L : sourceDept.parentId);
        dept.setAncestors(StringUtils.defaultIfBlank(sourceDept.ancestors, "0"));
        String deptName = sourceDept.deptName;
        if (sourceDept.placeholder && existing != null && StringUtils.isNotBlank(existing.getDeptName())) {
            deptName = existing.getDeptName();
        }
        dept.setDeptName(limit(firstNonBlank(deptName, "组织" + sourceDept.deptId), DEPT_NAME_MAX_LENGTH));
        dept.setOrderNum(sourceDept.orderNum);
        dept.setStatus(UserConstants.DEPT_NORMAL);
        dept.setDelFlag("0");
        dept.setCreateBy(properties.getOperator());
        dept.setUpdateBy(properties.getOperator());
        return dept;
    }

    private boolean deptNeedsUpdate(SysDept existing, SysDept target) {
        return !Objects.equals(defaultLong(existing.getParentId(), 0L), defaultLong(target.getParentId(), 0L))
                || !Objects.equals(StringUtils.defaultString(existing.getAncestors()), StringUtils.defaultString(target.getAncestors()))
                || !Objects.equals(StringUtils.defaultString(existing.getDeptName()), StringUtils.defaultString(target.getDeptName()))
                || !Objects.equals(existing.getOrderNum(), target.getOrderNum())
                || !Objects.equals(StringUtils.defaultString(existing.getStatus()), target.getStatus())
                || !Objects.equals(StringUtils.defaultString(existing.getDelFlag()), target.getDelFlag());
    }

    private void disableMissingDepartments(Set<Long> sourceDeptIds, Map<Long, SysDept> existingDeptMap, CompanyOrgSyncResult result) {
        for (SysDept existing : existingDeptMap.values()) {
            if (existing.getDeptId() == null || sourceDeptIds.contains(existing.getDeptId())) {
                continue;
            }
            if (!properties.isDisableNonSourceDepartments()
                    && !isSyncedByCompany(existing.getCreateBy(), null, null)) {
                continue;
            }
            if ("2".equals(existing.getDelFlag())) {
                continue;
            }
            SysDept dept = new SysDept();
            dept.setDeptId(existing.getDeptId());
            dept.setStatus(UserConstants.DEPT_DISABLE);
            dept.setDelFlag("2");
            dept.setUpdateBy(properties.getOperator());
            sysDeptMapper.updateDept(dept);
            result.incrementDeptDisabled();
        }
    }

    private Map<String, Long> syncRoles(List<CompanyUserRow> sourceRows, CompanyOrgSyncResult result) {
        List<SysRole> existingRoles = sysRoleMapper.selectRoleAll();
        Map<String, SysRole> rolesByName = new HashMap<>();
        Set<String> roleKeys = new HashSet<>();
        int nextSort = 0;
        for (SysRole role : existingRoles) {
            if (role.getRoleSort() != null) {
                nextSort = Math.max(nextSort, role.getRoleSort() + 1);
            }
            if (StringUtils.isNotBlank(role.getRoleKey())) {
                roleKeys.add(role.getRoleKey());
            }
            if (!"2".equals(role.getDelFlag()) && isGlobalRole(role) && StringUtils.isNotBlank(role.getRoleName())) {
                rolesByName.put(role.getRoleName(), role);
            }
        }

        Long defaultRoleId = resolveDefaultRoleId(rolesByName, roleKeys, nextSort, result);
        Map<String, Long> roleIdMap = new HashMap<>();
        String defaultRoleName = normalizedRoleName(properties.getDefaultRoleName());
        if (StringUtils.isNotBlank(defaultRoleName)) {
            roleIdMap.put(defaultRoleName, defaultRoleId);
        }

        for (CompanyUserRow row : sourceRows) {
            String roleName = normalizedRoleName(row.roleName);
            if (StringUtils.isBlank(roleName) || roleIdMap.containsKey(roleName)) {
                continue;
            }
            SysRole role = rolesByName.get(roleName);
            if (role == null && properties.isAutoCreateRole()) {
                role = createRole(roleName, roleKeys, nextSort++, result);
                rolesByName.put(roleName, role);
            }
            roleIdMap.put(roleName, role == null ? defaultRoleId : role.getRoleId());
        }
        return roleIdMap;
    }

    private Long resolveDefaultRoleId(Map<String, SysRole> rolesByName, Set<String> roleKeys, int nextSort, CompanyOrgSyncResult result) {
        if (properties.getDefaultRoleId() != null) {
            SysRole role = sysRoleMapper.selectRoleById(properties.getDefaultRoleId());
            if (role != null && !"2".equals(role.getDelFlag())) {
                return role.getRoleId();
            }
        }
        String defaultRoleName = normalizedRoleName(properties.getDefaultRoleName());
        SysRole role = rolesByName.get(defaultRoleName);
        if (role != null) {
            return role.getRoleId();
        }
        if (properties.isAutoCreateRole() && StringUtils.isNotBlank(defaultRoleName)) {
            return createRole(defaultRoleName, roleKeys, nextSort, result).getRoleId();
        }
        throw new ServiceException("Company org sync default role does not exist");
    }

    private SysRole createRole(String roleName, Set<String> roleKeys, int roleSort, CompanyOrgSyncResult result) {
        SysRole role = new SysRole();
        role.setRoleName(limit(roleName, ROLE_NAME_MAX_LENGTH));
        role.setRoleKey(generateRoleKey(roleName, roleKeys));
        role.setRoleSort(roleSort);
        role.setDataScope("1");
        role.setMenuCheckStrictly(true);
        role.setDeptCheckStrictly(true);
        role.setStatus(UserConstants.NORMAL);
        role.setCreateBy(properties.getOperator());
        role.setRemark("由公司组织用户同步自动创建");
        sysRoleMapper.insertRole(role);
        copyTemplateMenus(role.getRoleId());
        result.incrementRoleCreated();
        roleKeys.add(role.getRoleKey());
        return role;
    }

    private void copyTemplateMenus(Long roleId) {
        if (roleId == null || properties.getRoleMenuTemplateRoleId() == null) {
            return;
        }
        List<SysRoleMenu> templateMenus = sysRoleMenuMapper.getByRoleIdList(
                Collections.singletonList(properties.getRoleMenuTemplateRoleId()));
        if (templateMenus == null || templateMenus.isEmpty()) {
            return;
        }
        List<SysRoleMenu> newMenus = new ArrayList<>();
        for (SysRoleMenu templateMenu : templateMenus) {
            SysRoleMenu roleMenu = new SysRoleMenu();
            roleMenu.setRoleId(roleId);
            roleMenu.setMenuId(templateMenu.getMenuId());
            roleMenu.setProjectId(templateMenu.getProjectId());
            newMenus.add(roleMenu);
        }
        sysRoleMenuMapper.batchRoleMenuProjectId(newMenus);
    }

    private void syncUsers(List<CompanyUserRow> sourceRows, Map<String, Long> roleIdMap, CompanyOrgSyncResult result) {
        List<SysUser> existingUsers = sysUserMapper.selectUserAllList(new SysUser());
        Map<String, SysUser> usersByAuthId = existingUsers.stream()
                .filter(user -> StringUtils.isNotBlank(user.getAuthId()))
                .collect(Collectors.toMap(SysUser::getAuthId, user -> user, (left, right) -> left));
        Map<String, SysUser> usersByName = existingUsers.stream()
                .filter(user -> StringUtils.isNotBlank(user.getUserName()) && !"2".equals(user.getDelFlag()))
                .collect(Collectors.toMap(SysUser::getUserName, user -> user, (left, right) -> left));

        Set<String> sourceAuthIds = new HashSet<>();
        Set<String> sourceAccounts = new HashSet<>();
        for (CompanyUserRow row : sourceRows) {
            if (!isValidUserRow(row) || isAdminSourceRow(row)) {
                result.incrementUserSkipped();
                continue;
            }
            if (sourceAuthIds.contains(row.authId) || sourceAccounts.contains(row.account)) {
                result.incrementUserSkipped();
                continue;
            }
            sourceAuthIds.add(row.authId);
            sourceAccounts.add(row.account);

            SysUser existing = usersByAuthId.get(row.authId);
            if (existing == null) {
                SysUser sameNameUser = usersByName.get(row.account);
                if (sameNameUser != null && !isAdminUser(sameNameUser)) {
                    existing = sameNameUser;
                }
            }

            Long roleId = resolveUserRoleId(row, roleIdMap);
            SysUser target = toSysUser(row, existing);
            if (existing == null) {
                sysUserMapper.insertUser(target);
                result.incrementUserCreated();
            } else {
                target.setUserId(existing.getUserId());
                fillPasswordForUpdate(row, existing, target);
                if (userNeedsUpdate(existing, target)) {
                    sysUserMapper.updateUser(target);
                    result.incrementUserUpdated();
                } else {
                    result.incrementUserSkipped();
                }
            }
            usersByAuthId.put(row.authId, target);
            usersByName.put(row.account, target);

            if (syncUserRole(target.getUserId(), roleId)) {
                result.incrementUserRoleUpdated();
            }
        }

        if (properties.isDisableMissingUsers() || properties.isDisableNonSourceUsers()) {
            disableMissingUsers(existingUsers, sourceAuthIds, sourceAccounts, result);
        }
    }

    private SysUser toSysUser(CompanyUserRow row, SysUser existing) {
        SysUser user = new SysUser();
        user.setAuthId(row.authId);
        user.setDeptId(row.deptId);
        user.setUserName(row.account);
        user.setNickName(limit(firstNonBlank(row.nickName, row.account), NICK_NAME_MAX_LENGTH));
        user.setPhonenumber(row.mobile);
        user.setSex("2");
        user.setStatus(mapUserStatus(row.sourceStatus));
        user.setDelFlag("0");
        user.setCreateBy(properties.getOperator());
        user.setUpdateBy(properties.getOperator());
        user.setRemark(SYNC_REMARK + " [" + SYNC_MARK + "]");
        if (existing == null) {
            user.setPassword(encodePassword(firstNonBlank(row.password, properties.getFallbackPassword())));
        }
        return user;
    }

    private void fillPasswordForUpdate(CompanyUserRow row, SysUser existing, SysUser target) {
        SysUser fullExisting = existing;
        if (StringUtils.isBlank(fullExisting.getPassword()) && fullExisting.getUserId() != null) {
            fullExisting = sysUserMapper.selectUserById(fullExisting.getUserId());
        }
        if (StringUtils.isNotBlank(row.password)) {
            if (fullExisting == null || !passwordMatches(row.password, fullExisting.getPassword())) {
                target.setPassword(encodePassword(row.password));
            }
        } else if ((fullExisting == null || StringUtils.isBlank(fullExisting.getPassword()))
                && StringUtils.isNotBlank(properties.getFallbackPassword())) {
            target.setPassword(encodePassword(properties.getFallbackPassword()));
        }
    }

    private boolean syncUserRole(Long userId, Long roleId) {
        if (userId == null || roleId == null) {
            return false;
        }
        List<Long> existingRoleIds = sysRoleMapper.selectRoleListByUserId(userId);
        if (existingRoleIds != null && existingRoleIds.size() == 1 && Objects.equals(existingRoleIds.get(0), roleId)) {
            return false;
        }
        sysUserRoleMapper.deleteUserRoleByUserId(userId);
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        sysUserRoleMapper.batchUserRole(Collections.singletonList(userRole));
        return true;
    }

    private void disableMissingUsers(List<SysUser> existingUsers, Set<String> sourceAuthIds, Set<String> sourceAccounts, CompanyOrgSyncResult result) {
        for (SysUser existing : existingUsers) {
            if (isAdminUser(existing)
                    || sourceAuthIds.contains(existing.getAuthId())
                    || sourceAccounts.contains(existing.getUserName())) {
                continue;
            }
            if ("2".equals(existing.getDelFlag())) {
                continue;
            }
            if (!properties.isDisableNonSourceUsers()
                    && (StringUtils.isBlank(existing.getAuthId())
                    || !isSyncedByCompany(existing.getCreateBy(), existing.getUpdateBy(), existing.getRemark()))) {
                continue;
            }
            SysUser disabled = new SysUser(existing.getUserId());
            disabled.setStatus(UserConstants.USER_DISABLE);
            disabled.setDelFlag("2");
            disabled.setUpdateBy(properties.getOperator());
            disabled.setRemark(SYNC_REMARK + "，源表已不存在 [" + SYNC_MARK + "]");
            sysUserMapper.updateUser(disabled);
            result.incrementUserDisabled();
        }
    }

    private boolean userNeedsUpdate(SysUser existing, SysUser target) {
        return !Objects.equals(existing.getDeptId(), target.getDeptId())
                || !Objects.equals(StringUtils.defaultString(existing.getAuthId()), target.getAuthId())
                || !Objects.equals(StringUtils.defaultString(existing.getUserName()), target.getUserName())
                || !Objects.equals(StringUtils.defaultString(existing.getNickName()), target.getNickName())
                || !Objects.equals(StringUtils.defaultString(existing.getPhonenumber()), StringUtils.defaultString(target.getPhonenumber()))
                || !Objects.equals(StringUtils.defaultString(existing.getStatus()), target.getStatus())
                || !Objects.equals(StringUtils.defaultString(existing.getDelFlag()), target.getDelFlag())
                || !Objects.equals(StringUtils.defaultString(existing.getRemark()), target.getRemark())
                || StringUtils.isNotBlank(target.getPassword());
    }

    private Long resolveUserRoleId(CompanyUserRow row, Map<String, Long> roleIdMap) {
        String roleName = normalizedRoleName(row.roleName);
        Long roleId = StringUtils.isNotBlank(roleName) ? roleIdMap.get(roleName) : null;
        if (roleId != null) {
            return roleId;
        }
        String defaultRoleName = normalizedRoleName(properties.getDefaultRoleName());
        roleId = StringUtils.isNotBlank(defaultRoleName) ? roleIdMap.get(defaultRoleName) : null;
        return roleId == null ? properties.getDefaultRoleId() : roleId;
    }

    private String validateSourceTable(String sourceTable) {
        String table = StringUtils.trim(sourceTable);
        if (StringUtils.isBlank(table)) {
            throw new ServiceException("Company org sync source table is empty");
        }
        if (!SOURCE_TABLE_PATTERN.matcher(table).matches()) {
            throw new ServiceException("Company org sync source table is invalid");
        }
        return table;
    }

    private boolean isValidUserRow(CompanyUserRow row) {
        return row != null
                && StringUtils.isNotBlank(row.authId)
                && StringUtils.isNotBlank(row.mobile)
                && row.deptId != null
                && row.deptId > 0;
    }

    private Long resolveParentId(CompanyUserRow row) {
        if (row.parentPath != null && !row.parentPath.isEmpty()) {
            Long parentId = row.parentPath.get(row.parentPath.size() - 1);
            if (parentId != null && parentId > 0 && !parentId.equals(row.deptId)) {
                return parentId;
            }
        }
        if (row.sourceParentId != null && row.sourceParentId > 0 && !row.sourceParentId.equals(row.deptId)) {
            return row.sourceParentId;
        }
        return 0L;
    }

    private List<Long> parseParentPath(String parentIds, Long deptId) {
        List<Long> path = new ArrayList<>();
        if (StringUtils.isBlank(parentIds)) {
            return path;
        }
        String[] parts = parentIds.split(",");
        for (String part : parts) {
            Long value = toLong(part);
            if (value != null && value >= 0) {
                path.add(value);
            }
        }
        while (!path.isEmpty() && Objects.equals(path.get(path.size() - 1), deptId)) {
            path.remove(path.size() - 1);
        }
        return path;
    }

    private String normalizeAncestors(List<Long> path) {
        List<Long> ancestors = new ArrayList<>();
        if (path != null) {
            ancestors.addAll(path.stream().filter(item -> item != null && item >= 0).collect(Collectors.toList()));
        }
        if (ancestors.isEmpty() || !Objects.equals(ancestors.get(0), 0L)) {
            ancestors.add(0, 0L);
        }
        return ancestors.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private Long previousPositive(List<Long> path, int index) {
        for (int i = index - 1; i >= 0; i--) {
            Long value = path.get(i);
            if (value != null && value > 0) {
                return value;
            }
        }
        return 0L;
    }

    private String mapUserStatus(String sourceStatus) {
        return "1".equals(StringUtils.trim(sourceStatus)) ? UserConstants.NORMAL : UserConstants.USER_DISABLE;
    }

    private boolean isAdminAccount(String userName) {
        return "admin".equalsIgnoreCase(StringUtils.trim(userName));
    }

    private boolean isAdminSourceRow(CompanyUserRow row) {
        return row != null && isAdminAccount(row.sourceUserName);
    }

    private boolean isAdminUser(SysUser user) {
        return user != null && (SysUser.isAdmin(user.getUserId()) || isAdminAccount(user.getUserName()));
    }

    private boolean isGlobalRole(SysRole role) {
        return role != null && (role.getProjectId() == null || role.getProjectId() == 0L);
    }

    private boolean isSyncedByCompany(String createBy, String updateBy, String remark) {
        return Objects.equals(properties.getOperator(), createBy)
                || Objects.equals(properties.getOperator(), updateBy)
                || (remark != null && remark.contains(SYNC_MARK));
    }

    private String normalizedRoleName(String roleName) {
        return limit(StringUtils.trim(roleName), ROLE_NAME_MAX_LENGTH);
    }

    private String generateRoleKey(String roleName, Set<String> existingKeys) {
        String hash = Integer.toHexString(roleName.hashCode()).replace("-", "n");
        String prefix = StringUtils.defaultIfBlank(properties.getGeneratedRoleKeyPrefix(), "company_role");
        String roleKey = limit(prefix + "_" + hash, ROLE_KEY_MAX_LENGTH);
        int index = 1;
        while (existingKeys.contains(roleKey)) {
            roleKey = limit(prefix + "_" + hash + "_" + index++, ROLE_KEY_MAX_LENGTH);
        }
        return roleKey;
    }

    private String encodePassword(String password) {
        String value = StringUtils.trim(password);
        if (StringUtils.isBlank(value)) {
            return "";
        }
        return isBcrypt(value) ? value : SecurityUtils.encryptPassword(value);
    }

    private boolean passwordMatches(String sourcePassword, String existingPassword) {
        String source = StringUtils.trim(sourcePassword);
        if (StringUtils.isBlank(source) || StringUtils.isBlank(existingPassword)) {
            return false;
        }
        if (isBcrypt(source)) {
            return Objects.equals(source, existingPassword);
        }
        try {
            return SecurityUtils.matchesPassword(source, existingPassword);
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean isBcrypt(String password) {
        return StringUtils.isNotBlank(password) && BCRYPT_PATTERN.matcher(password).matches();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = StringUtils.trim(String.valueOf(value));
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String toText(Object value) {
        return value == null ? "" : StringUtils.trim(String.valueOf(value));
    }

    private String normalizeMobile(String mobile) {
        String value = StringUtils.trim(mobile);
        return value.length() > 11 ? "" : value;
    }

    private Long defaultLong(Long value, Long defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return StringUtils.trim(value);
            }
        }
        return "";
    }

    private String limit(String value, int maxLength) {
        String text = StringUtils.defaultString(StringUtils.trim(value));
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private static class CompanyUserRow {
        private String authId;
        private String employeeId;
        private String account;
        private String sourceUserName;
        private String nickName;
        private String mobile;
        private String sourceStatus;
        private Long deptId;
        private String deptName;
        private Long sourceParentId;
        private List<Long> parentPath = new ArrayList<>();
        private String password;
        private String roleName;
    }

    private static class CompanyDept {
        private Long deptId;
        private Long parentId;
        private String ancestors;
        private String deptName;
        private Integer orderNum;
        private boolean placeholder;

        private CompanyDept(Long deptId, Long parentId, String ancestors, String deptName, Integer orderNum, boolean placeholder) {
            this.deptId = deptId;
            this.parentId = parentId;
            this.ancestors = ancestors;
            this.deptName = deptName;
            this.orderNum = orderNum;
            this.placeholder = placeholder;
        }
    }
}
