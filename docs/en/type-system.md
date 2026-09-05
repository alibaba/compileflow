# Process Data Types and Conversion

Process definitions declare Java types for variables and action parameters. CompileFlow validates those declarations
before generating source code and performs a small, deterministic set of runtime conversions. Invalid or ambiguous
values fail; the engine does not silently truncate, guess a locale, or substitute a default.

## Type declarations

A declaration is a case-sensitive Java type name with optional generic arguments and array suffixes:

```text
java.lang.String
int[]
java.util.List<java.lang.String>
java.util.List<int[]>
java.util.Map.Entry<java.lang.String, java.lang.Integer>
java.util.Map<java.lang.String, java.util.List<java.lang.Integer[]>>[][]
```

Use fully qualified names outside `java.lang`. `String`, the primitive names, and the standard `java.lang` wrapper
simple names are recognized as built-ins. Every referenced generic type must be visible to the compilation ClassLoader,
and the number of generic arguments must match the declared class. Malformed suffixes, missing classes, primitive scalar
generic arguments (arrays are reference types), case mismatches, and arrays deeper than the JVM limit fail preflight.
Member classes use canonical names in definitions and generated source (for example,
`java.util.Map.Entry`); resolution converts them to JVM binary names only at the class-loading boundary.

Java erases generic arguments at runtime. CompileFlow retains them in declarations and generated source so `javac`
can check statically assignable paths. Values entering through dynamic boundaries such as `Map<String, Object>`,
scripts, or child-process results can only be checked by raw class. The engine does not implicitly traverse, coerce, or
inspect collection elements; applications remain responsible for the contents of `List<T>` and `Map<K,V>`.

## Runtime conversion

`null` remains `null`, and a value already assignable to the target type is returned unchanged. Other supported
conversions are:

| Target                                               | Accepted source                                                 | Rule                                                                                                                        |
|------------------------------------------------------|-----------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| Integral numbers and `BigInteger`                    | `Number` or decimal text                                        | Conversion must be exact and in range; fractional values, overflow, separators, and non-finite numbers fail                 |
| `float` / `double`                                   | `Number` or decimal text                                        | Result must be finite                                                                                                       |
| `BigDecimal`                                         | `Number` or decimal text                                        | Java locale-independent decimal syntax only                                                                                 |
| `boolean`                                            | `Boolean` or `true` / `false` text                              | No numeric, yes/no, or on/off guessing                                                                                      |
| `char`                                               | `Character` or one UTF-16 code unit                             | Longer and empty text fail                                                                                                  |
| `String`                                             | Any non-null object                                             | Uses the object's string representation                                                                                     |
| `LocalDate`, `LocalTime`, `LocalDateTime`, `Instant` | Matching JDBC temporal value where applicable, or ISO-8601 text | Strict ISO parsing; invalid calendar values fail                                                                            |
| JDBC `Date`, `Time`, `Timestamp`                     | Matching `java.time` value or strict ISO text                   | `Time` rejects fractional seconds it cannot preserve; `Timestamp` accepts local date-time or an absolute instant            |
| `java.util.Date`                                     | `Instant` or ISO instant text                                   | A timezone/offset and millisecond precision are mandatory; local date-time text and lossy nanosecond precision are rejected |

Conversions never use the host locale or default timezone. Conversion errors identify source and target types but do not
echo runtime values, which may contain credentials or personal data.

## Default values

Variable defaults are validated while Java source is generated:

- Primitive, wrapper, string, `BigInteger`, `BigDecimal`, `java.time`, and JDBC temporal literals use strict parsing.
- Direct `DataTypes` use produces a Java zero value for a missing primitive default. Primitive aliases in process
  variable declarations are normalized to wrappers, so process state remains nullable and its missing default is
  `null`.
- A missing input-map key preserves the declared default. A present key overrides it; an explicit `null` therefore
  remains distinct from an absent input.
- An empty `String` is a real empty-string default. Blank non-string defaults are treated as absent.
- Every default is a data literal. `@` has no special meaning, so a `String` default such as `@name` preserves those
  characters while the same text fails validation for an incompatible numeric or temporal type.
- Unsupported object defaults fail preflight. Supply those values through the input context instead.

Prefer `java.time` types for new definitions. Use legacy `java.util.Date` and `java.sql` types only at integration
boundaries where their semantics are required.
