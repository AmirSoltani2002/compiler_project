# FunctionCraft Compiler Front End

A Java compiler front end for **FunctionCraft**, a small language with Ruby-like syntax. The project uses **ANTLR 4.13.1** to tokenize and parse source files, constructs an abstract syntax tree (AST), and performs visitor-based type analysis with symbol tables.

The current implementation reports semantic/type diagnostics. It does **not** execute FunctionCraft programs or generate machine code, bytecode, or another target language.

## How it works

```text
FunctionCraft source (.fl)
        |
        v
ANTLR lexer and parser
        |
        v
Abstract syntax tree
        |
        v
TypeChecker visitor + symbol tables
        |
        v
Diagnostics sorted by source line
```

- **Grammar and AST construction:** `FunctionCraft.g4` contains lexer/parser rules and Java actions that construct AST nodes with source-line information.
- **Type representation:** integer, float, boolean, string, list, function-pointer, and `NoType` classes.
- **Semantic analysis:** visitor methods analyze expressions, assignments, conditions, function calls, lists, indexing, and loop ranges. Symbol tables track variables, functions, and patterns.
- **Diagnostics:** dedicated error classes produce messages for incompatible operands, invalid conditions, non-iterable values, invalid indexes/ranges, and incorrect arguments to built-ins such as `len`, `chop`, and `chomp`.

The grammar also defines functions with optional/default arguments, lambdas, function pointers, pattern declarations, `if`/`else`, `for`, `loop do`, `break`, `next`, and list/string operations. Grammar support is broader than the completed semantic checks; see [Current limitations](#current-limitations).

## Requirements

- **JDK 21**: matches the IntelliJ project configuration and was used to verify the commands below. A JRE alone is insufficient because building requires `javac`.
- **ANTLR 4.13.1**: the complete tool/runtime JAR is already included at `utilities/antlr-4.13.1-complete.jar`.
- A POSIX-compatible shell for the command examples below, such as Bash on Linux/macOS or Git Bash on Windows.

No Maven or Gradle configuration is included. The generated Java lexer/parser sources are committed, so parser generation is optional for the initial build.

## Build and run

Run these commands from the repository root:

```bash
git clone https://github.com/AmirSoltani2002/compiler_project.git
cd compiler_project

mkdir -p build/classes
find src -name '*.java' -print > build/sources.txt
javac -cp utilities/antlr-4.13.1-complete.jar \
  -d build/classes @build/sources.txt

java -cp 'build/classes:utilities/antlr-4.13.1-complete.jar' \
  main.FunctionCraft samples/sample3.fl
```

The program accepts the source file as its first argument. Use a different `.fl` path to analyze your own input. On Windows, replace the `:` in the Java runtime classpath with `;`.

`samples/sample3.fl` deliberately contains type errors. Its current console output is:

```text
Line:10-> operands of operator PLUS must be the same
Line:11-> only lists can be iterated over
Line:14-> type of `chop` function argument must be string
Line:19-> types of begin and end of ranges can only be integer
Line:23-> types of append operands must be string or same as list's type
Line:24-> types of append operands must be string or same as list's type
```

To save diagnostics reliably, redirect standard output:

```bash
java -cp 'build/classes:utilities/antlr-4.13.1-complete.jar' \
  main.FunctionCraft samples/sample3.fl > build/diagnostics.txt
```

Run from the repository root: the entry point also opens the hard-coded relative path `samples/out11.txt` on each completed analysis. That file is overwritten, and its writer is currently not flushed or closed, so the file can remain empty. Console output is the reliable diagnostic output in this version. ANTLR syntax diagnostics are emitted separately on standard error.

## Language examples

### Input with no reported diagnostics

Create `build/example.fl`:

```ruby
def main()
    number = 42;
    message = "hello";
    trimmed = chop(message);
    size = len(message);
    doubled = number * 2;
end
```

Then analyze it:

```bash
java -cp 'build/classes:utilities/antlr-4.13.1-complete.jar' \
  main.FunctionCraft build/example.fl
```

This example produces no console diagnostics with the current implementation. It does not print values or execute the assignments.

### Type error

```ruby
def main()
    result = 1 + "hello";
end
```

The checker reports:

```text
Line:2-> operands of operator PLUS must be the same
```

Statements generally end with `;`, and declaration/control-flow bodies use `end`. See `samples/in.fl`, `samples/in2.fl`, and `samples/sample3.fl` for larger inputs; they include intentional error cases rather than only valid programs.

## Regenerate the ANTLR parser

After editing `src/main/grammar/FunctionCraft.g4`, regenerate the parser sources with the bundled ANTLR version:

```bash
java -jar utilities/antlr-4.13.1-complete.jar \
  -Dlanguage=Java \
  -visitor \
  -package parsers \
  -Xexact-output-dir \
  -o src/parsers \
  src/main/grammar/FunctionCraft.g4
```

Then rerun the build commands. This overwrites generated artifacts in `src/parsers`; edit the grammar rather than hand-editing generated parser code. Keep the generator and runtime at the same ANTLR version.

## Repository structure

```text
.
├── src/
│   ├── main/
│   │   ├── FunctionCraft.java       # CLI entry point
│   │   ├── grammar/                 # ANTLR grammar with AST-building actions
│   │   ├── ast/                     # AST nodes, operators, and type classes
│   │   ├── visitor/                 # Visitor interfaces and TypeChecker
│   │   ├── symbolTable/             # Symbol tables, stack, and symbol items
│   │   └── compileError/            # Diagnostic definitions
│   └── parsers/                     # Generated ANTLR Java sources and metadata
├── samples/                         # FunctionCraft inputs and existing output files
├── utilities/
│   └── antlr-4.13.1-complete.jar
├── .idea/                           # IntelliJ project configuration
├── part2.iml                        # IntelliJ module; src is the source root
└── out/                             # Committed compiled artifacts; not needed to rebuild
```

For IntelliJ IDEA, open the repository, select JDK 21, and ensure `src` is marked as the source root and the bundled ANTLR JAR is on the module classpath. Run `main.FunctionCraft` with `samples/sample3.fl` as the program argument and the repository root as the working directory.

## Current limitations

This is a compiler-analysis project with incomplete semantic coverage:

- Some visitor methods are placeholders, including `puts` and `push`; pattern return-type checking is incomplete. Not every grammar construct has complete type validation.
- Missing arguments or invalid file paths are not handled by a dedicated CLI usage/error layer.
- Reported type errors do not cause a non-zero process exit status. An exit code of `0` alone does not establish that an input passed analysis.
- The top-level grammar rule does not require end-of-file, so trailing input may go unchecked. Some malformed or unsupported inputs can also trigger exceptions.
- The hard-coded diagnostic output file has the writer-lifecycle limitation described above.

## Verification

The following were checked with OpenJDK 21 and the bundled ANTLR 4.13.1 JAR:

- Compilation from the committed Java sources, without relying on `out/`.
- Running all three supplied `.fl` samples.
- The no-diagnostic and type-error examples shown above.
- Regenerating the parser from the grammar into a separate build directory, compiling it with the handwritten sources, and reproducing the `sample3.fl` diagnostics.

The samples are manual fixtures; the repository currently has no automated test runner or CI configuration.
