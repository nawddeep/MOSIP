package io.mosip.registration.liveness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import io.mosip.kernel.auditmanager.config.AuditConfig;
import io.mosip.kernel.core.auditmanager.spi.AuditHandler;
import io.mosip.registration.audit.AuditManagerSerivceImpl;
import io.mosip.registration.constants.AuditEvent;
import io.mosip.registration.constants.Components;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.livenessmanager.audit.LivenessAuditRecord;
import io.mosip.registration.livenessmanager.desktop.DesktopAuditEvent;
import io.mosip.registration.livenessmanager.desktop.DesktopLivenessAuditLogger;
import io.mosip.registration.livenessmanager.model.LivenessState;
import io.mosip.registration.livenessmanager.model.Workflow;

/**
 * The real audit write path against the client's own table: MOSIP's {@code AuditManagerSerivceImpl}, the kernel's
 * {@code AuditHandlerImpl} (JPA), and embedded Derby with {@code AUDIT.APP_AUDIT_LOG} created from the DDL in the
 * client's {@code initial.sql}. The liveness records go in through {@link MosipDesktopAuditManagerService} and are
 * read back with SQL.
 */
public class AuditRowsIntegrationTest {

	private static EmbeddedDataSource derby;
	private static AnnotationConfigApplicationContext spring;
	private static AuditManagerSerivceImpl auditService;

	@Configuration
	@EnableTransactionManagement
	@EnableJpaRepositories(basePackages = "io.mosip.kernel.auditmanager.repository")
	@Import(AuditConfig.class)
	static class Cfg {
		@Bean
		DataSource dataSource() {
			return derby;
		}

		@Bean
		LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
			LocalContainerEntityManagerFactoryBean f = new LocalContainerEntityManagerFactoryBean();
			f.setDataSource(ds);
			f.setPackagesToScan("io.mosip.kernel.auditmanager.entity");
			f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
			Properties p = new Properties();
			p.put("hibernate.dialect", "org.hibernate.dialect.DerbyTenSevenDialect");
			p.put("hibernate.hbm2ddl.auto", "none");
			f.setJpaProperties(p);
			return f;
		}

