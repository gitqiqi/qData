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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Company organization/user scheduled sync.
 */
@Component
public class CompanyOrgSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(CompanyOrgSyncScheduler.class);

    @Resource
    private CompanyOrgSyncProperties properties;
    @Resource
    private ICompanyOrgSyncService companyOrgSyncService;

    @Scheduled(cron = "${qdata.company-org-sync.cron:0 0 * * * ?}")
    public void sync() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            companyOrgSyncService.syncFromSource();
        } catch (Exception exception) {
            log.error("Company organization/user scheduled sync failed", exception);
        }
    }
}
