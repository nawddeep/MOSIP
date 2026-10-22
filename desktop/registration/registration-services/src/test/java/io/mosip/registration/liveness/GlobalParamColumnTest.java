package io.mosip.registration.liveness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.mosip.registration.livenessmanager.config.CanonicalJson;
import io.mosip.registration.livenessmanager.desktop.DesktopLivenessConfigAdapter;

/**
 * {@code REG.GLOBAL_PARAM.VAL} is {@code VARCHAR(512)} and a schema change is not allowed, so the synced liveness
 * config has to be compact. This runs against the client's real DDL (from the product {@code initial.sql}).
 */
public class GlobalParamColumnTest {

	private static final String COMPACT = "{\"schemaVersion\":1,\"configVersion\":\"c1\",\"policies\":{"
			+ "\"resident\":{\"enabled\":true,\"challengeCount\":2},"
			+ "\"operator\":{\"enabled\":true},\"supervisor\":{\"enabled\":true}}}";

	private static EmbeddedDataSource derby;

	@BeforeClass
	public static void start() throws Exception {
		derby = new EmbeddedDataSource();
		derby.setDatabaseName("memory:liveness-param-" + UUID.randomUUID());
		derby.setCreateDatabase("create");
		try (Connection c = derby.getConnection(); Statement s = c.createStatement()) {
			s.execute("CREATE SCHEMA REG");
			s.execute(globalParamDdl());
		}
	}

	private static String globalParamDdl() throws Exception {
		try (InputStream in = new FileInputStream(new File("src/main/resources/initial.sql"));
				BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				if (line.startsWith("CREATE TABLE \"REG\".\"GLOBAL_PARAM\"")) {
					return line.trim().replaceAll(";$", "");
				}
			}
		}
		throw new IllegalStateException("REG.GLOBAL_PARAM not found in initial.sql");
	}

	private static String sign(String json) {
		JsonObject o = JsonParser.parseString(json).getAsJsonObject();
		JsonObject integrity = new JsonObject();
		integrity.addProperty("algorithm", "SHA-256");
		integrity.addProperty("value", CanonicalJson.integrityDigest(o));
		o.add("integrity", integrity);
		return o.toString();
	}

	private static void insert(String value) throws SQLException {
		try (Connection c = derby.getConnection();
				PreparedStatement p = c.prepareStatement("INSERT INTO REG.GLOBAL_PARAM (CODE, NAME, VAL, TYP, LANG_CODE, "
						+ "IS_ACTIVE, CR_BY, CR_DTIMES, IS_DELETED) VALUES (?, ?, ?, 'CONFIGURATION', 'eng', true, "
						+ "'SYSTEM', CURRENT_TIMESTAMP, false)")) {
			String code = DesktopLivenessConfigAdapter.GLOBAL_PARAM_KEY + "." + UUID.randomUUID().toString().substring(0, 8);
			p.setString(1, code);
			p.setString(2, code);
			p.setString(3, value);
			p.executeUpdate();
		}
	}

	@Test
	public void aSignedCompactConfigFitsTheRealColumnAndComesBackIntact() throws Exception {
		String signed = sign(COMPACT);
		assertTrue("length " + signed.length(), signed.length() <= 512);
		insert(signed);
		try (Connection c = derby.getConnection(); Statement s = c.createStatement();
				ResultSet rs = s.executeQuery("SELECT VAL FROM REG.GLOBAL_PARAM WHERE VAL = '" + signed.replace("'", "''")
						+ "'")) {
			assertTrue(rs.next());
			assertEquals(signed, rs.getString(1));
		}
		System.out.println("Signed compact liveness config: " + signed.length() + " of 512 characters in REG.GLOBAL_PARAM.VAL");
	}

	@Test
	public void theFullDefaultConfigDoesNotFit() throws Exception {
		String full = DesktopLivenessConfigAdapter.readBundledConfigFromClasspath();
		assertTrue("bundled default is " + full.length() + " characters", full.length() > 512);
		try {
			insert(full);
			fail("a value longer than 512 characters must be rejected by the real column");
		} catch (SQLException expected) {
			assertFalse(expected.getMessage().isEmpty());
		}
	}
}