		@Bean
		PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
			return new JpaTransactionManager(emf);
		}
	}

	@BeforeClass
	public static void start() throws Exception {
		derby = new EmbeddedDataSource();
		derby.setDatabaseName("memory:liveness-audit-" + UUID.randomUUID());
		derby.setCreateDatabase("create");
		try (Connection c = derby.getConnection(); Statement s = c.createStatement()) {
			s.execute("CREATE SCHEMA AUDIT");
			s.execute(auditTableDdl());
		}
		spring = new AnnotationConfigApplicationContext(Cfg.class);

		Map<String, Object> app = new HashMap<>();
		app.put(RegistrationConstants.APP_ID, "REG");
		app.put(RegistrationConstants.APP_NAME, "REGISTRATION");
		app.put(RegistrationConstants.DEFAULT_HOST_NAME, "localhost");
		ApplicationContext.getInstance(); // creates the singleton that map() reads
		ApplicationContext.setApplicationMap(app);

		auditService = new AuditManagerSerivceImpl();
		Field handler = AuditManagerSerivceImpl.class.getDeclaredField("auditHandler");
		handler.setAccessible(true);
		handler.set(auditService, spring.getBean(AuditHandler.class));
	}

	@AfterClass
	public static void stop() {
		if (spring != null) {
			spring.close();
		}
	}

	/** The CREATE TABLE statement for AUDIT.APP_AUDIT_LOG, taken from the client's own initial.sql. */
	private static String auditTableDdl() throws Exception {
		// The product file, not the copy under src/test/resources that shadows it on the test classpath.
		try (InputStream in = new FileInputStream(new File("src/main/resources/initial.sql"));
				BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				if (line.startsWith("CREATE TABLE \"AUDIT\".\"APP_AUDIT_LOG\"")) {
					return line.trim().replaceAll(";$", "");
				}
			}
		}
		throw new IllegalStateException("AUDIT.APP_AUDIT_LOG not found in initial.sql");
	}

	private static List<String[]> rows(String where) throws Exception {
		List<String[]> out = new ArrayList<>();
		try (Connection c = derby.getConnection(); Statement s = c.createStatement();
				ResultSet rs = s.executeQuery("SELECT EVENT_ID, EVENT_TYPE, EVENT_NAME, MODULE_ID, REF_ID, REF_ID_TYPE, "
						+ "SESSION_USER_ID, LOG_DESC FROM AUDIT.APP_AUDIT_LOG WHERE " + where + " ORDER BY LOG_DTIMES")) {
			while (rs.next()) {
				out.add(new String[] { rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
						rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8) });
			}
		}
		return out;
	}

	@Test
	public void everyLivenessRecordTypeLandsInTheClientTableWithItsRegLivId() throws Exception {
		String session = UUID.randomUUID().toString();
		DesktopLivenessAuditLogger logger = new DesktopLivenessAuditLogger(
				new MosipDesktopAuditManagerService(auditService));
		long t = System.currentTimeMillis();
		logger.log(LivenessAuditRecord.builder(LivenessAuditRecord.Type.SESSION_STARTED, t)
				.session(session, Workflow.RESIDENT, 1).build());
		logger.log(LivenessAuditRecord.builder(LivenessAuditRecord.Type.STATE_TRANSITION, t + 1)
				.session(session, Workflow.RESIDENT, 1)
				.transition(LivenessState.POSITIONING, LivenessState.PASSIVE_CHECK).build());
		logger.log(LivenessAuditRecord.builder(LivenessAuditRecord.Type.PASSIVE_DECISION, t + 2)
				.session(session, Workflow.RESIDENT, 1).outcome(LivenessAuditRecord.Outcome.PASS)
				.detail("score", 0.94).build());
		logger.log(LivenessAuditRecord.builder(LivenessAuditRecord.Type.CHALLENGE_RESULT, t + 3)
				.session(session, Workflow.RESIDENT, 1).outcome(LivenessAuditRecord.Outcome.PASS).build());
		logger.log(LivenessAuditRecord.builder(LivenessAuditRecord.Type.CONFIG_APPLIED, t + 4)
				.session(session, Workflow.RESIDENT, 1).detail("configVersion", "c1").build());
		// Operator and supervisor map to their own existing modules.
		logger.log(LivenessAuditRecord.builder(LivenessAuditRecord.Type.SESSION_ENDED, t + 5)
				.session(session, Workflow.OPERATOR, 1).outcome(LivenessAuditRecord.Outcome.PASS).build());
		logger.log(LivenessAuditRecord.builder(LivenessAuditRecord.Type.SESSION_ENDED, t + 6)
				.session(session, Workflow.SUPERVISOR, 1).outcome(LivenessAuditRecord.Outcome.FAIL).build());

		List<String[]> rows = rows("REF_ID = '" + session + "'");
		assertEquals(7, rows.size());
		String[] ids = { "REG-LIV-001", "REG-LIV-002", "REG-LIV-003", "REG-LIV-004", "REG-LIV-005", "REG-LIV-001",
				"REG-LIV-001" };
		String[] modules = { Components.REG_BIOMETRICS.getId(), Components.REG_BIOMETRICS.getId(),
				Components.REG_BIOMETRICS.getId(), Components.REG_BIOMETRICS.getId(),
				Components.REG_BIOMETRICS.getId(), Components.LOGIN.getId(), Components.REG_OS_AUTH.getId() };
		for (int i = 0; i < rows.size(); i++) {
			String[] r = rows.get(i);
			assertEquals(ids[i], r[0]);
			assertEquals(modules[i], r[3]);
			assertEquals("session id is the reference", session, r[4]);
			assertEquals("LIVENESS_SESSION_ID", r[5]);
			assertEquals("the logged-in user is NA before login, as for every other row", "NA", r[6]);
			assertTrue(r[7], r[7].contains("session=" + session));
		}
		assertEquals("USER", rows.get(0)[1]);
		assertEquals("SYSTEM", rows.get(1)[1]);
		assertEquals("FACE_LIVENESS_SESSION", rows.get(0)[2]);
		assertTrue(rows.get(2)[7], rows.get(2)[7].contains("score=0.94"));

		System.out.println("Audit rows written by the real AuditManagerService into AUDIT.APP_AUDIT_LOG:");
		for (String[] r : rows) {
			System.out.println("  event_id=" + r[0] + " event_type=" + r[1] + " event_name=" + r[2] + " module_id="
					+ r[3] + " ref_id=" + r[4].substring(0, 8) + "... ref_id_type=" + r[5] + "\n    log_desc="
					+ r[7].replace(session, session.substring(0, 8) + "..."));
		}
	}

	@Test
	public void aBlockedCaptureIsAuditedAsRegLiv006ThroughTheSameChain() throws Exception {
		DesktopLivenessRuntime runtime = new DesktopLivenessRuntime(code -> null,
				new MosipDesktopAuditManagerService(auditService), Files.createTempDirectory("lv").toFile());
		assertFalse(runtime.checkAndConsumePass(Workflow.RESIDENT, "individualBiometrics"));

		List<String[]> rows = rows("EVENT_ID = 'REG-LIV-006'");
		assertFalse(rows.isEmpty());
		String[] r = rows.get(rows.size() - 1);
		assertEquals("FACE_LIVENESS_GATE_BLOCKED", r[2]);
		assertEquals("SYSTEM", r[1]);
		assertTrue(r[7], r[7].contains("individualBiometrics"));
		System.out.println("  event_id=" + r[0] + " event_type=" + r[1] + " log_desc=" + r[7]);
	}

	@Test
	public void descriptionsAreCutAt2048CharactersByTheRealColumn() throws Exception {
		String session = "long-" + UUID.randomUUID();
		StringBuilder big = new StringBuilder("session=" + session + ";note=");
		for (int i = 0; i < 6000; i++) {
			big.append(i % 7 == 0 ? ' ' : 'a'); // word breaks: this is not an image-like run
		}
		new MosipDesktopAuditManagerService(auditService).audit(DesktopAuditEvent.FACE_LIVENESS_STATE, "m", "n",
				big.toString());

		List<String[]> rows = rows("REF_ID = '" + session + "'");
		assertEquals(1, rows.size());
		assertEquals(2048, rows.get(0)[7].length());
	}

	@Test
	public void existingFourArgumentAuditsStillWorkInTheSameTable() throws Exception {
		auditService.audit(AuditEvent.NAV_HOME, Components.LOGIN, "someone", "USER_ID");
		List<String[]> rows = rows("EVENT_ID = 'REG-NAV-007'");
		assertFalse(rows.isEmpty());
		// Unchanged behaviour: with nobody logged in (user "NA") the caller's reference is stored as given; with a
		// logged-in user the four-argument method replaces it by that user (see AuditManagerDescriptionOverloadTest).
		assertEquals("someone", rows.get(rows.size() - 1)[4]);
		assertEquals("USER_ID", rows.get(rows.size() - 1)[5]);
	}
}
