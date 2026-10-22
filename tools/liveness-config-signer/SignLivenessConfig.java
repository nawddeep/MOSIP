import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.mosip.registration.livenessmanager.config.CanonicalJson;
import io.mosip.registration.livenessmanager.config.LivenessConfigParser;

/**
 * Signs a face-liveness config for the global parameter {@code mosip.registration.face.liveness.config}.
 *
 * <p>
 * Reads a config JSON (file argument, or standard input), adds the SHA-256 integrity block, validates it exactly as
 * the client will (schema keys, ranges, all three workflows), and prints the one-line value to store.
 * {@code global_param.val} is VARCHAR(512): list only the fields that differ from the defaults. A longer value is
 * reported and exits with status 2.
 *
 * <pre>
 *   java -cp registration-liveness-core.jar:gson.jar SignLivenessConfig [config.json]
 * </pre>
 */
public final class SignLivenessConfig {

	private static final int MAX_LENGTH = 512;

	private SignLivenessConfig() {
	}

	public static void main(String[] args) throws Exception {
		String json = args.length > 0 ? new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8)
				: readAll(System.in);
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		root.remove("integrity");
		JsonObject integrity = new JsonObject();
		integrity.addProperty("algorithm", "SHA-256");
		integrity.addProperty("value", CanonicalJson.integrityDigest(root));
		root.add("integrity", integrity);
		String signed = root.toString();

		try {
			// The client's own parser, with integrity required as for a synced config.
			new LivenessConfigParser().parse(signed, true);
		} catch (LivenessConfigParser.ConfigException e) {
			System.err.println("Rejected by the client's validation:");
			for (String error : e.getErrors()) {
				System.err.println("  " + error);
			}
			System.exit(1);
		}
		System.out.println(signed);
		System.err.println(signed.length() + " of " + MAX_LENGTH + " characters (global_param.val is VARCHAR(512))");
		if (signed.length() > MAX_LENGTH) {
			System.err.println("TOO LONG: list only the fields that differ from the defaults.");
			System.exit(2);
		}
	}

	private static String readAll(InputStream in) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[4096];
		int n;
		while ((n = in.read(buf)) > 0) {
			out.write(buf, 0, n);
		}
		return new String(out.toByteArray(), StandardCharsets.UTF_8);
	}
}
