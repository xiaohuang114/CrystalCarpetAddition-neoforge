import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import java.util.Locale;

/** Throwaway check of the predicate parser copied verbatim from CCAUtils#matches. */
public class VersionCheck {
    private static boolean matches(String actualVersion, String predicate) throws Exception {
        String spec = predicate == null ? "" : predicate.trim();
        if (spec.isEmpty() || "*".equals(spec)) {
            return true;
        }
        DefaultArtifactVersion actual = new DefaultArtifactVersion(actualVersion);
        if (spec.startsWith("[") || spec.startsWith("(")) {
            return VersionRange.createFromVersionSpec(spec).containsVersion(actual);
        }
        String op = "";
        String rest = spec;
        for (String candidate : new String[]{">=", "<=", "!=", "==", ">", "<", "="}) {
            if (spec.startsWith(candidate)) {
                op = candidate;
                rest = spec.substring(candidate.length()).trim();
                break;
            }
        }
        if (rest.isEmpty()) {
            return true;
        }
        String lower = rest.toLowerCase(Locale.ROOT);
        if (lower.indexOf('x') >= 0 || rest.indexOf('*') >= 0) {
            String prefix = rest.replaceAll("[xX*].*$", "");
            return actualVersion.startsWith(prefix);
        }
        int cmp = actual.compareTo(new DefaultArtifactVersion(rest));
        return switch (op) {
            case ">=" -> cmp >= 0;
            case ">" -> cmp > 0;
            case "<=" -> cmp <= 0;
            case "<" -> cmp < 0;
            case "!=" -> cmp != 0;
            default -> cmp == 0;
        };
    }

    public static void main(String[] args) throws Exception {
        // reference: plain Maven semantics for comparison
        System.out.println("maven-ish raw >=1.21.4 on 1.21.1 -> "
                + VersionRange.createFromVersionSpec(">=1.21.4").containsVersion(new DefaultArtifactVersion("1.21.1")));
        String[][] cases = {
                {"1.21.1", ">=1.21", "true"},
                {"1.21.1", ">=1.20.5", "true"},
                {"1.21.1", ">=1.21.4", "false"},
                {"1.21.1", "<1.21.4", "true"},
                {"1.21.1", "*", "true"},
                {"1.21.1", "", "true"},
                {"1.21.1", ">=1.21.1", "true"},
                {"1.21.1", "[1.21,1.21.1]", "true"},
                {"1.21.1", "[1.21.4,)", "false"},
                {"1.4.147+v1.0.8-251027", ">=1.4.0", "true"},
                {"1.4.147+v1.0.8-251027", ">=1.21", "false"},
                {"1.11.1-neoforge", ">=1.11", "true"},
                {"1.21.1", "1.21.x", "true"},
        };
        int fail = 0;
        for (String[] c : cases) {
            boolean got = matches(c[0], c[1]);
            boolean want = Boolean.parseBoolean(c[2]);
            if (got != want) fail++;
            System.out.printf("%-22s %-18s got=%-5s want=%-5s %s%n", c[0], c[1], got, want, got == want ? "OK" : "MISMATCH");
        }
        System.out.println("FAILURES=" + fail);
    }
}
