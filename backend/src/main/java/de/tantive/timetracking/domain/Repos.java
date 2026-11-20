package de.tantive.timetracking.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.*;

public final class Repos {
    private Repos() {}

    public interface Users extends JpaRepository<AppUser, Long> {
        Optional<AppUser> findByEmailIgnoreCase(String email);
        Optional<AppUser> findByInviteToken(String t);
        Optional<AppUser> findByResetToken(String t);
    }
    public interface Teams extends JpaRepository<Team, Long> { Optional<Team> findByNameIgnoreCase(String n); }
    public interface Projects extends JpaRepository<Project, Long> { Optional<Project> findByCodeIgnoreCase(String c); }
    public interface Activities extends JpaRepository<Activity, Long> {}
    public interface Schedules extends JpaRepository<TargetSchedule, Long> {
        List<TargetSchedule> findByUserIdOrderByValidFromAsc(Long userId);
    }
    public interface Holidays extends JpaRepository<Holiday, Long> {
        List<Holiday> findByDateBetween(LocalDate from, LocalDate to);
    }
    public interface WorkDays extends JpaRepository<WorkDay, Long> {
        Optional<WorkDay> findByUserIdAndWorkDate(Long userId, LocalDate d);
        List<WorkDay> findByUserIdAndWorkDateBetweenOrderByWorkDate(Long userId, LocalDate from, LocalDate to);
        List<WorkDay> findByWorkDateBetween(LocalDate from, LocalDate to);
        Optional<WorkDay> findFirstByUserIdAndWorkDateLessThanOrderByWorkDateDesc(Long userId, LocalDate d);
    }
    public interface Entries extends JpaRepository<ProjectEntry, Long> {
        List<ProjectEntry> findByUserIdAndEntryDateBetweenOrderByEntryDateAscStartAtAscIdAsc(Long userId, LocalDate from, LocalDate to);
        List<ProjectEntry> findByEntryDateBetween(LocalDate from, LocalDate to);
        List<ProjectEntry> findByUserIdAndEndAtIsNullAndStartAtIsNotNull(Long userId);
        List<ProjectEntry> findByProjectId(Long projectId);
        @Query("select e from ProjectEntry e where e.userId = ?1 order by e.id desc")
        List<ProjectEntry> recentForUser(Long userId, org.springframework.data.domain.Pageable p);
    }
    public interface Absences extends JpaRepository<Absence, Long> {
        Optional<Absence> findByUserIdAndAbsenceDate(Long userId, LocalDate d);
        List<Absence> findByUserIdAndAbsenceDateBetween(Long userId, LocalDate from, LocalDate to);
        List<Absence> findByAbsenceDateBetween(LocalDate from, LocalDate to);
    }
    public interface Locks extends JpaRepository<PeriodLock, Long> {
        List<PeriodLock> findByLockYearAndLockMonthAndActiveTrue(int y, int m);
    }
    public interface Audits extends JpaRepository<AuditLog, Long> {
        List<AuditLog> findByEntityTypeAndEntityIdOrderByIdDesc(String type, Long id);
        List<AuditLog> findByKindOrderByIdDesc(AuditLog.Kind k, org.springframework.data.domain.Pageable p);
        List<AuditLog> findBySubjectUserIdAndKindOrderByIdDesc(Long subject, AuditLog.Kind k);
    }
    public interface Mails extends JpaRepository<MailMessage, Long> {
        List<MailMessage> findAllByOrderByIdDesc();
    }
    public interface Notifications extends JpaRepository<AppNotification, Long> {
        List<AppNotification> findByUserIdOrderByIdDesc(Long userId);
    }
    public interface BudgetWarnings extends JpaRepository<BudgetWarning, Long> {
        boolean existsByProjectIdAndPeriodKeyAndThreshold(Long p, String k, int t);
    }
}
