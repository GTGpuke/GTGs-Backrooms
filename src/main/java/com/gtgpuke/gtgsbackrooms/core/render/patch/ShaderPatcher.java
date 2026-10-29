package com.gtgpuke.gtgsbackrooms.core.render.patch;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

/**
 * Patche un shader vanilla par enveloppe de {@code main} : sa {@code main} est renommee {@value #VANILLA_MAIN}, et une
 * nouvelle {@code main} l'appelle, entouree du code des injections ({@link InjectionPoint}). Un {@code return} anticipe
 * sort de la main vanilla et passe quand meme par la suite ; un {@code discard} garde son effet. Le code vanilla n'est
 * jamais modifie ligne a ligne.
 *
 * <p>La source arrive deja debarrassee de ses {@code #moj_import} (les bibliotheques y sont inserees, leur
 * {@code #version} mis en commentaire, des directives {@code #line} posees) : c'est la source compilee qui est
 * analysee. Les commentaires sont rendus blancs pour l'analyse, sans deplacer un seul caractere, et l'ecriture se fait
 * sur la source d'origine. Une source qui ne suit pas la signature attendue n'est pas patchee : exactement une directive
 * {@code #version 150}, exactement une {@code void main()}, aucune directive conditionnelle, aucune macro dans la
 * declaration de la sortie ou la signature de {@code main}, aucune directive inconnue, et pour un fragment shader
 * exactement une sortie {@code out vec4}. Jamais de patch "au mieux".
 */
public final class ShaderPatcher {

    /** Nom que prend la {@code main} vanilla une fois enveloppee ; sa presence dit qu'une source est deja patchee. */
    public static final String VANILLA_MAIN = "gtgs_vanilla_main";
    /** Seule version GLSL acceptee : celle des shaders vanilla, tenue par tous les pilotes vises. */
    public static final int REQUIRED_VERSION = 150;
    /** Refus : pas exactement une directive {@code #version 150}. */
    public static final String REFUSAL_VERSION = "version";
    /** Refus : directive conditionnelle, directive inconnue, ou macro dans la sortie ou la signature de {@code main}. */
    public static final String REFUSAL_DIRECTIVES = "directives";
    /** Refus : pas exactement une {@code void main()}. */
    public static final String REFUSAL_MAIN = "main";
    /** Refus : fragment shader sans exactement une sortie {@code out vec4}. */
    public static final String REFUSAL_OUTPUT = "output";
    /**
     * Dans le code d'une injection, remplace par l'expression de la position du sommet : {@code Position + ChunkOffset}
     * dans un shader de terrain (sommets relatifs a leur section), {@code Position} ailleurs.
     */
    public static final String POSITION_TOKEN = "${position}";
    /** Remplace par les coordonnees de texture du sommet ({@code UV0}), ou {@code vec2(-1.0)} si le shader n'en a pas. */
    public static final String UV_TOKEN = "${uv}";
    /** Remplace par la normale du sommet ({@code Normal}), ou {@code vec3(0.0)} si le shader n'en a pas. */
    public static final String NORMAL_TOKEN = "${normal}";
    /** Remplace par {@code 1.0} dans un shader de terrain (qui declare {@code ChunkOffset}), {@code 0.0} ailleurs. */
    public static final String TERRAIN_TOKEN = "${terrain}";
    private static final Pattern CHUNK_OFFSET = Pattern.compile("\\buniform\\s+vec3\\s+ChunkOffset\\s*;");
    private static final Pattern UV0 = Pattern.compile("\\bin\\s+vec2\\s+UV0\\s*;");
    private static final Pattern NORMAL = Pattern.compile("\\bin\\s+vec3\\s+Normal\\s*;");

