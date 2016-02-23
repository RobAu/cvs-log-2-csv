import org.supercsv.io.CsvListWriter;
import org.supercsv.prefs.CsvPreference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Main {

	public final static String REVISION = "revision ";
	public final static String WORKING_FILE = "Working file: ";
	public final static String REVISION_DATE = "date: ";

	public static void main(String[] args) {
		ParseState state = ParseState.OUTSIDE;
		try (
				CsvListWriter writer = new CsvListWriter(new PrintWriter(System.out), CsvPreference.STANDARD_PREFERENCE);
				BufferedReader fr = new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), StandardCharsets.UTF_8))
		) {
			Revision.writeHeader(writer);
			String s;
			String filename = null;
			Revision r = null;
			while ((s = fr.readLine()) != null) {
				switch (state) {
					case OUTSIDE:
						if (s.startsWith("======")) {
							state = ParseState.INSIDE_SECTION;
						}
						break;
					case INSIDE_SECTION:
						if (s.startsWith("------")) {
							state = ParseState.INSIDE_REVISION;
							r = new Revision(filename);
						} else if (s.startsWith(WORKING_FILE)) {
							filename = s.substring(WORKING_FILE.length());
						}
						break;
					case INSIDE_REVISION:

						if (s.startsWith("======")) {
							r.done();
							r.write(writer);
							state = ParseState.INSIDE_SECTION;
						} else if (s.startsWith("------")) {
							r.done();
							r.write(writer);
							r = new Revision(filename);
						} else if (s.startsWith(REVISION)) {
							r.version = s.substring(REVISION.length());
						} else if (s.startsWith(REVISION_DATE)) {
							r.parseDateAuthorEntry(s);
						} else {
							r.appendMsg(s);
						}
						break;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	enum ParseState {OUTSIDE, INSIDE_SECTION, INSIDE_REVISION}

	static class Revision {

		static SimpleDateFormat sm = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		static SimpleDateFormat ALMOST_ISO8601 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		static Pattern p = Pattern.compile("((?<!([A-Z]{1,10})-?)[A-Z]+-\\d+)");
		public String version;
		String filename;
		Date d;
		String author;
		String msg;
		StringBuilder msgB = new StringBuilder();
		List<String> jiraKeys;

		public Revision(String filename) {
			this.filename = filename;
		}

		private static List<String> parseJiraKeys(String msg) {
			Matcher m = p.matcher(msg);
			List<String> keys = new ArrayList<>();
			while (m.find()) {
				for (int i = 1; i <= m.groupCount(); i++) {
					if (m.group(i) != null)
						keys.add(m.group(i));

				}

			}
			return keys;
		}

		public static void writeHeader(CsvListWriter writer) throws IOException {
			writer.write("filename", "version", "author", "date", "jirakeys", "msg");

		}

		public void parseDateAuthorEntry(String s) {

			//date: 2007/04/24 13:51:51;  author: raudenaerde;  state: Exp;  lines: +3 -0
			String[] entries = s.split(";");
			String date = entries[0].substring("date :".length());
			String author = entries[1].substring("  author:".length());
			try {
				this.d = sm.parse(date);
			} catch (ParseException e) {
				e.printStackTrace();
			}
			this.author = author;
		}

		public void appendMsg(String s) {
			msgB.append(s);
		}

		public void done() {
			msg = msgB.toString();
			if ("*** empty log message ***".equals(msg)) {
				msg = "";
			}
			jiraKeys = parseJiraKeys(msg);
		}

		@Override
		public String toString() {
			return "F:" + filename + "  R:" + version + ": " + msg + " Auth:" + author + " Date:" + d + " Jiras:" + jiraKeys;

		}

		public void write(CsvListWriter writer) throws IOException {
			writer.write(filename, version, author, ALMOST_ISO8601.format(d), String.join(" ", jiraKeys), msg);
		}

	}
}
