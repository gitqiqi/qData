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

package tech.qiantong.qdata.module.system.service.company;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Company organization/user sync configuration.
 */
@Component
@ConfigurationProperties(prefix = "qdata.company-org-sync")
public class CompanyOrgSyncProperties {

    /**
     * Enable scheduled sync. Manual sync endpoint is available regardless of this switch.
     */
    private boolean enabled = false;

    /**
     * Saved qData datasource ID. Takes precedence over datasourceName when configured.
     */
    private Long datasourceId;

    /**
     * Saved qData datasource name for the PostgreSQL business database.
     */
    private String datasourceName = "db";

    /**
     * Read-only source table in the PostgreSQL business database.
     */
    private String sourceTable = "bi.dim_org_admin_user_info_hf";

    /**
     * Fallback password for rows whose source password is blank.
     */
    private String fallbackPassword = "qdata@123";

    /**
     * Fallback qData role ID used when source role_name is blank or cannot be resolved.
     */
    private Long defaultRoleId = 7L;

    /**
     * Fallback qData role name used when source role_name is blank.
     */
    private String defaultRoleName = "体验用户";

    /**
     * Role whose menu permissions are copied when a new source role_name is created in qData.
     */
    private Long roleMenuTemplateRoleId = 7L;

    /**
     * Create missing qData roles from source role_name.
     */
    private boolean autoCreateRole = true;

    /**
     * Disable qData users that were previously synced but no longer appear in the source table.
     */
    private boolean disableMissingUsers = true;

    /**
     * Disable existing qData non-admin users that do not appear in the source table.
     */
    private boolean disableNonSourceUsers = true;

    /**
     * Disable qData departments that were previously synced but no longer appear in the source table.
     */
    private boolean disableMissingDepartments = true;

    /**
     * Disable existing qData departments that do not appear in the source organization tree.
     */
    private boolean disableNonSourceDepartments = true;


    /**
     * Operator name written to qData audit fields.
     */
    private String operator = "company-org-sync";

    /**
     * Prefix used when generating role_key for roles created from source role_name.
     */
    private String generatedRoleKeyPrefix = "company_role";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getDatasourceId() {
        return datasourceId;
    }

    public void setDatasourceId(Long datasourceId) {
        this.datasourceId = datasourceId;
    }

    public String getDatasourceName() {
        return datasourceName;
    }

    public void setDatasourceName(String datasourceName) {
        this.datasourceName = datasourceName;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public String getFallbackPassword() {
        return fallbackPassword;
    }

    public void setFallbackPassword(String fallbackPassword) {
        this.fallbackPassword = fallbackPassword;
    }

    public Long getDefaultRoleId() {
        return defaultRoleId;
    }

    public void setDefaultRoleId(Long defaultRoleId) {
        this.defaultRoleId = defaultRoleId;
    }

    public String getDefaultRoleName() {
        return defaultRoleName;
    }

    public void setDefaultRoleName(String defaultRoleName) {
        this.defaultRoleName = defaultRoleName;
    }

    public Long getRoleMenuTemplateRoleId() {
        return roleMenuTemplateRoleId;
    }

    public void setRoleMenuTemplateRoleId(Long roleMenuTemplateRoleId) {
        this.roleMenuTemplateRoleId = roleMenuTemplateRoleId;
    }

    public boolean isAutoCreateRole() {
        return autoCreateRole;
    }

    public void setAutoCreateRole(boolean autoCreateRole) {
        this.autoCreateRole = autoCreateRole;
    }

    public boolean isDisableMissingUsers() {
        return disableMissingUsers;
    }

    public void setDisableMissingUsers(boolean disableMissingUsers) {
        this.disableMissingUsers = disableMissingUsers;
    }

    public boolean isDisableNonSourceUsers() {
        return disableNonSourceUsers;
    }

    public void setDisableNonSourceUsers(boolean disableNonSourceUsers) {
        this.disableNonSourceUsers = disableNonSourceUsers;
    }

    public boolean isDisableMissingDepartments() {
        return disableMissingDepartments;
    }

    public void setDisableMissingDepartments(boolean disableMissingDepartments) {
        this.disableMissingDepartments = disableMissingDepartments;
    }

    public boolean isDisableNonSourceDepartments() {
        return disableNonSourceDepartments;
    }

    public void setDisableNonSourceDepartments(boolean disableNonSourceDepartments) {
        this.disableNonSourceDepartments = disableNonSourceDepartments;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getGeneratedRoleKeyPrefix() {
        return generatedRoleKeyPrefix;
    }

    public void setGeneratedRoleKeyPrefix(String generatedRoleKeyPrefix) {
        this.generatedRoleKeyPrefix = generatedRoleKeyPrefix;
    }
}
