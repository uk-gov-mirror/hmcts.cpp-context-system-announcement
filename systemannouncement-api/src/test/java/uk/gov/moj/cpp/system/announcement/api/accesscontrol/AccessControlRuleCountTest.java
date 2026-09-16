package uk.gov.moj.cpp.system.announcement.api.accesscontrol;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;

/**
 * Guards the access-control rulebase: asserts the kbase compiled at least one rule, so a silent
 * zero-rule load (BC-20, e.g. Drools 7→10 packaging drift) fails loudly rather than making the
 * deny-tests in {@link GetAnnouncementAndBannerAccessControlTest} pass vacuously.
 */
public class AccessControlRuleCountTest {

    @Test
    public void kieBaseShouldCompileAtLeastOneRule() {
        final long ruleCount = KieServices.get().getKieClasspathContainer()
                .getKieBase("SystemAnnouncement.API")
                .getKiePackages().stream()
                .mapToLong(kiePackage -> kiePackage.getRules().size())
                .sum();

        assertTrue(ruleCount > 0,
                "SystemAnnouncement.API kbase compiled 0 rules — access-control deny-tests would pass vacuously");
    }
}