    private static final Pattern VERSION = Pattern.compile("^[ \\t]*#[ \\t]*version[ \\t]+(\\d+)", Pattern.MULTILINE);
    private static final Pattern DIRECTIVE = Pattern.compile("^[ \\t]*#[ \\t]*(\\w+)", Pattern.MULTILINE);
    private static final Pattern DEFINE = Pattern.compile("^[ \\t]*#[ \\t]*define[ \\t]+(\\w+)", Pattern.MULTILINE);
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)\\s*\\{");
    private static final Pattern OUTPUT = Pattern.compile("(?:layout\\s*\\([^)]*\\)\\s*)?\\bout\\s+vec4\\s+(\\w+)\\s*;");
    private static final Pattern DISCARD = Pattern.compile("\\bdiscard\\b");
    private static final Set<String> CONDITIONAL = Set.of("if", "ifdef", "ifndef", "elif", "else", "endif");
    private static final Set<String> HARMLESS = Set.of("version", "line", "define");
    private static final String INDENT = "    ";

    /**
     * Ce que l'analyse d'une source compilee releve.
     *
     * @param version version GLSL declaree, 0 si absente ou declaree plusieurs fois
     * @param mains nombre de {@code void main()}
     * @param outputs noms des sorties {@code out vec4}, dans l'ordre
     * @param discard {@code true} si la source peut rejeter un fragment
     * @param directives directives autres que {@code #version} et {@code #line}, dans l'ordre, repetitions comprises
     * @param macros noms des macros definies par {@code #define}
     * @param macroUsed {@code true} si une macro apparait dans la declaration d'une sortie ou la signature de {@code main}
     * @param unknownDirective {@code true} si une directive n'est ni conditionnelle, ni {@code #define}
     */
    public record Analysis(int version, int mains, List<String> outputs, boolean discard, List<String> directives, List<String> macros,
            boolean macroUsed, boolean unknownDirective) {

        /** {@code true} si une directive conditionnelle est presente. */
        public boolean conditional() {
            for (int i = 0; i < this.directives.size(); i++) {
                if (CONDITIONAL.contains(this.directives.get(i))) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Resultat d'un patch : la source patchee, ou la source recue (meme instance) et la raison du refus.
     *
     * @param source source patchee, ou source d'origine
     * @param refusal raison du refus ({@link #REFUSAL_VERSION}...), {@code null} si la source a ete patchee ou l'etait deja
     */
    public record Result(String source, @Nullable String refusal) {
    }

    private ShaderPatcher() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Analyse une source compilee (commentaires ignores). */
    public static Analysis analyze(String source) {
        String blank = blankComments(source);
        int version = 0;
        Matcher versions = VERSION.matcher(blank);
        int versionCount = 0;
        while (versions.find()) {
            versionCount++;
            version = Integer.parseInt(versions.group(1));
        }
        if (versionCount != 1) {
            version = 0;
        }
        List<String> directives = new ArrayList<>();
        boolean unknown = false;
        Matcher directive = DIRECTIVE.matcher(blank);
        while (directive.find()) {
            String name = directive.group(1);
            if (name.equals("version") || name.equals("line")) {
                continue;
            }
            directives.add(name);
            if (!CONDITIONAL.contains(name) && !HARMLESS.contains(name)) {
                unknown = true;
            }
        }
        List<String> macros = new ArrayList<>();
        Matcher define = DEFINE.matcher(blank);
        while (define.find()) {
            macros.add(define.group(1));
        }
        List<String> outputs = new ArrayList<>();
        List<String> signatureLines = new ArrayList<>();
        Matcher output = OUTPUT.matcher(blank);
        while (output.find()) {
            outputs.add(output.group(1));
            signatureLines.add(lineAt(blank, output.start()));
        }
        int mains = 0;
        Matcher main = MAIN.matcher(blank);
        while (main.find()) {
            mains++;
            signatureLines.add(lineAt(blank, main.start()));
        }
        boolean macroUsed = false;
        for (int i = 0; i < macros.size() && !macroUsed; i++) {
            Pattern use = Pattern.compile("\\b" + Pattern.quote(macros.get(i)) + "\\b");
            for (int j = 0; j < signatureLines.size(); j++) {
                if (use.matcher(signatureLines.get(j)).find()) {
                    macroUsed = true;
                    break;
                }
            }
        }
        return new Analysis(version, mains, List.copyOf(outputs), DISCARD.matcher(blank).find(), List.copyOf(directives), List.copyOf(macros),
                macroUsed, unknown);
    }

    /**
     * Raison pour laquelle une source analysee n'est pas patchable, ou {@code null} si elle l'est.
     *
     * @param kind etage du shader
     * @param analysis son analyse
     * @return {@link #REFUSAL_VERSION}, {@link #REFUSAL_DIRECTIVES}, {@link #REFUSAL_MAIN}, {@link #REFUSAL_OUTPUT}, ou {@code null}
     */
    public static @Nullable String refusal(ShaderKind kind, Analysis analysis) {
        if (analysis.version() != REQUIRED_VERSION) {
            return REFUSAL_VERSION;
        }
        if (analysis.conditional() || analysis.unknownDirective() || analysis.macroUsed()) {
            return REFUSAL_DIRECTIVES;
        }
        if (analysis.mains() != 1) {
            return REFUSAL_MAIN;
        }
        if (kind == ShaderKind.FRAGMENT && analysis.outputs().size() != 1) {
            return REFUSAL_OUTPUT;
        }
        return null;
    }

    /**
     * Patche une source compilee.
     *
     * @param kind etage du shader
     * @param source source compilee, {@code #moj_import} deja resolus
     * @param includes bibliotheques GLSL a inserer en tete, dans l'ordre
     * @param injections code a inserer, dans l'ordre
     * @return la source patchee ; ou la source recue (meme instance) si elle l'etait deja (sans refus), ou refusee (avec sa raison)
     */
    public static Result apply(ShaderKind kind, String source, List<String> includes, List<ShaderPatch.Injection> injections) {
        if (source.contains(VANILLA_MAIN)) {
            return new Result(source, null);
        }
        Analysis analysis = analyze(source);
        String refusal = refusal(kind, analysis);
        if (refusal != null) {
            return new Result(source, refusal);
        }
        String blank = blankComments(source);
        Matcher version = VERSION.matcher(blank);
        version.find();
        Matcher main = MAIN.matcher(blank);
        main.find();
        // Le code ajoute en tete suit la ligne de la version, qui precede main : sinon la source n'est pas un shader valide.
        int versionLineEnd = blank.indexOf('\n', version.end());
        if (versionLineEnd < 0 || versionLineEnd > main.start()) {
            return new Result(source, REFUSAL_VERSION);
        }
        int headAt = versionLineEnd + 1;
        String output = kind == ShaderKind.FRAGMENT ? analysis.outputs().get(0) : null;
        boolean terrain = CHUNK_OFFSET.matcher(blank).find();
        Tokens tokens = new Tokens(terrain ? "Position + ChunkOffset" : "Position", UV0.matcher(blank).find() ? "UV0" : "vec2(-1.0)",
                NORMAL.matcher(blank).find() ? "Normal" : "vec3(0.0)", terrain ? "1.0" : "0.0");

        StringBuilder patched = new StringBuilder(source.length() + 1024);
        patched.append(source, 0, headAt);
        for (int i = 0; i < includes.size(); i++) {
            appendLine(patched, includes.get(i));
        }
        appendInjections(patched, injections, InjectionPoint.HEAD, "", tokens);
        patched.append(source, headAt, main.start());
        patched.append("void ").append(VANILLA_MAIN).append("() {");
        patched.append(source, main.end(), source.length());
        appendLine(patched, "");
        patched.append("void main() {\n");
        appendInjections(patched, injections, InjectionPoint.MAIN_BEFORE, INDENT, tokens);
        patched.append(INDENT).append(VANILLA_MAIN).append("();\n");
        appendInjections(patched, injections, InjectionPoint.MAIN_AFTER, INDENT, tokens);
        for (int i = 0; i < injections.size(); i++) {
            ShaderPatch.Injection injection = injections.get(i);
            if (injection.point() == InjectionPoint.OUTPUT) {
                patched.append(INDENT).append(output).append(" = ").append(injection.code()).append('(').append(output).append(");\n");
            }
        }
        patched.append("}\n");
        return new Result(patched.toString(), null);
    }

    /** Les expressions que les jetons d'une injection prennent dans un shader donne. */
    private record Tokens(String position, String uv, String normal, String terrain) {

        private String apply(String code) {
            return code.replace(POSITION_TOKEN, this.position).replace(UV_TOKEN, this.uv).replace(NORMAL_TOKEN, this.normal)
                    .replace(TERRAIN_TOKEN, this.terrain);
        }
    }

    private static void appendInjections(StringBuilder out, List<ShaderPatch.Injection> injections, InjectionPoint point, String indent,
            Tokens tokens) {
        for (int i = 0; i < injections.size(); i++) {
            ShaderPatch.Injection injection = injections.get(i);
            if (injection.point() == point) {
                out.append(indent);
                appendLine(out, tokens.apply(injection.code()));
            }
        }
    }

    private static void appendLine(StringBuilder out, String text) {
        out.append(text);
        if (!text.endsWith("\n")) {
            out.append('\n');
        }
    }

    private static String lineAt(String text, int index) {
        int start = text.lastIndexOf('\n', index) + 1;
        int end = text.indexOf('\n', index);
        return text.substring(start, end < 0 ? text.length() : end);
    }

    /** Copie de la source ou chaque commentaire est remplace par des blancs, sauts de ligne gardes : memes positions. */
    static String blankComments(String source) {
        char[] chars = source.toCharArray();
        int i = 0;
        while (i < chars.length - 1) {
            if (chars[i] == '/' && chars[i + 1] == '/') {
                while (i < chars.length && chars[i] != '\n') {
                    chars[i++] = ' ';
                }
            } else if (chars[i] == '/' && chars[i + 1] == '*') {
                chars[i] = ' ';
                chars[i + 1] = ' ';
                i += 2;
                while (i < chars.length && !(chars[i] == '*' && i + 1 < chars.length && chars[i + 1] == '/')) {
                    if (chars[i] != '\n') {
                        chars[i] = ' ';
                    }
                    i++;
                }
                if (i < chars.length) {
                    chars[i] = ' ';
                    chars[i + 1] = ' ';
                    i += 2;
                }
            } else {
                i++;
            }
        }
        return new String(chars);
    }
}
