# Release notes

Newest first. Plugin ID `com.rajendarreddyj.intellij.jsp`; supports IntelliJ IDEA 2024.2 and newer (`sinceBuild` 242, no `untilBuild`).

## 0.4.5 (2026-10-29)

- Reduced logging: routine position-manager traces (start banner, waiting/mapped/loaded lines) are now debug-level in idea.log and no longer printed to the debug console. Set `-Djsp.community.debug.trace=true` in IDE VM options to print them to the console again. Actionable warnings (unmapped page, missing JSR-45 metadata) are still shown.

## 0.4.4 (2026-10-29)

- Fixed JSP breakpoints never reaching the debugger: breakpoints of the JSP type had no Java debugger handler (`debugger.javaBreakpointHandlerFactory`), so only legacy `java-line` breakpoints were installed.

## 0.4.3 (2026-10-28)

- Fixed JSP breakpoints not stopping when the project also contains a packaged copy of the web app (for example `target/<war>`): every page matched two roots and was treated as ambiguous, so no generated page could be mapped to its JSP. Build-output roots are now ignored when resolving debugger sources.

## 0.4.2 (2026-10-28)

- Debugger diagnostics: the position manager now prints (and logs to idea.log) a start banner with the generated package and web roots, the classes each breakpoint waits for, which generated class frames were mapped to a JSP, and why a frame could not be mapped.

## 0.4.1 (2026-10-28)

- Performance: web-root discovery (used by every include/taglib path resolution) is now cached, and SmartTomcat run-configuration reads are throttled to once per few seconds. Previously each resolution re-read run configurations and hit the file system, which could freeze the editor (the Ctrl+D thread-dump hint).

## 0.4.0 (2026-10-28)

- Rename and Find Usages for included files: renaming a `.jsp`/`.jspf` updates `<%@ include file=%>` and `<jsp:include page=%>`; Find Usages lists them.
- Warning for unresolved `page import` entries, with a "Remove unresolved import" quick fix.
- Fragments with several includers now resolve against the nearest includer (shared path, non-`.jspf` preferred).
- EL resolution beyond `useBean`: `c:set`, `c:forEach`/`c:forTokens` (`var`, `varStatus`, typed from `items`), `setAttribute("x", new T())`, tag-file `attribute`; property chains (`${a.b.c}`) resolve and complete through getters.
- Debugger Evaluate/Watch in JSP context: implicit objects, imports and scriptlet locals are in scope.
- SmartTomcat integration: docBase added as a JSP source root; Tomcat work dir read from the run configuration, and the debug console reports the compiled class path and warns when it is older than the JSP.
## 0.3.0 (2026-10-28)

- Tag attribute completion inside custom tags, driven by the TLD (`<attribute>` names, already-used attributes are hidden).
- Tag attribute validation against the TLD: warning for unknown attributes (unless the tag allows dynamic attributes) and error for missing required attributes.
- Test coverage added for attribute validation.

## 0.2.0 (2026-10-28)

- Undeclared tag-prefix check now works on markup regions only (no false positives in comments or scriptlets) and recognises prefixes declared via statically included files, the including page (for `.jspf` fragments), `tagdir` and `xmlns:`.
- Quick fix: "Add taglib directive" when a TLD with the prefix's short name exists.
- Web-root discovery unified between the debugger and editor features; any folder containing `WEB-INF` (outside `target`, `build`, `out`, `node_modules`) is a web root; Settings mappings are honoured for includes and taglibs.
- `.tag` / `.tagx` files recognised.
- Code folding (scriptlets, declarations, comments) and a structure view.
- `tag`, `attribute`, `variable` directive completion.
- Performance: cached regexes, per-file caches for taglibs and `useBean`, TLD index invalidated only by XML edits with a by-file lookup.
- Internal: removed duplicated path helpers, tidied the position manager.

## 0.1.1 (2026-10-28)

- Debug console diagnostics (`[JSP Community Support] ...`) showing when a JSP class loads for a breakpoint, when a page path cannot be mapped to a project JSP, and when no generated code maps to a line.
- Fixed a false "Missing JSR-45 debug metadata" report for nested classes generated from `<%! %>` declarations.
- Source lookup fallback by path suffix for non-conventional webapp roots (build output ignored, ambiguous matches rejected).
- Ctrl+Click now works on `<%@ include file %>` and `<%@ page import %>` (class references per import entry).
- Fuller plugin description and change notes.

## 0.1.0 (2026-10-27)

- Initial release: JSP/JSPF/JSPX file types, lexer, parser and syntax highlighting (directives, scriptlets, EL, JSP tags, embedded HTML and JavaScript), colour settings page.
- Java code insight in scriptlets via a Jasper-style translation (page imports, implicit objects, declarations, static includes, `.jspf` context).
- Completion for directives, actions, custom tags (TLD based), EL and scriptlet members; references for includes, taglibs and `useBean`.
- JSR-45 JSP line breakpoints for Tomcat/Jasper (SmartTomcat compatible; Tomcat 9, 10.1, 11), with configurable generated package and source-root mappings.
- Declared incompatible with IntelliJ IDEA Ultimate.
