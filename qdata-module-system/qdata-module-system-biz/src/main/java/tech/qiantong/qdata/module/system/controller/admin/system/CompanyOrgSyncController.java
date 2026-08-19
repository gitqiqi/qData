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

package tech.qiantong.qdata.module.system.controller.admin.system;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.qiantong.qdata.common.annotation.Log;
import tech.qiantong.qdata.common.core.domain.AjaxResult;
import tech.qiantong.qdata.common.enums.BusinessType;
import tech.qiantong.qdata.module.system.service.company.ICompanyOrgSyncService;

import javax.annotation.Resource;

/**
 * Company organization/user sync.
 */
@RestController
@RequestMapping("/system/company-org-sync")
public class CompanyOrgSyncController {

    @Resource
    private ICompanyOrgSyncService companyOrgSyncService;

    /**
     * Manually synchronize qData users/departments/roles from the read-only company PostgreSQL table.
     */
    @Log(title = "公司组织用户同步", businessType = BusinessType.IMPORT)
    @PreAuthorize("@ss.hasPermi('system:user:import')")
    @PostMapping("/sync")
    public AjaxResult sync() {
        return AjaxResult.success(companyOrgSyncService.syncFromSource());
    }
}
