package com.cqu.drs;

import com.cqu.drs.model.User;
import com.cqu.drs.repository.AlertRepository;
import com.cqu.drs.repository.AuditRepository;
import com.cqu.drs.repository.DepartmentRepository;
import com.cqu.drs.repository.DisasterReportRepository;
import com.cqu.drs.repository.JsonDataStore;
import com.cqu.drs.repository.UserRepository;
import com.cqu.drs.service.AccountService;
import com.cqu.drs.service.AlertService;
import com.cqu.drs.service.AssessmentService;
import com.cqu.drs.service.AuditService;
import com.cqu.drs.service.AuthenticationService;
import com.cqu.drs.service.CoordinationService;
import com.cqu.drs.service.ReportService;
import com.cqu.drs.model.UserRole;

import java.util.Optional;

/**
 * Builds the repositories and services once and hands them to whichever
 * controller needs them, so that the wiring is done in one place rather than
 * repeated in every screen.
 *
 * A single shared instance is used because this is a desktop prototype with
 * one user at the keyboard. A server application would inject these rather
 * than reach for a shared instance.
 */
public final class AppContext {

    private static AppContext instance;

    private final AccountService accountService;
    private final AlertService alertService;
    private final AssessmentService assessmentService;
    private final AuditService auditService;
    private final AuthenticationService authenticationService;
    private final CoordinationService coordinationService;
    private final ReportService reportService;
    private final DepartmentRepository departmentRepository;

    private AppContext() {
        JsonDataStore store = new JsonDataStore();

        DisasterReportRepository reportRepository = new DisasterReportRepository(store);
        UserRepository userRepository = new UserRepository(store);
        AlertRepository alertRepository = new AlertRepository(store);
        AuditRepository auditRepository = new AuditRepository(store);
        this.departmentRepository = new DepartmentRepository(store);

        this.auditService = new AuditService(auditRepository);
        this.accountService = new AccountService(userRepository, auditService);
        this.authenticationService = new AuthenticationService(userRepository, auditService);
        this.reportService = new ReportService(reportRepository, auditService);
        this.assessmentService = new AssessmentService(reportRepository, auditService);
        this.coordinationService = new CoordinationService(
                reportRepository, departmentRepository, auditService);
        this.alertService = new AlertService(reportRepository, alertRepository, auditService);

        departmentRepository.seedIfEmpty();
        seedAdministratorIfNoUsers();
    }

    /**
     * The shared context, created on first use.
     *
     * @return the context
     */
    public static synchronized AppContext getInstance() {
        if (instance == null) {
            instance = new AppContext();
        }
        return instance;
    }

    /**
     * Creates a starting administrator account when no accounts exist yet, so
     * that the system can be signed into the first time it is run.
     */
    private void seedAdministratorIfNoUsers() {
        if (accountService.findAll().isEmpty()) {
            accountService.create("admin", "admin123", "System Administrator",
                    UserRole.ADMINISTRATOR, null, "system");
            accountService.create("coordinator", "coord123", "Emergency Coordinator",
                    UserRole.COORDINATOR, null, "system");
            accountService.create("fire.crew", "crew1234", "Fire and Emergency Crew",
                    UserRole.RESPONDER, "DEP-01", "system");
        }
    }

    public AccountService getAccountService() {
        return accountService;
    }

    public AlertService getAlertService() {
        return alertService;
    }

    public AssessmentService getAssessmentService() {
        return assessmentService;
    }

    public AuditService getAuditService() {
        return auditService;
    }

    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    public CoordinationService getCoordinationService() {
        return coordinationService;
    }

    public ReportService getReportService() {
        return reportService;
    }

    public DepartmentRepository getDepartmentRepository() {
        return departmentRepository;
    }

    /**
     * The user currently signed in, if any.
     *
     * @return the current user, or an empty Optional
     */
    public Optional<User> getCurrentUser() {
        return authenticationService.getCurrentUser();
    }

    /**
     * The username to record against an action, or null when nobody is signed
     * in, which is the case for a report lodged by a member of the public.
     *
     * @return the current username, or null
     */
    public String getCurrentUsername() {
        return getCurrentUser().map(User::getUsername).orElse(null);
    }
}
