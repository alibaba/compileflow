import type { components, operations } from './generated/workbenchServerOpenApi'

type Mutable<Value> = Value extends readonly (infer Item)[]
  ? Mutable<Item>[]
  : Value extends object
    ? { -readonly [Key in keyof Value]: Mutable<Value[Key]> }
    : Value

/** Mutable application view of one generated Workbench Server wire schema. */
export type ServerSchema<Name extends keyof components['schemas']> = Mutable<
  components['schemas'][Name]
>

/** Mutable query parameters generated for one Workbench Server operation. */
export type ServerOperationQuery<Name extends keyof operations> = Mutable<
  NonNullable<operations[Name]['parameters']['query']>
>

/** Generated wire schema with explicit domain refinements for broad OpenAPI fields. */
export type RefinedServerSchema<
  Name extends keyof components['schemas'],
  Refinements extends object,
> = Omit<ServerSchema<Name>, keyof Refinements> & Refinements
