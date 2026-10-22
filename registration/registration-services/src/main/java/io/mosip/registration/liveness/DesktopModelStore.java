package io.mosip.registration.liveness;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.mosip.registration.livenessmanager.api.LivenessEngine.EngineException;
import io.mosip.registration.livenessmanager.config.CanonicalJson;

/**
 * The model files the Desktop engine runs, each pinned by SHA-256 in
 * {@code /liveness/desktop-models/manifest.json}. A file whose hash differs from the manifest is never loaded:
 * the engine fails closed with an integrity {@link EngineException} (state ENGINE_ERROR, never a pass).
 *
 * <p>
 * The face detector and landmark models are loaded straight from verified bytes. The PAD library reads every
 * {@code *.onnx} in a folder, so those two files are copied (after verification) to
 * {@code <dataDirectory>/models/<id>/}; anything else found in that folder is removed.
 */
public final class DesktopModelStore {

	public static final String MANIFEST_RESOURCE = "/liveness/desktop-models/manifest.json";
	private static final String DESKTOP_MODELS = "/liveness/desktop-models/";

	/** One pinned model file. */
	public static final class Entry {
		/** Classpath location. */
		public final String resource;
		public final String sha256;
		public final String version;

		Entry(String resource, String sha256, String version) {
			this.resource = resource;
			this.sha256 = sha256;
			this.version = version;
		}

		String fileName() {
			return resource.substring(resource.lastIndexOf('/') + 1);
		}
	}

	/** Where model bytes come from (the classpath, or a fake in tests). */
	public interface ResourceReader {
		/** @return the stream, or {@code null} when the resource does not exist */
		InputStream open(String resource) throws IOException;
	}

	private final Entry faceDetector;
	private final Entry faceLandmarks;
	private final List<Entry> pad;
	private final int padMinFaceSize;
	private final int padIntraOpThreads;
	private final ResourceReader reader;
	private final File dataDirectory;

	DesktopModelStore(Entry faceDetector, Entry faceLandmarks, List<Entry> pad, int padMinFaceSize,
			int padIntraOpThreads, ResourceReader reader, File dataDirectory) {
		this.faceDetector = faceDetector;
		this.faceLandmarks = faceLandmarks;
		this.pad = Collections.unmodifiableList(new ArrayList<>(pad));
		this.padMinFaceSize = padMinFaceSize;
		this.padIntraOpThreads = padIntraOpThreads;
		this.reader = reader;
		this.dataDirectory = dataDirectory;
	}

	/** The bundled manifest and models, with PAD copies placed under {@code dataDirectory}. */
	public static DesktopModelStore bundled(File dataDirectory) throws EngineException {
		return load(DesktopModelStore.class::getResourceAsStream, dataDirectory);
	}

	public static DesktopModelStore load(ResourceReader reader, File dataDirectory) throws EngineException {
		try (InputStream in = reader.open(MANIFEST_RESOURCE)) {
			if (in == null) {
				throw new EngineException("Model manifest is missing", null, true);
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
					.getAsJsonObject();
			Entry detector = entry(root.getAsJsonObject("faceDetector"), DESKTOP_MODELS);
			Entry landmarks = entry(root.getAsJsonObject("faceLandmarks"), DESKTOP_MODELS);
			JsonObject padObject = root.getAsJsonObject("pad");
			List<Entry> pad = new ArrayList<>();
			JsonArray models = padObject.getAsJsonArray("models");
			for (JsonElement m : models) {
				pad.add(entry(m.getAsJsonObject(), ""));
			}
			if (pad.isEmpty()) {
				throw new EngineException("Model manifest lists no PAD models", null, true);
			}
			return new DesktopModelStore(detector, landmarks, pad, padObject.get("minFaceSize").getAsInt(),
					padObject.get("intraOpThreads").getAsInt(), reader, dataDirectory);
		} catch (IOException | RuntimeException e) {
			throw new EngineException("Model manifest could not be read", e, true);
		}
	}

	private static Entry entry(JsonObject o, String folder) {
		String resource = o.has("resource") ? o.get("resource").getAsString() : folder + o.get("file").getAsString();
		String version = o.has("version") ? o.get("version").getAsString() : resource;
		return new Entry(resource, o.get("sha256").getAsString().toLowerCase(java.util.Locale.ROOT), version);
	}

	public Entry getFaceDetector() {
		return faceDetector;
	}

	public Entry getFaceLandmarks() {
		return faceLandmarks;
	}

	public List<Entry> getPad() {
		return pad;
	}

	public int getPadMinFaceSize() {
		return padMinFaceSize;
	}

	public int getPadIntraOpThreads() {
		return padIntraOpThreads;
	}

	/** Reads the model and checks its SHA-256 against the manifest. */
	public byte[] loadVerified(Entry entry) throws EngineException {
		byte[] bytes;
		try (InputStream in = reader.open(entry.resource)) {
			if (in == null) {
				throw new EngineException("Model file missing: " + entry.fileName(), null, true);
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
			byte[] buf = new byte[64 * 1024];
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
			}
			bytes = out.toByteArray();
		} catch (IOException e) {
			throw new EngineException("Model file unreadable: " + entry.fileName(), e, true);
		}
		if (!entry.sha256.equalsIgnoreCase(CanonicalJson.sha256Hex(bytes))) {
			throw new EngineException("Model file failed its integrity check: " + entry.fileName(), null, true);
		}
		return bytes;
	}

	/** Verified copies of the PAD models in a folder of their own; returns that folder. */
	public File materializePad() throws EngineException {
		StringBuilder key = new StringBuilder();
		for (Entry m : pad) {
			key.append(m.sha256);
		}
		File dir = new File(new File(dataDirectory, "models"), CanonicalJson.sha256Hex(key.toString()).substring(0, 16));
		if (!dir.isDirectory() && !dir.mkdirs()) {
			throw new EngineException("Cannot create the model directory", null);
		}
		Set<String> expected = new HashSet<>();
		for (Entry m : pad) {
			String name = m.fileName();
			expected.add(name);
			File target = new File(dir, name);
			if (target.isFile() && m.sha256.equalsIgnoreCase(hashOf(target))) {
				continue;
			}
			byte[] bytes = loadVerified(m);
			File tmp = new File(dir, name + ".tmp");
			try (FileOutputStream out = new FileOutputStream(tmp)) {
				out.write(bytes);
				out.getFD().sync();
			} catch (IOException e) {
				throw new EngineException("Cannot write model " + name, e);
			}
			if (target.exists() && !target.delete() || !tmp.renameTo(target)) {
				throw new EngineException("Cannot install model " + name, null);
			}
		}
		// The library loads every *.onnx it finds: nothing the manifest does not list may stay.
		File[] present = dir.listFiles();
		if (present != null) {
			for (File f : present) {
				if (!expected.contains(f.getName()) && !f.delete()) {
					throw new EngineException("Unexpected file in the model directory", null, true);
				}
			}
		}
		return dir;
	}

	private static String hashOf(File f) {
		try {
			return CanonicalJson.sha256Hex(Files.readAllBytes(f.toPath()));
		} catch (IOException e) {
			return "";
		}
	}
}
