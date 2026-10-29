# JSP Community Support (IntelliJ IDEA Community plugin)

Plugin ID / package: `com.rajendarreddyj.intellij.jsp`

Repository: [rajendarreddyj/rj-dev-intellj-jsp](https://github.com/rajendarreddyj/rj-dev-intellj-jsp)

Standalone IntelliJ IDEA **Community** plugin adding JSP/JSPX editing support and Tomcat (Jasper) JSP breakpoint debugging. It does not depend on IntelliJ Ultimate or on SmartTomcat. SmartTomcat (or any Tomcat launched with JDWP) handles deployment, and this plugin maps JSP breakpoints through JSR-45 SMAP data.

## Features

- File type for `.jsp`, `.jspf`, and `.jspx`, with a lexer, PSI, and syntax highlighting for directives, actions, scriptlets, declarations, expressions, EL, and template text. `.jspx` overrides Community's default XML association. XML-form `<jsp:scriptlet>`, `<jsp:declaration>`, and `<jsp:expression>` get Java injection.
- Styling: directive/action/scriptlet/EL delimiters, tag names, attribute names and values each have their own color (customizable under Settings | Editor | Color Scheme | JSP). Template text is highlighted as HTML, including embedded CSS and JavaScript (when the JavaScript plugin is present); `<script>` state persists across scriptlets.
- JSP line breakpoints can be set in `.jsp`/`.jspf`/`.jspx` files.
- Completion: directive names, standard `jsp:` actions, tags from declared tag libraries (TLD lookup, javax and jakarta URIs), EL implicit objects, `jsp:useBean` properties in EL, and Java members inside scriptlets (Java language injection).
- Java in scriptlets is modelled like Jasper's generated servlet, with one injected Java file per page, so resolve, completion, and navigation see:
  - `<%@ page import %>` and the default `servlet`, `servlet.http`, and `servlet.jsp` imports (jakarta when it is on the classpath, otherwise javax).
  - Implicit objects: `request`, `response`, `session`, `out`, `application`, `config`, `pageContext`, `page`, `exception`.
  - `<%! %>` declarations as class members.
  - Locals across all scriptlets of the page, including blocks split across scriptlets such as `<% if (x) { %> … <% } %>`.
  - Code, declarations, and imports from statically included files (`<%@ include file %>`, `<jsp:directive.include>`), inlined recursively at the include point.
  - When a `.jspf` fragment is edited on its own, the context of the first page (by path) that statically includes it, up to the include point.
- References: static/`jsp:include` paths (kept inside the webapp root), taglib URIs to TLD files, EL `useBean` names and bean properties to getters.
- Annotator: undeclared tag prefixes, TLD attribute validation, and unresolved `page import` entries (with a quick fix).
- Rename and Find Usages for included files; EL variables from `c:set`, `c:forEach`, `setAttribute` and tag-file attributes, with property-chain resolution and completion (0.4.0).
- Debugger Evaluate/Watch in JSP context, and SmartTomcat work-dir/docBase integration (0.4.0).
- Debugger: JSR-45 position manager for Jasper-generated classes (`org.apache.jsp`, stratum `JSP`). Source roots are discovered from project webapp roots (`src/main/webapp`, `WebContent`, `web`, ...) plus optional external mappings.

## Requirements

- IntelliJ IDEA 2024.2 or newer (`since-build 242`, no upper bound), including the unified IntelliJ IDEA 2025.3+ distribution. Requires the bundled Java plugin.
- Gradle provisions the build JDK automatically (Foojay toolchain, default JDK 21). Output is Java 17 bytecode, so the plugin also loads on 2024.2.

| IDE | Build | Plugin Verifier | Tests |
|-----|-------|-----------------|-------|
| IntelliJ IDEA Community 2024.2.6 | 242 | Compatible | CI |
| IntelliJ IDEA 2025.3 | 253 | Compatible (4 deprecated API usages) | CI |
| IntelliJ IDEA 2026.2.3 | 262 | Compatible (legacy API notes required for 242 support) | Local run: 76 pass, 1 skipped |
| Latest stable IDEA | Resolved at runtime | Plugin Verifier targets above are fixed | CI on every run |

CI tests the 2024.2.6 baseline, IDEA 2025.3, and the latest stable IDEA version returned by JetBrains on every run. Plugin verification remains pinned to the IDE builds above.

## Build and install

```powershell
.\gradlew.bat test verifyPlugin buildPlugin
```

`verifyPlugin` checks all three IDEs above. Install `build\distributions\intellij-jsp-<version>.zip` with **Settings | Plugins | ⚙ | Install Plugin from Disk...**. Use `.\gradlew.bat runIde` to try it in a sandbox IDE.

To compile, test, or run against the latest IDE (2026.x needs JDK 25):

```powershell
.\gradlew.bat test runIde '-PplatformVersion=2026.2.3' '-PjavaToolchain=25' '-PuseInstaller=false'
```

The defaults are `platformType=IntellijIdeaCommunity`, `platformVersion=2024.2.6`, `javaToolchain=21`, and `useInstaller=true`. Community builds from 2025.3 onward are Maven-only, so they need `useInstaller=false`.

## Supported Tomcat versions

| Tomcat | Spec | Taglib URIs | Status |
|--------|------|-------------|--------|
| 9.0 | JSP 2.3 (`javax.*`) | `http://java.sun.com/jsp/jstl/*` | Supported |
| 10.1 | Jakarta Pages 3.1 (`jakarta.*`) | `jakarta.tags.*` | Supported |
| 11.0 | Jakarta Pages 4.0 (`jakarta.*`) | `jakarta.tags.*` | Supported |

All three use Jasper with the JSR-45 `JSP` stratum and the same debug defaults. Debugging does not depend on the Servlet API namespace. TLDs are found in project sources and in library jars (for example JSTL 3.0 `META-INF/*.tld`).

## Debugging JSPs with SmartTomcat

1. Start the application with the SmartTomcat run configuration in **Debug** mode.
2. Set breakpoints directly in `.jsp` files and request the page. The debugger stops on the JSP line, and stack frames navigate to the JSP source.
3. If your JSPs live outside a detected webapp root, add a mapping in **Settings | JSP Source Roots**. Each mapping has:
   - **SMAP prefix**: the webapp-relative path prefix as Jasper records it (for example `/WEB-INF/views`, or empty for the root).
   - **Local source root**: the local directory that holds those files.

   A breakpoint is bound only when exactly one local file matches. Ambiguous matches are ignored.
4. If Jasper's `generatedJspPackageName` init-param is customised (default `org.apache.jsp`), enter the same value in **Generated JSP package** on that settings page.

## Jasper debugger prerequisites

- Keep Jasper SMAP generation enabled (`suppressSmap=false`, the Tomcat default).
- Keep class debug info enabled (`classdebuginfo=true`, the Tomcat default; `-g` for javac).
- `development=true` (the default) lets Jasper recompile changed JSPs while you debug.
- If the generated `org.apache.jsp` classes lack JSR-45 data, the plugin prints one message per debug session to the debugger console naming these settings.

## Limitations

- Only Jasper-generated classes are mapped (`org.apache.jsp` or the configured package). Tag files (`.tag`/`.tagx`) are not mapped.
- `.jspx` routing uses IntelliJ's `FileTypeOverrider`, which the Plugin Verifier marks as experimental API.
- No formatter, or Ultimate-level framework/EL type inference.
- IntelliJ does not report Java *errors* (such as "Cannot resolve symbol") inside injected fragments, so unresolved scriptlet code is not flagged. Resolve, completion, and navigation do work.
- Not modelled: `<include-prelude>`/`<include-coda>` from `web.xml`, and dynamic `<jsp:include>` (a runtime request, not shared code).
- IntelliJ IDEA Ultimate ships its own JSP support with the same language ID (`JSP`), so this plugin declares `<incompatible-with>com.intellij.modules.ultimate</incompatible-with>` and is disabled in Ultimate (installing it there would otherwise fail with "Language with ID 'JSP' is already registered"). Use Ultimate's built-in JSP support and web facet configuration there.

- Folding, structure view, .tag/.tagx support, and an *Add taglib directive* quick fix (0.2.0).
