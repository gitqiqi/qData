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

import java.util.Date;

/**
 * Company organization/user sync result.
 */
public class CompanyOrgSyncResult {

    private Date syncTime = new Date();
    private String datasource;
    private String sourceTable;
    private int sourceRows;
    private int deptCreated;
    private int deptUpdated;
    private int deptSkipped;
    private int deptDisabled;
    private int roleCreated;
    private int userCreated;
    private int userUpdated;
    private int userSkipped;
    private int userDisabled;
    private int userRoleUpdated;

    public Date getSyncTime() {
        return syncTime;
    }

    public void setSyncTime(Date syncTime) {
        this.syncTime = syncTime;
    }

    public String getDatasource() {
        return datasource;
    }

    public void setDatasource(String datasource) {
        this.datasource = datasource;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public int getSourceRows() {
        return sourceRows;
    }

    public void setSourceRows(int sourceRows) {
        this.sourceRows = sourceRows;
    }

    public int getDeptCreated() {
        return deptCreated;
    }

    public void setDeptCreated(int deptCreated) {
        this.deptCreated = deptCreated;
    }

    public void incrementDeptCreated() {
        this.deptCreated++;
    }

    public int getDeptUpdated() {
        return deptUpdated;
    }

    public void setDeptUpdated(int deptUpdated) {
        this.deptUpdated = deptUpdated;
    }

    public void incrementDeptUpdated() {
        this.deptUpdated++;
    }

    public int getDeptSkipped() {
        return deptSkipped;
    }

    public void setDeptSkipped(int deptSkipped) {
        this.deptSkipped = deptSkipped;
    }

    public void incrementDeptSkipped() {
        this.deptSkipped++;
    }

    public int getDeptDisabled() {
        return deptDisabled;
    }

    public void setDeptDisabled(int deptDisabled) {
        this.deptDisabled = deptDisabled;
    }

    public void incrementDeptDisabled() {
        this.deptDisabled++;
    }

    public int getRoleCreated() {
        return roleCreated;
    }

    public void setRoleCreated(int roleCreated) {
        this.roleCreated = roleCreated;
    }

    public void incrementRoleCreated() {
        this.roleCreated++;
    }

    public int getUserCreated() {
        return userCreated;
    }

    public void setUserCreated(int userCreated) {
        this.userCreated = userCreated;
    }

    public void incrementUserCreated() {
        this.userCreated++;
    }

    public int getUserUpdated() {
        return userUpdated;
    }

    public void setUserUpdated(int userUpdated) {
        this.userUpdated = userUpdated;
    }

    public void incrementUserUpdated() {
        this.userUpdated++;
    }

    public int getUserSkipped() {
        return userSkipped;
    }

    public void setUserSkipped(int userSkipped) {
        this.userSkipped = userSkipped;
    }

    public void incrementUserSkipped() {
        this.userSkipped++;
    }

    public int getUserDisabled() {
        return userDisabled;
    }

    public void setUserDisabled(int userDisabled) {
        this.userDisabled = userDisabled;
    }

    public void incrementUserDisabled() {
        this.userDisabled++;
    }

    public int getUserRoleUpdated() {
        return userRoleUpdated;
    }

    public void setUserRoleUpdated(int userRoleUpdated) {
        this.userRoleUpdated = userRoleUpdated;
    }

    public void incrementUserRoleUpdated() {
        this.userRoleUpdated++;
    }
}
